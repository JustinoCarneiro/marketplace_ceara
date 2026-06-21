import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { color, font, radius, space } from '../theme';

type Status =
  | 'PENDENTE' | 'PROPOSTO' | 'ACEITO' | 'EM_ANDAMENTO'
  | 'CONCLUIDO' | 'EM_DISPUTA' | 'CANCELADO'
  | 'RETIDO' | 'LIBERADO' | 'REEMBOLSADO';

const CONFIG: Record<Status, { label: string; bg: string; ink: string }> = {
  PENDENTE:     { label: 'Pendente',      bg: '#E6E9EA', ink: color.statusPendente },
  PROPOSTO:     { label: 'Proposta',      bg: color.sunTint,   ink: color.sunInk },
  ACEITO:       { label: 'Aceito',        bg: color.skyTint,   ink: color.statusAceito },
  EM_ANDAMENTO: { label: 'Em andamento',  bg: '#DFF5F3',       ink: color.statusAndamento },
  CONCLUIDO:    { label: 'Concluído',     bg: color.successTint, ink: color.successInk },
  EM_DISPUTA:   { label: 'Em disputa',    bg: color.terraTint, ink: color.terraInk },
  CANCELADO:    { label: 'Cancelado',     bg: color.dangerTint, ink: color.dangerInk },
  RETIDO:       { label: 'Retido',        bg: color.skyTint,   ink: color.statusAceito },
  LIBERADO:     { label: 'Liberado',      bg: color.successTint, ink: color.successInk },
  REEMBOLSADO:  { label: 'Reembolsado',   bg: color.terraTint, ink: color.terraInk },
};

interface Props {
  status: Status;
  size?: 'sm' | 'md';
}

export default function StatusBadge({ status, size = 'md' }: Props) {
  const cfg = CONFIG[status] ?? { label: status, bg: '#eee', ink: '#333' };
  return (
    <View style={[styles.badge, { backgroundColor: cfg.bg }, size === 'sm' && styles.sm]}>
      <View style={[styles.dot, { backgroundColor: cfg.ink }]} />
      <Text style={[styles.label, { color: cfg.ink }, size === 'sm' && styles.labelSm]}>
        {cfg.label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: space[3],
    paddingVertical: 5,
    borderRadius: radius.pill,
    alignSelf: 'flex-start',
  },
  sm: { paddingHorizontal: space[2], paddingVertical: 3 },
  dot: { width: 6, height: 6, borderRadius: 3 },
  label: { fontSize: font.size.caption, fontWeight: font.weight.semibold },
  labelSm: { fontSize: 11 },
});
