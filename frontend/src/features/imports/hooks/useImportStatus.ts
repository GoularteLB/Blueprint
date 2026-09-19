import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { getImport } from '../api';

export function useImportStatus(jobId?: string) {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['import', jobId],
    queryFn: () => getImport(jobId!),
    enabled: !!jobId,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === 'COMPLETED' || s === 'FAILED' ? false : 1000;
    },
  });

  const status = query.data?.status;

  useEffect(() => {
    if (status === 'COMPLETED') {
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    }
  }, [status, queryClient]);

  return query;
}
