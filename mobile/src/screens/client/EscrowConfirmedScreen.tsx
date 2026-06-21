import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'EscrowConfirmed'>;

export default function EscrowConfirmedScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>

        {/* Confirmação */}
        <View style={styles.heroCard}>
          <View style={styles.lockCircle}>
            <Text style={{ fontSize: 48 }}>🔒</Text>
          </View>
          <Text style={styles.title}>Pagamento retido!</Text>
          <Text style={styles.body}>
            Seu dinheiro está seguro no escrow Onda. Será liberado ao prestador
            <Text style={{ fontWeight: '700' }}> somente quando você confirmar</Text> que o serviço foi concluído.
          </Text>
        </View>

        {/* Status escrow */}
        <View style={styles.statusCard}>
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, { backgroundColor: color.success }]} />
            <Text style={styles.statusLabel}>Pagamento recebido</Text>
            <Text style={[styles.statusValue, { color: color.success }]}>✓</Text>
          </View>
          <View style={styles.statusDivider} />
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, { backgroundColor: color.institutional }]} />
            <Text style={styles.statusLabel}>Em escrow — protegido</Text>
            <Text style={[styles.statusValue, { color: color.institutional }]}>🔒</Text>
          </View>
          <View style={styles.statusDivider} />
          <View style={styles.statusRow}>
            <View style={[styles.statusDot, { backgroundColor: color.line }]} />
            <Text style={[styles.statusLabel, { color: color.textFaint }]}>Aguardando conclusão</Text>
            <Text style={styles.statusValue}>⏳</Text>
          </View>
        </View>

        {/* Próximos passos */}
        <View style={styles.nextSteps}>
          <Text style={styles.nextTitle}>O que acontece agora?</Text>
          <Text style={styles.nextItem}>• O prestador será notificado e irá entrar em contato</Text>
          <Text style={styles.nextItem}>• Você acompanha o progresso do serviço neste app</Text>
          <Text style={styles.nextItem}>• Ao concluir, confirme a entrega para liberar o pagamento</Text>
          <Text style={styles.nextItem}>• Em caso de problema, acione a mediação Onda</Text>
        </View>

        <Button
          label="Acompanhar serviço"
          onPress={() => nav.navigate('RequestDetail', { requestId: route.params.requestId })}
        />
        <Button label="Voltar ao início" variant="ghost" onPress={() => nav.popToTop()} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[7], paddingBottom: space[7], gap: space[4] },
  heroCard: {
    backgroundColor: color.institutional,
    borderRadius: radius.card,
    padding: space[6],
    alignItems: 'center',
    gap: space[4],
  },
  lockCircle: {
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.textOnAccent },
  body: { fontSize: font.size.body, color: color.accentSky, textAlign: 'center', lineHeight: font.size.body * font.lineHeight.body },
  statusCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  statusRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  statusDot: { width: 10, height: 10, borderRadius: 5 },
  statusLabel: { flex: 1, fontSize: font.size.body, color: color.text },
  statusValue: { fontSize: font.size.body },
  statusDivider: { height: 1, backgroundColor: color.lineSoft, marginLeft: space[4] + 10 },
  nextSteps: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  nextTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  nextItem: { fontSize: font.size.bodySm, color: color.textSoft, lineHeight: font.size.bodySm * 1.6 },
});
