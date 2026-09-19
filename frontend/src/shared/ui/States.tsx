export function LoadingState({ message = 'Carregando...' }: { message?: string }) {
  return <div className="py-10 text-center text-sm text-slate-500 dark:text-slate-400">{message}</div>;
}

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
      {message}
    </div>
  );
}

export function EmptyState({ message }: { message: string }) {
  return <div className="py-10 text-center text-sm text-slate-500 dark:text-slate-400">{message}</div>;
}
