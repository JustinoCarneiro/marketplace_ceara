import { API_BASE } from './config';
import { getCurrentCoords } from './location';
import { HttpError } from './errors';

/**
 * Espelha `NearbyProviderDto` (backend: discovery/NearbyProviderDto.java) campo a campo.
 *
 * <p>Ponto único do contrato de `GET /providers/nearby`: Home e Resultados consomem o mesmo
 * endpoint e antes cada tela declarava o seu próprio tipo — ambos errados, de formas
 * diferentes (`distanciaKm` inexistente derrubava a lista; `verificado` inexistente fazia
 * TODO prestador exibir o selo VERIFICADO). `fetch().json()` é `any`, então o TypeScript não
 * acusa esse tipo de divergência: manter este arquivo alinhado ao record Java é o que segura.
 */
export interface NearbyProvider {
  id: string;
  nome: string;
  categoria: string;
  bio: string | null;
  statusVerificacao: 'EM_VERIFICACAO' | 'VERIFICADO' | 'REPROVADO' | 'SUSPENSO';
  notaMedia: number | null;
  distanciaMetros: number | null;
}

export function isVerificado(p: NearbyProvider) {
  return p.statusVerificacao === 'VERIFICADO';
}

export function distanciaKm(p: NearbyProvider): number | null {
  return p.distanciaMetros != null ? p.distanciaMetros / 1000 : null;
}

/** `raio` é em METROS no backend (ST_DWithin), não em km. */
export async function fetchNearby(
  token: string | null,
  { raioMetros, categoria }: { raioMetros: number; categoria?: string },
): Promise<NearbyProvider[]> {
  const { lat, lng } = await getCurrentCoords();
  const params = new URLSearchParams({
    lat: String(lat),
    lng: String(lng),
    raio: String(raioMetros),
    ...(categoria ? { categoria } : {}),
  });
  const res = await fetch(`${API_BASE}/providers/nearby?${params}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new HttpError(res.status);
  const data = await res.json();
  return Array.isArray(data) ? data : [];
}
