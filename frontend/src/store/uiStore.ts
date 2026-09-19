import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type Theme = 'light' | 'dark';

export interface ExplorerFilters {
  category?: string;
  from?: string;
  to?: string;
}

interface UiState {
  activeJobId?: string;
  explorerFilters: ExplorerFilters;
  theme: Theme;
  setActiveJobId: (jobId?: string) => void;
  setExplorerFilters: (patch: Partial<ExplorerFilters>) => void;
  resetExplorerFilters: () => void;
  toggleTheme: () => void;
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      activeJobId: undefined,
      explorerFilters: {},
      theme: 'light',
      setActiveJobId: (jobId) => set({ activeJobId: jobId }),
      setExplorerFilters: (patch) =>
        set((state) => ({ explorerFilters: { ...state.explorerFilters, ...patch } })),
      resetExplorerFilters: () => set({ explorerFilters: {} }),
      toggleTheme: () => set((state) => ({ theme: state.theme === 'light' ? 'dark' : 'light' })),
    }),
    {
      name: 'ui-store',
      partialize: (state) => ({ theme: state.theme }),
    },
  ),
);
