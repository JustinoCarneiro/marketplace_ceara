import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ClientNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

const CATEGORIES = [
  { slug: 'eletrica',   label: 'Elétrica',       icon: '⚡', c: color.catEletrica },
  { slug: 'hidraulica', label: 'Hidráulica',      icon: '🔧', c: color.catHidraulica },
  { slug: 'limpeza',    label: 'Limpeza',         icon: '🧹', c: color.catLimpeza },
  { slug: 'pintura',    label: 'Pintura',         icon: '🎨', c: color.catPintura },
  { slug: 'reforma',    label: 'Reforma',         icon: '🏗️', c: color.catReforma },
  { slug: 'jardinagem', label: 'Jardinagem',      icon: '🌱', c: color.catJardinagem },
  { slug: 'geral',      label: 'Serviços Gerais', icon: '🔩', c: color.catGeral },
];

export default function HomeScreen() {
  const nav = useNavigation<ClientNavProp>();
  const nome = useAuthStore(s => s.nome);
  const [search, setSearch] = useState('');

  const firstName = nome?.split(' ')[0] ?? 'você';

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* Header */}
        <View style={styles.header}>
          <View>
            <Text style={styles.greeting}>Olá, {firstName} 👋</Text>
            <Text style={styles.subtitle}>Qual serviço você precisa hoje?</Text>
          </View>
          <TouchableOpacity style={styles.notifBtn} hitSlop={8}>
            <Text style={{ fontSize: 22 }}>🔔</Text>
          </TouchableOpacity>
        </View>

        {/* Search bar */}
        <TouchableOpacity
          style={styles.searchBar}
          onPress={() => nav.navigate('Results', {})}
          activeOpacity={0.85}
        >
          <Text style={{ fontSize: 18, color: color.textFaint }}>🔍</Text>
          <Text style={styles.searchPlaceholder}>Buscar por serviço ou bairro…</Text>
        </TouchableOpacity>

        {/* Trust strip */}
        <View style={styles.trustStrip}>
          <View style={[styles.trustChip, { backgroundColor: color.skyTint }]}>
            <Text style={styles.trustIcon}>🔒</Text>
            <Text style={styles.trustText}>Pagamento retido</Text>
          </View>
          <View style={[styles.trustChip, { backgroundColor: color.successTint }]}>
            <Text style={styles.trustIcon}>✅</Text>
            <Text style={styles.trustText}>Prestadores verificados</Text>
          </View>
        </View>

        {/* Categories */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Categorias</Text>
          <View style={styles.catGrid}>
            {CATEGORIES.map(cat => (
              <TouchableOpacity
                key={cat.slug}
                style={styles.catCard}
                onPress={() => nav.navigate('Results', { categoria: cat.slug })}
                activeOpacity={0.8}
              >
                <View style={[styles.catIcon, { backgroundColor: cat.c + '20' }]}>
                  <Text style={{ fontSize: 26 }}>{cat.icon}</Text>
                </View>
                <Text style={styles.catLabel}>{cat.label}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* New request CTA */}
        <TouchableOpacity
          style={styles.ctaBanner}
          onPress={() => nav.navigate('NewRequest', {})}
          activeOpacity={0.85}
        >
          <View>
            <Text style={styles.ctaTitle}>Descreva seu problema</Text>
            <Text style={styles.ctaBody}>Nossa IA sugere o serviço e o orçamento certo</Text>
          </View>
          <Text style={{ fontSize: 28 }}>📸</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  header: { flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' },
  greeting: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft, marginTop: 2 },
  notifBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center' },

  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    height: 52,
    borderRadius: radius.field,
    backgroundColor: color.surface,
    borderWidth: 1.5,
    borderColor: color.line,
    paddingHorizontal: space[4],
  },
  searchPlaceholder: { fontSize: font.size.body, color: color.textFaint },

  trustStrip: { flexDirection: 'row', gap: space[3] },
  trustChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    paddingHorizontal: space[3],
    paddingVertical: space[1] + 2,
    borderRadius: radius.pill,
  },
  trustIcon: { fontSize: 13 },
  trustText: { fontSize: font.size.caption, fontWeight: font.weight.semibold, color: color.text },

  section: { gap: space[3] },
  sectionTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  catGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: space[3] },
  catCard: {
    width: '30%',
    alignItems: 'center',
    gap: space[2],
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  catIcon: { width: 56, height: 56, borderRadius: radius.field, alignItems: 'center', justifyContent: 'center' },
  catLabel: { fontSize: font.size.caption, fontWeight: font.weight.semibold, color: color.text, textAlign: 'center' },

  ctaBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: color.institutional,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[3],
  },
  ctaTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.textOnAccent },
  ctaBody: { fontSize: font.size.bodySm, color: color.accentSky, marginTop: 4, maxWidth: 220 },
});
