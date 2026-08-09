import { create } from 'zustand';

interface FiltersState {
  raioKm: number;
  notaMin: number; // 0 = qualquer nota; caso contrário, nota mínima real (ex.: 4, 4.5)
  setRaioKm: (km: number) => void;
  setNotaMin: (nota: number) => void;
  reset: () => void;
}

const DEFAULTS = { raioKm: 8, notaMin: 0 };

export const useFiltersStore = create<FiltersState>((set) => ({
  ...DEFAULTS,
  setRaioKm: (raioKm) => set({ raioKm }),
  setNotaMin: (notaMin) => set({ notaMin }),
  reset: () => set({ ...DEFAULTS }),
}));
