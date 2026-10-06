import { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  KeyboardAvoidingView, Platform, TouchableOpacity, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ParamListBase } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Feather } from '@expo/vector-icons';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import { tornarPrestador } from '../../api/papeis';

const CATEGORIES = ['Elétrica', 'Hidráulica', 'Limpeza', 'Pintura', 'Reforma', 'Jardinagem', 'Geral'];

/**
 * "Quero ser prestador" — conta única com papéis: quem já é cliente passa a prestar serviço NA MESMA conta, sem outro
 * e-mail nem outra senha. Pede só o que falta (CPF, categoria, bio, aceite dos termos). O cadastro fica em verificação,
 * como o de qualquer prestador novo; ao enviar, a sessão passa para o modo prestador (o Perfil alterna de volta).
 *
 * O CPF precisa ser o mesmo se a conta já o confirmou num pagamento — o servidor recusa outro e diz por quê.
 */
export default function BecomeProviderScreen() {
  const nav = useNavigation<NativeStackNavigationProp<ParamListBase>>();
  const token = useAuthStore(s => s.accessToken);
  const refresh = useAuthStore(s => s.refreshToken);
  const login = useAuthStore(s => s.login);

  const [cpf, setCpf] = useState('');
  const [bio, setBio] = useState('');
  const [categoria, setCategoria] = useState('');
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function enviar() {
    setError('');
    if (!categoria) { setError('Selecione uma categoria.'); return; }
    if (!acceptedTerms) { setError('É preciso aceitar os Termos de Uso e a Política de Privacidade.'); return; }
    setLoading(true);
    const r = await tornarPrestador(token ?? '', { cpf, categoria, bio, aceitouTermos: acceptedTerms, refreshToken: refresh ?? '' });
    setLoading(false);
    if (!r.ok) { setError(r.mensagem); return; }
    login(r.sessao);   // já em modo prestador (em verificação); o RootNavigator troca a pilha
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={14} style={styles.back}
            accessibilityLabel="Voltar" accessibilityRole="button">
            <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
          </TouchableOpacity>

          <Text style={styles.title}>
            Quero ser{' '}
            <Text style={styles.titleAccent}>prestador</Text>
          </Text>
          <Text style={styles.subtitle}>
            Você continua com a mesma conta e pode alternar entre cliente e prestador quando quiser.
          </Text>

          <View style={styles.form}>
            <View style={styles.field}>
              <Text style={styles.label}>CPF</Text>
              <TextInput
                testID="input-cpf-prestador"
                style={styles.input}
                placeholder="123.456.789-00"
                placeholderTextColor={color.textFaint}
                value={cpf}
                onChangeText={t => setCpf(t.replace(/\D/g, '').replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4'))}
                keyboardType="numeric"
                maxLength={14}
              />
              <View style={styles.lgpdNotice}>
                <Feather name="lock" size={14} color={color.institutional2} />
                <Text style={styles.lgpdText}>Armazenado com segurança e usado só para verificação (LGPD).</Text>
              </View>
            </View>

            <View style={styles.field}>
              <Text style={styles.label}>CATEGORIA PRINCIPAL</Text>
              <View style={styles.chips}>
                {CATEGORIES.map(cat => {
                  const active = categoria === cat;
                  return (
                    <TouchableOpacity
                      key={cat}
                      style={[styles.chip, active && styles.chipActive]}
                      onPress={() => setCategoria(cat)}
                      activeOpacity={0.8}
                    >
                      <Text style={[styles.chipText, active && styles.chipTextActive]}>{cat}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            <View style={styles.field}>
              <Text style={styles.label}>BIO</Text>
              <TextInput
                style={[styles.input, styles.textarea]}
                placeholder="Eletricista há 12 anos em Fortaleza. Instalações residenciais, quadros e manutenção."
                placeholderTextColor={color.textFaint}
                value={bio}
                onChangeText={setBio}
                multiline
                numberOfLines={3}
                textAlignVertical="top"
              />
            </View>

            <TouchableOpacity
              testID="checkbox-termos-prestador"
              style={styles.termsRow}
              onPress={() => setAcceptedTerms(v => !v)}
              activeOpacity={0.7}
            >
              <View style={[styles.checkbox, acceptedTerms && styles.checkboxChecked]}>
                {acceptedTerms && <Feather name="check" size={13} color={color.textOnAccent} />}
              </View>
              <Text style={styles.termsText}>
                Li e aceito os{' '}
                <Text style={styles.termsLink} onPress={() => nav.navigate('Legal', { doc: 'terms' })} suppressHighlighting>
                  Termos de Uso
                </Text>
                {' '}e a{' '}
                <Text style={styles.termsLink} onPress={() => nav.navigate('Legal', { doc: 'privacy' })} suppressHighlighting>
                  Política de Privacidade
                </Text>
              </Text>
            </TouchableOpacity>
          </View>

          {error ? (
            <View style={styles.errorRow}>
              <Feather name="alert-circle" size={14} color={color.danger} accessibilityElementsHidden />
              <Text testID="erro-tornar-prestador" style={styles.errorText}>{error}</Text>
            </View>
          ) : null}
        </ScrollView>

        <View style={styles.footer}>
          <TouchableOpacity
            testID="btn-enviar-prestador"
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={enviar}
            disabled={loading}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>{loading ? 'Enviando...' : 'Enviar para verificação'}</Text>
          </TouchableOpacity>
          <Text style={styles.verifyNote}>
            Depois de enviar, seu cadastro de prestador fica{' '}
            <Text style={styles.verifyBadge}>EM VERIFICAÇÃO</Text>.
          </Text>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[5], paddingBottom: space[4] },

  back: { paddingVertical: space[3], minWidth: 48, minHeight: 48, justifyContent: 'center' },
  titleAccent: { color: color.primaryInk },
  title: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.025 * font.size.h1,
    marginTop: space[2],
  },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft, marginTop: space[2], marginBottom: space[5], lineHeight: 20 },

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
  textarea: { minHeight: 64, paddingTop: 14 },

  lgpdNotice: {
    flexDirection: 'row', alignItems: 'center', gap: 7,
    backgroundColor: color.skyTint, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8,
  },
  lgpdText: { flex: 1, fontSize: 12, color: color.institutional2, lineHeight: 12 * 1.4 },

  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    paddingHorizontal: 14, paddingVertical: 8, borderRadius: radius.pill,
    backgroundColor: color.surface, borderWidth: 1, borderColor: color.lineSoft,
  },
  chipActive: { backgroundColor: color.sunTint, borderWidth: 1.5, borderColor: color.warmSun },
  chipText: { fontSize: font.size.caption, fontWeight: font.weight.semibold, color: color.textSoft },
  chipTextActive: { fontWeight: font.weight.bold, color: color.text },

  termsRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, marginTop: 4 },
  checkbox: {
    width: 20, height: 20, borderRadius: 5, borderWidth: 1.5, borderColor: color.lineSoft,
    backgroundColor: color.surface, alignItems: 'center', justifyContent: 'center', marginTop: 1,
  },
  checkboxChecked: { backgroundColor: color.primary, borderColor: color.primary },
  termsText: { flex: 1, fontSize: font.size.caption + 0.5, color: color.textSoft, lineHeight: 18 },
  termsLink: { color: color.primaryInk, fontWeight: font.weight.semibold },

  errorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: space[3] },
  errorText: { flex: 1, fontSize: font.size.caption, color: color.danger, textAlign: 'center' },

  footer: {
    paddingHorizontal: space[5], paddingTop: space[3], paddingBottom: space[5],
    backgroundColor: color.surface, borderTopWidth: 1, borderTopColor: color.lineSoft, gap: space[3],
  },
  cta: {
    height: 56, backgroundColor: color.primary, borderRadius: radius.pill, alignItems: 'center', justifyContent: 'center',
    shadowColor: color.primary, shadowOffset: { width: 0, height: 16 }, shadowOpacity: 0.55, shadowRadius: 24, elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
  verifyNote: { fontSize: 12, color: color.textFaint, textAlign: 'center' },
  verifyBadge: { fontWeight: font.weight.bold, color: color.sunInk },
});
