import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ProviderNavProp, ProviderStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import Button from '../../components/Button';
import Input from '../../components/Input';

type RouteProps = RouteProp<ProviderStackParams, 'SendProposal'>;

export default function SendProposalScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [valor, setValor] = useState('');
  const [prazo, setPrazo] = useState('1');
  const [obs, setObs] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function sendProposal() {
    const valorNum = parseFloat(valor.replace(',', '.'));
    if (!valorNum || valorNum <= 0) { setError('Informe um valor válido.'); return; }
    setError('');
    setLoading(true);
    try {
      const res = await fetch('http://10.0.2.2:8080/api/v1/proposals', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Idempotency-Key': `prop-${route.params.requestId}-${Date.now()}`,
        },
        body: JSON.stringify({
          serviceRequestId: route.params.requestId,
          valor: valorNum,
          prazo_dias: parseInt(prazo, 10),
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao enviar proposta');
      nav.navigate('EscrowHeld', { requestId: route.params.requestId });
    } catch (e: any) {
      setError(e.message ?? 'Erro ao enviar proposta.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.handle} />
      <ScreenHeader title="Enviar proposta" onBack={() => nav.goBack()} />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">

          {/* Valor */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>VALOR DO SERVIÇO (R$)</Text>
            <View style={styles.valorRow}>
              <Text style={styles.currency}>R$</Text>
              <TextInput
                style={styles.valorInput}
                placeholder="0,00"
                placeholderTextColor={color.textFaint}
                value={valor}
                onChangeText={setValor}
                keyboardType="decimal-pad"
              />
            </View>
          </View>

          {/* Prazo */}
          <Input
            label="Prazo estimado (dias)"
            placeholder="1"
            value={prazo}
            onChangeText={setPrazo}
            keyboardType="number-pad"
            hint="Dias úteis para conclusão do serviço"
          />

          {/* Observações */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>OBSERVAÇÕES (OPCIONAL)</Text>
            <View style={styles.obsWrap}>
              <TextInput
                style={styles.obs}
                placeholder="Descreva o que está incluído no valor, materiais, etc."
                placeholderTextColor={color.textFaint}
                value={obs}
                onChangeText={setObs}
                multiline
                numberOfLines={4}
                textAlignVertical="top"
              />
            </View>
          </View>

          {/* Comissão info */}
          <View style={styles.commissionInfo}>
            <Text style={{ fontSize: 16 }}>ℹ️</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.commissionTitle}>Sobre a comissão Onda</Text>
              <Text style={styles.commissionBody}>
                Uma comissão de 15% sobre o valor do serviço é retida na conclusão. O cliente paga o valor total com escrow.
              </Text>
            </View>
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button label="Enviar proposta" onPress={sendProposal} loading={loading} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.surface },
  handle: { width: 40, height: 4, borderRadius: 2, backgroundColor: color.line, alignSelf: 'center', marginTop: space[3] },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  section: { gap: space[2] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  valorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    height: 72,
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.primary,
    backgroundColor: color.surface,
    paddingHorizontal: space[4],
  },
  currency: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.textFaint },
  valorInput: {
    flex: 1,
    fontSize: font.size.display,
    fontWeight: font.weight.black,
    color: color.text,
    fontFamily: font.family,
  },
  obsWrap: {
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.bg,
    padding: space[4],
    minHeight: 110,
  },
  obs: { fontSize: font.size.body, color: color.text, fontFamily: font.family },
  commissionInfo: {
    flexDirection: 'row',
    gap: space[3],
    backgroundColor: color.sunTint,
    borderRadius: radius.field,
    padding: space[4],
    alignItems: 'flex-start',
  },
  commissionTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.sunInk },
  commissionBody: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2, lineHeight: font.size.caption * 1.5 },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
});
