import { useEffect, useRef } from 'react';
import { useVirtualizer } from '@tanstack/react-virtual';
import { TransactionRow } from './TransactionRow';
import type { Transaction } from '../types';

interface Props {
  rows: Transaction[];
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  fetchNextPage: () => void;
}

const ROW_HEIGHT = 40;
const LOAD_MORE_THRESHOLD = 20;
const estimateRowSize = () => ROW_HEIGHT;

export function VirtualTable({ rows, hasNextPage, isFetchingNextPage, fetchNextPage }: Props) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => parentRef.current,
    estimateSize: estimateRowSize,
    overscan: 10,
  });

  const items = virtualizer.getVirtualItems();
  const lastIndex = items.length > 0 ? items[items.length - 1].index : -1;
  const paddingTop = items.length > 0 ? items[0].start : 0;
  const paddingBottom = items.length > 0 ? virtualizer.getTotalSize() - items[items.length - 1].end : 0;

  useEffect(() => {
    if (lastIndex < 0 || !hasNextPage || isFetchingNextPage) return;
    if (lastIndex >= rows.length - LOAD_MORE_THRESHOLD) fetchNextPage();
  }, [lastIndex, rows.length, hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div
      ref={parentRef}
      className="h-[600px] overflow-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
    >
      <table className="w-full table-fixed text-left text-sm">
        <colgroup>
          <col className="w-24" />
          <col className="w-36" />
          <col className="w-44" />
          <col className="w-32" />
          <col />
          <col className="w-32" />
          <col className="w-24" />
        </colgroup>
        <thead className="sticky top-0 z-10 bg-slate-100 text-xs uppercase text-slate-500 dark:bg-slate-800 dark:text-slate-400">
          <tr className="h-10">
            <th className="px-3 font-medium">ID</th>
            <th className="px-3 font-medium">Código</th>
            <th className="px-3 font-medium">Data (UTC)</th>
            <th className="px-3 font-medium">Categoria</th>
            <th className="px-3 font-medium">Descrição</th>
            <th className="px-3 text-right font-medium">Valor</th>
            <th className="px-3 font-medium">Origem</th>
          </tr>
        </thead>
        <tbody>
          {paddingTop > 0 && (
            <tr>
              <td colSpan={7} style={{ height: paddingTop, padding: 0 }} />
            </tr>
          )}
          {items.map((item) => {
            const tx = rows[item.index];
            return tx ? <TransactionRow key={tx.id} tx={tx} /> : null;
          })}
          {paddingBottom > 0 && (
            <tr>
              <td colSpan={7} style={{ height: paddingBottom, padding: 0 }} />
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
