import React from 'react';
import { View, TouchableOpacity, Text, StyleSheet } from 'react-native';
import { color, space, motion } from '../theme';

interface Props {
  value: number;
  max?: number;
  size?: number;
  onSelect?: (v: number) => void;
  readonly?: boolean;
}

export default function StarRating({ value, max = 5, size = 28, onSelect, readonly }: Props) {
  return (
    <View style={styles.row}>
      {Array.from({ length: max }).map((_, i) => {
        const filled = i < value;
        return readonly ? (
          <Text key={i} style={[styles.star, { fontSize: size, color: filled ? color.warmSun : color.line }]}>★</Text>
        ) : (
          <TouchableOpacity key={i} onPress={() => onSelect?.(i + 1)} hitSlop={6} style={{ minWidth: motion.touchMin / 2 }}>
            <Text style={[styles.star, { fontSize: size, color: filled ? color.warmSun : color.line }]}>★</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: space[1] },
  star: { lineHeight: undefined },
});
