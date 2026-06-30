import { API_BASE } from '../../api/config';
import React, { useEffect, useState, useCallback } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, ActivityIndicator, RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ProviderNavProp } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenState from '../../components/ScreenState';

interface ServiceRequest {
  id: string;
  titulo?: string;
  descricao?: string;
  categoria: string;
  bairro?: string;
  distanciaKm?: number;
  orcamentoMin?: number;
  orcamentoMax?: number;
  status: string;
  criadoEm?: string;
}

function timeAgo(iso?: string) {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `há ${mins} min`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `há ${hrs} h`;
  return `há ${Math.floor(hrs / 24)} d`;
}

const CAT_COLORS: Record<string, string> = {
  ELETRICA: color.sunInk,
  HIDRAULICA: color.institutional2,
  LIMPEZA: color.success,
  PINTURA: color.terraInk,
  REFORMA: '#244C86',
  JARDINAGEM: '#3C7A4E',
};
const CAT_BG: Record<string, string> = {
  ELETRICA: color.sunTint,
  HIDRAULICA: color.skyTint,
  LIMPEZA: color.successTint,
  PINTURA: color.terraTint,
  REFORMA: '#E8EEFA',
  JARDINAGEM: '#E2F0E6',
};

export default function AvailableRequestsScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const token = useAuthStore(s => s.accessToken);
  const [requests, setRequests] = useState<ServiceRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [hasError, setHasError] = useState(false);

  const load = useCallback(async () => {
    setHasError(false);
    try {
      const res = await fetch(`${API_BASE}/providers/available-requests`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('HTTP error');
      const data = await res.json();
      setRequests(Array.isArray(data) ? data : []);
    } catch {
      setHasError(true);
      setRequests([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [token]);

  useEffect(() => { load(); }, [load]);

  const catKey = (r: ServiceRequest) => (r.categoria ?? '').toUpperCase().replace(/\s/g, '');
  const catLabel = (r: ServiceRequest) => {
    const map: Record<string, string> = {
      ELETRICA: 'Elétrica', HIDRAULICA: 'Hidráulica',
      LIMPEZA: 'Limpeza', PINTURA: 'Pintura',
      REFORMA: 'Reforma', JARDINAGEM: 'Jardinagem',
    };
    return map[catKey(r)] ?? r.categoria;
  };

  const preco = (r: ServiceRequest) => {
    if (!r.orcamentoMin && !r.orcamentoMax) return null;
    if (r.orcamentoMin && r.orcamentoMax) return `R$ ${r.orcamentoMin} – R$ ${r.orcamentoMax}`;
    return `R$ ${r.orcamentoMin ?? r.orcamentoMax}`;
  };

  return (
    <SafeAreaView style={styles.safe}>
      {/* Sticky header */}
      <View style={styles.header}>
        <View style={styles.headerLeft}>
          <Text style={styles.headerTitle}>Pedidos disponíveis</Text>
          <Text style={styles.headerSub}>Elétrica · até 10 km</Text>
        </View>
        <TouchableOpacity style={styles.filterBtn} hitSlop={8}
          accessibilityLabel="Filtros" accessibilityRole="button">
          <Feather name="sliders" size={20} color={color.text} accessibilityElementsHidden />
        </TouchableOpacity>
      </View>

      {loading || hasError ? (
        <ScreenState
          state={loading ? 'loading' : 'error'}
          onRetry={() => { setLoading(true); load(); }}
        />
      ) : (
        <FlatList
          data={requests}
          keyExtractor={r => r.id}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: space[3] + 2 }} />}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} tintColor={color.primary} />}
          ListEmptyComponent={
            <ScreenState
              state="empty"
              icon="inbox"
              emptyTitle="Nenhum pedido no momento"
              emptyBody="Novos pedidos aparecem aqui em tempo real."
            />
          }
          renderItem={({ item: r }) => {
            const ck = catKey(r);
            const catColor = CAT_COLORS[ck] ?? color.textSoft;
            const catBg = CAT_BG[ck] ?? color.surface;
            return (
              <View style={styles.card}>
                {/* Status + cat + time */}
                <View style={styles.cardTopRow}>
                  <View style={styles.pendenteBadge}>
                    <View style={styles.pendenteDot} />
                    <Text style={styles.pendenteText}>PENDENTE</Text>
                  </View>
                  <View style={[styles.catChip, { backgroundColor: catBg }]}>
                    <Text style={[styles.catChipText, { color: catColor }]}>{catLabel(r)}</Text>
                  </View>
                  <Text style={styles.timeText}>{timeAgo(r.criadoEm)}</Text>
                </View>

                {/* Title + location */}
                <View style={styles.cardBody}>
                  <Text style={styles.cardTitle} numberOfLines={2}>
                    {r.titulo ?? r.descricao ?? 'Sem título'}
                  </Text>
                  {(r.bairro || r.distanciaKm) && (
                    <Text style={styles.cardLoc}>
                      {r.bairro}{r.bairro && r.distanciaKm ? ' · ' : ''}{r.distanciaKm ? `${r.distanciaKm.toFixed(1)} km de você` : ''}
                    </Text>
                  )}
                </View>

                {/* Price + CTA */}
                <View style={styles.cardFooter}>
                  {preco(r) && <Text style={styles.cardPreco}>{preco(r)}</Text>}
                  <TouchableOpacity
                    style={styles.proposalBtn}
                    onPress={() => nav.navigate('SendProposal', { requestId: r.id })}
                    activeOpacity={0.85}
                  >
                    <Text style={styles.proposalBtnText}>Enviar proposta</Text>
                  </TouchableOpacity>
                </View>
              </View>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[5],
    paddingVertical: space[3] + 2,
    backgroundColor: color.bg,
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
  },
  headerLeft: { gap: 2 },
  headerTitle: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.02 * font.size.h2 },
  headerSub: { fontSize: font.size.caption, color: color.textSoft },
  filterBtn: {
    width: 42,
    height: 42,
    borderRadius: radius.field,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },

  list: { padding: space[5], paddingTop: space[4] },

  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: space[4],
    gap: space[3],
    ...shadow.soft,
  },
  cardTopRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },

  pendenteBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: color.surface2,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.pill,
    paddingHorizontal: 9,
    paddingVertical: 4,
  },
  pendenteDot: { width: 7, height: 7, borderRadius: 3.5, backgroundColor: color.textFaint },
  pendenteText: { fontSize: 12, fontWeight: font.weight.black, color: color.textFaint, letterSpacing: 0.5 },

  catChip: { borderRadius: radius.pill, paddingHorizontal: 9, paddingVertical: 4 },
  catChipText: { fontSize: 12, fontWeight: font.weight.bold },

  timeText: { marginLeft: 'auto', fontSize: 12, color: color.textFaint },

  cardBody: { gap: 2 },
  cardTitle: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  cardLoc: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2 },

  cardFooter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  cardPreco: { fontSize: 15, fontWeight: font.weight.black, color: color.institutional2 },

  proposalBtn: {
    height: 42,
    paddingHorizontal: space[5],
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  proposalBtnText: { fontSize: 14, fontWeight: font.weight.bold, color: color.textOnAccent },
});
