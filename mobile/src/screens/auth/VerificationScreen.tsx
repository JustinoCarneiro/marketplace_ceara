import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useRoute, useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { AuthStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<AuthStackParams, 'VerificationPending'>;

const CONFIG = {
  EM_VERIFICACAO: {
    icon: '⏳',
    title: 'Verificação em andamento',
    body: 'Estamos analisando seus dados. Em até 24h você receberá uma confirmação por e-mail e poderá começar a receber chamados.',
    bg: color.sunTint,
    ink: color.sunInk,
    cta: null,
  },
  VERIFICADO: {
    icon: '✅',
    title: 'Identidade verificada!',
    body: 'Seu perfil está ativo. Você já pode receber chamados e enviar propostas para clientes da sua região.',
    bg: color.successTint,
    ink: color.successInk,
    cta: 'Começar a trabalhar',
  },
  REPROVADO: {
    icon: '❌',
    title: 'Verificação não aprovada',
    body: 'Não foi possível confirmar seus dados. Entre em contato com o suporte para entender o motivo e tentar novamente.',
    bg: color.dangerTint,
    ink: color.dangerInk,
    cta: 'Falar com suporte',
  },
};

export default function VerificationScreen() {
  const route = useRoute<RouteProps>();
  const logout = useAuthStore(s => s.logout);
  const { status } = route.params;
  const cfg = CONFIG[status as keyof typeof CONFIG];

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <View style={[styles.card, { backgroundColor: cfg.bg }]}>
          <Text style={styles.icon}>{cfg.icon}</Text>
          <Text style={[styles.title, { color: cfg.ink }]}>{cfg.title}</Text>
          <Text style={styles.body}>{cfg.body}</Text>
        </View>

        <View style={styles.actions}>
          {cfg.cta && <Button label={cfg.cta} onPress={() => {}} />}
          <Button label="Sair" variant="ghost" onPress={logout} />
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
  card: {
    borderRadius: radius.card,
    padding: space[6],
    gap: space[4],
    alignItems: 'center',
  },
  icon: { fontSize: 56 },
  title: {
    fontSize: font.size.h2,
    fontWeight: font.weight.bold,
    textAlign: 'center',
    lineHeight: font.size.h2 * font.lineHeight.heading,
  },
  body: {
    fontSize: font.size.body,
    color: color.textSoft,
    textAlign: 'center',
    lineHeight: font.size.body * font.lineHeight.body,
  },
  actions: { gap: space[3] },
});
