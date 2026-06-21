import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ClientNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';
import StarRating from '../../components/StarRating';

const DISTANCES = ['2 km', '5 km', '10 km', '20 km'];
const CATEGORIES = ['Elétrica', 'Hidráulica', 'Limpeza', 'Pintura', 'Reforma', 'Jardinagem', 'Serviços Gerais'];
const SORT_OPTIONS = ['Mais próximo', 'Melhor avaliado', 'Mais pedidos'];

export default function FiltersScreen() {
  const nav = useNavigation<ClientNavProp>();
  const [dist, setDist] = useState('10 km');
  const [cats, setCats] = useState<string[]>([]);
  const [minNota, setMinNota] = useState(0);
  const [sort, setSort] = useState('Mais próximo');
  const [verifiedOnly, setVerifiedOnly] = useState(false);

  function toggleCat(c: string) {
    setCats(prev => prev.includes(c) ? prev.filter(x => x !== c) : [...prev, c]);
  }

  function apply() { nav.goBack(); }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.handle} />
      <View style={styles.headerRow}>
        <Text style={styles.title}>Filtros</Text>
        <TouchableOpacity onPress={() => { setDist('10 km'); setCats([]); setMinNota(0); setSort('Mais próximo'); setVerifiedOnly(false); }}>
          <Text style={styles.reset}>Limpar</Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* Distância */}
        <View style={styles.section}>
          <Text style={styles.sectionLabel}>RAIO DE BUSCA</Text>
          <View style={styles.chipRow}>
            {DISTANCES.map(d => (
              <TouchableOpacity
                key={d}
                style={[styles.chip, dist === d && styles.chipActive]}
                onPress={() => setDist(d)}
              >
                <Text style={[styles.chipText, dist === d && styles.chipTextActive]}>{d}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Categorias */}
        <View style={styles.section}>
          <Text style={styles.sectionLabel}>CATEGORIAS</Text>
          <View style={styles.chipRow}>
            {CATEGORIES.map(c => (
              <TouchableOpacity
                key={c}
                style={[styles.chip, cats.includes(c) && styles.chipActive]}
                onPress={() => toggleCat(c)}
              >
                <Text style={[styles.chipText, cats.includes(c) && styles.chipTextActive]}>{c}</Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        {/* Nota mínima */}
        <View style={styles.section}>
          <Text style={styles.sectionLabel}>NOTA MÍNIMA</Text>
          <View style={styles.starRow}>
            <StarRating value={minNota} onSelect={setMinNota} size={32} />
            {minNota > 0 && <Text style={styles.notaText}>{minNota}+ estrelas</Text>}
          </View>
        </View>

        {/* Só verificados */}
        <TouchableOpacity style={styles.toggleRow} onPress={() => setVerifiedOnly(v => !v)}>
          <View>
            <Text style={styles.toggleLabel}>Apenas prestadores verificados</Text>
            <Text style={styles.toggleHint}>Identidade confirmada pela Onda</Text>
          </View>
          <View style={[styles.toggle, verifiedOnly && styles.toggleOn]}>
            <View style={[styles.thumb, verifiedOnly && styles.thumbOn]} />
          </View>
        </TouchableOpacity>

        {/* Ordenação */}
        <View style={styles.section}>
          <Text style={styles.sectionLabel}>ORDENAR POR</Text>
          {SORT_OPTIONS.map(o => (
            <TouchableOpacity
              key={o}
              style={styles.sortRow}
              onPress={() => setSort(o)}
            >
              <Text style={[styles.sortLabel, sort === o && styles.sortLabelActive]}>{o}</Text>
              {sort === o && <Text style={{ color: color.primary, fontSize: 18 }}>✓</Text>}
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <Button label="Aplicar filtros" onPress={apply} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.surface },
  handle: { width: 40, height: 4, borderRadius: 2, backgroundColor: color.line, alignSelf: 'center', marginTop: space[3] },
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', padding: space[5] },
  title: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  reset: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.primary },
  content: { paddingHorizontal: space[5], paddingBottom: space[4], gap: space[5] },
  section: { gap: space[3] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  chip: {
    paddingHorizontal: space[3],
    paddingVertical: space[2],
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.bg,
  },
  chipActive: { borderColor: color.primary, backgroundColor: '#DFF5F3' },
  chipText: { fontSize: font.size.bodySm, color: color.textSoft, fontWeight: font.weight.medium },
  chipTextActive: { color: color.primary, fontWeight: font.weight.bold },
  starRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  notaText: { fontSize: font.size.bodySm, color: color.textSoft },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: color.bg,
    borderRadius: radius.field,
    padding: space[4],
    borderWidth: 1,
    borderColor: color.line,
  },
  toggleLabel: { fontSize: font.size.body, fontWeight: font.weight.semibold, color: color.text },
  toggleHint: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2 },
  toggle: {
    width: 48,
    height: 28,
    borderRadius: 14,
    backgroundColor: color.line,
    justifyContent: 'center',
    paddingHorizontal: 3,
  },
  toggleOn: { backgroundColor: color.primary },
  thumb: { width: 22, height: 22, borderRadius: 11, backgroundColor: color.textOnAccent },
  thumbOn: { alignSelf: 'flex-end' },
  sortRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: space[3],
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
  },
  sortLabel: { fontSize: font.size.body, color: color.textSoft },
  sortLabelActive: { color: color.text, fontWeight: font.weight.bold },
  footer: { padding: space[5], paddingBottom: space[6] },
});
