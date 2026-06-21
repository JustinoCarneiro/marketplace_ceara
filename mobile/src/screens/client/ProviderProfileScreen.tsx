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
import ScreenHeader from '../../components/ScreenHeader';
import StarRating from '../../components/StarRating';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'ProviderProfile'>;

interface ProviderProfile {
  userId: string;
  nome: string;
  categoria: string;
  bio?: string;
  notaMedia: number | null;
  statusVerificacao: string;
  totalServicos?: number;
}

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
          `http://10.0.2.2:8080/api/v1/providers/${route.params.providerId}`,
          { headers: { Authorization: `Bearer ${token}` } }
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
        <ScreenHeader title="Prestador" onBack={() => nav.goBack()} />
        <View style={styles.center}><ActivityIndicator color={color.primary} size="large" /></View>
      </SafeAreaView>
    );
  }

  if (!profile) {
    return (
      <SafeAreaView style={styles.safe}>
        <ScreenHeader title="Prestador" onBack={() => nav.goBack()} />
        <View style={styles.center}><Text style={styles.emptyText}>Perfil não encontrado.</Text></View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="" onBack={() => nav.goBack()} />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* Avatar + nome */}
        <View style={styles.heroCard}>
          <View style={styles.avatar}>
            <Text style={{ fontSize: 40 }}>👷</Text>
          </View>
          <Text style={styles.name}>{profile.nome}</Text>
          <Text style={styles.categoria}>{profile.categoria}</Text>

          <View style={styles.ratingRow}>
            <StarRating value={profile.notaMedia ?? 0} size={20} readonly />
            <Text style={styles.ratingText}>
              {profile.notaMedia ? profile.notaMedia.toFixed(1) : 'Novo'}
            </Text>
          </View>

          {profile.statusVerificacao === 'VERIFICADO' && (
            <View style={styles.verifiedBanner}>
              <Text style={styles.verifiedIcon}>🛡️</Text>
              <View>
                <Text style={styles.verifiedTitle}>Identidade verificada</Text>
                <Text style={styles.verifiedSub}>Conferido pela equipe Onda</Text>
              </View>
            </View>
          )}
        </View>

        {/* Bio */}
        {profile.bio && (
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>SOBRE MIM</Text>
            <Text style={styles.bio}>{profile.bio}</Text>
          </View>
        )}

        {/* Stats */}
        <View style={styles.statsRow}>
          <View style={styles.stat}>
            <Text style={styles.statValue}>{profile.totalServicos ?? 0}</Text>
            <Text style={styles.statLabel}>Serviços</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.stat}>
            <Text style={styles.statValue}>{profile.notaMedia?.toFixed(1) ?? '—'}</Text>
            <Text style={styles.statLabel}>Nota média</Text>
          </View>
          <View style={styles.statDivider} />
          <View style={styles.stat}>
            <Text style={styles.statValue}>✓</Text>
            <Text style={styles.statLabel}>Verificado</Text>
          </View>
        </View>

        {/* Escrow info */}
        <View style={styles.escrowBanner}>
          <Text style={styles.escrowIcon}>🔒</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.escrowTitle}>Pagamento seguro com escrow</Text>
            <Text style={styles.escrowBody}>Seu dinheiro fica retido até o serviço ser concluído com sua aprovação.</Text>
          </View>
        </View>
      </ScrollView>

      {/* CTA fixo */}
      <View style={styles.footer}>
        <Button
          label="Solicitar serviço"
          onPress={() => nav.navigate('NewRequest', { providerId: profile.userId, categoria: profile.categoria })}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: font.size.bodySm, color: color.textSoft },
  content: { paddingHorizontal: space[5], paddingBottom: space[7], gap: space[4] },

  heroCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    alignItems: 'center',
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
    ...shadow.soft,
  },
  avatar: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  name: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  categoria: { fontSize: font.size.bodySm, color: color.textSoft },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  ratingText: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.textSoft },

  verifiedBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    backgroundColor: color.institutional,
    borderRadius: radius.field,
    padding: space[3],
    paddingHorizontal: space[4],
    alignSelf: 'stretch',
  },
  verifiedIcon: { fontSize: 22 },
  verifiedTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.textOnAccent },
  verifiedSub: { fontSize: font.size.caption, color: color.accentSky },

  section: { gap: space[2] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  bio: { fontSize: font.size.body, color: color.textSoft, lineHeight: font.size.body * font.lineHeight.body },

  statsRow: {
    flexDirection: 'row',
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  stat: { flex: 1, alignItems: 'center', gap: 4 },
  statValue: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.text },
  statLabel: { fontSize: font.size.caption, color: color.textSoft },
  statDivider: { width: 1, backgroundColor: color.line, marginVertical: space[2] },

  escrowBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
  },
  escrowIcon: { fontSize: 22, marginTop: 2 },
  escrowTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.institutional },
  escrowBody: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2, lineHeight: font.size.caption * 1.5 },

  footer: { padding: space[5], paddingBottom: space[6], borderTopWidth: 1, borderTopColor: color.lineSoft, backgroundColor: color.bg },
});
