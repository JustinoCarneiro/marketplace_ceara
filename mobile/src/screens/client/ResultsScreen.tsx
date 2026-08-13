import { fetchNearby, isVerificado, distanciaKm as toKm, type NearbyProvider } from '../../api/nearby';
import { screenStateError, type ScreenErrorInfo } from '../../api/errors';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import { useFiltersStore } from '../../store/filters';
import ScreenState from '../../components/ScreenState';
import { SkeletonList } from '../../components/Skeleton';

type RouteProps = RouteProp<ClientStackParams, 'Results'>;

type Provider = NearbyProvider;

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

const RAIO_STEPS = [5, 8, 15];

const CATEGORIA_PLURAL: Record<string, string> = {
  'Elétrica': 'Eletricistas',
  'Hidráulica': 'Encanadores',
  'Limpeza': 'Diaristas',
  'Pintura': 'Pintores',
  'Reforma': 'Profissionais de reforma',
  'Jardinagem': 'Jardineiros',
  'Geral': 'Profissionais gerais',
};

export default function ResultsScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(true);
  const [maisProximos, setMaisProximos] = useState(false);

  const raioKm = useFiltersStore(s => s.raioKm);
  const notaMin = useFiltersStore(s => s.notaMin);
  const setRaioKm = useFiltersStore(s => s.setRaioKm);
  const setNotaMin = useFiltersStore(s => s.setNotaMin);

  const categoria = route.params?.categoria;
  const titulo = categoria
    ? (CATEGORIA_PLURAL[categoria] ?? categoria)
    : 'Todos os prestadores';

  useEffect(() => {
    load();
  }, [categoria, raioKm]);

  const [hasError, setHasError] = useState<ScreenErrorInfo | null>(null);

  async function load() {
    setLoading(true);
    setHasError(null);
    try {
      setProviders(await fetchNearby(token, { raioMetros: raioKm * 1000, categoria }));
    } catch (e) {
      setHasError(screenStateError(e));
      setProviders([]);
    } finally {
      setLoading(false);
    }
  }

  // Sem distância conhecida vai pro fim da lista, em vez de virar NaN e bagunçar a ordenação.
  const filtered = providers
    .filter(p => (p.notaMedia ?? 0) >= notaMin)
    .sort((a, b) => (maisProximos
      ? (a.distanciaMetros ?? Infinity) - (b.distanciaMetros ?? Infinity)
      : 0));

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.stickyHeader}>
        <View style={styles.headerTop}>
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={14} style={styles.backBtn}
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
              const next = RAIO_STEPS[(RAIO_STEPS.indexOf(raioKm) + 1) % RAIO_STEPS.length] ?? RAIO_STEPS[0];
              setRaioKm(next);
            }}
          >
            <Text style={[styles.chipText, styles.chipTextActive]}>Raio {raioKm} km</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.chip, notaMin >= 4 && styles.chipActive]}
            onPress={() => setNotaMin(notaMin >= 4 ? 0 : 4)}
          >
            <Text style={[styles.chipText, notaMin >= 4 && styles.chipTextActive]}>★ 4+</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.chip, maisProximos && styles.chipActive]}
            onPress={() => setMaisProximos(v => !v)}
          >
            <Text style={[styles.chipText, maisProximos && styles.chipTextActive]}>Mais próximos</Text>
          </TouchableOpacity>
        </View>
      </View>

      {loading ? (
        <View style={styles.list}>
          <SkeletonList variant="provider" count={4} />
        </View>
      ) : hasError || filtered.length === 0 ? (
        <ScreenState
          state={hasError ? 'error' : 'empty'}
          icon="search"
          emptyTitle="Nenhum profissional no seu raio"
          emptyBody="Tente ampliar o raio ou mudar os filtros."
          errorTitle={hasError?.title}
          errorBody={hasError?.body}
          onRetry={load}
        />
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={i => i.id}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: 14 }} />}
          renderItem={({ item }) => (
            <ResultProviderCard
              item={item}
              onPress={() => nav.navigate('ProviderProfile', { providerId: item.id })}
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
  const distanciaKm = toKm(item);
  const isVerified = isVerificado(item);

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
            </View>
          )}
          {nota > 0 && distanciaKm != null && <Text style={styles.dot}>·</Text>}
          {distanciaKm != null && (
            <Text style={styles.metaText}>{distanciaKm.toFixed(1)} km</Text>
          )}
        </View>
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
