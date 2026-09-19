import { useQuery } from '@tanstack/react-query';
import { getCategories, getMonthly, getOverview } from '../api';
import type { MonthRange } from '../types';

const STALE_TIME = 30_000;

export function useOverview() {
  return useQuery({
    queryKey: ['analytics', 'overview'],
    queryFn: getOverview,
    staleTime: STALE_TIME,
  });
}

export function useMonthly(range: MonthRange, enabled: boolean) {
  return useQuery({
    queryKey: ['analytics', 'monthly', range.from, range.to],
    queryFn: () => getMonthly(range),
    staleTime: STALE_TIME,
    enabled,
  });
}

export function useCategories(range: MonthRange, enabled: boolean) {
  return useQuery({
    queryKey: ['analytics', 'categories', range.from, range.to],
    queryFn: () => getCategories(range),
    staleTime: STALE_TIME,
    enabled,
  });
}

export function useCategoryNames() {
  return useQuery({
    queryKey: ['analytics', 'categoryNames'],
    queryFn: async () => {
      const points = await getCategories();
      return points.map((p) => p.category).sort();
    },
    staleTime: STALE_TIME,
  });
}
