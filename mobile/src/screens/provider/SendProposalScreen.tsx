import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, TextInput,
  TouchableOpacity, ActivityIndicator,
  KeyboardAvoidingView, Platform,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ProviderNavProp, ProviderStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ProviderStackParams, 'SendProposal'>;

const COMISSAO = 0.1;

export default function SendProposalScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [valor, setValor] = useState('');
  const [prazoDias, setPrazoDias] = useState('2');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const valorNum = parseFloat(valor.replace(',', '.')) || 0;
  const recebeNum = valorNum * (1 - COMISSAO);

  async function send() {
    if (!valor || !prazoDias) { setError('Preencha o valor e o prazo.'); return; }
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/proposals`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          serviceRequestId: route.params.requestId,
          valor: valorNum,
          prazoDias: parseInt(prazoDias, 10),
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao enviar');
      nav.goBack();
    } catch (e: any) {
      setError(e.message ?? 'Erro ao enviar. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={styles.overlay}>
      {/* Tap to dismiss */}
      <TouchableOpacity style={styles.dimArea} onPress={() => nav.goBack()} activeOpacity={1} />

      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.sheet}>
          <View style={styles.handle} />

          {/* Title */}
          <View style={styles.titleBlock}>
            <Text style={styles.title}>Enviar proposta</Text>
            <Text style={styles.subtitle}>Troca de tomada com faísca · Aldeota</Text>
          </View>

          {/* Valor */}
          <View style={styles.field}>
            <Text style={styles.label}>SEU VALOR</Text>
            <View style={styles.valorInput}>
              <Text style={styles.valorPrefix}>R$</Text>
              <TextInput
                style={styles.valorField}
                placeholder="0,00"
                placeholderTextColor={color.textFaint}
                value={valor}
                onChangeText={setValor}
                keyboardType="decimal-pad"
              />
            </View>
          </View>

          {/* Prazo */}
          <View style={styles.field}>
            <Text style={styles.label}>PRAZO</Text>
            <View style={styles.prazoRow}>
              <View style={styles.prazoInput}>
                <TextInput
                  style={styles.prazoField}
                  value={prazoDias}
                  onChangeText={setPrazoDias}
                  keyboardType="number-pad"
                  maxLength={2}
                />
              </View>
              <Text style={styles.prazoUnit}>dias</Text>
            </View>
          </View>

          {/* Recebe após comissão */}
          {valorNum > 0 && (
            <View style={styles.comissaoRow}>
              <Text style={styles.comissaoLabel}>Você recebe após comissão</Text>
              <Text style={styles.comissaoVal}>R$ {recebeNum.toFixed(2).replace('.', ',')}</Text>
            </View>
          )}

          {error ? <Text style={styles.error}>{error}</Text> : null}

          {/* CTA */}
          <TouchableOpacity
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={send}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? (
              <ActivityIndicator color={color.textOnAccent} />
            ) : (
              <Text style={styles.ctaText}>Enviar proposta</Text>
            )}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
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

  titleBlock: { gap: 2 },
  title: { fontSize: 22, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.02 * 22 },
  subtitle: { fontSize: font.size.caption + 0.5, color: color.textSoft },

  field: { gap: 8 },
  label: { fontSize: font.size.eyebrow, fontWeight: font.weight.semibold, color: color.institutional2, letterSpacing: 0.1 * font.size.eyebrow },

  valorInput: {
    flexDirection: 'row',
    alignItems: 'baseline',
    backgroundColor: color.bg,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
    paddingVertical: space[3],
    gap: 6,
  },
  valorPrefix: { fontSize: 18, fontWeight: font.weight.black, color: color.textSoft },
  valorField: { flex: 1, fontSize: 26, fontWeight: font.weight.black, color: color.text },

  prazoRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  prazoInput: {
    flex: 1,
    backgroundColor: color.bg,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
    paddingVertical: space[3] + 2,
  },
  prazoField: { fontSize: 18, fontWeight: font.weight.bold, color: color.text },
  prazoUnit: { fontSize: 15, fontWeight: font.weight.semibold, color: color.textSoft },

  comissaoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
    paddingVertical: 12,
  },
  comissaoLabel: { fontSize: font.size.caption, color: color.institutional2 },
  comissaoVal: { fontSize: font.size.body, fontWeight: font.weight.black, color: color.institutional },

  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },

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
