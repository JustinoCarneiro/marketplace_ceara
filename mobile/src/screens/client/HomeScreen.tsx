import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ClientNavProp } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ProviderCard, { ProviderData } from '../../components/ProviderCard';
import { API_BASE } from '../../api/config';

const CATEGORIES = [
  { slug: 'eletrica',   label: 'Elétrica',    icon: '⚡', bg: color.sunTint,    border: color.warmSun,    ink: color.sunInk },
  { slug: 'hidraulica', label: 'Hidráulica',  icon: '🔧', bg: color.skyTint,    border: color.institutional2, ink: color.institutional2 },
  { slug: 'limpeza',    label: 'Limpeza',     icon: '🧹', bg: color.successTint,border: color.success,    ink: color.successInk },
  { slug: 'pintura',    label: 'Pintura',     icon: '🎨', bg: color.terraTint,  border: color.warmTerra,  ink: color.terraInk },
  { slug: 'reforma',    label: 'Reforma',     icon: '🏗️', bg: '#E8EBF5',        border: color.catReforma, ink: color.catReforma },
  { slug: 'jardinagem', label: 'Jardinagem',  icon: '🌱', bg: '#E3F0E8',        border: color.catJardinagem, ink: color.catJardinagem },
  { slug: 'geral',      label: 'Serviços',    icon: '🔩', bg: '#DFF5F3',        border: color.primary,    ink: color.primary },
];

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
        {/* ── Header: localização + avatar ── */}
        <View style={styles.header}>
          <TouchableOpacity style={styles.locationBtn} activeOpacity={0.7}>
            <Text style={styles.locationPin}>📍</Text>
            <Text style={styles.locationText}>Fortaleza, CE</Text>
            <Text style={styles.locationChevron}>▾</Text>
          </TouchableOpacity>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{initStr}</Text>
          </View>
        </View>

        {/* ── Saudação ── */}
        <View style={styles.greetingBlock}>
          <Text style={styles.greetingText}>
            {greeting()}, {firstName}.
          </Text>
          <Text style={styles.greetingSubtitle}>
            Do que sua casa precisa hoje?
          </Text>
        </View>

        {/* ── Barra de busca pílula ── */}
        <TouchableOpacity
          style={styles.searchBar}
          onPress={() => nav.navigate('Results', {})}
          activeOpacity={0.85}
        >
          <Text style={styles.searchIcon}>🔍</Text>
          <Text style={styles.searchPlaceholder}>Buscar serviço ou profissional</Text>
        </TouchableOpacity>

        {/* ── Categorias (scroll horizontal) ── */}
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
                <Text style={{ fontSize: 24 }}>{cat.icon}</Text>
              </View>
              <Text style={[styles.catLabel, { color: cat.ink }]}>{cat.label}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        {/* ── Próximos de você ── */}
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Próximos de você</Text>
          <TouchableOpacity onPress={() => nav.navigate('Results', {})}>
            <Text style={styles.sectionLink}>Ver todos</Text>
          </TouchableOpacity>
        </View>

        {loading ? (
          <View style={styles.loadingBox}>
            <ActivityIndicator color={color.primary} />
          </View>
        ) : nearby.length === 0 ? (
          /* Banner CTA quando não há prestadores carregados */
          <TouchableOpacity
            style={styles.ctaBanner}
            onPress={() => nav.navigate('NewRequest', {})}
            activeOpacity={0.85}
          >
            <View style={{ flex: 1 }}>
              <Text style={styles.ctaTitle}>Descreva seu problema</Text>
              <Text style={styles.ctaBody}>
                Nossa IA sugere o serviço e o orçamento certo para você
              </Text>
            </View>
            <Text style={{ fontSize: 30 }}>📸</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.providerList}>
            {nearby.map(p => (
              <ProviderCard
                key={p.id}
                data={p}
                onPress={() => nav.navigate('ProviderProfile', { providerId: p.id })}
              />
            ))}
          </View>
        )}

        {/* ── Banner IA (sempre visível) ── */}
        <TouchableOpacity
          style={styles.iaBanner}
          onPress={() => nav.navigate('NewRequest', {})}
          activeOpacity={0.85}
        >
          <Text style={styles.iaBannerIcon}>✨</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.iaBannerTitle}>Abrir novo pedido com IA</Text>
            <Text style={styles.iaBannerBody}>
              Descreva, tire foto ou grave áudio — a IA organiza tudo
            </Text>
          </View>
          <Text style={[styles.locationChevron, { color: color.textOnAccent }]}>›</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: {
    paddingTop: space[3],
    paddingBottom: space[7],
    gap: space[5],
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[5],
  },
  locationBtn: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  locationPin: { fontSize: 15 },
  locationText: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.text },
  locationChevron: { fontSize: 13, color: color.textFaint, fontWeight: font.weight.bold },
  avatar: {
    width: 42,
    height: 42,
    borderRadius: 12,
    backgroundColor: color.institutional2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { fontSize: font.size.bodySm, fontWeight: font.weight.black, color: color.textOnAccent },

  // Greeting
  greetingBlock: { paddingHorizontal: space[5], gap: 4 },
  greetingText: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: font.tracking.display * font.size.h1,
    lineHeight: font.size.h1 * 1.1,
  },
  greetingSubtitle: {
    fontSize: font.size.body,
    color: color.textSoft,
    lineHeight: font.size.body * 1.5,
  },

  // Search
  searchBar: {
    marginHorizontal: space[5],
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    height: 52,
    borderRadius: radius.pill,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    paddingHorizontal: space[4] + 4,
    ...shadow.soft,
  },
  searchIcon: { fontSize: 17, color: color.textFaint },
  searchPlaceholder: { fontSize: font.size.body, color: color.textFaint },

  // Categories
  catRow: { paddingHorizontal: space[5], gap: space[3] },
  catItem: { alignItems: 'center', gap: space[2] },
  catIcon: {
    width: 60,
    height: 60,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  catLabel: {
    fontSize: font.size.caption,
    fontWeight: font.weight.semibold,
    textAlign: 'center',
  },

  // Section header
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[5],
  },
  sectionTitle: {
    fontSize: font.size.h3,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * font.size.h3,
  },
  sectionLink: {
    fontSize: font.size.caption,
    fontWeight: font.weight.bold,
    color: color.primary,
  },

  loadingBox: { height: 80, alignItems: 'center', justifyContent: 'center' },

  providerList: { paddingHorizontal: space[5], gap: space[3] + 2 },

  // CTA banner (oceano)
  ctaBanner: {
    marginHorizontal: space[5],
    backgroundColor: color.institutional,
    borderRadius: radius.card,
    padding: space[5],
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[4],
  },
  ctaTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.textOnAccent },
  ctaBody: { fontSize: font.size.bodySm, color: color.accentSky, marginTop: 4 },

  // IA banner
  iaBanner: {
    marginHorizontal: space[5],
    backgroundColor: color.primary,
    borderRadius: radius.card,
    padding: space[5],
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.38,
    shadowRadius: 24,
    elevation: 5,
  },
  iaBannerIcon: { fontSize: 26 },
  iaBannerTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.textOnAccent },
  iaBannerBody: { fontSize: font.size.caption, color: 'rgba(255,255,255,0.8)', marginTop: 3 },
});
