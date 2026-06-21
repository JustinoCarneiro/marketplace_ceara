import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'RequestCreated'>;

export default function RequestCreatedScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();

  const steps = [
    { icon: '✅', label: 'Pedido criado', done: true },
    { icon: '⏳', label: 'Aguardando propostas', done: false },
    { icon: '💳', label: 'Pagamento com escrow', done: false },
    { icon: '🔨', label: 'Serviço executado', done: false },
    { icon: '⭐', label: 'Avaliar prestador', done: false },
  ];

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        {/* Confirmação */}
        <View style={styles.confirmCard}>
          <View style={styles.checkCircle}>
            <Text style={{ fontSize: 40 }}>✅</Text>
          </View>
          <Text style={styles.title}>Pedido criado!</Text>
          <Text style={styles.body}>
            Prestadores da sua região já podem visualizar o chamado e enviar propostas de orçamento.
          </Text>
        </View>

        {/* Timeline de etapas */}
        <View style={styles.timelineCard}>
          <Text style={styles.timelineTitle}>Próximos passos</Text>
          {steps.map((step, i) => (
            <View key={i} style={styles.stepRow}>
              <View style={[styles.stepDot, step.done && styles.stepDotDone]}>
                <Text style={{ fontSize: 14 }}>{step.done ? '✓' : (i + 1).toString()}</Text>
              </View>
              {i < steps.length - 1 && <View style={[styles.stepLine, step.done && styles.stepLineDone]} />}
              <Text style={[styles.stepLabel, step.done && styles.stepLabelDone]}>{step.label}</Text>
            </View>
          ))}
        </View>

        {/* Escrow info */}
        <View style={styles.escrowInfo}>
          <Text style={{ fontSize: 20 }}>🔒</Text>
          <View style={{ flex: 1 }}>
            <Text style={styles.escrowTitle}>Seu dinheiro fica protegido</Text>
            <Text style={styles.escrowBody}>
              O pagamento só é liberado ao prestador quando você confirmar que o serviço foi concluído.
            </Text>
          </View>
        </View>

        <Button
          label="Ver propostas"
          onPress={() => nav.navigate('CompareProposals', { requestId: route.params.requestId })}
        />
        <Button label="Voltar ao início" variant="ghost" onPress={() => nav.popToTop()} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[7], paddingBottom: space[7], gap: space[4] },

  confirmCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[6],
    alignItems: 'center',
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  checkCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: color.successTint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.text },
  body: { fontSize: font.size.body, color: color.textSoft, textAlign: 'center', lineHeight: font.size.body * font.lineHeight.body },

  timelineCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  timelineTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  stepDot: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
    borderColor: color.line,
  },
  stepDotDone: { backgroundColor: color.successTint, borderColor: color.success },
  stepLine: { position: 'absolute', left: 15, top: 36, width: 2, height: 16, backgroundColor: color.line },
  stepLineDone: { backgroundColor: color.success },
  stepLabel: { fontSize: font.size.bodySm, color: color.textSoft },
  stepLabelDone: { color: color.text, fontWeight: font.weight.semibold },

  escrowInfo: {
    flexDirection: 'row',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
    alignItems: 'flex-start',
  },
  escrowTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.institutional },
  escrowBody: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2, lineHeight: font.size.caption * 1.5 },
});
