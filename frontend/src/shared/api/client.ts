import axios from 'axios';

export const api = axios.create({ baseURL: '/api' });

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const detail = error.response?.data?.detail;
    if (typeof detail === 'string' && detail.length > 0) return detail;
    if (error.response?.status === 429) return 'Fila de processamento cheia. Tente novamente em instantes.';
    return error.message;
  }
  return error instanceof Error ? error.message : 'Erro inesperado';
}
