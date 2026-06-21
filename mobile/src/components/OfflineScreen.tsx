import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { color, font, space, radius } from '../theme';
import Button from './Button';

interface Props {
  onRetry: () => void;
}

export default function OfflineScreen({ onRetry }: Props) {
  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        {/* Illustration */}
        <View style={styles.illustration}>
          <View style={styles.waveOuter}>
            <View style={styles.waveMid}>
              <View style={styles.waveInner} />
            </View>
          </View>
          <View style={styles.iconCircle}>
            <Text style={styles.icon}>📡</Text>
          </View>
        </View>

        {/* Text */}
        <View style={styles.textBlock}>
          <Text style={styles.title}>Sem conexão</Text>
          <Text style={styles.body}>
            Não foi possível conectar à internet.{'\n'}
            Verifique o Wi-Fi ou os dados móveis e tente de novo.
          </Text>
        </View>

        {/* CTA */}
        <View style={styles.actions}>
          <Button label="Tentar de novo" onPress={onRetry} />
        </View>

        {/* Tip */}
        <View style={styles.tip}>
          <Text style={styles.tipText}>
            Seus dados em aberto estão seguros — nenhuma transação é confirmada sem internet.
          </Text>
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
    gap: space[6],
  },

  // Concentric wave rings (sand stroke)
  illustration: {
    width: 160,
    height: 160,
    alignItems: 'center',
    justifyContent: 'center',
  },
  waveOuter: {
    position: 'absolute',
    width: 160,
    height: 160,
    borderRadius: 80,
    borderWidth: 1.5,
    borderColor: color.sandStroke,
    opacity: 0.4,
    alignItems: 'center',
    justifyContent: 'center',
  },
  waveMid: {
    width: 120,
    height: 120,
    borderRadius: 60,
    borderWidth: 1.5,
    borderColor: color.sandStroke,
    opacity: 0.6,
    alignItems: 'center',
    justifyContent: 'center',
  },
  waveInner: {
    width: 80,
    height: 80,
    borderRadius: 40,
    borderWidth: 1.5,
    borderColor: color.sandStrokeDeep,
    opacity: 0.8,
  },
  iconCircle: {
    position: 'absolute',
    width: 64,
    height: 64,
    borderRadius: radius.field,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: color.line,
  },
  icon: { fontSize: 28 },

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
    maxWidth: 280,
  },

  actions: {
    width: '100%',
  },

  tip: {
    backgroundColor: color.bgAlt,
    borderRadius: radius.field,
    padding: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  tipText: {
    fontSize: font.size.caption,
    color: color.textFaint,
    textAlign: 'center',
    lineHeight: font.size.caption * 1.55,
  },
});
