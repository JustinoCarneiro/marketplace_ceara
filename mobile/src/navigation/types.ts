import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';

// ─── Auth Stack ───────────────────────────────────────────────────────────────
export type AuthStackParams = {
  Splash: undefined;
  Login: undefined;
  RegisterClient: undefined;
  RegisterProvider: undefined;
  VerificationPending: { status: 'EM_VERIFICACAO' | 'VERIFICADO' | 'REPROVADO' };
};

// ─── Client Tabs ─────────────────────────────────────────────────────────────
export type ClientTabParams = {
  Home: undefined;
  MyRequests: undefined;
  Profile: undefined;
};

// ─── Client Stack (dentro do tab Home) ───────────────────────────────────────
export type ClientStackParams = {
  ClientTabs: undefined;
  Results: { categoria?: string; lat?: number; lng?: number };
  Filters: undefined;
  ProviderProfile: { providerId: string };
  NewRequest: { providerId?: string; categoria?: string };
  AiAssistant: { requestId: string };
  RequestCreated: { requestId: string };
  CompareProposals: { requestId: string };
  PaymentChoice: { requestId: string; proposalId: string; valor: number };
  PaymentPix: { requestId: string; valor: number };
  PaymentCard: { requestId: string; valor: number };
  EscrowConfirmed: { requestId: string };
  RequestDetail: { requestId: string };
  OpenDispute: { requestId: string };
  Rate: { requestId: string; avaliadoId: string; avaliadoNome: string };
  RateConfirm: { nota: number; comentario: string };
  Sos: { requestId: string };
  SosActive: { alertId: string };
};

// ─── Provider Stack ───────────────────────────────────────────────────────────
export type ProviderStackParams = {
  ProviderTabs: undefined;
  AvailableRequests: undefined;
  SendProposal: { requestId: string };
  EscrowHeld: { requestId: string };
  RequestDetail: { requestId: string };
  OpenDispute: { requestId: string };
  Rate: { requestId: string; avaliadoId: string; avaliadoNome: string };
  RateConfirm: { nota: number; comentario: string };
  Sos: { requestId: string };
  SosActive: { alertId: string };
};

// ─── Root Stack ───────────────────────────────────────────────────────────────
export type RootStackParams = {
  Auth: undefined;
  Client: undefined;
  Provider: undefined;
};

// ─── Navigation prop helpers ─────────────────────────────────────────────────
export type AuthNavProp = NativeStackNavigationProp<AuthStackParams>;
export type ClientNavProp = NativeStackNavigationProp<ClientStackParams>;
export type ProviderNavProp = NativeStackNavigationProp<ProviderStackParams>;
export type ClientTabNavProp = BottomTabNavigationProp<ClientTabParams>;
