import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TextInput,
  KeyboardAvoidingView, Platform, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import { useAuthStore } from '../../store/auth';

const C = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#8A989B',
  primary: '#14A8A0',
  institutional2: '#15596E',
  lineSoft: '#E6DDC9',
  warmTerra: '#DA6A32',
  danger: '#C0392B',
};

const REASONS = [
  'Serviço não foi concluído',
  'Qualidade abaixo do combinado',
  'Profissional não compareceu',
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
      const res = await fetch(`${API_BASE}/service-requests/${route.params.requestId}/disputes`, {
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
      <View style={styles.screen}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => nav.goBack()} activeOpacity={0.7}>
            <Feather name="chevron-left" size={22} color={C.text} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Abrir disputa</Text>
        </View>

        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {/* Warning banner */}
            <View style={styles.warningBanner}>
              <Feather name="alert-triangle" size={20} color="#C2572A" style={{ flexShrink: 0 }} />
              <Text style={styles.warningText}>
                Seu dinheiro <Text style={styles.warningBold}>permanece retido</Text> enquanto avaliamos a disputa. Nossa equipe entra em contato em até 48h.
              </Text>
            </View>

            {/* Reason */}
            <View style={styles.section}>
              <Text style={styles.sectionLabel}>Qual o motivo?</Text>
              <View style={styles.reasonList}>
                {REASONS.map(r => (
                  <TouchableOpacity
                    key={r}
                    style={[styles.reasonOption, reason === r && styles.reasonOptionSelected]}
                    onPress={() => setReason(r)}
                    activeOpacity={0.7}
                  >
                    <View style={[styles.radio, reason === r && styles.radioSelected]} />
                    <Text style={[styles.reasonText, reason === r && styles.reasonTextSelected]}>{r}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>

            {/* Description */}
            <View style={styles.section}>
              <Text style={styles.sectionLabel}>Conte o que aconteceu</Text>
              <TextInput
                style={styles.textArea}
                placeholder="O profissional desligou a energia e foi embora sem terminar a instalação."
                placeholderTextColor={C.textFaint}
                value={details}
                onChangeText={setDetails}
                multiline
                numberOfLines={4}
                textAlignVertical="top"
              />
            </View>

            {error ? <Text style={styles.error}>{error}</Text> : null}
          </ScrollView>

          <View style={styles.footer}>
            <TouchableOpacity
              style={[styles.btnDanger, loading && { opacity: 0.7 }]}
              onPress={openDispute}
              activeOpacity={0.85}
              disabled={loading}
            >
              <Text style={styles.btnDangerText}>
                {loading ? 'Abrindo disputa…' : 'Abrir disputa'}
              </Text>
            </TouchableOpacity>
          </View>
        </KeyboardAvoidingView>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.bg },
  screen: { flex: 1, backgroundColor: C.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 8,
    paddingBottom: 16,
  },
  headerTitle: {
    fontSize: 21,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: C.text,
  },
  content: {
    paddingHorizontal: 20,
    paddingBottom: 24,
    gap: 18,
  },
  warningBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    backgroundColor: '#F7E3D6',
    borderWidth: 1,
    borderColor: '#E6BFA6',
    borderRadius: 12,
    padding: 14,
  },
  warningText: {
    flex: 1,
    fontSize: 13,
    lineHeight: 13 * 1.5,
    color: '#9A4A22',
  },
  warningBold: {
    fontWeight: '700',
  },
  section: {
    gap: 8,
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: C.institutional2,
  },
  reasonList: {
    gap: 10,
  },
  reasonOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 12,
    padding: 14,
  },
  reasonOptionSelected: {
    borderWidth: 2,
    borderColor: C.warmTerra,
  },
  radio: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: '#DCD2BC',
  },
  radioSelected: {
    borderWidth: 6,
    borderColor: C.warmTerra,
  },
  reasonText: {
    fontSize: 14.5,
    color: C.textSoft,
  },
  reasonTextSelected: {
    fontWeight: '600',
    color: C.text,
  },
  textArea: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 12,
    padding: 14,
    minHeight: 80,
    fontSize: 14.5,
    lineHeight: 14.5 * 1.55,
    color: C.textSoft,
  },
  error: {
    fontSize: 12,
    color: C.danger,
    textAlign: 'center',
  },
  footer: {
    padding: 14,
    paddingBottom: 20,
    backgroundColor: C.surface,
    borderTopWidth: 1,
    borderTopColor: C.lineSoft,
  },
  btnDanger: {
    height: 56,
    borderRadius: 100,
    backgroundColor: C.warmTerra,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: C.warmTerra,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.5,
    shadowRadius: 16,
    elevation: 6,
  },
  btnDangerText: {
    fontSize: 16,
    fontWeight: '800',
    color: '#fff',
  },
});
