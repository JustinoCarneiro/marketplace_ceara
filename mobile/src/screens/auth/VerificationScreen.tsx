import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { RouteProp } from '@react-navigation/native';
import type { AuthStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<AuthStackParams, 'VerificationPending'>;

function VerificationPending() {
  const logout = useAuthStore(s => s.logout);
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: color.sunTint }]}>
      <View style={styles.container}>
        <View style={styles.iconWrap}>
          <View style={[styles.iconCircle, { backgroundColor: '#FEE9A0', borderColor: color.warmSun }]}>
            <Feather name="clock" size={44} color={color.sunInk} />
          </View>
        </View>
        <View style={styles.textBlock}>
          <View style={[styles.statusBadge, { backgroundColor: '#FEF5CE', borderColor: color.warmSun }]}>
            <Text style={[styles.badgeText, { color: color.sunInk }]}>EM VERIFICAÇÃO</Text>
          </View>
          <Text style={styles.title}>Verificação em andamento</Text>
          <Text style={styles.body}>
            Estamos analisando seus dados. Em até 24h você receberá uma confirmação por e-mail e poderá começar a receber chamados.
          </Text>
        </View>
        <TouchableOpacity style={styles.ghostBtn} onPress={logout}>
          <Text style={styles.ghostBtnText}>Sair</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

function VerificationSuccess() {
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: color.institutional }]}>
      <View style={styles.container}>
        <View style={styles.iconWrap}>
          <View style={[styles.iconCircle, { backgroundColor: 'rgba(255,255,255,0.15)', borderColor: 'rgba(255,255,255,0.3)' }]}>
            <Feather name="shield" size={44} color="#fff" />
          </View>
        </View>
        <View style={styles.textBlock}>
          <Text style={[styles.title, { color: '#fff' }]}>Identidade verificada!</Text>
          <Text style={[styles.body, { color: 'rgba(255,255,255,0.75)' }]}>
            Seu perfil está ativo. Você já pode receber chamados e enviar propostas para clientes da sua região.
          </Text>
        </View>
        <TouchableOpacity
          style={[styles.cta, { backgroundColor: color.primary }]}
          activeOpacity={0.85}
        >
          <Text style={styles.ctaText}>Começar a trabalhar</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

function VerificationRejected() {
  const logout = useAuthStore(s => s.logout);
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: color.dangerTint }]}>
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.iconWrap}>
          <View style={[styles.iconCircle, { backgroundColor: '#F9C9C3', borderColor: color.danger }]}>
            <Feather name="x-circle" size={44} color={color.danger} />
          </View>
        </View>
        <View style={styles.textBlock}>
          <View style={[styles.statusBadge, { backgroundColor: color.dangerTint, borderColor: color.danger }]}>
            <Text style={[styles.badgeText, { color: color.dangerInk }]}>REPROVADO</Text>
          </View>
          <Text style={styles.title}>Verificação não aprovada</Text>
          <Text style={styles.body}>
            Não foi possível confirmar seus dados. Entre em contato com o suporte para entender o motivo e tentar novamente.
          </Text>
        </View>
        <TouchableOpacity style={[styles.cta, { backgroundColor: color.danger }]} activeOpacity={0.85}>
          <Text style={styles.ctaText}>Falar com suporte</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.ghostBtn} onPress={logout}>
          <Text style={styles.ghostBtnText}>Sair</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

export default function VerificationScreen() {
  const route = useRoute<RouteProps>();
  const { status } = route.params;
  if (status === 'VERIFICADO') return <VerificationSuccess />;
  if (status === 'REPROVADO') return <VerificationRejected />;
  return <VerificationPending />;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: {
    flex: 1,
    paddingHorizontal: space[5],
    paddingTop: space[7],
    paddingBottom: space[5],
    gap: space[5],
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconWrap: { alignItems: 'center' },
  iconCircle: {
    width: 100,
    height: 100,
    borderRadius: 50,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textBlock: { alignItems: 'center', gap: space[3] },
  statusBadge: {
    borderRadius: radius.pill,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 5,
  },
  badgeText: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.bold,
    letterSpacing: 0.12 * font.size.eyebrow,
    textTransform: 'uppercase',
  },
  title: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    textAlign: 'center',
    letterSpacing: -0.02 * font.size.h1,
  },
  body: {
    fontSize: font.size.body,
    color: color.textSoft,
    textAlign: 'center',
    lineHeight: font.size.body * font.lineHeight.body,
    maxWidth: 300,
  },
  cta: {
    width: '100%',
    height: 56,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.2,
    shadowRadius: 16,
    elevation: 4,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: '#fff' },
  ghostBtn: { alignSelf: 'center', padding: 10 },
  ghostBtnText: { fontSize: font.size.bodySm, color: color.textSoft, fontWeight: font.weight.semibold },
});
