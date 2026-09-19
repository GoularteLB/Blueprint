import { useState } from 'react';
import { useCategories, useMonthly, useOverview } from '../hooks/useAnalytics';
import { MetricCard } from '../components/MetricCard';
import { MonthlyChart } from '../components/MonthlyChart';
import { CategoryChart } from '../components/CategoryChart';
import { MonthRangePicker } from '../components/MonthRangePicker';
import { EmptyState, ErrorState, LoadingState } from '../../../shared/ui/States';
import { getErrorMessage } from '../../../shared/api/client';
import { formatCurrency, formatInteger } from '../../../shared/format';
import type { MonthRange } from '../types';

function toYearMonth(date: Date): string {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

function defaultRange(): MonthRange {
  const now = new Date();
  const start = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 11, 1));
  return { from: toYearMonth(start), to: toYearMonth(now) };
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
      <h2 className="mb-4 text-sm font-medium">{title}</h2>
      {children}
    </section>
  );
}

export function DashboardPage() {
  const [range, setRange] = useState<MonthRange>(defaultRange);
  const validRange = range.from <= range.to;

  const overview = useOverview();
  const monthly = useMonthly(range, validRange);
  const categories = useCategories(range, validRange);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold">Dashboard</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            Os cartões consideram todos os imports. Os gráficos seguem o período escolhido.
          </p>
        </div>
        <MonthRangePicker value={range} onChange={setRange} />
      </div>

      {overview.isLoading && <LoadingState />}
      {overview.isError && <ErrorState message={getErrorMessage(overview.error)} />}
      {overview.data && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <MetricCard label="Registros" value={formatInteger(overview.data.totalRecords)} />
          <MetricCard label="Total" value={formatCurrency(overview.data.totalAmount)} />
          <MetricCard label="Ticket médio" value={formatCurrency(overview.data.averageAmount)} />
          <MetricCard label="Categorias" value={formatInteger(overview.data.categories)} />
        </div>
      )}

      {!validRange && <ErrorState message="O mês inicial deve ser anterior ou igual ao mês final." />}

      {validRange && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Panel title="Total por mês">
            {monthly.isLoading && <LoadingState />}
            {monthly.isError && <ErrorState message={getErrorMessage(monthly.error)} />}
            {monthly.data && monthly.data.length === 0 && <EmptyState message="Sem dados no período." />}
            {monthly.data && monthly.data.length > 0 && <MonthlyChart data={monthly.data} />}
          </Panel>
          <Panel title="Total por categoria">
            {categories.isLoading && <LoadingState />}
            {categories.isError && <ErrorState message={getErrorMessage(categories.error)} />}
            {categories.data && categories.data.length === 0 && <EmptyState message="Sem dados no período." />}
            {categories.data && categories.data.length > 0 && <CategoryChart data={categories.data} />}
          </Panel>
        </div>
      )}
    </div>
  );
}
