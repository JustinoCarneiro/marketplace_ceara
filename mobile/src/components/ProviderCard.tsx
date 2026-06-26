import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { color, font, space, radius, shadow } from '../theme';

export interface ProviderData {
  id: string;
  nome: string;
  categoria: string;
  nota?: number;
  avaliacoes?: number;
  distanciaKm?: number;
  precoMin?: number;
  precoMax?: number;
  verificado?: boolean;
  avatarColor?: string;
}

interface Props {
  data: ProviderData;
  onPress?: () => void;
}

function initials(nome: string) {
  return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
}

const AVATAR_COLORS = [
  color.warmTerra, color.catHidraulica, color.catLimpeza,
  color.catReforma, color.catJardinagem, color.catGeral,
];

function avatarColor(nome: string) {
  const idx = nome.charCodeAt(0) % AVATAR_COLORS.length;
  return AVATAR_COLORS[idx];
}

export default function ProviderCard({ data, onPress }: Props) {
  const bgColor = data.avatarColor ?? avatarColor(data.nome);
  const initStr = initials(data.nome);
  const nota = data.nota ?? 0;
  const fullStars = Math.floor(nota);
  const precoStr = data.precoMin && data.precoMax
    ? `R$ ${data.precoMin} – R$ ${data.precoMax}`
    : null;

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.85}>
      {/* Avatar */}
      <View style={[styles.avatar, { backgroundColor: bgColor }]}>
        <Text style={styles.avatarText}>{initStr}</Text>
      </View>

      {/* Info */}
      <View style={styles.info}>
        {/* Nome + badge */}
        <View style={styles.nameRow}>
          <Text style={styles.nome} numberOfLines={1}>{data.nome}</Text>
          {data.verificado !== false && (
            <View style={styles.badge}>
              <Text style={styles.badgeIcon}>✓</Text>
              <Text style={styles.badgeText}>VERIFICADO</Text>
            </View>
          )}
        </View>

        {/* Meta: nota, categoria, distância */}
        <View style={styles.meta}>
          {nota > 0 && (
            <View style={styles.ratingRow}>
              <Text style={styles.star}>★</Text>
              <Text style={styles.nota}>{nota.toFixed(1)}</Text>
              {data.avaliacoes ? <Text style={styles.metaText}>({data.avaliacoes})</Text> : null}
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

        {/* Preço */}
        {precoStr && <Text style={styles.preco}>{precoStr}</Text>}
      </View>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: space[4],
    flexDirection: 'row',
    gap: space[4] - 2,
    ...shadow.soft,
  },
  avatar: {
    width: 54,
    height: 54,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  avatarText: {
    fontSize: font.size.h3,
    fontWeight: font.weight.black,
    color: color.textOnAccent,
  },
  info: { flex: 1, gap: 5, minWidth: 0 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: space[2], flexWrap: 'wrap' },
  nome: {
    fontSize: font.size.h3,
    fontWeight: font.weight.bold,
    color: color.text,
    flexShrink: 1,
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    backgroundColor: color.institutional,
    borderRadius: radius.pill,
    paddingHorizontal: 7,
    paddingVertical: 3,
  },
  badgeIcon: { fontSize: 9, color: color.textOnAccent, fontWeight: font.weight.black },
  badgeText: {
    fontSize: 10,
    fontWeight: font.weight.black,
    color: color.textOnAccent,
    letterSpacing: 0.5,
  },
  meta: { flexDirection: 'row', alignItems: 'center', gap: 4, flexWrap: 'wrap' },
  ratingRow: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  star: { fontSize: 13, color: color.warmSun },
  nota: { fontSize: font.size.caption, fontWeight: font.weight.bold, color: color.text },
  dot: { fontSize: font.size.caption, color: color.textFaint },
  metaText: { fontSize: font.size.caption, color: color.textSoft },
  preco: {
    fontSize: font.size.bodySm,
    fontWeight: font.weight.bold,
    color: color.institutional2,
  },
});
