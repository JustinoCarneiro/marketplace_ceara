import { create } from 'zustand';
export const usePushStore = create<{ revision: number; changed: () => void }>(set => ({
  revision: 0, changed: () => set(s => ({ revision: s.revision + 1 })),
}));
