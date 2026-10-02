import { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TextInput,
  TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { AuthNavProp, AuthStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { emailValido, pedirCodigo } from '../../api/passwordReset';

/**
 * Recuperação de senha, passo 1 (US35): o usuário informa o e-mail e recebe um código. A resposta
 * do servidor é a mesma exista a conta ou não — a tela avança igual nos dois casos, de propósito.
 */
export default function ForgotPasswordScreen() {
  const nav = useNavigation<AuthNavProp>();
  const route = useRoute<RouteProp<AuthStackParams, 'ForgotPassword'>>();
  const [email, setEmail] = useState(route.params?.email ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function enviar() {
    if (!emailValido(email)) {
      setError('Informe um e-mail válido.');
      return;
    }
    setError('');
    setLoading(true);
    const resultado = await pedirCodigo(email);
    setLoading(false);
    if (!resultado.ok) {
      setError(resultado.mensagem);
      return;
    }
    nav.navigate('ResetPassword', { email: email.trim() });
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
            Esqueci a <Text style={styles.titleAccent}>senha</Text>
          </Text>
          <Text style={styles.subtitle}>
            Informe o e-mail da sua conta. Se ele estiver cadastrado, enviamos um código para você
            redefinir a senha.
          </Text>

          <View style={styles.field}>
            <Text style={styles.label}>E-MAIL</Text>
            <TextInput
              testID="input-email-recuperacao"
              style={[styles.input, !!error && styles.inputError]}
              value={email}
              onChangeText={t => { setEmail(t); setError(''); }}
              placeholder="seu@email.com"
              placeholderTextColor={color.textFaint}
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              autoCorrect={false}
            />
          </View>

          {error ? (
            <View style={styles.errorRow}>
              <Feather name="alert-circle" size={14} color={color.danger} accessibilityElementsHidden />
              <Text testID="erro-recuperacao" style={styles.errorText}>{error}</Text>
            </View>
          ) : null}

          <TouchableOpacity
            testID="btn-enviar-codigo"
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={enviar}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? <ActivityIndicator color={color.textOnAccent} /> : <Text style={styles.ctaText}>Enviar código</Text>}
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
  inputError: { borderColor: color.danger, borderWidth: 1.5 },

  errorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: space[3] },
  errorText: { fontSize: font.size.caption, color: color.danger, textAlign: 'center', flexShrink: 1 },

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
