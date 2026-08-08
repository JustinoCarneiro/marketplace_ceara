import { API_BASE } from '../../api/config';
import { HttpError, screenStateError, type ScreenErrorInfo } from '../../api/errors';
import React, { useCallback, useEffect, useState } from 'react';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import type { ProviderNavProp } from '../../navigation/types';
import { color } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenState from '../../components/ScreenState';

/**
 * Tab "Em Andamento" do prestador. RequestDetailScreen exige um requestId nos params —
 * essa tela resolve qual é o atendimento ativo do prestador (GET /service-requests/active)
 * e empurra pro RequestDetail real; sem isso a tab não tinha como abrir nada (só existia
 * mapeada direto pro RequestDetailScreen sem params).
 */
export default function ActiveJobScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const token = useAuthStore(s => s.accessToken);
  const [loading, setLoading] = useState(true);
  const [hasError, setHasError] = useState<ScreenErrorInfo | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setHasError(null);
    try {
      const res = await fetch(`${API_BASE}/service-requests/active`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new HttpError(res.status);
      const data = await res.json();
      if (data.requestId) {
        nav.navigate('RequestDetail', { requestId: data.requestId });
      } else {
        setLoading(false);
      }
    } catch (e) {
      setHasError(screenStateError(e));
      setLoading(false);
    }
  }, [token, nav]);

  useEffect(() => { load(); }, [load]);
  // Refaz a busca sempre que a tab ganha foco de novo (ex.: prestador aceitou nova proposta).
  useFocusEffect(useCallback(() => { load(); }, [load]));

  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: color.bg }}>
      <ScreenState
        state={hasError ? 'error' : loading ? 'loading' : 'empty'}
        icon="clipboard"
        emptyTitle="Nenhum atendimento em andamento"
        emptyBody="Aceite uma proposta em Disponíveis para começar."
        errorTitle={hasError?.title}
        errorBody={hasError?.body}
        onRetry={load}
      />
    </SafeAreaView>
  );
}
