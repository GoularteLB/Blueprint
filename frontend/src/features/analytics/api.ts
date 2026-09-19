import { api } from '../../shared/api/client';
import type { CategoryPoint, MonthlyPoint, MonthRange, Overview } from './types';

const ALL_TIME: MonthRange = { from: '2000-01', to: '2099-12' };

function monthStart(yearMonth: string): string {
  return `${yearMonth}-01T00:00:00Z`;
}

function nextMonthStart(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  const next = new Date(Date.UTC(year, month, 1));
  return next.toISOString().replace('.000Z', 'Z');
}

function rangeParams(range: MonthRange) {
  return { from: monthStart(range.from), to: nextMonthStart(range.to) };
}

export async function getOverview(): Promise<Overview> {
  const response = await api.get<Overview>('/analytics/overview');
  return response.data;
}

export async function getMonthly(range: MonthRange): Promise<MonthlyPoint[]> {
  const response = await api.get<MonthlyPoint[]>('/analytics/monthly', { params: rangeParams(range) });
  return response.data;
}

export async function getCategories(range: MonthRange = ALL_TIME): Promise<CategoryPoint[]> {
  const response = await api.get<CategoryPoint[]>('/analytics/categories', { params: rangeParams(range) });
  return response.data;
}
