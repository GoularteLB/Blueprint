# Large-Scale Data Ingestion & Analytics

Sistema de ingestão e análise de arquivos CSV grandes: upload assíncrono, processamento em streaming com memória limitada, consulta paginada por cursor e analytics agregados. Back-end Spring Boot (JDBC puro) + PostgreSQL, front-end React, tudo orquestrado com Docker Compose.

Todos os números deste documento foram medidos nesta máquina (Windows 11, Docker Desktop, PostgreSQL 16, backend com `-Xmx512m` e `mem_limit` de 768 MiB). Onde a medição tem variação relevante, isso está dito.

## 1. Como rodar

```
cp .env.example .env
docker compose up --build
```

Abra `http://localhost:3000`.

Para gerar um dataset (os arquivos ficam fora do git):

```
python scripts/generate_csv.py --rows 1000000 --out data/transactions.csv
python scripts/generate_csv.py --rows 5000000 --out data/transactions_5m.csv
```

Depois envie o arquivo pela tela de Upload ou via API:

```
curl -F "file=@data/transactions.csv" localhost:8080/api/imports
```

O gerador usa seed fixa (dataset reproduzível), timestamps sempre em UTC com `Z` e 0,5% de linhas inválidas (`--error-rate`), o que torna demonstrável a contagem de erros do job.

## 2. Arquitetura e stack

```
navegador ──► nginx (frontend :3000) ──/api──► backend Spring Boot (:8080) ──► PostgreSQL 16
                 SPA React                       │  executor limitado + semáforo
                                                 └─► volume uploads (/data/uploads)
```

- **Arquitetura:** monólito modular no back-end (pacotes por feature: `imports`, `transactions`, `analytics`, `infra`), SPA orientada a features no front, REST, Nginx como reverse proxy de borda. O navegador só conhece `localhost:3000`, sem CORS.
- **Back-end:** Java 21, Spring Boot 3.5, Spring JDBC (`JdbcTemplate`, sem JPA), Flyway, Commons CSV, Commons IO, Actuator.
- **Front-end:** React 18, TypeScript, Vite, TanStack Query, TanStack Virtual, Zustand, Recharts, Tailwind, axios.

**Padrões adotados**

- Back-end: Repository (JDBC explícito), DTO nos contratos, Unit of Work (`TransactionTemplate` por unidade de commit), Batch + Streaming Processing (`BatchReader`), Asynchronous Job (executor limitado + `import_job`), Keyset Pagination, Strategy em `source=direct|summary`, Materialized Aggregate incremental (`category_month_summary`).
- Front-end: Server State (TanStack Query) separado de UI State (Zustand), módulos por feature, Virtualization + cursor-based infinite scroll.

## 3. Ingestão

### 3.1 Como evitamos OOM

- O upload vai direto para disco (`file-size-threshold: 0`, `Files.copy` de stream; nunca `getBytes()`).
- O worker lê o arquivo em streaming (`CountingInputStream` → `CSVParser`) e monta um batch por vez.
- Nunca existe mais que `BATCH_SIZE × COMMIT_EVERY_N_BATCHES` linhas não comitadas. Em memória ficam só o batch corrente, o estado de agregação (poucas dezenas de chaves) e a amostra de erros (máximo 20 itens de 500 caracteres).
- A JVM roda com `-Xmx512m` dentro de um container com `mem_limit: 768m` (heap + metaspace + threads + memória nativa).

**Benchmark A: o arquivo cresce, o heap não.** Rodado com os defaults provisórios (batch 5000, commit a cada 2 batches, `synchronous_commit=off`), JVM reiniciada a cada rodada. Heap amostrado a cada ~1 s via Actuator, memória do container via `docker stats`.

| Linhas | CSV | Tempo | Linhas/s | Pico de heap | Pico do container | Pausas de GC | Resultado |
|---|---|---|---|---|---|---|---|
| 100k | 6 MB | 8,5 s | 11.792 | 45 MiB | 187 MiB | 26 (0,21 s) | OK |
| 500k | 30 MB | 49,8 s | 10.037 | 47 MiB | 199 MiB | 115 (0,84 s) | OK |
| 1M | 60 MB | 63,3 s | 15.800 | 51 MiB | 205 MiB | 217 (1,23 s) | OK |
| 2M | 120 MB | 141,9 s | 14.090 | 49 MiB | 209 MiB | 415 (2,34 s) | OK |
| 5M | 300 MB | 563,1 s | 8.879 | 62 MiB | 249 MiB | 1.017 (9,16 s) | OK |

