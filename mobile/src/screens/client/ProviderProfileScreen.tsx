import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  ActivityIndicator, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import StarRating from '../../components/StarRating';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'ProviderProfile'>;

interface Review { autorNome: string; nota: number; comentario: string; }
interface ProviderProfile {
  userId: string;
  nome: string;
  categoria: string;
  bio?: string;
  notaMedia: number | null;
  totalAvaliacoes?: number;
  totalServicos?: number;
  tempoRespostaMin?: number;
  pontualidadePct?: number;
  precoMin?: number;
  precoMax?: number;
  statusVerificacao: string;
  avaliacoes?: Review[];
}

function initials(nome: string) {
  return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
}

const AVATAR_COLORS = [color.warmTerra, color.catHidraulica, color.catLimpeza, color.catReforma];
function avatarBg(nome: string) { return AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length]; }

export default function ProviderProfileScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [profile, setProfile] = useState<ProviderProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(
          `${API_BASE}/providers/${route.params.providerId}`,
          { headers: { Authorization: `Bearer ${token}` } },
        );
        const data = await res.json();
        setProfile(res.ok ? data : null);
      } catch {
        setProfile(null);
      } finally {
        setLoading(false);
      }
    })();
  }, [route.params.providerId]);

  if (loading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}><ActivityIndicator color={color.primary} size="large" /></View>
      </SafeAreaView>
    );
  }

  if (!profile) {
    return (
      <SafeAreaView style={styles.safe}>
        <TouchableOpacity onPress={() => nav.goBack()} style={styles.backPad}>
          <Text style={styles.backIcon}>‹</Text>
        </TouchableOpacity>
        <View style={styles.center}><Text style={styles.emptyText}>Perfil não encontrado.</Text></View>
      </SafeAreaView>
    );
  }

  const bg = avatarBg(profile.nome);
  const init = initials(profile.nome);
  const nota = profile.notaMedia ?? 0;
  const isVerified = profile.statusVerificacao === 'VERIFICADO';
  const precoStr = profile.precoMin && profile.precoMax
    ? `R$ ${profile.precoMin} – R$ ${profile.precoMax}`
    : null;

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* Back button */}
        <TouchableOpacity onPress={() => nav.goBack()} style={styles.backPad} hitSlop={12}>
          <Text style={styles.backIcon}>‹</Text>
        </TouchableOpacity>

        {/* Hero: avatar + nome + badge + nota */}
        <View style={styles.hero}>
          <View style={[styles.avatar, { backgroundColor: bg }]}>
            <Text style={styles.avatarText}>{init}</Text>
          </View>
          <Text style={styles.name}>{profile.nome}</Text>
          {isVerified && (
            <View style={styles.verifiedBadge}>
              <Text style={styles.verifiedBadgeIcon}>✓</Text>
              <Text style={styles.verifiedBadgeText}>PRESTADOR VERIFICADO</Text>
            </View>
          )}
          <View style={styles.ratingMeta}>
            <StarRating value={nota} size={16} readonly />
            <Text style={styles.ratingVal}>{nota > 0 ? nota.toFixed(1) : 'Novo'}</Text>
            {profile.totalAvaliacoes ? (
              <Text style={styles.metaText}>· {profile.totalAvaliacoes} avaliações</Text>
            ) : null}
            <Text style={styles.metaText}>· {profile.categoria}</Text>
          </View>
        </View>

        {/* Stats row */}
        <View style={styles.statsRow}>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{profile.totalServicos ?? 0}+</Text>
            <Text style={styles.statLabel}>serviços</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>
              {profile.tempoRespostaMin ? `~${profile.tempoRespostaMin} min` : '—'}
            </Text>
            <Text style={styles.statLabel}>resposta</Text>
          </View>
          <View style={[styles.statBox, { borderRightWidth: 0 }]}>
            <Text style={[styles.statValue, { color: color.success }]}>
              {profile.pontualidadePct ? `${profile.pontualidadePct}%` : '—'}
            </Text>
            <Text style={styles.statLabel}>no prazo</Text>
          </View>
        </View>

        {/* Faixa de preço */}
        {precoStr && (
          <View style={styles.priceChip}>
            <Text style={styles.priceLabel}>Faixa de preço</Text>
            <Text style={styles.priceValue}>{precoStr}</Text>
          </View>
        )}

        {/* Bio */}
        {profile.bio && (
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>SOBRE MIM</Text>
            <Text style={styles.bio}>{profile.bio}</Text>
          </View>
        )}

        {/* Avaliações */}
        {profile.avaliacoes && profile.avaliacoes.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>AVALIAÇÕES RECENTES</Text>
            <View style={styles.reviewList}>
              {profile.avaliacoes.slice(0, 3).map((r, i) => (
                <View key={i} style={styles.reviewCard}>
                  <View style={styles.reviewHeader}>
                    <View style={styles.reviewAvatar}>
                      <Text style={styles.reviewAvatarText}>
                        {r.autorNome.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
                      </Text>
                    </View>
                    <Text style={styles.reviewAuthor}>{r.autorNome}</Text>
                    <View style={styles.reviewStars}>
                      {Array.from({ length: 5 }).map((_, si) => (
                        <Text key={si} style={{ fontSize: 12, color: si < r.nota ? color.warmSun : color.lineSoft }}>★</Text>
                      ))}
                    </View>
                  </View>
                  <Text style={styles.reviewText}>{r.comentario}</Text>
                </View>
              ))}
            </View>
          </View>
        )}

        {/* Escrow trust */}
        <View style={styles.escrowBanner}>
          <Text style={{ fontSize: 20 }}>🔒</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.escrowTitle}>Pagamento seguro com escrow</Text>
            <Text style={styles.escrowBody}>
              Seu dinheiro fica retido até o serviço ser concluído com sua aprovação.
            </Text>
          </View>
        </View>

        <View style={{ height: space[7] }} />
      </ScrollView>

      {/* CTA fixo */}
      <View style={styles.footer}>
        <Button
          label="Solicitar serviço"
          onPress={() => nav.navigate('NewRequest', { providerId: profile.userId, categoria: profile.categoria })}
          icon={<Text style={{ color: color.textOnAccent, fontSize: 18 }}>→</Text>}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: font.size.bodySm, color: color.textSoft },

  scroll: { paddingBottom: space[4] },

  backPad: { paddingHorizontal: space[5], paddingVertical: space[3] },
  backIcon: { fontSize: 28, color: color.text, lineHeight: 32 },

  hero: { alignItems: 'center', gap: space[3], paddingHorizontal: space[5], paddingBottom: space[5] },
  avatar: { width: 88, height: 88, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontSize: 30, fontWeight: font.weight.black, color: color.textOnAccent },
  name: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.02 * font.size.h1 },
  verifiedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: color.institutional,
    borderRadius: radius.pill,
    paddingHorizontal: space[3] + 2,
    paddingVertical: 5,
  },
  verifiedBadgeIcon: { fontSize: 11, color: color.textOnAccent, fontWeight: font.weight.black },
  verifiedBadgeText: { fontSize: 11, fontWeight: font.weight.black, color: color.textOnAccent, letterSpacing: 0.5 },
  ratingMeta: { flexDirection: 'row', alignItems: 'center', gap: 5, flexWrap: 'wrap', justifyContent: 'center' },
  ratingVal: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.text },
  metaText: { fontSize: font.size.caption, color: color.textSoft },

  statsRow: {
    flexDirection: 'row',
    marginHorizontal: space[5],
    backgroundColor: color.surface,
    borderRadius: radius.field,
    borderWidth: 1,
    borderColor: color.lineSoft,
    overflow: 'hidden',
  },
  statBox: {
    flex: 1,
    alignItems: 'center',
    padding: space[4],
    borderRightWidth: 1,
    borderRightColor: color.lineSoft,
    gap: 3,
  },
  statValue: { fontSize: font.size.h3, fontWeight: font.weight.black, color: color.text },
  statLabel: { fontSize: font.size.caption, color: color.textSoft },

  priceChip: {
    marginHorizontal: space[5],
    backgroundColor: color.skyTint,
    borderWidth: 1,
    borderColor: color.accentSky,
    borderRadius: radius.field,
    padding: space[4],
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  priceLabel: { fontSize: font.size.caption, fontWeight: font.weight.semibold, color: color.institutional2 },
  priceValue: { fontSize: font.size.h3, fontWeight: font.weight.black, color: color.institutional },

  section: { paddingHorizontal: space[5], gap: space[3] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  bio: { fontSize: font.size.body, color: color.textSoft, lineHeight: font.size.body * font.lineHeight.body },

  reviewList: { gap: space[3] },
  reviewCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: space[4],
    gap: space[2],
    ...shadow.soft,
  },
  reviewHeader: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  reviewAvatar: {
    width: 34,
    height: 34,
    borderRadius: 11,
    backgroundColor: color.institutional2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reviewAvatarText: { fontSize: 12, fontWeight: font.weight.bold, color: color.textOnAccent },
  reviewAuthor: { flex: 1, fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.text },
  reviewStars: { flexDirection: 'row', gap: 1 },
  reviewText: { fontSize: font.size.caption, color: color.textSoft, lineHeight: font.size.caption * 1.55 },

  escrowBanner: {
    marginHorizontal: space[5],
    flexDirection: 'row',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
  },
  escrowTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.institutional },
  escrowBody: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2, lineHeight: font.size.caption * 1.5 },

  footer: {
    padding: space[5],
    paddingBottom: space[6],
    borderTopWidth: 1,
    borderTopColor: color.lineSoft,
    backgroundColor: color.surface,
  },
});
