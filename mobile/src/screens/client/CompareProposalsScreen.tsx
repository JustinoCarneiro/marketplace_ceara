import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ClientStackParams, 'CompareProposals'>;

interface Proposal {
  id: string;
  prestadorId: string;
  prestadorNome?: string;
  prestadorNota?: number;
  prestadorAvaliacoes?: number;
  valor: number;
  prazoDias: number;
}

const AVATAR_COLORS = [color.warmTerra, '#244C86', color.catLimpeza, '#3C7A4E', color.catHidraulica];
function avatarBg(nome: string) { return AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length]; }
function initials(nome: string) { return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase(); }

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
          { headers: { Authorization: `Bearer ${token}` } },
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

  function accept(p: Proposal) {
    nav.navigate('PaymentChoice', {
      requestId: route.params.requestId,
      proposalId: p.id,
      valor: p.valor,
    });
  }

  return (
    <SafeAreaView style={styles.safe}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12}>
          <Feather name="chevron-left" size={22} color={color.text} />
        </TouchableOpacity>
        <View style={styles.headerTitles}>
          <Text style={styles.headerTitle}>Propostas recebidas</Text>
          <Text style={styles.headerSub}>{proposals.length} propostas · Instalação elétrica</Text>
        </View>
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color={color.primary} size="large" />
        </View>
      ) : proposals.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.emptyTitle}>Aguardando propostas</Text>
          <Text style={styles.emptyBody}>
            Prestadores próximos estão visualizando seu pedido. Você receberá uma notificação.
          </Text>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
          {proposals.map((p, i) => {
            const isBest = i === 0;
            const bg = avatarBg(p.prestadorNome ?? 'P');
            const init = initials(p.prestadorNome ?? 'Prestador');
            return (
              <View key={p.id} style={isBest ? styles.bestWrapper : styles.cardWrapper}>
                <View style={isBest ? styles.bestCard : styles.card}>
                  {isBest && (
                    <View style={styles.bestBadge}>
                      <Text style={styles.bestBadgeText}>MELHOR CUSTO</Text>
                    </View>
                  )}

                  {/* Provider row */}
                  <View style={styles.providerRow}>
                    <View style={[styles.avatar, { backgroundColor: bg }]}>
                      <Text style={styles.avatarText}>{init}</Text>
                    </View>
                    <View style={styles.providerInfo}>
                      <View style={styles.nameRow}>
                        <Text style={styles.name}>{p.prestadorNome ?? 'Prestador'}</Text>
                        <Feather name="shield" size={13} color={color.institutional} />
                      </View>
                      <View style={styles.ratingRow}>
                        <Feather name="star" size={13} color={color.warmSun} />
                        <Text style={styles.ratingText}>
                          {(p.prestadorNota ?? 0).toFixed(1)}
                          {p.prestadorAvaliacoes ? ` · ${p.prestadorAvaliacoes} avaliações` : ''}
                        </Text>
                      </View>
                    </View>
                  </View>

                  {/* Price + deadline boxes */}
                  <View style={styles.infoBoxes}>
                    <View style={styles.infoBox}>
                      <Text style={styles.infoBoxLabel}>Valor</Text>
                      <Text style={styles.infoBoxValue}>R$ {p.valor}</Text>
                    </View>
                    <View style={styles.infoBox}>
                      <Text style={styles.infoBoxLabel}>Prazo</Text>
                      <Text style={styles.infoBoxValue}>{p.prazoDias} {p.prazoDias === 1 ? 'dia' : 'dias'}</Text>
                    </View>
                  </View>

                  {/* CTA */}
                  <TouchableOpacity
                    style={isBest ? styles.acceptBtnFilled : styles.acceptBtnOutline}
                    onPress={() => accept(p)}
                    activeOpacity={0.85}
                  >
                    <Text style={isBest ? styles.acceptBtnFilledText : styles.acceptBtnOutlineText}>
                      Aceitar proposta
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>
            );
          })}

          {/* Footer note */}
          <View style={styles.footerNote}>
            <Feather name="info" size={15} color={color.textFaint} />
            <Text style={styles.footerText}>
              Ao aceitar uma proposta, as demais são encerradas e você segue para o pagamento.
            </Text>
          </View>
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space[3], padding: space[5] },
  emptyTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  emptyBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center', lineHeight: font.size.bodySm * 1.6 },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[5],
    paddingVertical: space[4],
  },
  headerTitles: { flexDirection: 'column' },
  headerTitle: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.02 * font.size.h2 },
  headerSub: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2 },

  scroll: { paddingHorizontal: space[5], paddingBottom: space[7], gap: space[4] },

  bestWrapper: { paddingTop: 10 },
  cardWrapper: {},
  bestCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1.5,
    borderColor: color.primary,
    padding: space[4],
    gap: space[4],
    ...shadow.soft,
    shadowColor: color.primary,
    shadowOpacity: 0.3,
  },
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: space[4],
    gap: space[4],
    ...shadow.soft,
  },

  bestBadge: {
    position: 'absolute',
    top: -10,
    left: 16,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    paddingHorizontal: space[3],
    paddingVertical: 3,
  },
  bestBadgeText: { fontSize: 12, fontWeight: font.weight.black, color: color.textOnAccent, letterSpacing: 0.06 * 12 },

  providerRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontSize: 16, fontWeight: font.weight.black, color: color.textOnAccent },
  providerInfo: { flex: 1, gap: 4 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  name: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  ratingText: { fontSize: font.size.caption, color: color.textSoft },

  infoBoxes: { flexDirection: 'row', gap: space[3] },
  infoBox: {
    flex: 1,
    backgroundColor: color.bg,
    borderRadius: radius.field,
    padding: space[3],
  },
  infoBoxLabel: { fontSize: 12, color: color.textSoft },
  infoBoxValue: { fontSize: font.size.h3, fontWeight: font.weight.black, color: color.text, marginTop: 2 },

  acceptBtnFilled: {
    height: 48,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.4,
    shadowRadius: 16,
    elevation: 4,
  },
  acceptBtnFilledText: { fontSize: 15, fontWeight: font.weight.bold, color: color.textOnAccent },

  acceptBtnOutline: {
    height: 48,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: color.text,
    alignItems: 'center',
    justifyContent: 'center',
  },
  acceptBtnOutlineText: { fontSize: 15, fontWeight: font.weight.bold, color: color.text },

  footerNote: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space[2],
    paddingHorizontal: space[1],
  },
  footerText: { flex: 1, fontSize: 12, color: color.textFaint, lineHeight: 12 * 1.4 },
});
