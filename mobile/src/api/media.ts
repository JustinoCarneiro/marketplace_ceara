import { Platform } from 'react-native';
import { API_BASE } from './config';

export type MediaTipo = 'FOTO' | 'AUDIO';

/**
 * RN nativo aceita {uri, name, type} direto num FormData. react-native-web usa o FormData
 * do navegador, que só aceita Blob — por isso o branch por plataforma.
 */
async function appendFile(form: FormData, uri: string, name: string, mime: string) {
  if (Platform.OS === 'web') {
    const blob = await (await fetch(uri)).blob();
    form.append('file', blob, name);
  } else {
    form.append('file', { uri, name, type: mime } as any);
  }
}

export async function uploadMedia(
  requestId: string,
  tipo: MediaTipo,
  uri: string,
  name: string,
  mime: string,
  token: string | null,
): Promise<void> {
  const form = new FormData();
  await appendFile(form, uri, name, mime);
  form.append('tipo', tipo);

  const res = await fetch(`${API_BASE}/service-requests/${requestId}/media`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  });
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new Error(data?.message ?? 'Erro ao enviar anexo.');
  }
}
