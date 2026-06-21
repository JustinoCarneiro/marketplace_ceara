import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Animated, Easing } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';

export default function SosActiveScreen() {
  const nav = useNavigation<any>();
  const [pulse] = useState(new Animated.Value(1));

  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1.15, duration: 700, useNativeDriver: true, easing: Easing.inOut(Easing.ease) }),
        Animated.timing(pulse, { toValue: 1, duration: 700, useNativeDriver: true, easing: Easing.inOut(Easing.ease) }),
      ])
    ).start();
  }, []);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <Animated.View style={[styles.pulseRing, { transform: [{ scale: pulse }] }]}>
          <View style={styles.sosCircle}>
            <Text style={{ fontSize: 56 }}>🆘</Text>
          </View>
        </Animated.View>

        <Text style={styles.title}>SOS Acionado</Text>
        <Text style={styles.body}>
          Nossa equipe foi notificada e está monitorando sua situação. Mantenha-se em local seguro.
        </Text>

        <View style={styles.infoCard}>
          <View style={styles.infoRow}>
            <Text style={{ fontSize: 18 }}>📍</Text>
            <Text style={styles.infoText}>Sua localização foi registrada</Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={{ fontSize: 18 }}>📞</Text>
            <Text style={styles.infoText}>Emergência: <Text style={styles.infoBold}>192 (SAMU) · 190 (PM)</Text></Text>
          </View>
          <View style={styles.infoRow}>
            <Text style={{ fontSize: 18 }}>🛡️</Text>
            <Text style={styles.infoText}>Mediação Onda notificada</Text>
          </View>
        </View>

        <View style={styles.actions}>
          <Button label="Estou em segurança — desativar" variant="outline" onPress={() => nav.popToTop()} />
          <Button label="Voltar ao início" variant="ghost" onPress={() => nav.popToTop()} />
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#1A0505' },
  content: {
    flex: 1,
    paddingHorizontal: space[5],
    paddingTop: space[7],
    paddingBottom: space[6],
    alignItems: 'center',
    gap: space[5],
  },
  pulseRing: {
    width: 180,
    height: 180,
    borderRadius: 90,
    backgroundColor: color.danger + '30',
    alignItems: 'center',
    justifyContent: 'center',
  },
  sosCircle: {
    width: 140,
    height: 140,
    borderRadius: 70,
    backgroundColor: color.danger,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: color.danger,
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.9,
    shadowRadius: 32,
    elevation: 12,
  },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.textOnAccent },
  body: {
    fontSize: font.size.body,
    color: '#C0AAA6',
    textAlign: 'center',
    lineHeight: font.size.body * font.lineHeight.body,
  },
  infoCard: {
    backgroundColor: '#2A0A0A',
    borderRadius: radius.card,
    padding: space[5],
    gap: space[4],
    alignSelf: 'stretch',
    borderWidth: 1,
    borderColor: color.danger + '40',
  },
  infoRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  infoText: { fontSize: font.size.bodySm, color: '#C0AAA6' },
  infoBold: { fontWeight: font.weight.bold, color: color.textOnAccent },
  actions: { gap: space[3], alignSelf: 'stretch', marginTop: 'auto' },
});
