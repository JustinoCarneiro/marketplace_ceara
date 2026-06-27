import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';

export default function SplashScreen() {
  const nav = useNavigation<AuthNavProp>();

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        {/* Hero */}
        <View style={styles.hero}>
          <View style={styles.logo}>
            <Text style={styles.logoWave}>∿</Text>
          </View>
          <View style={styles.headline}>
            <Text style={styles.appName}>Onda</Text>
            <Text style={styles.tagline}>
              Profissionais de confiança para a sua casa —{' '}
              <Text style={styles.taglineBold}>com pagamento retido até o serviço terminar.</Text>
            </Text>
          </View>
          <View style={styles.trustBadge}>
            <Feather name="lock" size={13} color={color.textSoft} />
            <Text style={styles.trustText}>Pagamento seguro com escrow</Text>
          </View>
        </View>

        {/* CTAs */}
        <View style={styles.actions}>
          <Text style={styles.eyebrow}>Como você quer começar?</Text>

          <TouchableOpacity
            style={styles.ctaPrimary}
            onPress={() => nav.navigate('RegisterClient')}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaPrimaryText}>Sou Cliente</Text>
            <Feather name="arrow-right" size={18} color={color.textOnAccent} />
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.ctaOutline}
            onPress={() => nav.navigate('RegisterProvider')}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaOutlineText}>Sou Prestador</Text>
          </TouchableOpacity>

          <TouchableOpacity onPress={() => nav.navigate('Login')} activeOpacity={0.7}>
            <Text style={styles.ghostLink}>Já tenho conta</Text>
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  container: {
    flex: 1,
    paddingHorizontal: space[5],
    paddingTop: space[7],
    paddingBottom: space[5],
    justifyContent: 'space-between',
  },
  hero: { gap: space[5] },
  logo: {
    width: 78,
    height: 78,
    borderRadius: 26,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 18 },
    shadowOpacity: 0.55,
    shadowRadius: 24,
    elevation: 8,
  },
  logoWave: { fontSize: 36, color: color.textOnAccent },
  headline: { gap: space[3] },
  appName: {
    fontSize: font.size.display,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: font.tracking.display * font.size.display,
    lineHeight: font.size.display * font.lineHeight.tight,
  },
  tagline: {
    fontSize: font.size.h3,
    color: color.textSoft,
    lineHeight: font.size.h3 * font.lineHeight.body,
    maxWidth: 300,
  },
  taglineBold: { color: color.text, fontWeight: font.weight.bold },
  trustBadge: {
    flexDirection: 'row',
    alignSelf: 'flex-start',
    alignItems: 'center',
    gap: 7,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  trustText: {
    fontSize: font.size.caption,
    color: color.institutional,
    fontWeight: font.weight.semibold,
  },
  actions: { gap: space[3] },
  eyebrow: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.institutional2,
    letterSpacing: font.tracking.eyebrow * font.size.eyebrow,
    textTransform: 'uppercase',
  },
  ctaPrimary: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 6,
  },
  ctaPrimaryText: {
    fontSize: font.size.body,
    fontWeight: font.weight.bold,
    color: color.textOnAccent,
  },
  ctaOutline: {
    height: 56,
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: color.text,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ctaOutlineText: {
    fontSize: font.size.body,
    fontWeight: font.weight.bold,
    color: color.text,
  },
  ghostLink: {
    textAlign: 'center',
    fontSize: font.size.bodySm,
    fontWeight: font.weight.semibold,
    color: color.institutional2,
    paddingVertical: 8,
  },
});
