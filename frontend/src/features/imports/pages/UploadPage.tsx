import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { uploadImport } from '../api';
import { useImportStatus } from '../hooks/useImportStatus';
import { UploadDropzone } from '../components/UploadDropzone';
import { ProgressCard } from '../components/ProgressCard';
import { ProgressBar } from '../../../shared/ui/ProgressBar';
import { ErrorState, LoadingState } from '../../../shared/ui/States';
import { getErrorMessage } from '../../../shared/api/client';
import { formatPercent } from '../../../shared/format';
import { useUiStore } from '../../../store/uiStore';

export function UploadPage() {
  const activeJobId = useUiStore((s) => s.activeJobId);
  const setActiveJobId = useUiStore((s) => s.setActiveJobId);
  const [uploadPercent, setUploadPercent] = useState(0);

  const upload = useMutation({
    mutationFn: (file: File) => uploadImport(file, setUploadPercent),
    onMutate: () => setUploadPercent(0),
    onSuccess: (accepted) => setActiveJobId(accepted.jobId),
  });

  const status = useImportStatus(activeJobId);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold">Importar transações</h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          O arquivo é enviado, enfileirado e processado em segundo plano. O progresso é atualizado a cada segundo.
        </p>
      </div>

      <UploadDropzone onFile={(file) => upload.mutate(file)} disabled={upload.isPending} />

      {upload.isPending && (
        <ProgressBar percent={uploadPercent} label={`Enviando arquivo: ${formatPercent(uploadPercent)}`} />
      )}

      {upload.isError && <ErrorState message={getErrorMessage(upload.error)} />}

      {activeJobId && status.isLoading && <LoadingState message="Consultando job..." />}
      {activeJobId && status.isError && <ErrorState message={getErrorMessage(status.error)} />}
      {status.data && <ProgressCard job={status.data} />}
    </div>
  );
}
