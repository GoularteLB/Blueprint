import type { MonthRange } from '../types';

interface Props {
  value: MonthRange;
  onChange: (range: MonthRange) => void;
}

const inputClass =
  'rounded-md border border-slate-300 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900';

export function MonthRangePicker({ value, onChange }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-3 text-sm">
      <label className="flex items-center gap-2">
        <span className="text-slate-500 dark:text-slate-400">De</span>
        <input
          type="month"
          value={value.from}
          max={value.to}
          onChange={(event) => event.target.value && onChange({ ...value, from: event.target.value })}
          className={inputClass}
        />
      </label>
      <label className="flex items-center gap-2">
        <span className="text-slate-500 dark:text-slate-400">Até</span>
        <input
          type="month"
          value={value.to}
          min={value.from}
          onChange={(event) => event.target.value && onChange({ ...value, to: event.target.value })}
          className={inputClass}
        />
      </label>
    </div>
  );
}
