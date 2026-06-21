import { create } from 'zustand';

type Role = 'ROLE_CLIENT' | 'ROLE_PROVIDER' | null;

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  role: Role;
  userId: string | null;
  nome: string | null;
  email: string | null;
  login: (data: { accessToken: string; refreshToken: string; role: Role; userId: string; nome: string; email: string }) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  role: null,
  userId: null,
  nome: null,
  email: null,

  login: (data) => set({
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    role: data.role,
    userId: data.userId,
    nome: data.nome,
    email: data.email,
  }),

  logout: () => set({
    accessToken: null,
    refreshToken: null,
    role: null,
    userId: null,
    nome: null,
    email: null,
  }),
}));
