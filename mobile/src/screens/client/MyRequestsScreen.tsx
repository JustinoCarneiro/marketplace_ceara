import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ClientNavProp } from '../../navigation/types';
import { useAuthStore } from '../../store/auth';
import ScreenState from '../../components/ScreenState';

interface Request {
  id: string;
  categoria: string;
  descricao: string;
  status: string;
  updatedAt: string;
  prestadorNome?: string;
  propostas?: number;
  transacao?: { valorTotal: number };
}

const COLORS = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#8A989B',
  primary: '#14A8A0',
  institutional: '#0E3F52',
  institutional2: '#15596E',
  lineSoft: '#E6DDC9',
  line: '#DCD2BC',
  bgAlt: '#EAE0CB',
  danger: '#C0392B',
  concluido: '#15756E',
  concluidoBg: '#DDF0EC',
  proposta: '#B5810A',
  propostaBg: '#FDF3D6',
  propostaDot: '#F2B015',
};

type GroupKey = 'EM_ANDAMENTO' | 'AGUARDANDO' | 'CONCLUIDOS' | 'OUTROS';

function getGroup(status: string): GroupKey {
  if (status === 'EM_ANDAMENTO') return 'EM_ANDAMENTO';
  if (['PROPOSTO', 'ACEITO', 'PENDENTE'].includes(status)) return 'AGUARDANDO';
  if (status === 'CONCLUIDO') return 'CONCLUIDOS';
  return 'OUTROS';
}

const GROUP_LABELS: Record<GroupKey, string> = {
  EM_ANDAMENTO: 'Em andamento',
  AGUARDANDO: 'Aguardando',
  CONCLUIDOS: 'Concluídos',
  OUTROS: 'Outros',
};

const GROUP_ORDER: GroupKey[] = ['EM_ANDAMENTO', 'AGUARDANDO', 'CONCLUIDOS', 'OUTROS'];

function StatusBadgeInline({ status, propostas }: { status: string; propostas?: number }) {
  if (status === 'EM_ANDAMENTO') {
    return (
      <View style={[inlineBadge.wrap, { backgroundColor: COLORS.primary }]}>
        <View style={[inlineBadge.dot, { backgroundColor: '#fff' }]} />
        <Text style={[inlineBadge.text, { color: '#fff' }]}>EM ANDAMENTO</Text>
      </View>
    );
  }
  if (status === 'PROPOSTO' && propostas && propostas > 0) {
    return (
      <View style={[inlineBadge.wrap, { backgroundColor: COLORS.propostaBg }]}>
        <View style={[inlineBadge.dot, { backgroundColor: COLORS.propostaDot }]} />
        <Text style={[inlineBadge.text, { color: COLORS.proposta }]}>{propostas} PROPOSTAS</Text>
      </View>
    );
  }
  if (status === 'CONCLUIDO') {
    return (
      <View style={[inlineBadge.wrap, { backgroundColor: COLORS.concluidoBg }]}>
        <Feather name="check" size={11} color={COLORS.concluido} />
        <Text style={[inlineBadge.text, { color: COLORS.concluido }]}>CONCLUÍDO</Text>
      </View>
    );
  }
  if (status === 'ACEITO') {
    return (
      <View style={[inlineBadge.wrap, { backgroundColor: '#E2EEF2' }]}>
        <View style={[inlineBadge.dot, { backgroundColor: '#15596E' }]} />
        <Text style={[inlineBadge.text, { color: '#15596E' }]}>ACEITO</Text>
      </View>
    );
  }
  return (
    <View style={[inlineBadge.wrap, { backgroundColor: '#F6EEDC', borderWidth: 1, borderColor: COLORS.lineSoft }]}>
      <View style={[inlineBadge.dot, { backgroundColor: COLORS.textFaint }]} />
      <Text style={[inlineBadge.text, { color: COLORS.textFaint }]}>{status}</Text>
    </View>
  );
}

const inlineBadge = StyleSheet.create({
  wrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingVertical: 4,
    paddingHorizontal: 9,
    borderRadius: 100,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
  text: {
    fontSize: 12,
    fontWeight: '700',
  },
});

