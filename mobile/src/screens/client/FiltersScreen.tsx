import React, { useRef, useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity, PanResponder,
} from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { ClientNavProp } from '../../navigation/types';
import { color, font, radius } from '../../theme';
import { useFiltersStore } from '../../store/filters';

const RATING_CHIPS = [
  { label: 'Todas', value: 0 },
  { label: '3+', value: 3 },
  { label: '4+', value: 4 },
  { label: '4,5+', value: 4.5 },
];

const RAIO_MIN = 1;
const RAIO_MAX = 20;

export default function FiltersScreen() {
  const nav = useNavigation<ClientNavProp>();
  const storeRaioKm = useFiltersStore(s => s.raioKm);
  const storeNotaMin = useFiltersStore(s => s.notaMin);
  const setStoreRaioKm = useFiltersStore(s => s.setRaioKm);
  const setStoreNotaMin = useFiltersStore(s => s.setNotaMin);

  const [raioKm, setRaioKm] = useState(storeRaioKm);
  const [notaMin, setNotaMin] = useState(storeNotaMin);

  const trackWidth = useRef(0);
  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: e => updateFromX(e.nativeEvent.locationX),
      onPanResponderMove: e => updateFromX(e.nativeEvent.locationX),
    }),
  ).current;

  function updateFromX(x: number) {
    if (trackWidth.current <= 0) return;
    const pct = Math.min(1, Math.max(0, x / trackWidth.current));
    setRaioKm(Math.round(RAIO_MIN + pct * (RAIO_MAX - RAIO_MIN)));
  }

  function handleClear() {
    setRaioKm(8);
    setNotaMin(0);
  }

  function apply() {
    setStoreRaioKm(raioKm);
    setStoreNotaMin(notaMin);
    nav.goBack();
  }

  const sliderPct = Math.round(((raioKm - RAIO_MIN) / (RAIO_MAX - RAIO_MIN)) * 100);

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.backdrop} activeOpacity={1} onPress={() => nav.goBack()} />
      <View style={styles.sheet}>
        <View style={styles.handle} />

        <View style={styles.titleRow}>
          <Text style={styles.title}>Filtros</Text>
          <TouchableOpacity onPress={handleClear}>
            <Text style={styles.clearBtn}>Limpar</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.section}>
          <View style={styles.sectionTitleRow}>
            <Text style={styles.sectionTitle}>Raio de busca</Text>
            <Text style={styles.sectionValue}>{raioKm} km</Text>
          </View>
          <View
            style={styles.sliderTrack}
            onLayout={e => { trackWidth.current = e.nativeEvent.layout.width; }}
            hitSlop={{ top: 14, bottom: 14 }}
            {...panResponder.panHandlers}
          >
            <View style={[styles.sliderFill, { width: `${sliderPct}%` }]} />
            <View style={[styles.sliderThumb, { left: `${sliderPct}%` }]} />
          </View>
          <View style={styles.sliderLabels}>
            <Text style={styles.sliderLabel}>1 km</Text>
            <Text style={styles.sliderLabel}>20 km</Text>
          </View>
        </View>

        <View style={styles.divider} />

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Nota mínima</Text>
          <View style={styles.chipRow}>
            {RATING_CHIPS.map(chip => {
              const active = notaMin === chip.value;
              return (
                <TouchableOpacity
                  key={chip.label}
                  style={[styles.ratingChip, active && styles.ratingChipActive]}
                  onPress={() => setNotaMin(chip.value)}
                >
                  <Feather name="star" size={14} color={color.warmSun} />
                  <Text style={[styles.ratingChipText, active && styles.ratingChipTextActive]}>
                    {chip.label}
                  </Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>

        <TouchableOpacity style={styles.applyBtn} onPress={apply} activeOpacity={0.85}>
          <Text style={styles.applyBtnText}>Aplicar filtros</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(14,42,51,0.55)',
  },
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
  sheet: {
    backgroundColor: color.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 22,
    paddingBottom: 24,
    paddingTop: 14,
    gap: 24,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: -20 },
    shadowOpacity: 0.5,
    shadowRadius: 50,
    elevation: 20,
  },
  handle: {
    width: 44,
    height: 5,
    borderRadius: radius.pill,
    backgroundColor: '#DCD2BC',
    alignSelf: 'center',
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  title: {
    fontSize: 22,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * 22,
  },
  clearBtn: {
    fontSize: 14,
    fontWeight: font.weight.bold,
    color: color.textFaint,
  },

  section: { gap: 14 },
  sectionTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sectionTitle: { fontSize: 15, fontWeight: font.weight.bold, color: color.text },
  sectionValue: { fontSize: 15, fontWeight: font.weight.black, color: color.primaryInk },

  sliderTrack: {
    height: 8,
    borderRadius: radius.pill,
    backgroundColor: color.lineSoft,
    position: 'relative',
    overflow: 'visible',
  },
  sliderFill: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    borderRadius: radius.pill,
    backgroundColor: color.primary,
  },
  sliderThumb: {
    position: 'absolute',
    top: -7,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#fff',
    borderWidth: 3,
    borderColor: color.primary,
    marginLeft: -11,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.4,
    shadowRadius: 10,
    elevation: 4,
  },
  sliderLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sliderLabel: { fontSize: 12, color: color.textFaint },

  divider: { height: 1, backgroundColor: color.lineSoft },

  chipRow: { flexDirection: 'row', gap: 8 },
  ratingChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: radius.pill,
    backgroundColor: color.bg,
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  ratingChipActive: {
    backgroundColor: '#FDF3D6',
    borderWidth: 1.5,
    borderColor: color.warmSun,
  },
  ratingChipText: {
    fontSize: 13,
    fontWeight: font.weight.bold,
    color: color.textSoft,
  },
  ratingChipTextActive: {
    fontSize: 13,
    fontWeight: font.weight.black,
    color: color.text,
  },

  applyBtn: {
    width: '100%',
    height: 56,
    borderRadius: radius.pill,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: 'rgba(20,168,160,0.85)',
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 8,
  },
  applyBtnText: {
    fontSize: 16,
    fontWeight: font.weight.bold,
    color: color.textOnAccent,
  },
});
