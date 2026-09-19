interface Props {
  percent: number;
  label?: string;
}

export function ProgressBar({ percent, label }: Props) {
  const clamped = Math.min(100, Math.max(0, percent));
  return (
    <div>
      {label && <div className="mb-1 text-sm text-slate-600 dark:text-slate-400">{label}</div>}
      <div className="h-2.5 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
        <div
          className="h-full rounded-full bg-indigo-600 transition-[width] duration-300"
          style={{ width: `${clamped}%` }}
        />
      </div>
    </div>
  );
}
