import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { color, font, space } from '../theme';

interface Props {
  title: string;
  onBack?: () => void;
  right?: React.ReactNode;
  transparent?: boolean;
}

export default function ScreenHeader({ title, onBack, right, transparent }: Props) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.container, { paddingTop: insets.top + space[2] }, transparent && styles.transparent]}>
      <TouchableOpacity onPress={onBack} style={styles.back} hitSlop={8} disabled={!onBack}>
        {onBack && <Text style={styles.backIcon}>←</Text>}
      </TouchableOpacity>
      <Text style={styles.title} numberOfLines={1}>{title}</Text>
      <View style={styles.right}>{right}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: space[4],
    paddingBottom: space[3],
    backgroundColor: color.bg,
    gap: space[3],
  },
  transparent: { backgroundColor: 'transparent' },
  back: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  backIcon: { fontSize: 22, color: color.text, fontWeight: '600' },
  title: {
    flex: 1,
    fontSize: font.size.h3,
    fontWeight: font.weight.bold,
    color: color.text,
    textAlign: 'center',
  },
  right: { width: 36, alignItems: 'flex-end' },
});
