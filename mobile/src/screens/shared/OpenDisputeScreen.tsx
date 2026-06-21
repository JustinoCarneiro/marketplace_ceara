import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import Button from '../../components/Button';

const REASONS = [
  'Serviço não foi realizado',
  'Qualidade abaixo do esperado',
  'Prestador não compareceu',
  'Cobrança diferente do combinado',
  'Dano causado durante o serviço',
  'Outro motivo',
];

export default function OpenDisputeScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const token = useAuthStore(s => s.accessToken);
  const [reason, setReason] = useState('');
  const [details, setDetails] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function openDispute() {
    if (!reason) { setError('Selecione o motivo da disputa.'); return; }
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`http://10.0.2.2:8080/api/v1/service-requests/${route.params.requestId}/disputes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ motivo: `${reason}${details ? ': ' + details : ''}` }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao abrir disputa');
      nav.goBack();
    } catch (e: any) {
      setError(e.message ?? 'Erro ao abrir disputa.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.handle} />
      <ScreenHeader title="Abrir disputa" onBack={() => nav.goBack()} />
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">

          {/* Aviso */}
          <View style={styles.warning}>
            <Text style={{ fontSize: 20 }}>⚠️</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.warningTitle}>Antes de abrir uma disputa</Text>
              <Text style={styles.warningBody}>
                Tente resolver diretamente com a outra parte. A mediação Onda é o último recurso e pode levar até 5 dias úteis.
              </Text>
            </View>
          </View>

          {/* Motivo */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>MOTIVO DA DISPUTA</Text>
            {REASONS.map(r => (
              <TouchableOption
                key={r}
                label={r}
                selected={reason === r}
                onPress={() => setReason(r)}
              />
            ))}
          </View>

          {/* Detalhes */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>DETALHES ADICIONAIS</Text>
            <View style={styles.textAreaWrap}>
              <TextInput
                style={styles.textArea}
                placeholder="Descreva o problema com detalhes para ajudar a mediação…"
                placeholderTextColor={color.textFaint}
                value={details}
                onChangeText={setDetails}
                multiline
                numberOfLines={4}
                textAlignVertical="top"
              />
            </View>
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button label="Abrir disputa" variant="danger" onPress={openDispute} loading={loading} />
          <Button label="Cancelar" variant="ghost" onPress={() => nav.goBack()} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

function TouchableOption({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  const { TouchableOpacity } = require('react-native');
  return (
    <TouchableOpacity
      style={[styles.option, selected && styles.optionSelected]}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <View style={[styles.radio, selected && styles.radioOn]} />
      <Text style={[styles.optionText, selected && styles.optionTextSelected]}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.surface },
  handle: { width: 40, height: 4, borderRadius: 2, backgroundColor: color.line, alignSelf: 'center', marginTop: space[3] },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  warning: {
    flexDirection: 'row',
    gap: space[3],
    backgroundColor: color.terraTint,
    borderRadius: radius.field,
    padding: space[4],
    alignItems: 'flex-start',
  },
  warningTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.terraInk },
  warningBody: { fontSize: font.size.caption, color: color.textSoft, marginTop: 2, lineHeight: font.size.caption * 1.5 },
  section: { gap: space[2] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    padding: space[3],
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.bg,
  },
  optionSelected: { borderColor: color.danger, backgroundColor: color.dangerTint },
  radio: { width: 20, height: 20, borderRadius: 10, borderWidth: 2, borderColor: color.line },
  radioOn: { borderColor: color.danger, backgroundColor: color.danger },
  optionText: { flex: 1, fontSize: font.size.bodySm, color: color.textSoft },
  optionTextSelected: { color: color.dangerInk, fontWeight: font.weight.semibold },
  textAreaWrap: {
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.bg,
    padding: space[4],
    minHeight: 110,
  },
  textArea: { fontSize: font.size.body, color: color.text, fontFamily: font.family },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
});
