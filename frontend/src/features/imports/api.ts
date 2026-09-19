import { api } from '../../shared/api/client';
import type { ImportAccepted, ImportJob } from './types';

export async function uploadImport(file: File, onProgress: (percent: number) => void): Promise<ImportAccepted> {
  const form = new FormData();
  form.append('file', file);
  const response = await api.post<ImportAccepted>('/imports', form, {
    onUploadProgress: (event) => {
      if (event.total) onProgress((event.loaded * 100) / event.total);
    },
  });
  return response.data;
}

export async function getImport(id: string): Promise<ImportJob> {
  const response = await api.get<ImportJob>(`/imports/${id}`);
  return response.data;
}
