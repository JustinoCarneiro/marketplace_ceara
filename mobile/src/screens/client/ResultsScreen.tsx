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

type RouteProps = RouteProp<ClientStackParams, 'Results'>;

interface Provider {
  userId: string;
  nome: string;
  categoria: string;
  notaMedia: number | null;
  statusVerificacao: string;
  distanciaKm: number;
}

export default function ResultsScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const params = new URLSearchParams({
          lat: '-3.7319',
          lng: '-38.5267',
          raioKm: '10',
          ...(route.params?.categoria ? { categoria: route.params.categoria } : {}),
        });
        const res = await fetch(`${API_BASE}/providers/nearby?${params}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        const data = await res.json();
        setProviders(Array.isArray(data) ? data : []);
      } catch {
        setProviders([]);
      } finally {
        setLoading(false);
      }
    })();
  }, [route.params?.categoria]);

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader
        title={route.params?.categoria ? route.params.categoria : 'Prestadores próximos'}
        onBack={() => nav.goBack()}
        right={
          <TouchableOpacity onPress={() => nav.navigate('Filters')} hitSlop={8}>
            <Text style={{ fontSize: 20 }}>⚙️</Text>
          </TouchableOpacity>
        }
      />

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color={color.primary} size="large" />
          <Text style={styles.loadingText}>Buscando na sua região…</Text>
        </View>
      ) : providers.length === 0 ? (
        <View style={styles.center}>
          <Text style={{ fontSize: 48 }}>🔍</Text>
          <Text style={styles.emptyTitle}>Nenhum prestador encontrado</Text>
          <Text style={styles.emptyBody}>Tente outra categoria ou amplie o raio de busca.</Text>
        </View>
      ) : (
        <FlatList
          data={providers}
          keyExtractor={i => i.userId}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: space[3] }} />}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.card}
              onPress={() => nav.navigate('ProviderProfile', { providerId: item.userId })}
              activeOpacity={0.85}
            >
              <View style={styles.avatar}>
                <Text style={{ fontSize: 28 }}>👷</Text>
              </View>
              <View style={styles.info}>
                <View style={styles.nameRow}>
                  <Text style={styles.name}>{item.nome}</Text>
                  {item.statusVerificacao === 'VERIFICADO' && (
                    <View style={styles.verifiedBadge}>
                      <Text style={styles.verifiedText}>✓ Verificado</Text>
                    </View>
                  )}
                </View>
                <Text style={styles.categoria}>{item.categoria}</Text>
                <View style={styles.meta}>
                  <StarRating value={item.notaMedia ?? 0} size={14} readonly />
                  <Text style={styles.metaText}>
                    {item.notaMedia ? item.notaMedia.toFixed(1) : 'Novo'}
                  </Text>
                  <Text style={styles.dot}>·</Text>
                  <Text style={styles.metaText}>{item.distanciaKm.toFixed(1)} km</Text>
                </View>
              </View>
              <Text style={{ fontSize: 18, color: color.textFaint }}>›</Text>
            </TouchableOpacity>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space[3], padding: space[5] },
  loadingText: { fontSize: font.size.bodySm, color: color.textSoft },
  emptyTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  emptyBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center' },

  list: { paddingHorizontal: space[5], paddingTop: space[3], paddingBottom: space[7] },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[4],
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
    ...shadow.soft,
  },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: radius.field,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  info: { flex: 1, gap: 4 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space[2] },
  name: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  verifiedBadge: {
    backgroundColor: color.successTint,
    borderRadius: radius.pill,
    paddingHorizontal: space[2],
    paddingVertical: 2,
  },
  verifiedText: { fontSize: 10, fontWeight: font.weight.bold, color: color.successInk },
  categoria: { fontSize: font.size.caption, color: color.textSoft },
  meta: { flexDirection: 'row', alignItems: 'center', gap: space[1] },
  metaText: { fontSize: font.size.caption, color: color.textSoft },
  dot: { color: color.textFaint },
});
