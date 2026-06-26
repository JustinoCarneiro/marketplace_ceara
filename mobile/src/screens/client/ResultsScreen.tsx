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
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ProviderCard, { ProviderData } from '../../components/ProviderCard';

type RouteProps = RouteProp<ClientStackParams, 'Results'>;

interface Provider {
  userId: string;
  nome: string;
  categoria: string;
  notaMedia: number | null;
  totalAvaliacoes?: number;
  statusVerificacao: string;
  distanciaKm: number;
  precoMin?: number;
  precoMax?: number;
}

const RAIO_OPTIONS = ['5 km', '8 km', '15 km'];
const NOTA_OPTIONS = ['★ 4+', '★ 4.5+', 'Todos'];

export default function ResultsScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);
  const [raio, setRaio] = useState(1);
  const [notaMin, setNotaMin] = useState(0);

  const categoria = route.params?.categoria;
  const raioKm = [5, 8, 15][raio];
  const titulo = categoria
    ? categoria.charAt(0).toUpperCase() + categoria.slice(1)
    : 'Todos os prestadores';

  useEffect(() => {
    load();
  }, [categoria, raio]);

  async function load() {
    setLoading(true);
    try {
      const params = new URLSearchParams({
        lat: '-3.7319', lng: '-38.5267',
        raioKm: String(raioKm),
        ...(categoria ? { categoria } : {}),
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
  }

  const filtered = providers.filter(p => {
    if (notaMin === 1) return (p.notaMedia ?? 0) >= 4;
    if (notaMin === 2) return (p.notaMedia ?? 0) >= 4.5;
    return true;
  });

  function toProviderData(p: Provider): ProviderData {
    return {
      id: p.userId,
      nome: p.nome,
      categoria: p.categoria,
      nota: p.notaMedia ?? undefined,
      avaliacoes: p.totalAvaliacoes,
      distanciaKm: p.distanciaKm,
      precoMin: p.precoMin,
      precoMax: p.precoMax,
      verificado: p.statusVerificacao === 'VERIFICADO',
    };
  }

  return (
    <SafeAreaView style={styles.safe}>
      {/* Sticky header */}
      <View style={styles.stickyHeader}>
        <View style={styles.headerTop}>
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12} style={styles.backBtn}>
            <Text style={styles.backIcon}>‹</Text>
          </TouchableOpacity>
          <View style={styles.headerInfo}>
            <Text style={styles.headerTitle}>{titulo}</Text>
            <Text style={styles.headerSub}>
              {loading ? 'Buscando…' : `${filtered.length} profissional${filtered.length !== 1 ? 'is' : ''} · até ${raioKm} km`}
            </Text>
          </View>
          <TouchableOpacity
            style={styles.filterBtn}
            onPress={() => nav.navigate('Filters')}
            hitSlop={8}
          >
            <Text style={styles.filterIcon}>⚙</Text>
          </TouchableOpacity>
        </View>

        {/* Filter chips */}
        <View style={styles.chips}>
          {RAIO_OPTIONS.map((r, i) => (
            <TouchableOpacity
              key={r}
              style={[styles.chip, raio === i && styles.chipActive]}
              onPress={() => setRaio(i)}
            >
              <Text style={[styles.chipText, raio === i && styles.chipTextActive]}>{r}</Text>
            </TouchableOpacity>
          ))}
          {NOTA_OPTIONS.map((n, i) => (
            <TouchableOpacity
              key={n}
              style={[styles.chip, notaMin === i && styles.chipActive]}
              onPress={() => setNotaMin(i)}
            >
              <Text style={[styles.chipText, notaMin === i && styles.chipTextActive]}>{n}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color={color.primary} size="large" />
          <Text style={styles.loadingText}>Buscando na sua região…</Text>
        </View>
      ) : filtered.length === 0 ? (
        <View style={styles.center}>
          <Text style={{ fontSize: 48 }}>🔍</Text>
          <Text style={styles.emptyTitle}>Nenhum profissional no seu raio</Text>
          <Text style={styles.emptyBody}>Tente ampliar o raio ou mudar os filtros.</Text>
        </View>
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={i => i.userId}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: space[3] + 2 }} />}
          renderItem={({ item }) => (
            <ProviderCard
              data={toProviderData(item)}
              onPress={() => nav.navigate('ProviderProfile', { providerId: item.userId })}
            />
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },

  stickyHeader: {
    backgroundColor: color.bg,
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
    paddingBottom: space[3],
  },
  headerTop: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[5],
    paddingTop: space[3],
    paddingBottom: space[3],
  },
  backBtn: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  backIcon: { fontSize: 28, color: color.text, lineHeight: 32 },
  headerInfo: { flex: 1 },
  headerTitle: {
    fontSize: font.size.h3,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * font.size.h3,
  },
  headerSub: { fontSize: font.size.caption, color: color.textSoft, marginTop: 1 },
  filterBtn: {
    width: 42,
    height: 42,
    borderRadius: 12,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterIcon: { fontSize: 18, color: color.text },

  chips: {
    flexDirection: 'row',
    gap: space[2],
    paddingHorizontal: space[5],
    flexWrap: 'wrap',
  },
  chip: {
    paddingHorizontal: space[3],
    paddingVertical: 5,
    borderRadius: radius.pill,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  chipActive: {
    backgroundColor: color.skyTint,
    borderColor: color.institutional2,
  },
  chipText: { fontSize: font.size.caption, fontWeight: font.weight.semibold, color: color.textSoft },
  chipTextActive: { color: color.institutional2 },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space[3], padding: space[5] },
  loadingText: { fontSize: font.size.bodySm, color: color.textSoft },
  emptyTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text, textAlign: 'center' },
  emptyBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center' },

  list: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7] },
});
