import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, Animated, Easing, Linking } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';

const COLORS = {
  danger: '#C0392B',
  dangerOverlay: 'rgba(255,255,255,0.16)',
  dangerInfo: 'rgba(255,255,255,0.14)',
  white: '#fff',
  whiteSub: 'rgba(255,255,255,0.9)',
  whiteAudit: 'rgba(255,255,255,0.8)',
};

export default function SosActiveScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const [pulse] = useState(new Animated.Value(1));

  const activatedAt = new Date();
  const timeStr = activatedAt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  const dateStr = activatedAt.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' });

  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulse, { toValue: 1.12, duration: 800, useNativeDriver: true, easing: Easing.inOut(Easing.ease) }),
        Animated.timing(pulse, { toValue: 1, duration: 800, useNativeDriver: true, easing: Easing.inOut(Easing.ease) }),
      ])
    ).start();
  }, []);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <View style={styles.body}>
          <Animated.View style={[styles.iconRing, { transform: [{ scale: pulse }] }]}>
            <Feather name="shield" size={52} color={COLORS.white} />
          </Animated.View>

          <View style={styles.textBlock}>
            <Text style={styles.title}>Emergência acionada</Text>
            <Text style={styles.bodyText}>
              Nosso canal de segurança foi notificado. Mantenha o app aberto se for seguro.
            </Text>
          </View>

          <View style={styles.infoCard}>
            <View style={styles.infoRow}>
              <Feather name="map-pin" size={18} color={COLORS.white} />
              <Text style={styles.infoText}>Localização registrada · Aldeota, Fortaleza</Text>
            </View>
            <View style={styles.infoRow}>
              <Feather name="clock" size={18} color={COLORS.white} />
              <Text style={styles.infoText}>Acionado às {timeStr} · {dateStr}</Text>
            </View>
          </View>

          <View style={styles.auditRow}>
            <Feather name="lock" size={13} color={COLORS.whiteAudit} />
            <Text style={styles.auditText}>Registro auditável guardado com segurança.</Text>
          </View>
        </View>

        <TouchableOpacity
          style={styles.callBtn}
          onPress={() => Linking.openURL('tel:190')}
          activeOpacity={0.85}
        >
          <Text style={styles.callBtnText}>Ligar para 190</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: {
    flex: 1,
    backgroundColor: COLORS.danger,
  },
  content: {
    flex: 1,
    paddingHorizontal: 28,
    paddingTop: 28,
    paddingBottom: 28,
  },
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 22,
  },
  iconRing: {
    width: 104,
    height: 104,
    borderRadius: 52,
    backgroundColor: COLORS.dangerOverlay,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textBlock: {
    alignItems: 'center',
    gap: 12,
  },
  title: {
    fontSize: 26,
    fontWeight: '800',
    letterSpacing: -0.5,
    color: COLORS.white,
    textAlign: 'center',
  },
  bodyText: {
    fontSize: 16,
    lineHeight: 25.6,
    color: COLORS.whiteSub,
    textAlign: 'center',
    maxWidth: 300,
  },
  infoCard: {
    width: '100%',
    backgroundColor: COLORS.dangerInfo,
    borderRadius: 12,
    padding: 16,
    gap: 12,
  },
  infoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  infoText: {
    fontSize: 13.5,
    color: COLORS.white,
    flex: 1,
  },
  auditRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  auditText: {
    fontSize: 12.5,
    color: COLORS.whiteAudit,
  },
  callBtn: {
    width: '100%',
    height: 56,
    borderRadius: 100,
    backgroundColor: COLORS.white,
    alignItems: 'center',
    justifyContent: 'center',
  },
  callBtnText: {
    fontSize: 16,
    fontWeight: '800',
    color: COLORS.danger,
  },
});
