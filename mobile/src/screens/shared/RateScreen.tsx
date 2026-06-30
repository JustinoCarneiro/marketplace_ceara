import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TextInput, KeyboardAvoidingView, Platform,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import { useAuthStore } from '../../store/auth';

const COLORS = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#8A989B',
  primary: '#14A8A0',
  institutional2: '#15596E',
  lineSoft: '#E6DDC9',
  line: '#DCD2BC',
  danger: '#C0392B',
  warmTerra: '#DA6A32',
  warmSun: '#F2B015',
  concluido: '#15756E',
  concluidoBg: '#DDF0EC',
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
      const res = await fetch(`${API_BASE}/service-requests/${requestId}/review`, {
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

  const initials = typeof avaliadoNome === 'string'
    ? avaliadoNome.split(' ').slice(0, 2).map((w: string) => w[0]).join('').toUpperCase()
    : 'JW';

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scrollContent} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={8} style={styles.backBtn}
            accessibilityLabel="Voltar" accessibilityRole="button">
            <Feather name="chevron-left" size={22} color={COLORS.text} accessibilityElementsHidden />
          </TouchableOpacity>

          <View style={styles.hero}>
            <View style={[styles.completedBadge]}>
              <Feather name="check" size={13} color={COLORS.concluido} />
              <Text style={styles.completedText}>SERVIÇO CONCLUÍDO</Text>
            </View>

            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{initials}</Text>
            </View>

            <Text style={styles.heroTitle}>Como foi o serviço de {avaliadoNome}?</Text>
          </View>

          <View style={styles.starsRow} accessibilityRole="radiogroup" accessibilityLabel="Nota de 1 a 5 estrelas">
            {[1, 2, 3, 4, 5].map(i => (
              <TouchableOpacity key={i} onPress={() => setNota(i)} hitSlop={6} activeOpacity={0.7}
                accessibilityRole="radio"
                accessibilityLabel={`${i} estrela${i > 1 ? 's' : ''}`}
                accessibilityState={{ checked: nota === i }}>
                <Text style={[styles.star, { color: i <= nota ? COLORS.warmSun : COLORS.line }]}>★</Text>
              </TouchableOpacity>
            ))}
          </View>

          <View style={styles.fields}>
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>Comentário</Text>
              <View style={styles.textAreaWrap}>
                <TextInput
                  style={styles.textArea}
                  placeholder="Trabalho limpo e rápido, explicou tudo direitinho. Recomendo!"
                  placeholderTextColor={COLORS.textSoft}
                  value={comentario}
                  onChangeText={setComentario}
                  multiline
                  numberOfLines={4}
                  textAlignVertical="top"
                  maxLength={500}
                />
              </View>
            </View>

            <TouchableOpacity style={styles.photoBtn} activeOpacity={0.75}>
              <View style={styles.photoBox}>
                <Feather name="camera" size={22} color={COLORS.primary} />
              </View>
              <Text style={styles.photoBtnText}>
                Adicionar foto <Text style={styles.photoOptional}>(opcional)</Text>
              </Text>
            </TouchableOpacity>
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}

        </ScrollView>

        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.submitBtn, (nota === 0 || loading) && { opacity: 0.5 }]}
            onPress={submit}
            disabled={nota === 0 || loading}
            activeOpacity={0.85}
          >
            <Text style={styles.submitBtnText}>{loading ? 'Enviando…' : 'Enviar avaliação'}</Text>
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.bg },
  scrollContent: {
    paddingBottom: 24,
  },
  backBtn: {
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 0,
  },
  hero: {
    paddingHorizontal: 20,
    paddingTop: 18,
    paddingBottom: 8,
    alignItems: 'center',
    gap: 14,
  },
  completedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: COLORS.concluidoBg,
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 100,
  },
  completedText: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.06,
    color: COLORS.concluido,
  },
  avatar: {
    width: 74,
    height: 74,
    borderRadius: 24,
    backgroundColor: COLORS.warmTerra,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    fontSize: 24,
    fontWeight: '800',
    color: '#fff',
  },
  heroTitle: {
    fontSize: 26,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: COLORS.text,
    textAlign: 'center',
    lineHeight: 32,
  },
  starsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 10,
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 20,
  },
  star: {
    fontSize: 42,
  },
  fields: {
    paddingHorizontal: 20,
    gap: 16,
  },
  fieldGroup: {
    gap: 8,
  },
  fieldLabel: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 0.1,
    textTransform: 'uppercase',
    color: COLORS.institutional2,
  },
  textAreaWrap: {
    backgroundColor: COLORS.surface,
    borderWidth: 1,
    borderColor: COLORS.lineSoft,
    borderRadius: 12,
    padding: 14,
    minHeight: 84,
  },
  textArea: {
    fontSize: 14.5,
    lineHeight: 22,
    color: COLORS.textSoft,
  },
  photoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  photoBox: {
    width: 60,
    height: 60,
    borderRadius: 12,
    borderWidth: 1.5,
    borderStyle: 'dashed',
    borderColor: '#B7DCE3',
    backgroundColor: COLORS.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  photoBtnText: {
    fontSize: 13.5,
    color: COLORS.textSoft,
  },
  photoOptional: {
    color: COLORS.textFaint,
  },
  error: {
    fontSize: 12,
    color: COLORS.danger,
    textAlign: 'center',
    marginHorizontal: 20,
    marginTop: 8,
  },
  footer: {
    backgroundColor: COLORS.surface,
    borderTopWidth: 1,
    borderTopColor: COLORS.lineSoft,
    paddingHorizontal: 20,
    paddingTop: 14,
    paddingBottom: 20,
  },
  submitBtn: {
    width: '100%',
    height: 56,
    borderRadius: 100,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 6,
  },
  submitBtnText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#fff',
  },
});
