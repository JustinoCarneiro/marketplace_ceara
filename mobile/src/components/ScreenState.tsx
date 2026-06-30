import React from 'react';
import { View, Text, TouchableOpacity, ActivityIndicator, StyleSheet } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { color, font, radius, space } from '../theme';

type Props = {
  state: 'loading' | 'empty' | 'error';
  icon?: React.ComponentProps<typeof Feather>['name'];
  emptyTitle?: string;
  emptyBody?: string;
  errorTitle?: string;
  errorBody?: string;
  action?: { label: string; onPress: () => void };
  onRetry?: () => void;
};

export default function ScreenState({
  state,
  icon,
  emptyTitle = 'Nada por aqui',
  emptyBody,
  errorTitle = 'Não foi possível carregar',
  errorBody = 'Verifique sua conexão e tente novamente.',
  action,
  onRetry,
}: Props) {
  if (state === 'loading') {
    return (
      <View style={styles.center}>
        <ActivityIndicator color={color.primary} size="large" />
      </View>
    );
  }

  const isError = state === 'error';
  const iconName = isError
    ? 'wifi-off'
    : (icon ?? 'inbox');
  const title   = isError ? errorTitle : emptyTitle;
  const body    = isError ? errorBody  : emptyBody;

  return (
    <View style={styles.center} accessibilityLiveRegion="polite">
      <View style={[styles.iconWrap, isError && styles.iconWrapError]}>
        <Feather
          name={iconName}
          size={32}
          color={isError ? color.danger : color.textFaint}
          accessibilityElementsHidden
        />
      </View>

      <Text style={[styles.title, isError && styles.titleError]}>{title}</Text>

      {!!body && <Text style={styles.body}>{body}</Text>}

      {isError && onRetry && (
        <TouchableOpacity style={styles.retryBtn} onPress={onRetry} activeOpacity={0.8}
          accessibilityRole="button" accessibilityLabel="Tentar novamente">
          <Feather name="refresh-cw" size={15} color={color.primary} />
          <Text style={styles.retryText}>Tentar novamente</Text>
        </TouchableOpacity>
      )}

      {!isError && action && (
        <TouchableOpacity style={styles.actionBtn} onPress={action.onPress} activeOpacity={0.85}
          accessibilityRole="button">
          <Text style={styles.actionText}>{action.label}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space[5],
    paddingVertical: space[6],
    gap: 10,
  },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: color.surface2,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  iconWrapError: {
    backgroundColor: '#FBE6E2',
  },
  title: {
    fontSize: font.size.h3,
    fontWeight: font.weight.bold,
    color: color.text,
    textAlign: 'center',
  },
  titleError: {
    color: color.danger,
  },
  body: {
    fontSize: font.size.bodySm,
    color: color.textSoft,
    textAlign: 'center',
    lineHeight: font.size.bodySm * 1.5,
  },
  retryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 4,
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: color.primary,
  },
  retryText: {
    fontSize: font.size.bodySm,
    fontWeight: font.weight.bold,
    color: color.primary,
  },
  actionBtn: {
    marginTop: 4,
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: radius.pill,
    backgroundColor: color.primary,
  },
  actionText: {
    fontSize: font.size.bodySm,
    fontWeight: font.weight.bold,
    color: color.textOnAccent,
  },
});
