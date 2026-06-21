import React from 'react';
import { View, Text, StyleSheet, Linking } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { color, font, space, radius } from '../theme';
import Button from './Button';

type PermissionType = 'camera' | 'microphone' | 'location';

interface Props {
  type: PermissionType;
  onSkip?: () => void;
}

const CONFIG: Record<PermissionType, {
  icon: string;
  title: string;
  body: string;
  why: string;
  skipLabel?: string;
}> = {
  camera: {
    icon: '📷',
    title: 'Câmera bloqueada',
    body: 'Para anexar fotos ao pedido, o Onda precisa de acesso à câmera.',
    why: 'A foto ajuda o prestador a entender o problema antes de enviar a proposta — reduz idas e vindas.',
    skipLabel: 'Continuar sem foto',
  },
  microphone: {
    icon: '🎙️',
    title: 'Microfone bloqueado',
    body: 'Para gravar áudios descritivos, o Onda precisa de acesso ao microfone.',
    why: 'Descrever o problema por voz é mais rápido do que digitar, especialmente em situações de urgência.',
    skipLabel: 'Continuar sem áudio',
  },
  location: {
    icon: '📍',
    title: 'Localização bloqueada',
    body: 'Para encontrar prestadores próximos, o Onda precisa da sua localização.',
    why: 'A geobusca por proximidade é o coração do serviço — sem ela, não há como filtrar prestadores do seu bairro.',
  },
};

export default function PermissionDenied({ type, onSkip }: Props) {
  const cfg = CONFIG[type];

  async function openSettings() {
    await Linking.openSettings();
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        {/* Icon */}
        <View style={styles.iconWrap}>
          <View style={styles.iconBadge}>
            <Text style={styles.icon}>{cfg.icon}</Text>
          </View>
          {/* Blocked overlay */}
          <View style={styles.blockBadge}>
            <Text style={styles.blockText}>✕</Text>
          </View>
        </View>

        {/* Text */}
        <View style={styles.textBlock}>
          <Text style={styles.title}>{cfg.title}</Text>
          <Text style={styles.body}>{cfg.body}</Text>
        </View>

        {/* Why banner */}
        <View style={styles.whyBanner}>
          <Text style={styles.whyIcon}>💡</Text>
          <Text style={styles.whyText}>{cfg.why}</Text>
        </View>

        {/* How to fix */}
        <View style={styles.steps}>
          <Text style={styles.stepsLabel}>COMO RESOLVER</Text>
          {[
            'Toque em "Abrir configurações" abaixo',
            `Localize "${type === 'camera' ? 'Câmera' : type === 'microphone' ? 'Microfone' : 'Localização'}" nas permissões do Onda`,
            'Altere para "Permitir ao usar o app"',
            'Volte ao Onda e tente de novo',
          ].map((step, i) => (
            <View key={i} style={styles.stepRow}>
              <View style={styles.stepNum}>
                <Text style={styles.stepNumText}>{i + 1}</Text>
              </View>
              <Text style={styles.stepText}>{step}</Text>
            </View>
          ))}
        </View>

        {/* Actions */}
        <View style={styles.actions}>
          <Button label="Abrir configurações" onPress={openSettings} />
          {cfg.skipLabel && onSkip && (
            <Button label={cfg.skipLabel} variant="ghost" onPress={onSkip} />
          )}
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: color.bg,
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: space[5],
    paddingVertical: space[6],
    gap: space[5],
  },

  iconWrap: {
    position: 'relative',
    width: 88,
    height: 88,
  },
  iconBadge: {
    width: 88,
    height: 88,
    borderRadius: 24,
    backgroundColor: color.sunTint,
    borderWidth: 1,
    borderColor: color.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  icon: { fontSize: 40 },
  blockBadge: {
    position: 'absolute',
    bottom: -6,
    right: -6,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: color.danger,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: color.bg,
  },
  blockText: {
    fontSize: 12,
    fontWeight: '800',
    color: '#fff',
  },

  textBlock: {
    alignItems: 'center',
    gap: space[2],
  },
  title: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * font.size.h1,
    textAlign: 'center',
  },
  body: {
    fontSize: font.size.body,
    color: color.textSoft,
    textAlign: 'center',
    lineHeight: font.size.body * font.lineHeight.body,
    maxWidth: 300,
  },

  whyBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
    alignSelf: 'stretch',
  },
  whyIcon: { fontSize: 18, marginTop: 1 },
  whyText: {
    flex: 1,
    fontSize: font.size.bodySm,
    color: color.institutional,
    lineHeight: font.size.bodySm * font.lineHeight.body,
  },

  steps: {
    alignSelf: 'stretch',
    gap: space[3],
  },
  stepsLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textFaint,
    textTransform: 'uppercase',
    letterSpacing: 0.2 * font.size.eyebrow,
    marginBottom: space[1],
  },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space[3],
  },
  stepNum: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
    marginTop: 1,
  },
  stepNumText: {
    fontSize: 11,
    fontWeight: '800',
    color: color.textOnAccent,
  },
  stepText: {
    flex: 1,
    fontSize: font.size.bodySm,
    color: color.textSoft,
    lineHeight: font.size.bodySm * 1.5,
  },

  actions: {
    alignSelf: 'stretch',
    gap: space[3],
  },
});
