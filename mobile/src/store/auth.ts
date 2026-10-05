import { create } from 'zustand';

type Role = 'ROLE_CLIENT' | 'ROLE_PROVIDER' | null;

/** O que o login/cadastro/troca de papel devolve (AuthResponse do backend). */
export interface Sessao {
  accessToken: string;
  refreshToken: string;
  /** Papel EM USO na sessão (o do token): é ele que escolhe a navegação. */
  role: Role;
  userId: string;
  nome: string;
  email: string;
  /** Todos os papéis que a conta tem (conta única: a mesma pessoa é cliente e prestador). */
  papeis?: string[];
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  role: Role;
  papeis: string[];
  userId: string | null;
  nome: string | null;
  email: string | null;
  login: (data: Sessao) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  role: null,
  papeis: [],
  userId: null,
  nome: null,
  email: null,

  login: (data) => set({
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    role: data.role,
    papeis: data.papeis ?? (data.role ? [data.role] : []),
    userId: data.userId,
    nome: data.nome,
    email: data.email,
  }),

  logout: () => set({
    accessToken: null,
    refreshToken: null,
    role: null,
    papeis: [],
    userId: null,
    nome: null,
    email: null,
  }),
}));
