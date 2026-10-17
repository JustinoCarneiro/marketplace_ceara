import { useEffect } from 'react';
import NetInfo from '@react-native-community/netinfo';
import { serializePush } from '../api/pushCoordinator';
import { AppState } from 'react-native';
import { useAuthStore } from '../store/auth';
import { usePushStore } from '../store/push';
import { bindPushSession } from '../api/pushDevice';

// Serializa revogação/registro entre sessões para uma resposta antiga não desfazer o novo cadastro.
export default function PushSession() {
  const jwt = useAuthStore(s => s.accessToken);
  const userId = useAuthStore(s => s.userId);
  const revision = usePushStore(s => s.revision);
  useEffect(() => {
    let active = true;
    let generation = 0;
    let close: (() => Promise<void>) | undefined;
    function bind() {
      const current = ++generation;
      void serializePush(async () => {
        await close?.(); close = undefined;
        if (!active || current !== generation) return;
        const cleanup = await bindPushSession(jwt!, () => active && current === generation);
        if (!active || current !== generation) await cleanup(); else close = cleanup;
      }).catch(() => {});
    }
    bind();
    const network = NetInfo.addEventListener(s => { if (s.isConnected) bind(); });
    const subscription = AppState.addEventListener('change', state => { if (state === 'active') bind(); });
    return () => {
      active = false; generation++; subscription.remove(); network();
      void serializePush(async () => { await close?.(); close = undefined; }).catch(() => {});
    };
  }, [jwt, userId, revision]);
  return null;
}
