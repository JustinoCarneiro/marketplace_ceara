import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';

export default function EscrowHeldScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>

        {/* Hero */}
        <View style={styles.heroCard}>
          <View style={styles.icon}>
            <Text style={{ fontSize: 48 }}>🔒</Text>
          </View>
          <Text style={styles.title}>Proposta aceita!</Text>
          <Text style={styles.subtitle}>Pagamento retido no escrow</Text>
          <Text style={styles.body}>
            O cliente aceitou sua proposta e efetuou o pagamento. O valor está retido e será liberado ao concluir o serviço com sucesso.
          </Text>
        </View>

        {/* Instruções */}
        <View style={styles.stepsCard}>
          <Text style={styles.stepsTitle}>Próximos passos</Text>
          {[
            { icon: '📞', step: 'Entre em contato com o cliente para combinar a visita' },
            { icon: '🔨', step: 'Execute o serviço com qualidade e pontualidade' },
            { icon: '✅', step: 'Marque como concluído no app para o cliente confirmar' },
            { icon: '💰', step: 'Após confirmação, o pagamento é liberado em 1–2 dias' },
          ].map((item, i) => (
            <View key={i} style={styles.step}>
              <Text style={{ fontSize: 20 }}>{item.icon}</Text>
              <Text style={styles.stepText}>{item.step}</Text>
            </View>
          ))}
        </View>

        {/* Aviso comissão */}
        <View style={styles.commissionInfo}>
          <Text style={{ fontSize: 16 }}>ℹ️</Text>
          <Text style={styles.commissionText}>
            A comissão Onda (15%) será descontada automaticamente na liberação. O valor líquido será creditado em sua conta.
          </Text>
        </View>

        <Button
          label="Acompanhar chamado"
          onPress={() => nav.navigate('RequestDetail', { requestId: route.params.requestId })}
        />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[6], paddingBottom: space[7], gap: space[4] },
  heroCard: {
    backgroundColor: color.institutional,
    borderRadius: radius.card,
    padding: space[6],
    alignItems: 'center',
    gap: space[3],
  },
  icon: {
    width: 88,
    height: 88,
    borderRadius: 44,
    backgroundColor: 'rgba(255,255,255,0.15)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.textOnAccent },
  subtitle: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.accentSky },
  body: {
    fontSize: font.size.body,
    color: color.accentSky,
    textAlign: 'center',
    lineHeight: font.size.body * font.lineHeight.body,
  },
  stepsCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  stepsTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  step: { flexDirection: 'row', gap: space[3], alignItems: 'flex-start' },
  stepText: { flex: 1, fontSize: font.size.bodySm, color: color.text, lineHeight: font.size.bodySm * 1.6, paddingTop: 2 },
  commissionInfo: {
    flexDirection: 'row',
    gap: space[3],
    backgroundColor: color.sunTint,
    borderRadius: radius.field,
    padding: space[4],
    alignItems: 'flex-start',
  },
  commissionText: { flex: 1, fontSize: font.size.caption, color: color.textSoft, lineHeight: font.size.caption * 1.5 },
});
