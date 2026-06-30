import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenState from '../../components/ScreenState';

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

const AVATAR_COLORS = [
  color.warmTerra, color.catHidraulica, color.catLimpeza,
  color.catReforma, color.catJardinagem, color.catGeral,
];

function avatarBgColor(nome: string) {
  return AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length];
}

function initials(nome: string) {
  return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
}

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
    ? categoria.charAt(0).toUpperCase() + categoria.slice(1) + 'istas'
    : 'Todos os prestadores';

  useEffect(() => {
    load();
  }, [categoria, raio]);

  const [hasError, setHasError] = useState(false);

  async function load() {
    setLoading(true);
    setHasError(false);
    try {
      const params = new URLSearchParams({
        lat: '-3.7319', lng: '-38.5267',
        raioKm: String(raioKm),
        ...(categoria ? { categoria } : {}),
      });
      const res = await fetch(`${API_BASE}/providers/nearby?${params}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error('HTTP error');
      const data = await res.json();
      setProviders(Array.isArray(data) ? data : []);
    } catch {
      setHasError(true);
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

  const RAIO_CHIPS = [
    { label: `Raio ${raioKm} km`, active: true },
    { label: '★ 4+', active: notaMin === 1 },
    { label: 'Mais próximos', active: false },
  ];

  function handleFilterChipPress(index: number) {
    if (index === 1) setNotaMin(v => (v === 1 ? 0 : 1));
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.stickyHeader}>
        <View style={styles.headerTop}>
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12} style={styles.backBtn}
            accessibilityLabel="Voltar" accessibilityRole="button">
            <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
          </TouchableOpacity>
          <View style={styles.headerInfo}>
            <Text style={styles.headerTitle}>{titulo}</Text>
            <Text style={styles.headerSub}>
              {loading
                ? 'Buscando…'
                : `${filtered.length} profissional${filtered.length !== 1 ? 'is' : ''} · até ${raioKm} km`}
            </Text>
          </View>
          <TouchableOpacity
            style={styles.filterBtn}
            onPress={() => nav.navigate('Filters')}
            hitSlop={8}
            accessibilityLabel="Filtros"
            accessibilityRole="button"
          >
            <Feather name="sliders" size={20} color={color.text} accessibilityElementsHidden />
          </TouchableOpacity>
        </View>

        <View style={styles.chips}>
          <TouchableOpacity
            style={[styles.chip, styles.chipActive]}
            onPress={() => {
              const next = [5, 8, 15][(([5, 8, 15].indexOf(raioKm) + 1) % 3)];
              setRaio([5, 8, 15].indexOf(next));
            }}
          >
            <Text style={[styles.chipText, styles.chipTextActive]}>Raio {raioKm} km</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.chip, notaMin === 1 && styles.chipActive]}
            onPress={() => setNotaMin(v => (v === 1 ? 0 : 1))}
          >
            <Text style={[styles.chipText, notaMin === 1 && styles.chipTextActive]}>★ 4+</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.chip}
            onPress={() => {}}
          >
            <Text style={styles.chipText}>Mais próximos</Text>
          </TouchableOpacity>
        </View>
      </View>

      {loading || hasError || filtered.length === 0 ? (
        <ScreenState
          state={loading ? 'loading' : hasError ? 'error' : 'empty'}
          icon="search"
          emptyTitle="Nenhum profissional no seu raio"
          emptyBody="Tente ampliar o raio ou mudar os filtros."
          onRetry={load}
        />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={i => i.userId}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: 14 }} />}
          renderItem={({ item }) => (
            <ResultProviderCard
              item={item}
              onPress={() => nav.navigate('ProviderProfile', { providerId: item.userId })}
            />
          )}
        />
      )}
    </SafeAreaView>
  );
}

function ResultProviderCard({ item, onPress }: { item: Provider; onPress: () => void }) {
  const bgColor = avatarBgColor(item.nome);
  const init = initials(item.nome);
  const nota = item.notaMedia ?? 0;
  const precoStr = item.precoMin && item.precoMax
    ? `R$ ${item.precoMin} – R$ ${item.precoMax}`
    : null;
  const isVerified = item.statusVerificacao === 'VERIFICADO';

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.85}>
      <View style={[styles.cardAvatar, { backgroundColor: bgColor }]}>
        <Text style={styles.cardAvatarText}>{init}</Text>
      </View>
      <View style={styles.cardInfo}>
        <View style={styles.cardNameRow}>
          <Text style={styles.cardName} numberOfLines={1}>{item.nome}</Text>
          {isVerified && (
            <View style={styles.verifiedBadge}>
              <Feather name="shield" size={11} color="#fff" />
              <Text style={styles.verifiedBadgeText}>VERIFICADO</Text>
            </View>
          )}
        </View>
        <View style={styles.cardMeta}>
          {nota > 0 && (
            <View style={styles.ratingRow}>
              <Feather name="star" size={14} color={color.warmSun} />
              <Text style={styles.ratingVal}>{nota.toFixed(1)}</Text>
              {item.totalAvaliacoes ? (
                <Text style={styles.metaText}>({item.totalAvaliacoes})</Text>
              ) : null}
            </View>
          )}
          {nota > 0 && <Text style={styles.dot}>·</Text>}
          <Text style={styles.metaText}>{item.distanciaKm.toFixed(1)} km</Text>
        </View>
        {precoStr && <Text style={styles.cardPreco}>{precoStr}</Text>}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },

  stickyHeader: {
    backgroundColor: color.bg,
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
    paddingBottom: 14,
  },
  headerTop: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 12,
  },
  backBtn: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  headerInfo: { flex: 1 },
  headerTitle: {
    fontSize: 18,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * 18,
  },
  headerSub: { fontSize: 12.5, color: color.textSoft, marginTop: 1 },
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

  chips: {
    flexDirection: 'row',
    gap: 8,
    paddingHorizontal: 20,
  },
  chip: {
    paddingHorizontal: 12,
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
  chipText: { fontSize: 12, fontWeight: font.weight.semibold, color: color.textSoft },
  chipTextActive: { color: color.institutional2 },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space[3], padding: space[5] },
  loadingText: { fontSize: font.size.bodySm, color: color.textSoft },
  emptyTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text, textAlign: 'center' },
  emptyBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center' },

  list: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 48 },

  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: 16,
    flexDirection: 'row',
    gap: 14,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.45,
    shadowRadius: 20,
    elevation: 5,
  },
  cardAvatar: {
    width: 54,
    height: 54,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  cardAvatarText: { fontSize: 18, fontWeight: font.weight.black, color: color.textOnAccent },
  cardInfo: { flex: 1, gap: 6 },
  cardNameRow: { flexDirection: 'row', alignItems: 'center', gap: 7, flexWrap: 'wrap' },
  cardName: { fontSize: 18, fontWeight: font.weight.bold, color: color.text, flexShrink: 1 },
  verifiedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    backgroundColor: color.institutional,
    borderRadius: radius.pill,
    paddingLeft: 6,
    paddingRight: 8,
    paddingVertical: 3,
  },
  verifiedBadgeText: {
    fontSize: 12,
    fontWeight: font.weight.bold,
    color: color.textOnAccent,
    letterSpacing: 0.5,
  },
  cardMeta: { flexDirection: 'row', alignItems: 'center', gap: 8, flexWrap: 'wrap' },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  ratingVal: { fontSize: 13, fontWeight: font.weight.bold, color: color.text },
  dot: { fontSize: 13, color: color.textSoft },
  metaText: { fontSize: 13, color: color.textSoft },
  cardPreco: { fontSize: 14, fontWeight: font.weight.bold, color: color.institutional2 },
});
