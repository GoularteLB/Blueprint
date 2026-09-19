# Large-Scale Data Ingestion & Analytics

Importa CSVs grandes (testado com até 5 milhões de linhas, 300 MB) para o PostgreSQL usando Spring Boot, e mostra os dados num front React com dashboard e uma listagem virtualizada.

Os números abaixo foram medidos numa máquina Windows com Docker Desktop, backend com `-Xmx512m` e `mem_limit` de 768 MiB.

## 1. Como rodar

```
cp .env.example .env
docker compose up --build
```

Abra `http://localhost:3000`.

Para gerar um dataset (a seed é fixa, as datas saem em UTC e 0,5% das linhas são inválidas de propósito):

```
python scripts/generate_csv.py --rows 1000000 --out data/transactions.csv
```

Depois é só enviar o arquivo pela tela de Upload, ou por `curl -F "file=@data/transactions.csv" localhost:8080/api/imports`.

## 2. Arquitetura e stack

```
navegador -> nginx (front, :3000) -> backend Spring Boot (:8080) -> PostgreSQL 16
                                          |
                                          +-> volume uploads
```

- Back-end: Java 21, Spring Boot, Spring JDBC (sem JPA), Flyway, Commons CSV.
- Front-end: React 18, TypeScript, Vite, TanStack Query e Virtual, Zustand, Recharts, Tailwind.
- O back-end é um monólito modular, com um pacote por feature (`imports`, `transactions`, `analytics`, `infra`). O nginx serve o front e faz proxy de `/api`, então não há CORS.
- Padrões usados: Repository com JDBC explícito, Unit of Work (uma transação por unidade de commit), processamento em streaming com batches, job assíncrono com executor limitado, paginação keyset, uma summary table incremental para agregações, e no front server state (TanStack Query) separado de UI state (Zustand).

## 3. Ingestão

### 3.1 Memória

O upload é copiado direto para disco e o worker lê o arquivo em streaming, um batch por vez. Nunca há mais que `BATCH_SIZE x COMMIT_EVERY_N_BATCHES` linhas não comitadas em memória, mais um estado de agregação pequeno e a amostra de erros (20 itens).

Medido com o arquivo crescendo (a JVM é reiniciada a cada rodada; o heap é amostrado a cada segundo pelo Actuator):

| Linhas | CSV | Tempo | Pico de heap | Pico do container |
|---|---|---|---|---|
| 100 mil | 6 MB | 8,5 s | 45 MiB | 187 MiB |
| 500 mil | 30 MB | 49,8 s | 47 MiB | 199 MiB |
| 1 milhão | 60 MB | 63,3 s | 51 MiB | 205 MiB |
| 2 milhões | 120 MB | 141,9 s | 49 MiB | 209 MiB |
| 5 milhões | 300 MB | 563,1 s | 62 MiB | 249 MiB |

O arquivo cresceu 50 vezes e o heap ficou entre 45 e 62 MiB, longe do teto de 495 MiB da JVM. O container passou de no máximo 249 MiB dos 768 MiB. No 5M houve 1.017 pausas de GC, 9,2 s no total, cerca de 1,6% do tempo.

A vazão cai de ~15,8 mil linhas/s (1M) para ~8,9 mil (5M) com o heap estável. Suspeito do custo de manter os três índices com inserções em ordem aleatória, mas não isolei isso.

### 3.2 Batch e transação

`JdbcTemplate.batchUpdate` com `reWriteBatchedInserts=true`, então o driver manda INSERTs de várias linhas em vez de um round trip por linha. O tamanho do batch (`BATCH_SIZE`) e quantos batches entram em cada transação (`COMMIT_EVERY_N_BATCHES`) são parâmetros separados. `SYNCHRONOUS_COMMIT=false` liga `SET LOCAL synchronous_commit = off` só na sessão de ingestão.

Cada unidade de commit grava as linhas, atualiza a summary e o progresso do job na mesma transação. A última também grava `COMPLETED`.

Benchmark com 1M linhas, 3 rodadas por configuração, mediana em linhas/s:

| Batch | Linhas/s | Pico de heap |
|---|---|---|
| 1000 | 14.113 | 37 MiB |
| 2000 | 11.323 | 37 MiB |
| 5000 | 12.137 | 58 MiB |
| 10000 | 13.247 | 67 MiB |

| Commit a cada N batches | fsync síncrono | fsync desligado |
|---|---|---|
| 1 | 11.754 | 14.950 |
| 5 | 17.875 | 16.700 |
| 10 | 22.291 | 20.558 |

(a segunda tabela usa batch 1000)

A variação entre rodadas foi grande: a mesma configuração foi de 8,4 mil a 24,6 mil linhas/s. Por isso o tamanho do batch, de 1000 a 10000, não mostrou diferença de vazão além do ruído; o de 1000 ganhou por ter a melhor mediana e usar bem menos heap. O efeito que apareceu com clareza foi a transação mais longa: de 1 para 10 batches por commit a vazão quase dobrou. Desligar o fsync só ajudou com commit frequente (+27% no commit a cada batch) e deixou de ajudar com transações longas.