O arquivo cresceu 50 vezes e o pico de heap ficou entre 45 e 62 MiB, contra um teto de 495 MiB reportado pela JVM. O container nunca passou de 249 MiB dos 768 MiB. O `-Xmx512m` nunca foi atingido, e o custo total de GC no 5M foi de 9 s em 563 s (1,6%). O pico de heap é o máximo das amostras de `jvm.memory.used`, que inclui lixo ainda não coletado; é um limite superior.

Um import de 5M repetido depois com a configuração final (batch 1000, commit a cada 10, `synchronous_commit=on`) levou 667 s (7.494 linhas/s), sem melhora sobre os 563 s desta tabela. Ou seja, os ganhos medidos com 1M linhas em B e E não se repetiram em 5M numa única rodada; isso é coerente com a variação entre rodadas descrita em 3.2 e com a hipótese do custo dos índices, mas nenhuma das duas foi isolada.

Observação: a vazão caiu de ~15,8 mil linhas/s (1M) para ~8,9 mil (5M) sem que o heap mudasse. A hipótese mais plausível é o custo de manter três índices B-tree com inserções em ordem aleatória de `occurred_at` numa tabela maior, mas isso não foi medido isoladamente.

### 3.2 Batch processing

- `JdbcTemplate.batchUpdate` com `reWriteBatchedInserts=true` na URL JDBC: o driver reescreve o lote em INSERTs multi-linha em vez de N round trips.
- **Batch e transação são parâmetros separados.** `BATCH_SIZE` governa os round trips; `COMMIT_EVERY_N_BATCHES` governa quantos batches entram numa transação (e portanto quantos fsyncs acontecem). `SYNCHRONOUS_COMMIT=false` aplica `SET LOCAL synchronous_commit = off` só na sessão de ingestão.
- Cada unidade de commit é um `TransactionTemplate`: lê o arquivo, insere os batches, faz o upsert do summary, atualiza o progresso e, na última unidade, grava `COMPLETED`, tudo no mesmo commit.

**Benchmark B: tamanho do batch** (1M linhas, commit a cada 1 batch, 3 rodadas cada, JVM reiniciada a cada rodada):

| Batch | Linhas/s (3 rodadas) | Mediana | Pico de heap |
|---|---|---|---|
| 1000 | 12.506 / 14.113 / 23.417 | 14.113 | 37 MiB |
| 2000 | 11.323 / 20.473 / 8.471 | 11.323 | 37 MiB |
| 5000 | 10.416 / 21.758 / 12.137 | 12.137 | 58 MiB |
| 10000 | 13.247 / 24.624 / 9.422 | 13.247 | 67 MiB |

**Leitura honesta:** a mesma configuração variou de 8,4 mil a 24,6 mil linhas/s entre rodadas, mais do que qualquer diferença entre tamanhos de batch. Não há efeito de vazão distinguível entre 1000 e 10000 nesta máquina; a variação provavelmente vem do ambiente (Docker no Windows, e o sistema chegou a reportar pouca memória durante a bateria), mas isso não foi isolado. O que separa os tamanhos com clareza é a memória: o batch de 1000 usa 37 MiB de pico de heap contra 67 MiB do de 10000. O valor adotado foi **1000** (melhor mediana e menor heap).

**Benchmark E: transação e fsync** (batch 1000, 1M linhas, 3 rodadas cada; mediana de linhas/s):

| Commit a cada N batches | `synchronous_commit=on` | `synchronous_commit=off` |
|---|---|---|
| 1 | 11.754 (10.586 / 12.637 / 11.754) | 14.950 (13.618 / 17.318 / 14.950) |
| 5 | 17.875 (14.267 / 17.875 / 20.316) | 16.700 (16.618 / 16.700 / 24.317) |
| 10 | **22.291** (24.244 / 16.491 / 22.291) | 20.558 (20.558 / 15.140 / 24.665) |

Pico de heap: 37 MiB em todas as combinações.

Uma passada anterior, com batch 10000 e uma única rodada por combinação, deu resultados na mesma direção (o melhor ponto foi commit a cada 10 batches sem fsync síncrono, 19.222 linhas/s; as demais ficaram entre 10.530 e 12.556).