export default function MyRequestsScreen() {
  const nav = useNavigation<ClientNavProp>();
  const token = useAuthStore(s => s.accessToken);
  const [requests, setRequests] = useState<Request[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [hasError, setHasError] = useState(false);
  const [tab, setTab] = useState<'cliente' | 'prestador'>('cliente');

  async function load() {
    setHasError(false);
    try {
      const res = await fetch(`${API_BASE}/service-requests/my`, {
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
  }

  useEffect(() => { load(); }, []);

  const grouped: Partial<Record<GroupKey, Request[]>> = {};
  for (const r of requests) {
    const g = getGroup(r.status);
    if (!grouped[g]) grouped[g] = [];
    grouped[g]!.push(r);
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={() => { setRefreshing(true); load(); }}
            colors={[COLORS.primary]}
            tintColor={COLORS.primary}
          />
        }
      >
        <View style={styles.headerBlock}>
          <Text style={styles.title}>Meus pedidos</Text>
          <View style={styles.tabs}>
            <TouchableOpacity
              style={[styles.tabPill, tab === 'cliente' && styles.tabPillActive]}
              onPress={() => setTab('cliente')}
              activeOpacity={0.8}
            >
              <Text style={[styles.tabText, tab === 'cliente' && styles.tabTextActive]}>Como cliente</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.tabPill, tab === 'prestador' && styles.tabPillActive]}
              onPress={() => setTab('prestador')}
              activeOpacity={0.8}
            >
              <Text style={[styles.tabText, tab === 'prestador' && styles.tabTextActive]}>Como prestador</Text>
            </TouchableOpacity>
          </View>
        </View>

        {loading || hasError || requests.length === 0 ? (
          <ScreenState
            state={loading ? 'loading' : hasError ? 'error' : 'empty'}
            icon="clipboard"
            emptyTitle="Nenhum pedido ainda"
            emptyBody="Seus chamados aparecerão aqui."
            onRetry={() => { setLoading(true); load(); }}
          />
        ) : (
          <View style={styles.groups}>
            {GROUP_ORDER.map(g => {
              const items = grouped[g];
              if (!items || items.length === 0) return null;
              return (
                <View key={g} style={styles.group}>
                  <Text style={styles.groupLabel}>{GROUP_LABELS[g]}</Text>
                  {items.map(item => (
                    <TouchableOpacity
                      key={item.id}
                      style={styles.card}
                      onPress={() => nav.navigate('RequestDetail', { requestId: item.id })}
                      activeOpacity={0.85}
                    >
                      <View style={styles.cardTop}>
                        <Text style={styles.cardTitle}>{item.categoria}</Text>
                        <StatusBadgeInline status={item.status} propostas={item.propostas} />
                      </View>
                      <View style={styles.cardBottom}>
                        {item.prestadorNome ? (
                          <Text style={styles.cardSub}>{item.prestadorNome}{item.transacao ? ` · R$ ${item.transacao.valorTotal.toFixed(2).replace('.', ',')} retido` : ''}</Text>
                        ) : item.status === 'CONCLUIDO' ? (
                          <View style={styles.cardBottomRow}>
                            <Text style={styles.cardSub}>{item.prestadorNome ?? ''} · {new Date(item.updatedAt).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' })}</Text>
                            <Text style={styles.avaliarLink}>Avaliar</Text>
                          </View>
                        ) : (
                          <Text style={styles.cardSub}>{item.categoria} · {new Date(item.updatedAt).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' })}</Text>
                        )}
                      </View>
                    </TouchableOpacity>
                  ))}
                </View>
              );
            })}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.bg },
  scrollContent: { paddingBottom: 40 },
  headerBlock: {
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 14,
    gap: 14,
  },
  title: {
    fontSize: 24,
    fontWeight: '800',
    letterSpacing: -0.5,
    color: COLORS.text,
  },
  tabs: {
    flexDirection: 'row',
    backgroundColor: COLORS.bgAlt,
    borderRadius: 100,
    padding: 4,
    gap: 0,
  },
  tabPill: {
    flex: 1,
    paddingVertical: 9,
    borderRadius: 100,
    alignItems: 'center',
  },
  tabPillActive: {
    backgroundColor: COLORS.institutional,
  },
  tabText: {
    fontSize: 13.5,
    fontWeight: '600',
    color: COLORS.textSoft,
  },
  tabTextActive: {
    color: '#fff',
    fontWeight: '700',
  },
  groups: {
    paddingHorizontal: 20,
    paddingTop: 4,
    gap: 18,
  },
  group: {
    gap: 10,
  },
  groupLabel: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.1,
    textTransform: 'uppercase',
    color: COLORS.textFaint,
  },
  card: {
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.lineSoft,
    borderRadius: 24,
    padding: 15,
    gap: 10,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.45,
    shadowRadius: 28,
    elevation: 3,
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: COLORS.text,
    flex: 1,
    marginRight: 8,
  },
  cardBottom: {},
  cardBottomRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  cardSub: {
    fontSize: 13,
    color: COLORS.textSoft,
  },
  avaliarLink: {
    fontSize: 13,
    fontWeight: '700',
    color: COLORS.primary,
  },
});
