import { API_BASE } from '../../api/config';
import { HttpError, screenStateError, type ScreenErrorInfo } from '../../api/errors';
import React, { useCallback, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, RefreshControl } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ProviderNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenState from '../../components/ScreenState';
import { SkeletonList } from '../../components/Skeleton';

interface HistoryItem {
  id: string;
  categoria: string;
  status: string;
  updatedAt: string;
  clienteNome: string;
  valor: number;
}

const STATUS_LABEL: Record<string, string> = {
  CONCLUIDO: 'Concluído',
  CANCELADO: 'Cancelado',
  EM_DISPUTA: 'Em disputa',
};

const STATUS_COLOR: Record<string, { bg: string; text: string }> = {
  CONCLUIDO: { bg: color.successTint, text: color.success },
  CANCELADO: { bg: color.surface, text: color.textFaint },
  EM_DISPUTA: { bg: color.terraTint, text: color.terraInk },
};

// Antes deste endpoint, o único jeito de um prestador ver o que já atendeu era
// ActiveJobScreen — que resolve só o atendimento ATIVO (GET /service-requests/active).
// Histórico (concluído/cancelado/em disputa) não tinha tela nenhuma (Trello: "Histórico do
// prestador"), depois da limpeza que removeu a aba fake "Como prestador" do MyRequestsScreen.
export default function ProviderHistoryScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const token = useAuthStore(s => s.accessToken);
  const [items, setItems] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [hasError, setHasError] = useState<ScreenErrorInfo | null>(null);

  const load = useCallback(async () => {
    setHasError(null);
    try {
      const res = await fetch(`${API_BASE}/service-requests/history`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new HttpError(res.status);
      const data = await res.json();
      setItems(Array.isArray(data) ? data : []);
    } catch (e) {
      setHasError(screenStateError(e));
      setItems([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token]);

  useFocusEffect(useCallback(() => { load(); }, [load]));

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.headerBlock}>
        <Text style={styles.title}>Histórico</Text>
      </View>

      {loading ? (
        <View style={styles.groups}><SkeletonList variant="request" count={3} /></View>
      ) : hasError || items.length === 0 ? (
        <ScreenState
          state={hasError ? 'error' : 'empty'}
          icon="clock"
          emptyTitle="Nenhum atendimento no histórico ainda"
          emptyBody="Serviços concluídos, cancelados ou em disputa aparecem aqui."
          errorTitle={hasError?.title}
          errorBody={hasError?.body}
          onRetry={() => { setLoading(true); load(); }}
        />
      ) : (
        <ScrollView
          contentContainerStyle={styles.list}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }}
              colors={[color.primary]} tintColor={color.primary} />
          }
        >
          {items.map(item => {
            const st = STATUS_COLOR[item.status] ?? { bg: color.surface, text: color.textSoft };
            return (
              <TouchableOpacity
                key={item.id}
                testID="historico-card"
                style={styles.card}
                onPress={() => nav.navigate('RequestDetail', { requestId: item.id })}
                activeOpacity={0.85}
              >
                <View style={styles.cardTop}>
                  <Text style={styles.cardTitle}>{item.categoria}</Text>
                  <View style={[styles.badge, { backgroundColor: st.bg }]}>
                    <Text style={[styles.badgeText, { color: st.text }]}>
                      {STATUS_LABEL[item.status] ?? item.status}
                    </Text>
                  </View>
                </View>
                <View style={styles.cardBottom}>
                  <Text style={styles.cardSub}>
                    {item.clienteNome} · {new Date(item.updatedAt).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' })}
                  </Text>
                  {item.status === 'CONCLUIDO' && (
                    <Text style={styles.cardValor}>R$ {item.valor.toFixed(2).replace('.', ',')}</Text>
                  )}
                </View>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  headerBlock: { paddingHorizontal: space[5], paddingTop: 8, paddingBottom: 14 },
  title: { fontSize: 24, fontWeight: font.weight.black, letterSpacing: -0.5, color: color.text },
  groups: { paddingHorizontal: space[5], paddingTop: 4 },
  list: { paddingHorizontal: space[5], paddingBottom: 40, gap: 12 },
  card: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.card,
    padding: 15,
    gap: 10,
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardTitle: { fontSize: 16, fontWeight: font.weight.bold, color: color.text, flex: 1, marginRight: 8 },
  badge: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: radius.pill },
  badgeText: { fontSize: 11.5, fontWeight: font.weight.bold },
  cardBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardSub: { fontSize: 13, color: color.textSoft },
  cardValor: { fontSize: 14, fontWeight: font.weight.bold, color: color.institutional2 },
});
