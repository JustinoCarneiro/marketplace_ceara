import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { color, font, radius, space } from '../theme';

interface Props {
  label: string;
  icon?: string;
}

export default function TrustBadge({ label, icon = '🔒' }: Props) {
  return (
    <View style={styles.badge}>
      <Text style={styles.icon}>{icon}</Text>
      <Text style={styles.label}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    paddingHorizontal: space[3],
    paddingVertical: space[1] + 2,
    borderRadius: radius.pill,
    alignSelf: 'flex-start',
  },
  icon: { fontSize: 13 },
  label: {
    fontSize: font.size.caption,
    fontWeight: font.weight.semibold,
    color: color.institutional,
  },
});
