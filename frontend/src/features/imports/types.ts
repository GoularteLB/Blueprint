export type ImportStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface RowError {
  line: number;
  reason: string;
  raw: string;
}

export interface ImportJob {
  id: string;
  fileName: string;
  status: ImportStatus;
  fileSizeBytes: number;
  bytesRead: number;
  progressPercent: number;
  processedLines: number;
  successLines: number;
  errorLines: number;
  startedAt: string | null;
  finishedAt: string | null;
  elapsedMs: number | null;
  linesPerSecond: number | null;
  errorSample: RowError[];
  errorMessage: string | null;
}

export interface ImportAccepted {
  jobId: string;
  status: ImportStatus;
  fileName: string;
  fileSizeBytes: number;
}
