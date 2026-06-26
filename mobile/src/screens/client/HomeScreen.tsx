import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { ClientNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import { ProviderData } from '../../components/ProviderCard';
import { API_BASE } from '../../api/config';

const CATEGORIES = [
  {
    slug: 'eletrica',
    label: 'Elétrica',
    bg: '#FDF3D6',
    border: '#F2B015',
    icon: <Feather name="zap" size={26} color="#B5810A" />,
  },
  {
    slug: 'hidraulica',
    label: 'Hidráulica',
    bg: '#E2EEF2',
    border: '#15596E',
    icon: <Feather name="droplet" size={26} color="#15596E" />,
  },
  {
    slug: 'limpeza',
    label: 'Limpeza',
    bg: '#DDF0EC',
    border: '#1B8C84',
    icon: <Feather name="edit-3" size={26} color="#15756E" />,
  },
  {
    slug: 'pintura',
    label: 'Pintura',
    bg: '#F7E3D6',
    border: '#DA6A32',
    icon: <Feather name="edit-2" size={26} color="#C2572A" />,
  },
];

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

function greeting() {
  const h = new Date().getHours();
  if (h < 12) return 'Bom dia';
  if (h < 18) return 'Boa tarde';
  return 'Boa noite';
}

export default function HomeScreen() {
  const nav = useNavigation<ClientNavProp>();
  const nome = useAuthStore(s => s.nome);
  const token = useAuthStore(s => s.accessToken);
  const [nearby, setNearby] = useState<ProviderData[]>([]);
  const [loading, setLoading] = useState(true);

  const firstName = nome?.split(' ')[0] ?? 'você';
  const initStr = nome ? nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase() : 'EU';

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch(
          `${API_BASE}/providers/nearby?lat=-3.7319&lng=-38.5267&raioKm=8`,
          { headers: { Authorization: `Bearer ${token}` } },
        );
        if (res.ok) {
          const data = await res.json();
          setNearby(Array.isArray(data) ? data.slice(0, 4) : []);
        }
      } catch {
        setNearby([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.topPad}>
          <View style={styles.header}>
            <TouchableOpacity style={styles.locationRow} activeOpacity={0.7}>
              <Feather name="map-pin" size={16} color={color.primary} />
              <Text style={styles.locationText}>Aldeota, Fortaleza</Text>
              <Feather name="chevron-down" size={16} color={color.textFaint} />
            </TouchableOpacity>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{initStr}</Text>
            </View>
          </View>

          <Text style={styles.greeting}>
            {greeting()}, {firstName}.{'\n'}Do que sua casa precisa hoje?
          </Text>

          <TouchableOpacity
            style={styles.searchBar}
            onPress={() => nav.navigate('Results', {})}
            activeOpacity={0.85}
          >
            <Feather name="search" size={20} color={color.textFaint} />
            <Text style={styles.searchPlaceholder}>Buscar serviço ou profissional</Text>
          </TouchableOpacity>
        </View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.catRow}
        >
          {CATEGORIES.map(cat => (
            <TouchableOpacity
              key={cat.slug}
              style={styles.catItem}
              onPress={() => nav.navigate('Results', { categoria: cat.slug })}
              activeOpacity={0.8}
            >
              <View style={[styles.catIcon, { backgroundColor: cat.bg, borderColor: cat.border }]}>
                {cat.icon}
              </View>
              <Text style={styles.catLabel}>{cat.label}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Próximos de você</Text>
          <TouchableOpacity onPress={() => nav.navigate('Results', {})}>
            <Text style={styles.sectionLink}>Ver todos</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.providerList}>
          {loading ? (
            <View style={styles.loadingBox}>
              <ActivityIndicator color={color.primary} />
            </View>
          ) : nearby.length === 0 ? (
            <TouchableOpacity
              style={styles.emptyCard}
              onPress={() => nav.navigate('NewRequest', {})}
              activeOpacity={0.85}
            >
              <Text style={styles.emptyCardText}>Nenhum prestador encontrado próximo. Toque para criar um pedido.</Text>
            </TouchableOpacity>
          ) : (
            nearby.map(p => (
              <ProviderCardInline
                key={p.id}
                data={p}
                onPress={() => nav.navigate('ProviderProfile', { providerId: p.id })}
              />
            ))
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function ProviderCardInline({ data, onPress }: { data: ProviderData; onPress: () => void }) {
  const bgColor = data.avatarColor ?? avatarBgColor(data.nome);
  const init = initials(data.nome);
  const nota = data.nota ?? 0;
  const precoStr = data.precoMin && data.precoMax
    ? `R$ ${data.precoMin} – R$ ${data.precoMax}`
    : null;

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.85}>
      <View style={[styles.cardAvatar, { backgroundColor: bgColor }]}>
        <Text style={styles.cardAvatarText}>{init}</Text>
      </View>
      <View style={styles.cardInfo}>
        <View style={styles.cardNameRow}>
          <Text style={styles.cardName} numberOfLines={1}>{data.nome}</Text>
          {data.verificado !== false && (
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
          {nota > 0 && <Text style={styles.dot}>·</Text>}
          <Text style={styles.metaText}>{data.categoria}</Text>
          {data.distanciaKm != null && (
            <>
              <Text style={styles.dot}>·</Text>
              <Text style={styles.metaText}>{data.distanciaKm.toFixed(1)} km</Text>
            </>
          )}
        </View>
        {precoStr && <Text style={styles.cardPreco}>{precoStr}</Text>}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { paddingBottom: 48 },

  topPad: {
    paddingHorizontal: 20,
    paddingTop: 8,
    gap: 16,
    paddingBottom: 18,
    flexDirection: 'column',
  },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  locationRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  locationText: { fontSize: 14, fontWeight: font.weight.bold, color: color.text },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: radius.field,
    backgroundColor: color.institutional2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontSize: 15, fontWeight: font.weight.black, color: color.textOnAccent },

  greeting: {
    fontSize: 26,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.025 * 26,
    lineHeight: 26 * 1.1,
  },

  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    height: 52,
    borderRadius: radius.pill,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    paddingHorizontal: 18,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.45,
    shadowRadius: 22,
    elevation: 6,
  },
  searchPlaceholder: { fontSize: 15, color: color.textFaint },

  catRow: { paddingHorizontal: 20, paddingBottom: 18, gap: 10 },
  catItem: { alignItems: 'center', gap: 8 },
  catIcon: {
    width: 60,
    height: 60,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  catLabel: { fontSize: 12, fontWeight: font.weight.semibold, color: color.textSoft },

  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * 18,
  },
  sectionLink: { fontSize: 13, fontWeight: font.weight.bold, color: color.primary },

  providerList: { paddingHorizontal: 20, gap: 14, paddingBottom: 24 },

  loadingBox: { height: 80, alignItems: 'center', justifyContent: 'center' },

  emptyCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: space[4],
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 80,
  },
  emptyCardText: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center' },

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
  cardInfo: { flex: 1, gap: 6, minWidth: 0 },
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
  cardMeta: { flexDirection: 'row', alignItems: 'center', gap: 4, flexWrap: 'wrap' },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: 3 },
  ratingVal: { fontSize: 13, fontWeight: font.weight.bold, color: color.text },
  dot: { fontSize: 13, color: color.textSoft },
  metaText: { fontSize: 13, color: color.textSoft },
  cardPreco: { fontSize: 14, fontWeight: font.weight.bold, color: color.institutional2 },
});
