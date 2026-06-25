import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import StarRating from '../../components/StarRating';

type RouteProps = RouteProp<ClientStackParams, 'CompareProposals'>;

interface Proposal {
  id: string;
  prestadorId: string;
  prestadorNome?: string;
  prestadorNota?: number;
  valor: number;
  prazoDias: number;
  status: string;
}

export default function CompareProposalsScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(
          `${API_BASE}/service-requests/${route.params.requestId}/proposals`,
          { headers: { Authorization: `Bearer ${token}` } }
        );
        const data = await res.json();
        setProposals(Array.isArray(data) ? data : []);
      } catch {
        setProposals([]);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  function accept(proposal: Proposal) {
    nav.navigate('PaymentChoice', {
      requestId: route.params.requestId,
      proposalId: proposal.id,
      valor: proposal.valor,
    });
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="Propostas recebidas" onBack={() => nav.goBack()} />

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color={color.primary} size="large" />
        </View>
      ) : proposals.length === 0 ? (
        <View style={styles.center}>
          <Text style={{ fontSize: 48 }}>⏳</Text>
          <Text style={styles.emptyTitle}>Aguardando propostas</Text>
          <Text style={styles.emptyBody}>
            Prestadores próximos estão visualizando seu pedido. Você será notificado quando chegarem propostas.
          </Text>
        </View>
      ) : (
        <FlatList
          data={proposals}
          keyExtractor={p => p.id}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: space[3] }} />}
          ListHeaderComponent={
            <View style={styles.tip}>
              <Text style={{ fontSize: 16 }}>💡</Text>
              <Text style={styles.tipText}>Compare prazo, valor e avaliação antes de aceitar.</Text>
            </View>
          }
          renderItem={({ item }) => (
            <View style={styles.card}>
              <View style={styles.cardTop}>
                <View style={styles.avatar}>
                  <Text style={{ fontSize: 24 }}>👷</Text>
                </View>
                <View style={styles.info}>
                  <Text style={styles.name}>{item.prestadorNome ?? 'Prestador'}</Text>
                  <StarRating value={item.prestadorNota ?? 0} size={14} readonly />
                </View>
                <View style={styles.valueBlock}>
                  <Text style={styles.value}>R$ {item.valor.toFixed(2).replace('.', ',')}</Text>
                  <Text style={styles.prazo}>{item.prazoDias}d</Text>
                </View>
              </View>

              <View style={styles.escrowRow}>
                <Text style={{ fontSize: 13 }}>🔒</Text>
                <Text style={styles.escrowText}>
                  Pagamento retido no escrow · liberado ao prestador após conclusão
                </Text>
              </View>

              <TouchableOpacity style={styles.acceptBtn} onPress={() => accept(item)} activeOpacity={0.85}>
                <Text style={styles.acceptBtnText}>Aceitar esta proposta</Text>
              </TouchableOpacity>
            </View>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space[3], padding: space[5] },
  emptyTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  emptyBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center', lineHeight: font.size.bodySm * 1.6 },
  list: { paddingHorizontal: space[5], paddingTop: space[3], paddingBottom: space[7], gap: space[3] },
  tip: {
    flexDirection: 'row',
    gap: space[2],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[3],
    marginBottom: space[2],
  },
  tipText: { flex: 1, fontSize: font.size.caption, color: color.textSoft },
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
    ...shadow.soft,
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', gap: space[4] },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: radius.field,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  info: { flex: 1, gap: 4 },
  name: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  valueBlock: { alignItems: 'flex-end', gap: 2 },
  value: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.primary },
  prazo: { fontSize: font.size.caption, color: color.textSoft },
  escrowRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[3],
  },
  escrowText: { flex: 1, fontSize: font.size.caption, color: color.institutional, fontWeight: font.weight.medium },
  acceptBtn: {
    height: 52,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.35,
    shadowRadius: 16,
    elevation: 4,
  },
  acceptBtnText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
});
