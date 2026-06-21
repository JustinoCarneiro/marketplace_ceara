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
import StarRating from '../../components/StarRating';
import Button from '../../components/Button';

const NOTA_LABELS: Record<number, string> = {
  1: 'Muito ruim', 2: 'Ruim', 3: 'Regular', 4: 'Bom', 5: 'Excelente!',
};

export default function RateScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const token = useAuthStore(s => s.accessToken);
  const { requestId, avaliadoId, avaliadoNome } = route.params;
  const [nota, setNota] = useState(0);
  const [comentario, setComentario] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit() {
    if (nota === 0) { setError('Selecione uma nota de 1 a 5 estrelas.'); return; }
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`http://10.0.2.2:8080/api/v1/service-requests/${requestId}/review`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ avaliadoId, nota, comentario }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao avaliar');
      nav.navigate('RateConfirm', { nota, comentario });
    } catch (e: any) {
      setError(e.message ?? 'Erro ao enviar avaliação.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScreenHeader title="Avaliar" onBack={() => nav.goBack()} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">

          {/* Quem está avaliando */}
          <View style={styles.whoCard}>
            <View style={styles.avatar}>
              <Text style={{ fontSize: 32 }}>👤</Text>
            </View>
            <Text style={styles.whoText}>Como foi {avaliadoNome}?</Text>
          </View>

          {/* Estrelas */}
          <View style={styles.starsSection}>
            <StarRating value={nota} onSelect={setNota} size={44} />
            {nota > 0 && (
              <Text style={styles.notaLabel}>{NOTA_LABELS[nota]}</Text>
            )}
          </View>

          {/* Comentário */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>COMENTÁRIO (OPCIONAL)</Text>
            <View style={styles.textAreaWrap}>
              <TextInput
                style={styles.textArea}
                placeholder="Conte mais sobre a sua experiência…"
                placeholderTextColor={color.textFaint}
                value={comentario}
                onChangeText={setComentario}
                multiline
                numberOfLines={4}
                textAlignVertical="top"
                maxLength={500}
              />
            </View>
            <Text style={styles.charCount}>{comentario.length}/500</Text>
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button label="Enviar avaliação" onPress={submit} loading={loading} disabled={nota === 0} />
          <Button label="Pular" variant="ghost" onPress={() => nav.popToTop()} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  whoCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    alignItems: 'center',
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  avatar: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  whoText: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  starsSection: { alignItems: 'center', gap: space[3] },
  notaLabel: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.warmSun },
  section: { gap: space[2] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  textAreaWrap: {
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.surface,
    padding: space[4],
    minHeight: 120,
  },
  textArea: { fontSize: font.size.body, color: color.text, fontFamily: font.family },
  charCount: { fontSize: font.size.caption, color: color.textFaint, textAlign: 'right' },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
});
