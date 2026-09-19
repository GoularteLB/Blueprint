import { useCategoryNames } from '../../analytics/hooks/useAnalytics';
import { useUiStore } from '../../../store/uiStore';

const inputClass =
  'rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900';

export function Filters() {
  const filters = useUiStore((s) => s.explorerFilters);
  const setFilters = useUiStore((s) => s.setExplorerFilters);
  const resetFilters = useUiStore((s) => s.resetExplorerFilters);
  const categories = useCategoryNames();

  return (
    <div className="flex flex-wrap items-end gap-4 text-sm">
      <label className="flex flex-col gap-1">
        <span className="text-slate-500 dark:text-slate-400">Categoria</span>
        <select
          value={filters.category ?? ''}
          onChange={(event) => setFilters({ category: event.target.value || undefined })}
          className={inputClass}
        >
          <option value="">Todas</option>
          {categories.data?.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-slate-500 dark:text-slate-400">De</span>
        <input
          type="date"
          value={filters.from ?? ''}
          max={filters.to}
          onChange={(event) => setFilters({ from: event.target.value || undefined })}
          className={inputClass}
        />
      </label>
      <label className="flex flex-col gap-1">
        <span className="text-slate-500 dark:text-slate-400">Até</span>
        <input
          type="date"
          value={filters.to ?? ''}
          min={filters.from}
          onChange={(event) => setFilters({ to: event.target.value || undefined })}
          className={inputClass}
        />
      </label>
      <button
        type="button"
        onClick={resetFilters}
        className="rounded-md border border-slate-300 px-3 py-1.5 hover:bg-slate-100 dark:border-slate-700 dark:hover:bg-slate-800"
      >
        Limpar
      </button>
    </div>
  );
}
