import { API_BASE } from './config';

export async function pushRequest(jwt: string, method: 'GET' | 'PUT' | 'DELETE', token?: string, revocationKey?: string): Promise<boolean> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${API_BASE}/push/${method === 'GET' ? 'status' : 'devices'}`, {
      method, signal: controller.signal,
      headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
      ...(token ? { body: JSON.stringify({ token, revocationKey }) } : {}),
    });
    if (method === 'GET') return response.ok && (await response.json()).enabled === true;
    return response.status === 204;
  } catch { return false; }
  finally { clearTimeout(timer); }
}

/** A capacidade só revoga um cadastro; não autentica usuário nem permite leitura. */
export async function revokePush(token: string, revocationKey: string): Promise<boolean> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${API_BASE}/push/revoke`, {
      method: 'POST', signal: controller.signal,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, revocationKey }),
    });
    return response.status === 204;
  } catch { return false; }
  finally { clearTimeout(timer); }
}

export async function reservePush(jwt: string, token: string): Promise<string | null> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${API_BASE}/push/registrations`, {
      method: 'POST', signal: controller.signal,
      headers: { Authorization: `Bearer ${jwt}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!response.ok) return null;
    const value = (await response.json()).revocationKey;
    return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value) ? value : null;
  } catch { return null; }
  finally { clearTimeout(timer); }
}
