import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ViewStyle, ActivityIndicator } from 'react-native';
import { color, font, radius, space, motion } from '../theme';

type Variant = 'primary' | 'outline' | 'ghost' | 'danger';

interface Props {
  label: string;
  onPress?: () => void;
  variant?: Variant;
  loading?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
  icon?: React.ReactNode;
}

export default function Button({ label, onPress, variant = 'primary', loading, disabled, style, icon }: Props) {
  const isDisabled = disabled || loading;
  return (
    <TouchableOpacity
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.82}
      style={[styles.base, styles[variant], isDisabled && styles.disabled, style]}
    >
      {loading ? (
        <ActivityIndicator color={variant === 'primary' ? color.textOnAccent : color.primary} size="small" />
      ) : (
        <>
          <Text style={[styles.label, labelColors[variant]]}>{label}</Text>
          {icon}
        </>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  base: {
    height: 56,
    borderRadius: radius.pill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[2],
    paddingHorizontal: space[5],
    minWidth: motion.touchMin,
  },
  primary: {
    backgroundColor: color.primary,
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.38,
    shadowRadius: 24,
    elevation: 5,
  },
  outline: {
    backgroundColor: 'transparent',
    borderWidth: 1.5,
    borderColor: color.text,
  },
  ghost: {
    backgroundColor: 'transparent',
  },
  danger: {
    backgroundColor: color.danger,
  },
  disabled: {
    opacity: 0.45,
  },
  label: {
    fontSize: font.size.body,
    fontFamily: font.family,
    fontWeight: font.weight.bold,
  },
});

const labelColors: Record<Variant, { color: string }> = {
  primary: { color: color.textOnAccent },
  outline: { color: color.text },
  ghost:   { color: color.primaryInk },
  danger:  { color: color.textOnAccent },
};
