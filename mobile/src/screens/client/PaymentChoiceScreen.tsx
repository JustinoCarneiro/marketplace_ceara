import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ClientStackParams, 'PaymentChoice'>;
type Method = 'pix' | 'card';

const COMISSAO = 0.1;

export default function PaymentChoiceScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const { requestId, valor } = route.params;
  const comissao = Math.round(valor * COMISSAO * 100) / 100;
  const total = valor + comissao;

  const [method, setMethod] = useState<Method>('pix');
  const [loading, setLoading] = useState(false);

  async function pay() {
    setLoading(true);
    try {
      await fetch(`${API_BASE}/payments/initiate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ requestId, method }),
      });
      if (method === 'pix') {
        nav.replace('PaymentPix', { requestId, valor: total });
      } else {
        nav.replace('PaymentCard', { requestId, valor: total });
      }
    } catch {
      setLoading(false);
    }
  }

  return (
    <View style={styles.overlay}>
      {/* Tap overlay to dismiss */}
      <TouchableOpacity style={styles.dimArea} onPress={() => nav.goBack()} activeOpacity={1} />

      {/* Sheet */}
      <View style={styles.sheet}>
        <View style={styles.handle} />

        <Text style={styles.title}>Pagamento</Text>

        {/* Summary */}
        <View style={styles.summaryBox}>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Serviço · José Wagner</Text>
            <Text style={styles.summaryVal}>R$ {valor.toFixed(2).replace('.', ',')}</Text>
          </View>
          <View style={styles.summaryRow}>
            <Text style={styles.summaryLabel}>Comissão Onda (10%)</Text>
            <Text style={styles.summaryVal}>R$ {comissao.toFixed(2).replace('.', ',')}</Text>
          </View>
          <View style={styles.divider} />
          <View style={styles.summaryRow}>
            <Text style={styles.totalLabel}>Total</Text>
            <Text style={styles.totalVal}>R$ {total.toFixed(2).replace('.', ',')}</Text>
          </View>
        </View>

        {/* Payment methods */}
        <View style={styles.methods}>
          <TouchableOpacity
            style={[styles.method, method === 'pix' && styles.methodActive]}
            onPress={() => setMethod('pix')}
            activeOpacity={0.8}
          >
            <View style={[styles.methodIcon, method === 'pix' && styles.methodIconActive]}>
              <Feather name="grid" size={22} color={method === 'pix' ? color.textOnAccent : color.institutional2} />
            </View>
            <View style={styles.methodInfo}>
              <Text style={styles.methodName}>Pix</Text>
              <Text style={styles.methodSub}>Aprovação na hora</Text>
            </View>
            {method === 'pix' ? (
              <View style={styles.radioFilled}>
                <Feather name="check" size={13} color={color.textOnAccent} />
              </View>
            ) : (
              <View style={styles.radioEmpty} />
            )}
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.method, styles.methodWhite, method === 'card' && styles.methodActive]}
            onPress={() => setMethod('card')}
            activeOpacity={0.8}
          >
            <View style={[styles.methodIcon, styles.methodIconCard]}>
              <Feather name="credit-card" size={22} color={color.institutional2} />
            </View>
            <View style={styles.methodInfo}>
              <Text style={styles.methodName}>Cartão de crédito</Text>
              <Text style={styles.methodSub}>Em até 12x</Text>
            </View>
            {method === 'card' ? (
              <View style={styles.radioFilled}>
                <Feather name="check" size={13} color={color.textOnAccent} />
              </View>
            ) : (
              <View style={styles.radioEmpty} />
            )}
          </TouchableOpacity>
        </View>

        {/* Escrow notice */}
        <View style={styles.escrowNotice}>
          <Feather name="shield" size={18} color={color.textOnAccent} />
          <Text style={styles.escrowText}>
            Pagamento <Text style={{ fontWeight: font.weight.black }}>retido com segurança</Text>. Só é liberado quando você confirmar o serviço.
          </Text>
        </View>

        {/* CTA */}
        <TouchableOpacity style={styles.cta} onPress={pay} activeOpacity={0.85} disabled={loading}>
          {loading ? (
            <ActivityIndicator color={color.textOnAccent} />
          ) : (
            <Text style={styles.ctaText}>Pagar R$ {total.toFixed(2).replace('.', ',')}</Text>
          )}
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(14,42,51,0.55)', justifyContent: 'flex-end' },
  dimArea: { flex: 1 },

  sheet: {
    backgroundColor: color.surface,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    paddingHorizontal: space[5] - 2,
    paddingBottom: space[5] + 4,
    paddingTop: space[3] - 2,
    gap: 18,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: -20 },
    shadowOpacity: 0.3,
    shadowRadius: 40,
    elevation: 20,
  },
  handle: {
    width: 44,
    height: 5,
    borderRadius: 100,
    backgroundColor: color.line,
    alignSelf: 'center',
    marginBottom: 4,
  },
  title: {
    fontSize: 22,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * 22,
  },

  summaryBox: {
    backgroundColor: color.bg,
    borderRadius: radius.field,
    padding: space[4],
    gap: space[3],
  },
  summaryRow: { flexDirection: 'row', justifyContent: 'space-between' },
  summaryLabel: { fontSize: 14, color: color.textSoft },
  summaryVal: { fontSize: 14, fontWeight: font.weight.semibold, color: color.text },
  divider: { height: 1, backgroundColor: color.line },
  totalLabel: { fontSize: 16, fontWeight: font.weight.bold, color: color.text },
  totalVal: { fontSize: 16, fontWeight: font.weight.black, color: color.text },

  methods: { gap: space[3] },
  method: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderWidth: 2,
    borderColor: color.primary,
    borderRadius: radius.field,
    padding: space[3] + 2,
  },
  methodWhite: { backgroundColor: color.textOnAccent, borderWidth: 1, borderColor: color.lineSoft },
  methodActive: { backgroundColor: color.skyTint, borderWidth: 2, borderColor: color.primary },
  methodIcon: {
    width: 42,
    height: 42,
    borderRadius: radius.field,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  methodIconActive: { backgroundColor: color.primary },
  methodIconCard: { backgroundColor: color.bg },
  methodInfo: { flex: 1 },
  methodName: { fontSize: 15, fontWeight: font.weight.bold, color: color.text },
  methodSub: { fontSize: font.size.caption, color: color.textSoft, marginTop: 1 },
  radioFilled: {
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioEmpty: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: color.line,
  },

  escrowNotice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 9,
    backgroundColor: color.institutional,
    borderRadius: radius.field,
    padding: space[3] + 2,
  },
  escrowText: { flex: 1, fontSize: font.size.caption, color: color.textOnAccent, lineHeight: font.size.caption * 1.45 },

  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.55,
    shadowRadius: 24,
    elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
});