**Conclusão:** alongar a transação compensa muito mais do que relaxar a durabilidade. De 1 para 10 batches por commit a vazão sobe ~1,9x com fsync síncrono (11,7 mil para 22,3 mil linhas/s). Desligar o fsync síncrono só ajudou quando o commit é frequente (+27% com commit a cada batch); com transações longas a diferença desaparece (22,3 mil contra 20,6 mil, dentro do ruído). Como não há ganho medido, a durabilidade é mantida.

**Valores adotados no `.env.example`** (substituem os provisórios 5000 / 2 / false):

| Variável | Valor |
|---|---|
| `BATCH_SIZE` | 1000 |
| `COMMIT_EVERY_N_BATCHES` | 10 |
| `SYNCHRONOUS_COMMIT` | true |

O máximo de linhas não comitadas continua 10.000. Se um dia `SYNCHRONOUS_COMMIT=false` for usado, o custo é perder as últimas centenas de milissegundos de commits num crash do PostgreSQL, aceitável porque o import é reexecutável a partir do arquivo.

### 3.3 Assíncrono

- `POST /api/imports` responde `202 Accepted` com o `jobId`; o processamento roda em `@Async` num executor limitado (core 1, max 2, fila 10).
- A admissão é decidida por um `Semaphore` (`max-in-flight` = 12, igual a `maxPoolSize + queueCapacity`) **antes** de gravar o arquivo permanente, criar o job ou ocupar vaga. Quando cheio, a resposta é `429`, sem nada para compensar. O `AbortPolicy` do executor é só defesa em profundidade. O semáforo não protege o ingress HTTP/multipart temporário, e isso é deliberado.
- O front consulta `GET /api/imports/{id}` a cada 1 s (polling). O progresso é `bytes_read / file_size_bytes`, uma medida direta gravada a cada commit, mais linhas por segundo. Não há ETA: extrapolar tempo restante seria precisão falsa.
- **Job órfão:** a versão atual não retoma importação interrompida. Jobs `PENDING` ou `RUNNING` durante um restart são marcados como `FAILED` no boot (um `PENDING` também ficou abandonado se o worker nunca iniciou). O arquivo permanece no volume e pode ser reenviado.
- `COMPLETED` é gravado dentro da última unidade transacional, então um import persistido nunca vira `FAILED`; `markFailed` só atua sobre jobs `RUNNING`.

### 3.4 Erros por linha

Toda validação de conteúdo acontece antes do banco: `external_id` não vazio; `occurred_at` em ISO-8601 com offset obrigatório (timestamp sem offset é erro, nunca é interpretado na timezone da JVM ou do Postgres); `category` não vazia (normalizada em maiúsculas); `amount` decimal válido; colunas faltantes são erro. O job guarda contadores (`processed = success + error`) e uma amostra de até 20 erros com linha, motivo e trecho cru.

Um erro de banco no meio do lote é tratado como falha de infraestrutura: rollback da unidade e job `FAILED`. Não há fallback linha a linha, porque no PostgreSQL um erro SQL aborta a transação inteira.

**Benchmark F: crash no meio da ingestão** (5M linhas, `docker kill` do backend perto de 50%):

Procedimento: importar o arquivo de 5M, `docker kill` do container do backend quando o progresso chegou a 50,0% (2.500.000 linhas processadas segundo a API), conferir o banco com o backend parado e depois reiniciar o backend. Configuração final (batch 1000, commit a cada 10, `synchronous_commit=on`).

```sql
SELECT success_lines,
       (SELECT count(*) FROM transactions WHERE job_id = :job)
  FROM import_job WHERE id = :job;

SELECT (SELECT coalesce(sum(tx_count), 0) FROM category_month_summary WHERE job_id = :job),
       (SELECT count(*) FROM transactions WHERE job_id = :job);

SELECT (SELECT coalesce(sum(total_amount), 0) FROM category_month_summary WHERE job_id = :job),
       (SELECT coalesce(sum(amount), 0) FROM transactions WHERE job_id = :job);

SELECT processed_lines, success_lines, error_lines,
       processed_lines = success_lines + error_lines, status
  FROM import_job WHERE id = :job;
```

| Verificação | Resultado |
|---|---|
| `success_lines` = `COUNT(*)` da tabela bruta | 2.497.455 = 2.497.455 |
| `SUM(summary.tx_count)` = `COUNT(*)` | 2.497.455 = 2.497.455 |
| `SUM(summary.total_amount)` = `SUM(transactions.amount)` | 82.677.646,98 = 82.677.646,98 |
| `processed_lines` = `success_lines + error_lines` | 2.510.000 = 2.497.455 + 12.545 (verdadeiro) |
| Status logo após o kill | `RUNNING` (o processo morreu sem marcar nada) |
| Status depois do restart | `FAILED`, `Worker interrupted by application restart` |

