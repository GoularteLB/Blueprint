import { Link } from 'react-router-dom';
import { ProgressBar } from '../../../shared/ui/ProgressBar';
import { formatBytes, formatDuration, formatInteger, formatPercent } from '../../../shared/format';
import type { ImportJob, ImportStatus } from '../types';

interface Props {
  job: ImportJob;
}

const statusLabel: Record<ImportStatus, string> = {
  PENDING: 'Na fila',
  RUNNING: 'Processando',
  COMPLETED: 'Concluído',
  FAILED: 'Falhou',
};

const statusClass: Record<ImportStatus, string> = {
  PENDING: 'bg-slate-200 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
  RUNNING: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300',
  COMPLETED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  FAILED: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
};

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs text-slate-500 dark:text-slate-400">{label}</div>
      <div className="text-lg font-semibold tabular-nums">{value}</div>
    </div>
  );
}

export function ProgressCard({ job }: Props) {
  const terminal = job.status === 'COMPLETED' || job.status === 'FAILED';

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate font-medium">{job.fileName}</div>
          <div className="text-xs text-slate-500 dark:text-slate-400">{formatBytes(job.fileSizeBytes)}</div>
        </div>
        <span className={`shrink-0 rounded-full px-3 py-1 text-xs font-medium ${statusClass[job.status]}`}>
          {statusLabel[job.status]}
        </span>
      </div>

      <ProgressBar
        percent={job.progressPercent}
        label={`${formatPercent(job.progressPercent)} lido (${formatBytes(job.bytesRead)} de ${formatBytes(job.fileSizeBytes)})`}
      />

      <div className="mt-5 grid grid-cols-2 gap-4 sm:grid-cols-5">
        <Stat label="Linhas processadas" value={formatInteger(job.processedLines)} />
        <Stat label="Sucesso" value={formatInteger(job.successLines)} />
        <Stat label="Erros" value={formatInteger(job.errorLines)} />
        <Stat
          label="Linhas/s"
          value={job.linesPerSecond == null ? '-' : formatInteger(Math.round(job.linesPerSecond))}
        />
        <Stat label="Tempo decorrido" value={job.elapsedMs == null ? '-' : formatDuration(job.elapsedMs)} />
      </div>

      {job.status === 'FAILED' && job.errorMessage && (
        <div className="mt-4 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
          {job.errorMessage}
        </div>
      )}

      {job.errorSample.length > 0 && (
        <div className="mt-4">
          <div className="mb-2 text-sm font-medium">Amostra de erros ({job.errorSample.length})</div>
          <div className="max-h-56 overflow-auto rounded-md border border-slate-200 dark:border-slate-800">
            <table className="w-full text-left text-xs">
              <thead className="sticky top-0 bg-slate-100 dark:bg-slate-800">
                <tr>
                  <th className="px-3 py-2 font-medium">Linha</th>
                  <th className="px-3 py-2 font-medium">Motivo</th>
                  <th className="px-3 py-2 font-medium">Conteúdo</th>
                </tr>
              </thead>
              <tbody>
                {job.errorSample.map((error) => (
                  <tr key={error.line} className="border-t border-slate-200 dark:border-slate-800">
                    <td className="px-3 py-1.5 tabular-nums">{error.line}</td>
                    <td className="px-3 py-1.5">{error.reason}</td>
                    <td className="max-w-xs truncate px-3 py-1.5 font-mono">{error.raw}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {terminal && (
        <div className="mt-5">
          <Link
            to="/dashboard"
            className="inline-block rounded-md bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            Ver dashboard
          </Link>
        </div>
      )}
    </div>
  );
}
