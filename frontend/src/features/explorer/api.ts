import { api } from '../../shared/api/client';
import type { Filters, TransactionPage } from './types';

interface ListParams extends Filters {
  cursor?: string;
  size: number;
}

function dayStart(day: string): string {
  return `${day}T00:00:00Z`;
}

function nextDayStart(day: string): string {
  const [year, month, date] = day.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, date + 1)).toISOString().replace('.000Z', 'Z');
}

export async function listTransactions(params: ListParams): Promise<TransactionPage> {
  const response = await api.get<TransactionPage>('/transactions', {
    params: {
      size: params.size,
      cursor: params.cursor,
      category: params.category || undefined,
      jobId: params.jobId || undefined,
      from: params.from ? dayStart(params.from) : undefined,
      to: params.to ? nextDayStart(params.to) : undefined,
    },
  });
  return response.data;
}