Valores adotados no `.env.example`: `BATCH_SIZE=1000`, `COMMIT_EVERY_N_BATCHES=10`, `SYNCHRONOUS_COMMIT=true`. Os defaults provisórios eram 5000, 2 e false.

### 3.3 Assíncrono

- `POST /api/imports` responde 202 e o processamento roda em `@Async`, num executor limitado (core 1, máximo 2, fila de 10).
- Um `Semaphore` (12 vagas) decide a admissão antes de gravar arquivo ou criar o job. Se estiver cheio, a resposta é 429 e nada precisa ser desfeito.
- O front consulta `GET /api/imports/{id}` a cada segundo. O progresso é `bytes_read / file_size_bytes`, sem estimativa de tempo restante.
- Jobs `PENDING` ou `RUNNING` que estavam ativos num restart viram `FAILED` ao subir. A importação não é retomada; o arquivo continua no volume e pode ser reenviado.
- `COMPLETED` sai no mesmo commit da última unidade, e `markFailed` só age sobre jobs `RUNNING`, então um import persistido nunca vira `FAILED`.

### 3.4 Erros por linha

A validação acontece antes do banco: `external_id` e `category` não vazios, `occurred_at` em ISO-8601 com offset obrigatório, `amount` decimal válido, colunas faltantes. O job guarda os contadores (`processed = success + error`) e até 20 erros de exemplo com linha, motivo e trecho da linha.

Um erro de banco no meio do lote faz rollback da unidade e o job fica `FAILED`. Não há fallback linha a linha, porque no PostgreSQL um erro SQL aborta a transação inteira.

### 3.5 Crash no meio da ingestão

Importei 5M linhas e dei `docker kill` no backend com o progresso em 50%. Com o backend parado, conferi o banco (`:job` é o id do job):

```sql
SELECT success_lines, (SELECT count(*) FROM transactions WHERE job_id = :job)
  FROM import_job WHERE id = :job;

SELECT (SELECT sum(tx_count) FROM category_month_summary WHERE job_id = :job),
       (SELECT count(*) FROM transactions WHERE job_id = :job);

SELECT (SELECT sum(total_amount) FROM category_month_summary WHERE job_id = :job),
       (SELECT sum(amount) FROM transactions WHERE job_id = :job);
```

| Verificação | Resultado |
|---|---|
| `success_lines` = linhas na tabela | 2.497.455 = 2.497.455 |
| soma da summary (contagem) = linhas na tabela | 2.497.455 = 2.497.455 |
| soma da summary (valor) = soma da tabela | 82.677.646,98 = 82.677.646,98 |
| `processed_lines` = `success_lines + error_lines` | 2.510.000 = 2.497.455 + 12.545 |

Logo após o kill o job continuava `RUNNING`; depois do restart passou a `FAILED` com a mensagem `Worker interrupted by application restart`.

## 4. Banco

### 4.1 Índices

| Índice | Atende |
|---|---|
| `(occurred_at DESC, id DESC)` | listagem sem filtro |
| `(category, occurred_at DESC, id DESC) INCLUDE (amount)` | listagem e agregação por categoria |
| `(job_id, occurred_at DESC, id DESC)` | listagem de um import |

O `INCLUDE (amount)` funciona como esperado: `SUM/COUNT` de uma categoria num mês (1M linhas) usa Index Only Scan com `Heap Fetches: 0` e leva 2,4 ms; no ano inteiro, 23 ms. Já o `GROUP BY category` de todas as categorias lê a tabela quase toda e o planner escolhe Parallel Seq Scan (136 ms em 1M).

Também testei um índice BRIN em `occurred_at`. O planner não chegou a usá-lo, porque as datas do dataset estão em ordem aleatória na tabela e o BRIN precisa de ordem física correlacionada. Sem ganho, removi.

### 4.2 Paginação keyset

O cursor é o par `(occurred_at, id)` do último item, em base64. O WHERE só inclui os filtros presentes, e a query busca `size + 1` linhas para saber se há mais páginas, sem `COUNT(*)`. Comparação com `OFFSET` usando `EXPLAIN (ANALYZE, BUFFERS)`, 1M linhas, sem filtro:

| Profundidade | OFFSET | Cursor |
|---|---|---|
| 0 | 0,24 ms, 54 buffers | 0,19 ms, 54 buffers |
| 10.000 | 12,8 ms, 10.096 buffers | 0,28 ms, 54 buffers |
| 500.000 | 461 ms, 502.355 buffers | 0,38 ms, 55 buffers |
| 950.000 | 860 ms, 954.565 buffers | 0,52 ms, 54 buffers |

