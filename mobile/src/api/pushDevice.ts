import { Platform } from 'react-native';
import { useAuthStore } from '../store/auth';
import { usePushStore } from '../store/push';
import { pushRequest, revokePush, reservePush } from './push';
import { createPushSession } from './pushSession';
import { serializePush } from './pushCoordinator';

type Binding = { token: string; revocationKey: string; userId: string; createdAt: number };
const preferenceKey = (userId: string) => `onda-push-enabled-${userId}`;
const activeKey = 'onda-push-active';
const pendingKey = 'onda-push-revocations';
const nativeEnabled = () => Platform.OS === 'android' && process.env.EXPO_PUBLIC_PUSH_ENABLED === 'true';
const current = (jwt: string) => useAuthStore.getState().accessToken === jwt;
async function storage() { return import('expo-secure-store'); }

async function pending(): Promise<Binding[]> {
  const store = await storage();
  return JSON.parse(await store.getItemAsync(pendingKey) ?? '[]') as Binding[];
}
async function flushPending(): Promise<boolean> {
  const store = await storage();
  const remaining: Binding[] = [];
  for (const item of await pending()) {
    // Cadastros expiram no servidor em 24h; descarte local só depois de 48h.
    if (Date.now() - item.createdAt > 48 * 3600000) continue;
    if (!await revokePush(item.token, item.revocationKey)) remaining.push(item);
  }
  await store.setItemAsync(pendingKey, JSON.stringify(remaining));
  return remaining.length === 0;
}
async function revoke(binding: Binding): Promise<boolean> {
  const store = await storage();
  const items = await pending();
  if (!items.some(i => i.token === binding.token && i.revocationKey === binding.revocationKey)) items.push(binding);
  // Persistir antes da rede permite retomar após queda do app, logout ou JWT expirado.
  await store.setItemAsync(pendingKey, JSON.stringify(items));
  const active = JSON.parse(await store.getItemAsync(activeKey) ?? 'null') as Binding | null;
  if (active?.token === binding.token && active.revocationKey === binding.revocationKey) await store.deleteItemAsync(activeKey);
  return flushPending();
}
async function register(jwt: string, userId: string, token: string, isCurrent: () => boolean): Promise<boolean> {
  if (!isCurrent() || !await flushPending()) return false;
  const store = await storage();
  const previous = JSON.parse(await store.getItemAsync(activeKey) ?? 'null') as Binding | null;
  // Nunca perder a capacidade anterior se a tentativa seguinte falhar ou repetir o token.
  if (previous && !await revoke(previous)) return false;
  const revocationKey = await reservePush(jwt, token);
  if (!revocationKey) return false;
  const binding: Binding = { userId, token, revocationKey, createdAt: Date.now() };
  // Registrar a capacidade antes do PUT também cobre perda da resposta HTTP.
  await store.setItemAsync(activeKey, JSON.stringify(binding));
  if (!isCurrent()) { await revoke(binding); return false; }
  const ok = await pushRequest(jwt, 'PUT', token, binding.revocationKey);
  if (!ok || !isCurrent()) { await revoke(binding); return false; }
  return true;
}
export function enablePush(jwt: string): Promise<string> {
  return serializePush(async () => {
    const userId = useAuthStore.getState().userId;
    if (!userId || !current(jwt)) return 'Entre na sua conta para ativar notificações.';
    if (!nativeEnabled()) return 'Notificações ainda não estão disponíveis nesta versão do aplicativo.';
    if (!await flushPending() || !await pushRequest(jwt, 'GET')) return 'Notificações indisponíveis neste momento. Conecte-se e tente novamente.';
    try {
      const Notifications = await import('expo-notifications');
      await Notifications.setNotificationChannelAsync('onda-updates', {
        name: 'Atualizações do Onda', importance: Notifications.AndroidImportance.HIGH,
      });
      let permission = await Notifications.getPermissionsAsync();
      if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
      if (!permission.granted) return 'Permissão recusada. Você pode ativar as notificações nas configurações do Android.';
      const token = await Notifications.getDevicePushTokenAsync();
      if (token.type !== 'android' || typeof token.data !== 'string' ||
          !await register(jwt, userId, token.data, () => current(jwt))) return 'Não foi possível ativar as notificações. Tente novamente.';
      await (await storage()).setItemAsync(preferenceKey(userId), 'true');
      usePushStore.getState().changed();
      return 'Notificações ativadas neste celular.';
    } catch { return 'Não foi possível ativar as notificações neste celular.'; }
  });
}
export function disablePush(jwt: string): Promise<string> {
  return serializePush(async () => {
    const userId = useAuthStore.getState().userId;
    if (!userId || !current(jwt)) return 'Entre na sua conta para alterar notificações.';
    if (Platform.OS !== 'android') return 'Notificações não estão ativas nesta versão.';
    try {
      const store = await storage();
      await store.deleteItemAsync(preferenceKey(userId));
      const binding = JSON.parse(await store.getItemAsync(activeKey) ?? 'null') as Binding | null;
      const ok = !binding || await revoke(binding);
      usePushStore.getState().changed();
      return ok ? 'Notificações desativadas neste celular.' :
        'Desativação salva neste celular. Conecte-se à internet para concluir no servidor.';
    } catch { return 'Não foi possível desativar as notificações. Tente novamente.'; }
  });
}

/** Chamado dentro de serializePush; a limpeza devolvida também deve entrar nessa fila. */
export async function bindPushSession(jwt: string | null, isCurrent: () => boolean): Promise<() => Promise<void>> {
  if (Platform.OS !== 'android') return async () => {};
  let remove: (() => void) | undefined;
  let close: (() => Promise<void>) | undefined;
  try {
    const store = await storage();
    await flushPending();
    const userId = useAuthStore.getState().userId;
    const previous = JSON.parse(await store.getItemAsync(activeKey) ?? 'null') as Binding | null;
    const optedIn = userId && await store.getItemAsync(preferenceKey(userId)) === 'true';
    if (previous && (!jwt || previous.userId !== userId || !optedIn || !nativeEnabled())) await revoke(previous);
    if (!jwt || !userId || !current(jwt) || !optedIn || !nativeEnabled() || !isCurrent()) return async () => {};
    const Notifications = await import('expo-notifications');
    if (!(await Notifications.getPermissionsAsync()).granted) {
      if (previous) await revoke(previous);
      return async () => {};
    }
    const session = createPushSession(
      token => register(jwt, userId, token, () => isCurrent() && current(jwt)),
      async token => {
        const binding = JSON.parse(await store.getItemAsync(activeKey) ?? 'null') as Binding | null;
        if (binding?.token === token && binding.userId === userId) return revoke(binding);
        return flushPending();
      },
    );
    close = () => session.close();
    const subscription = Notifications.addPushTokenListener(token => {
      if (token.type === 'android' && typeof token.data === 'string') void serializePush(() => session.update(token.data));
    });
    remove = () => subscription.remove();
    const token = await Notifications.getDevicePushTokenAsync();
    if (token.type === 'android' && typeof token.data === 'string') await session.update(token.data);
    return async () => { remove?.(); await close?.(); };
  } catch {
    remove?.(); await close?.().catch(() => {});
    return async () => {};
  }
}