Os dados comitados antes do crash ficaram consistentes: a tabela bruta, a summary e os contadores do job saem no mesmo commit, então nunca divergem, mesmo com a morte abrupta do processo.

## 4. Banco

### 4.1 Índices

| Índice | Atende |
|---|---|
| `idx_tx_occurred_id (occurred_at DESC, id DESC)` | listagem keyset sem filtro |
| `idx_tx_category_occurred_id (category, occurred_at DESC, id DESC) INCLUDE (amount)` | listagem filtrada por categoria e agregação filtrada por categoria |
| `idx_tx_job_occurred_id (job_id, occurred_at DESC, id DESC)` | listagem filtrada por import |

O `INCLUDE (amount)` foi validado: para agregação filtrada por categoria o plano é *Index Only Scan* com `Heap Fetches: 0`.

| Consulta (1M linhas) | Plano | Tempo | Buffers |
|---|---|---|---|
| `SUM/COUNT` de FOOD, 1 mês | Index Only Scan em `idx_tx_category_occurred_id` | 2,4 ms | 77 |
| `SUM/COUNT` de FOOD, 12 meses | Index Only Scan em `idx_tx_category_occurred_id` | 23,3 ms | 768 |
| `GROUP BY category`, 12 meses (todas as categorias) | Parallel Seq Scan | 136 ms | 13.108 |

O endpoint `/categories` com `source=direct` agrega todas as categorias e precisa ler a tabela inteira, então o planner escolhe corretamente varredura sequencial: o índice não ajuda esse caso, e quem resolve é a summary table.

**BRIN (experimento):** um índice BRIN em `occurred_at` foi criado (24 kB contra 38 MB do B-tree) e o planner **não o usou**: sem BRIN a consulta de faixa de um mês fez Bitmap Heap Scan no B-tree (54,6 ms); com BRIN presente fez Parallel Seq Scan (43,6 ms), sem tocar no BRIN. BRIN só funciona quando a ordem física da tabela acompanha o valor da coluna, e o dataset tem datas em ordem aleatória. Sem ganho atribuível, o índice foi removido.

### 4.2 Paginação keyset

O cursor é `base64url("<occurred_at ISO>|<id>")` gerado a partir do último item devolvido; o WHERE é montado só com os filtros presentes (o padrão `:p IS NULL OR col = :p` faz o planner cair em generic plan e ignorar o índice); busca-se `size + 1` linhas para calcular `hasMore`, sem `COUNT(*)`.

**Benchmark C:** `EXPLAIN (ANALYZE, BUFFERS)`, 1M linhas, `LIMIT 51`, uma execução por ponto com cache aquecido. Em todos os casos o plano foi Index Scan.

Sem filtro (994.983 linhas):

| Profundidade | OFFSET (ms / buffers) | Cursor (ms / buffers) |
|---|---|---|
| 0 | 0,24 / 54 | 0,19 / 54 |
| 10.000 | 12,8 / 10.096 | 0,28 / 54 |
| 500.000 | 461 / 502.355 | 0,38 / 55 |
| 950.000 | 860 / 954.565 | 0,52 / 54 |

`category=FOOD` (99.822 linhas; profundidades proporcionais ao tamanho do conjunto):

| Profundidade | OFFSET (ms / buffers) | Cursor (ms / buffers) |
|---|---|---|
| 0 | 0,24 / 54 | 0,34 / 54 |
| 10.000 | 16,6 / 10.130 | 0,50 / 55 |
| 49.911 | 126 / 14.348 | 0,45 / 54 |
| 94.830 | 128 / 14.668 | 0,76 / 54 |

`jobId` (994.983 linhas):

| Profundidade | OFFSET (ms / buffers) | Cursor (ms / buffers) |
|---|---|---|
| 0 | 0,29 / 54 | 0,22 / 54 |
| 10.000 | 12,8 / 10.126 | 0,35 / 54 |
| 500.000 | 410 / 503.963 | 0,41 / 55 |
| 950.000 | 950 / 957.477 | 0,51 / 54 |

O cursor custa ~54 buffers e menos de 1 ms em qualquer profundidade e em qualquer variante de filtro; o OFFSET lê tudo o que pula (quase 1 milhão de buffers na página mais profunda).