Com filtro por categoria e por `jobId` o resultado tem o mesmo formato. Na maior profundidade testada, 128 ms de OFFSET contra 0,76 ms de cursor para a categoria, e 950 ms contra 0,51 ms para o `jobId`. Todos os planos foram Index Scan.

### 4.3 Agregação

Há dois caminhos. O direto faz `GROUP BY` na tabela e aceita qualquer intervalo. A summary (`category_month_summary`) é atualizada no mesmo commit de cada unidade de ingestão, mas só responde intervalos alinhados ao mês. Sem `source`, o back-end escolhe sozinho; `source=summary` com intervalo não alinhado devolve 400. O mês é sempre calculado em UTC.

Tempos com período de 12 meses, melhor de 3 execuções. Em todos os casos, inclusive por `jobId`, os dois caminhos devolveram valores idênticos.

| Endpoint | 1M direct | 1M summary | 5M direct | 5M summary |
|---|---|---|---|---|
| `/monthly` | 695 ms | 9,9 ms | 3.440 ms | 11,1 ms |
| `/categories` | 168 ms | 9,8 ms | 678 ms | 9,0 ms |
| `/overview` | 205 ms | 9,6 ms | 1.770 ms | 27,5 ms |

A summary não é uma otimização da query: é trabalho movido do momento da leitura para o da ingestão.

Latência da API com 1M linhas, 20 conexões por 30 s (gerador de carga em Python na mesma máquina, sem `hey`/`wrk`):

| Endpoint | Req/s | p50 | p95 | p99 |
|---|---|---|---|---|
| `/api/transactions?size=50` | 547 | 31,6 ms | 76,4 ms | 119,9 ms |
| `/api/transactions?size=50` com cursor a ~900 mil linhas | 947 | 14,4 ms | 46,4 ms | 161,9 ms |
| `/api/analytics/overview` | 1.980 | 9,1 ms | 19,4 ms | 28,0 ms |
| `/api/analytics/monthly` | 1.824 | 9,7 ms | 21,1 ms | 33,6 ms |

Não houve erros em nenhuma das rodadas.

## 5. Front-end

- Tudo que vem do servidor fica no TanStack Query. O Zustand guarda só o job ativo, os filtros do explorer e o tema.
- O upload mostra a barra de envio e depois o progresso do job (percentual por bytes, linhas, erros, linhas/s, tempo decorrido). Ao terminar, invalida os caches de analytics e transações.
- O dashboard tem quatro cards, o gráfico mensal e o de categorias, com seletor de meses.
- O explorer usa `useInfiniteQuery` com cursor e `useVirtualizer` (linha de 40 px, `overscan` 10). A linha é um `React.memo`.
- Com mais de 1M linhas no banco, contei `document.querySelectorAll('tbody tr')` no DevTools: 26 no topo da lista e entre 37 e 38 rolando, sem crescer.

## 6. Variáveis de ambiente e volumes

| Variável | Padrão | Uso |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `ingestion` | credenciais do Postgres |
| `BACKEND_MEM_LIMIT` | `768m` | limite de memória do container |
| `JVM_MAX_HEAP` | `512m` | `-Xmx` |
| `BATCH_SIZE` | `1000` | linhas por batch |
| `COMMIT_EVERY_N_BATCHES` | `10` | batches por transação |
| `SYNCHRONOUS_COMMIT` | `true` | `false` desliga o fsync síncrono na ingestão |

Volumes: `pgdata` (dados do Postgres) e `uploads` (arquivos enviados, em `/data/uploads`).

## 7. Trade-offs

- **Polling em vez de SSE:** menos código e menos pontos de falha, e o enunciado aceita.
- **JDBC em vez de JPA:** controle direto de batch, transação e memória.
- **`batchUpdate` em vez de `COPY`:** convive melhor com a rejeição de linhas individuais. Não implementei nem medi o `COPY`.
- **Sem broker, sem partições:** o requisito é assíncrono, não distribuído.
- **Sem retomada de import** e sem savepoints por linha: um erro de banco derruba o job.

Limitações que conheço:

- `amount` aceita qualquer decimal válido. Com mais de 2 casas ou fora de `NUMERIC(14,2)`, a tabela e a summary poderiam divergir ou o lote falhar. O gerador só produz valores com 2 casas, então não exercitei isso.
- Um CSV só com cabeçalho deixa o job `RUNNING` até o próximo restart.
- O back-end não tem testes automatizados; a verificação de correção são os benchmarks.

## 8. O que faria com mais tempo

- Testes de integração com Testcontainers cobrindo os invariantes de contagem e o crash.
- Validar faixa e escala de `amount` antes do banco.
- Retomar o import a partir de `bytes_read`.
- Comparar `batchUpdate` com `COPY` em tabela de staging.
- Repetir os benchmarks B e E num ambiente mais estável, para reduzir a variação entre rodadas.
