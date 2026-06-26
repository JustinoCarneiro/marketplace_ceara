import React from 'react';
import { View, Text, StyleSheet, SafeAreaView } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';
import TrustBadge from '../../components/TrustBadge';

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
              Profissionais de confiança para a sua casa — com pagamento{' '}
              <Text style={styles.bold}>retido até o serviço terminar.</Text>
            </Text>
          </View>
          <TrustBadge label="Pagamento seguro com escrow" icon="🔒" />
        </View>

        {/* CTAs */}
        <View style={styles.actions}>
          <Text style={styles.eyebrow}>Como você quer começar?</Text>
          <Button
            label="Sou Cliente"
            onPress={() => nav.navigate('RegisterClient')}
            icon={<Text style={styles.arrow}>→</Text>}
          />
          <Button
            label="Sou Prestador"
            variant="outline"
            onPress={() => nav.navigate('RegisterProvider')}
          />
          <Button
            label="Já tenho conta"
            variant="ghost"
            onPress={() => nav.navigate('Login')}
          />
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
    shadowOpacity: 0.6,
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
  bold: { color: color.text, fontWeight: font.weight.bold },
  actions: { gap: space[3] },
  eyebrow: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.institutional2,
    letterSpacing: font.tracking.eyebrow,
    textTransform: 'uppercase',
  },
  arrow: { color: color.textOnAccent, fontSize: 18 },
});