### 4.3 Agregação

Duas estratégias, com regra de escolha:

- **Direct:** `GROUP BY` sobre `transactions`, com faixa indexável em `occurred_at`. É o baseline honesto e aceita qualquer intervalo.
- **Summary:** `category_month_summary (job_id, month, category)`, alimentada por upsert no mesmo commit de cada unidade de ingestão. Uma leitura de poucas centenas de linhas, mas com granularidade mensal.

Sem `source`, o back-end escolhe: `from`/`to` alinhados ao primeiro dia do mês (UTC) usam summary; intervalo parcial usa direct. `source=summary` com intervalo não alinhado devolve 400. O mês é calculado em UTC de ponta a ponta (`date_trunc` sobre `occurred_at AT TIME ZONE 'UTC'`), para não depender da timezone da JVM ou do Postgres.

**Benchmark D, 1M linhas** (período de 12 meses, melhor de 3 execuções; os resultados de `direct` e `summary` foram idênticos em `totalAmount` e `txCount` em todas as linhas):

| Escopo | Endpoint | Direct | Summary |
|---|---|---|---|
| global | `/monthly` | 695 ms | 9,9 ms |
| global | `/categories` | 168 ms | 9,8 ms |
| global | `/overview` | 205 ms | 9,6 ms |
| por `jobId` | `/monthly` | 681 ms | 11,8 ms |
| por `jobId` | `/categories` | 166 ms | 10,9 ms |
| por `jobId` | `/overview` | 1.054 ms | 8,2 ms |

**Benchmark D, 5M linhas:**

Mesmo período de 12 meses, melhor de 3 execuções; os resultados de `direct` e `summary` foram idênticos em todas as linhas. O import de 5M usou a configuração final (batch 1000, commit a cada 10 batches, `synchronous_commit=on`) e levou 667 s (7.494 linhas/s), com 4.974.893 linhas válidas e 25.107 rejeitadas.

| Escopo | Endpoint | Direct | Summary |
|---|---|---|---|
| global | `/monthly` | 3.440 ms | 11,1 ms |
| global | `/categories` | 678 ms | 9,0 ms |
| global | `/overview` | 1.770 ms | 27,5 ms |
| por `jobId` | `/monthly` | 3.414 ms | 9,7 ms |
| por `jobId` | `/categories` | 736 ms | 9,0 ms |
| por `jobId` | `/overview` | 14.067 ms | 6,8 ms |

O `EXPLAIN` do caminho direto de `/categories` em 5M é Parallel Seq Scan (809 ms, 65.464 buffers), tanto global quanto por `jobId`: o índice com `INCLUDE (amount)` não entra porque a consulta agrega todas as categorias sobre praticamente toda a tabela. O `/overview` por `jobId` no caminho direto (14 s) é dominado pelo `COUNT(DISTINCT category)`.

A diferença é grande por construção: o caminho direto calcula sob demanda sobre milhões de linhas, e a summary é uma materialização incremental. Não é uma "otimização de query", é troca de trabalho no tempo de leitura por trabalho no tempo de ingestão.

**Medição de API** (1M linhas, 20 conexões simultâneas, 30 s por endpoint, gerador de carga em Python com keep-alive rodando na mesma máquina; `hey`/`wrk` não estavam instalados):

| Endpoint | Requisições | Req/s | p50 | p95 | p99 | Erros |
|---|---|---|---|---|---|---|
| `GET /api/transactions?size=50` (primeira página) | 16.421 | 547 | 31,6 ms | 76,4 ms | 119,9 ms | 0 |
| `GET /api/transactions?size=50&cursor=...` (~900 mil linhas de profundidade) | 28.419 | 947 | 14,4 ms | 46,4 ms | 161,9 ms | 0 |
| `GET /api/analytics/overview` | 59.407 | 1.980 | 9,1 ms | 19,4 ms | 28,0 ms | 0 |
| `GET /api/analytics/monthly` (summary) | 54.730 | 1.824 | 9,7 ms | 21,1 ms | 33,6 ms | 0 |

A primeira página foi mais lenta que a página profunda; não investiguei o motivo.

## 5. Front-end

