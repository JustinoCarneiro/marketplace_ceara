import React, { useEffect, useRef } from 'react';
import { View, Animated, StyleSheet, Easing } from 'react-native';
import { color, radius } from '../theme';

function usePulse() {
  const opacity = useRef(new Animated.Value(0.45)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(opacity, { toValue: 1, duration: 700, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
        Animated.timing(opacity, { toValue: 0.45, duration: 700, easing: Easing.inOut(Easing.ease), useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [opacity]);

  return opacity;
}

function SkeletonBlock({ width, height, borderRadius = 6, style }: {
  width: number | `${number}%`;
  height: number;
  borderRadius?: number;
  style?: object;
}) {
  const opacity = usePulse();
  return (
    <Animated.View style={[{ width, height, borderRadius, backgroundColor: color.lineSoft, opacity }, style]} />
  );
}

/** Skeleton do card "prestador" (avatar + nome + meta + preço) — HomeScreen/ResultsScreen. */
export function SkeletonProviderCard() {
  return (
    <View style={styles.providerCard}>
      <SkeletonBlock width={54} height={54} borderRadius={12} />
      <View style={styles.providerInfo}>
        <SkeletonBlock width="65%" height={17} />
        <SkeletonBlock width="40%" height={13} />
        <SkeletonBlock width="30%" height={14} />
      </View>
    </View>
  );
}

/** Skeleton do card "pedido" (badge + título + linha de meta) — MyRequestsScreen/AvailableRequestsScreen. */
export function SkeletonRequestCard() {
  return (
    <View style={styles.requestCard}>
      <View style={styles.requestTopRow}>
        <SkeletonBlock width="45%" height={16} />
        <SkeletonBlock width={72} height={20} borderRadius={radius.pill} />
      </View>
      <SkeletonBlock width="90%" height={13} />
      <SkeletonBlock width="35%" height={13} />
    </View>
  );
}

export function SkeletonList({
  variant, count = 3,
}: { variant: 'provider' | 'request'; count?: number }) {
  const Item = variant === 'provider' ? SkeletonProviderCard : SkeletonRequestCard;
  return (
    <View style={styles.list}>
      {Array.from({ length: count }, (_, i) => <Item key={i} />)}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: 14 },

  providerCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: 16,
    flexDirection: 'row',
    gap: 14,
  },
  providerInfo: { flex: 1, gap: 8, justifyContent: 'center' },

  requestCard: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: 24,
    padding: 15,
    gap: 10,
  },
  requestTopRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
});
