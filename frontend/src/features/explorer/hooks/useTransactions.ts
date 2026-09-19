import { useInfiniteQuery } from '@tanstack/react-query';
import { listTransactions } from '../api';
import type { Filters } from '../types';

export function useTransactions(filters: Filters) {
  return useInfiniteQuery({
    queryKey: ['transactions', filters],
    queryFn: ({ pageParam }) => listTransactions({ ...filters, cursor: pageParam, size: 100 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore ? (last.nextCursor ?? undefined) : undefined),
  });
}
