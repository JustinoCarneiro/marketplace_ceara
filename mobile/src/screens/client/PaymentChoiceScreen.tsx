import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'PaymentChoice'>;

export default function PaymentChoiceScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [method, setMethod] = useState<'PIX' | 'CARTAO' | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const { requestId, proposalId, valor } = route.params;

  async function proceed() {
    if (!method) { setError('Selecione um método de pagamento.'); return; }
    setError('');
    setLoading(true);
    try {
      const idempotencyKey = `pay-${proposalId}-${Date.now()}`;
      const res = await fetch(`${API_BASE}/proposals/${proposalId}/accept`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({ metodo: method }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao processar pagamento');
      if (method === 'PIX') {
        nav.navigate('PaymentPix', { requestId, valor });
      } else {
        nav.navigate('PaymentCard', { requestId, valor });
      }
    } catch (e: any) {
      setError(e.message ?? 'Erro ao processar pagamento.');
    } finally {
      setLoading(false);
    }
  }

  const comissao = valor * 0.15;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.handle} />
      <ScreenHeader title="Pagamento" onBack={() => nav.goBack()} />
      <View style={styles.content}>

        {/* Resumo do valor */}
        <View style={styles.summaryCard}>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Valor do serviço</Text>
            <Text style={styles.summaryValue}>R$ {valor.toFixed(2).replace('.', ',')}</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Comissão Onda (15%)</Text>
            <Text style={styles.summaryValueSub}>R$ {comissao.toFixed(2).replace('.', ',')}</Text>
          </View>
          <View style={[styles.summaryRow, styles.totalRow]}>
            <Text style={styles.totalLabel}>Total a pagar</Text>
            <Text style={styles.totalValue}>R$ {valor.toFixed(2).replace('.', ',')}</Text>
          </View>
        </View>

        {/* Métodos */}
        <View style={styles.methods}>
          <Text style={styles.methodsLabel}>ESCOLHA O MÉTODO</Text>
          <TouchableOpacity
            style={[styles.methodCard, method === 'PIX' && styles.methodCardActive]}
            onPress={() => setMethod('PIX')}
            activeOpacity={0.85}
          >
            <Text style={{ fontSize: 28 }}>⚡</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.methodName}>PIX</Text>
              <Text style={styles.methodSub}>Instantâneo · Sem taxa extra</Text>
            </View>
            <View style={[styles.radio, method === 'PIX' && styles.radioOn]} />
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.methodCard, method === 'CARTAO' && styles.methodCardActive]}
            onPress={() => setMethod('CARTAO')}
            activeOpacity={0.85}
          >
            <Text style={{ fontSize: 28 }}>💳</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.methodName}>Cartão de crédito</Text>
              <Text style={styles.methodSub}>Débito em 1–2 dias úteis</Text>
            </View>
            <View style={[styles.radio, method === 'CARTAO' && styles.radioOn]} />
          </TouchableOpacity>
        </View>

        {/* Escrow info */}
        <View style={styles.escrowBanner}>
          <Text style={{ fontSize: 18 }}>🔒</Text>
          <Text style={styles.escrowText}>
            O valor fica <Text style={{ fontWeight: '700' }}>retido</Text> até você confirmar que o serviço foi concluído. Seguro e sem riscos.
          </Text>
        </View>

        {error ? <Text style={styles.error}>{error}</Text> : null}
        <Button label={`Pagar R$ ${valor.toFixed(2).replace('.', ',')}`} onPress={proceed} loading={loading} disabled={!method} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.surface },
  handle: { width: 40, height: 4, borderRadius: 2, backgroundColor: color.line, alignSelf: 'center', marginTop: space[3] },
  content: { flex: 1, paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[6], gap: space[4] },
  summaryCard: {
    backgroundColor: color.bg,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  summaryLabel: { fontSize: font.size.bodySm, color: color.textSoft },
  summaryValue: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  summaryValueSub: { fontSize: font.size.bodySm, color: color.textSoft },
  divider: { height: 1, backgroundColor: color.lineSoft },
  totalRow: { marginTop: space[1] },
  totalLabel: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  totalValue: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.primary },
  methods: { gap: space[3] },
  methodsLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  methodCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[4],
    backgroundColor: color.bg,
    borderRadius: radius.field,
    padding: space[4],
    borderWidth: 1.5,
    borderColor: color.line,
  },
  methodCardActive: { borderColor: color.primary, backgroundColor: '#DFF5F3' },
  methodName: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  methodSub: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2 },
  radio: { width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: color.line },
  radioOn: { borderColor: color.primary, backgroundColor: color.primary },
  escrowBanner: {
    flexDirection: 'row',
    gap: space[3],
    alignItems: 'flex-start',
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
  },
  escrowText: { flex: 1, fontSize: font.size.bodySm, color: color.institutional, lineHeight: font.size.bodySm * 1.55 },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
});
