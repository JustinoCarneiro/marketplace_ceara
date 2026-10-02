import { API_BASE } from '../../api/config';
import { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TextInput,
  TouchableOpacity, ActivityIndicator,
  KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ProviderNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

/**
 * Chave Pix do prestador — destino do repasse quando o pedido é concluído
 * (MKT-49, Modelo A). O backend nunca devolve a chave em claro: o GET só diz
 * se já existe; o PUT sobrescreve. Sem chave cadastrada, o repasse não tem
 * pra onde ir quando o serviço fecha.
 */
export default function ChavePixScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const token = useAuthStore(s => s.accessToken);

  const [chavePix, setChavePix] = useState('');
  const [cadastrada, setCadastrada] = useState<boolean | null>(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    let ativo = true;
    (async () => {
      try {
        const res = await fetch(`${API_BASE}/providers/me/chave-pix`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        const data = await res.json();
        if (ativo && res.ok) setCadastrada(!!data.cadastrada);
      } catch {
        // Sem status inicial não bloqueia o cadastro — só não mostra o selo "já cadastrada".
      } finally {
        if (ativo) setLoadingStatus(false);
      }
    })();
    return () => { ativo = false; };
  }, [token]);

  async function salvar() {
    if (!chavePix.trim()) {
      setError('Informe sua chave Pix.');
      return;
    }
    setError('');
    setSuccess(false);
    setSaving(true);
    try {
      const res = await fetch(`${API_BASE}/providers/me/chave-pix`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ chavePix: chavePix.trim() }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao salvar a chave Pix.');
      setCadastrada(true);
      setChavePix('');
      setSuccess(true);
    } catch (e: any) {
      setError(e.message ?? 'Erro ao salvar. Tente novamente.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Back */}
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={14} style={styles.back}
            accessibilityLabel="Voltar" accessibilityRole="button">
            <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
          </TouchableOpacity>

          <Text style={styles.title}>
            Chave <Text style={styles.titleAccent}>Pix</Text>
          </Text>
          <Text style={styles.subtitle}>
            É pra essa chave que o pagamento do serviço é repassado quando você conclui um
            atendimento. Sem ela cadastrada, o repasse fica pendente.
          </Text>

          {!loadingStatus && cadastrada && (
            <View style={styles.statusRow}>
              <Feather name="check-circle" size={16} color={color.success} accessibilityElementsHidden />
              <Text style={styles.statusText}>Você já tem uma chave Pix cadastrada.</Text>
            </View>
          )}

          <View style={styles.form}>
            <View style={styles.field}>
              <Text style={styles.label}>
                {cadastrada ? 'NOVA CHAVE PIX (SUBSTITUI A ATUAL)' : 'SUA CHAVE PIX'}
              </Text>
              <TextInput
                testID="input-chave-pix"
                style={styles.input}
                placeholder="CPF, CNPJ, e-mail, telefone (+55…) ou aleatória"
                placeholderTextColor={color.textFaint}
                value={chavePix}
                onChangeText={setChavePix}
                autoCapitalize="none"
                autoCorrect={false}
              />
              <View style={styles.lgpdNotice}>
                <Feather name="lock" size={14} color={color.institutional2} />
                <Text style={styles.lgpdText}>
                  Armazenada com segurança (cifrada) e nunca exibida de volta na tela.
                </Text>
              </View>
            </View>
          </View>

          {success ? (
            <View style={styles.successRow}>
              <Feather name="check-circle" size={14} color={color.success} accessibilityElementsHidden />
              <Text style={styles.successText}>Chave Pix salva.</Text>
            </View>
          ) : null}

          {error ? (
            <View style={styles.errorRow}>
              <Feather name="alert-circle" size={14} color={color.danger} accessibilityElementsHidden />
              <Text style={styles.errorText}>{error}</Text>
            </View>
          ) : null}

          <TouchableOpacity
            testID="btn-salvar-chave-pix"
            style={[styles.cta, saving && { opacity: 0.7 }]}
            onPress={salvar}
            disabled={saving}
            activeOpacity={0.85}
          >
            {saving ? (
              <ActivityIndicator color={color.textOnAccent} />
            ) : (
              <Text style={styles.ctaText}>Salvar chave Pix</Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[5], paddingBottom: space[4] },

  back: { paddingVertical: space[3] },
  title: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.025 * font.size.h1,
    marginTop: space[2],
  },
  titleAccent: { color: color.primaryInk },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft, marginTop: space[2], marginBottom: space[5], lineHeight: 20 },

  statusRow: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    backgroundColor: color.skyTint, borderRadius: radius.field,
    paddingHorizontal: space[4], paddingVertical: 10, marginBottom: space[5],
  },
  statusText: { fontSize: font.size.caption + 0.5, color: color.institutional2, fontWeight: font.weight.semibold },

  form: { gap: 16 },
  field: { gap: 7 },
  label: { fontSize: font.size.eyebrow, fontWeight: font.weight.semibold, color: color.institutional2, letterSpacing: 0.1 * font.size.eyebrow },

  input: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
    paddingVertical: 14,
    fontSize: 15,
    color: color.text,
  },

  lgpdNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    backgroundColor: color.skyTint,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  lgpdText: { flex: 1, fontSize: 12, color: color.institutional2, lineHeight: 12 * 1.4 },

  successRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: space[4] },
  successText: { fontSize: font.size.caption, color: color.institutional2, textAlign: 'center', fontWeight: font.weight.semibold },

  errorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: space[3] },
  errorText: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },

  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: space[5],
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.55,
    shadowRadius: 24,
    elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
});
