import { memo } from 'react';
import { formatCurrency, formatDateTime } from '../../../shared/format';
import type { Transaction } from '../types';

interface Props {
  tx: Transaction;
}

export const TransactionRow = memo(function TransactionRow({ tx }: Props) {
  return (
    <tr className="h-10 border-b border-slate-100 dark:border-slate-800">
      <td className="truncate px-3 tabular-nums">{tx.id}</td>
      <td className="truncate px-3 font-mono text-xs">{tx.externalId}</td>
      <td className="truncate px-3 tabular-nums">{formatDateTime(tx.occurredAt)}</td>
      <td className="truncate px-3">{tx.category}</td>
      <td className="truncate px-3">{tx.description ?? '-'}</td>
      <td className="truncate px-3 text-right tabular-nums">{formatCurrency(tx.amount)}</td>
      <td className="truncate px-3">{tx.source ?? '-'}</td>
    </tr>
  );
});
