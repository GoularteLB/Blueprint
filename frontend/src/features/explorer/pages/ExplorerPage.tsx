import { useMemo } from 'react';
import { useTransactions } from '../hooks/useTransactions';
import { Filters } from '../components/Filters';
import { VirtualTable } from '../components/VirtualTable';
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/States';
import { getErrorMessage } from '../../../shared/api/client';
import { formatInteger } from '../../../shared/format';
import { useUiStore } from '../../../store/uiStore';

export function ExplorerPage() {
  const filters = useUiStore((s) => s.explorerFilters);
  const query = useTransactions(filters);

  const rows = useMemo(() => query.data?.pages.flatMap((page) => page.data) ?? [], [query.data]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-semibold">Explorer</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Listagem paginada por cursor e virtualizada: só as linhas visíveis existem no DOM.
        </p>
      </div>

      <Filters />

      {query.isLoading && <LoadingState />}
      {query.isError && <ErrorState message={getErrorMessage(query.error)} />}
      {query.data && rows.length === 0 && <EmptyState message="Nenhuma transação encontrada." />}
      {rows.length > 0 && (
        <>
          <VirtualTable
            rows={rows}
            hasNextPage={query.hasNextPage}
            isFetchingNextPage={query.isFetchingNextPage}
            fetchNextPage={query.fetchNextPage}
          />
          <div className="text-xs text-slate-500 dark:text-slate-400">
            {formatInteger(rows.length)} linhas carregadas
            {query.isFetchingNextPage && ' - carregando mais...'}
            {!query.hasNextPage && ' - fim da lista'}
          </div>
        </>
      )}
    </div>
  );
}