- **Estado:** TanStack Query guarda tudo o que vem do servidor; Zustand (`uiStore`) guarda só estado de UI (job ativo, filtros do explorer, tema). Nenhum dado de API é copiado para o Zustand.
- **Upload:** dropzone, barra de upload em bytes, depois polling a cada 1 s até `COMPLETED`/`FAILED`, mostrando percentual por bytes, linhas, erros, linhas/s e tempo decorrido (sem ETA). Ao concluir, invalida `['analytics']` e `['transactions']`. O dashboard não é atualizado a cada poll: a summary incremental existe por consistência, não para streaming de dashboard.
- **Dashboard:** quatro cards (registros, total, ticket médio, categorias), gráfico mensal e gráfico por categoria, com seletor de meses alinhado à summary e `staleTime` de 30 s.
- **Explorer:** `useInfiniteQuery` com cursor (páginas de 100) e `useVirtualizer` (`estimateSize` 40, `overscan` 10) numa tabela de altura fixa; `TransactionRow` é `React.memo` com `key={tx.id}`. Filtros no `uiStore` mudam a `queryKey` e reiniciam a lista.

**Linhas no DOM (1M+ linhas no banco), contando `document.querySelectorAll('tbody tr')` no DevTools:** 26 no topo da lista e entre 37 e 38 durante a rolagem (mais duas linhas de espaçamento do virtualizador). Dentro do limite de ~30 a 40, e sem crescer ao rolar.

## 6. Variáveis de ambiente e volumes

| Variável | Padrão | Função |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `ingestion` | credenciais do Postgres |
| `BACKEND_MEM_LIMIT` | `768m` | `mem_limit` do container do backend |
| `JVM_MAX_HEAP` | `512m` | `-Xmx` da JVM |
| `BATCH_SIZE` | `1000` | linhas por `batchUpdate` (vencedor de B) |
| `COMMIT_EVERY_N_BATCHES` | `10` | batches por transação (vencedor de E) |
| `SYNCHRONOUS_COMMIT` | `true` | `false` aplica `SET LOCAL synchronous_commit = off` na ingestão |

Volumes nomeados: `pgdata` (dados do PostgreSQL) e `uploads` (`/data/uploads`, arquivos enviados). O container do backend também escreve um heap dump em `/data/heapdump.hprof` se houver OOM.

## 7. Trade-offs

- **Polling vs SSE:** polling de 1 s dá menos código e menos pontos de falha, e o enunciado o aceita. SSE reduziria requisições, mas não muda a experiência com um único job.
- **JDBC vs JPA:** o controle explícito de batch, transação e memória pesou mais que a conveniência do ORM.
- **`batchUpdate` vs `COPY`:** `batchUpdate` convive com a validação linha a linha e com a rejeição individual de linhas; `COPY` não lida bem com isso. `COPY` não foi implementado nem medido.
- **Sem broker:** o requisito é assíncrono, não distribuído. `@Async` com executor limitado e semáforo resolve; Kafka, RabbitMQ, Redis, WebSocket e particionamento ficaram de fora por falta de problema que os justifique.
- **Sem retomada de import:** um restart marca os jobs em andamento como `FAILED`; o arquivo permanece e pode ser reenviado.
- **Erro SQL derruba o job:** sem savepoints por linha, um erro de banco no lote faz rollback da unidade e falha o job.
- **`synchronous_commit`:** a suposta vantagem de relaxar a durabilidade não apareceu com transações longas, então o padrão é síncrono.

**Limitações conhecidas**

- A validação de `amount` aceita qualquer decimal válido. Um valor com mais de 2 casas decimais é arredondado pelo `NUMERIC(14,2)` na tabela, enquanto a summary soma o valor original antes de gravar, o que poderia gerar divergência; e um valor fora do intervalo de `NUMERIC(14,2)` faz o lote falhar. O gerador de dataset só produz valores com 2 casas, então isso não foi exercitado.
- Um CSV só com cabeçalho deixa o job em `RUNNING` até o próximo restart.
- Arquivos com BOM UTF-8 têm o primeiro nome de coluna lido com o BOM.
- O backend não tem testes automatizados; a verificação de correção é feita pelos benchmarks D e F.

## 8. O que faria com mais tempo

- Testes de integração com Testcontainers cobrindo os invariantes de contagem e o crash.
- Validação de faixa e escala de `amount` antes do banco.
- Retomada de import a partir de `bytes_read`.
- Savepoints por linha, ou `COPY` para uma tabela de staging, medindo contra `batchUpdate`.
- Repetir os benchmarks B e E num ambiente mais estável (Linux nativo) para reduzir a variação entre rodadas.
- Proteção do ingress HTTP/multipart com filtro de tamanho antes da leitura do corpo.
