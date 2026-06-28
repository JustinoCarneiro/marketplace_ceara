import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  KeyboardAvoidingView, Platform, TouchableOpacity, TextInput,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

function passwordStrength(p: string) {
  if (p.length === 0) return 0;
  if (p.length < 8) return 1; // too short — backend rejects < 8
  const hasNum = /\d/.test(p);
  const hasSym = /[^A-Za-z0-9]/.test(p);
  if (hasNum && hasSym) return 4;
  if (hasNum || hasSym) return 3;
  return 2;
}

const STRENGTH_COLORS = ['', color.danger, color.warning, color.success, color.success];
const STRENGTH_LABELS = ['', 'Muito curta', 'Regular', 'Boa', 'Forte'];

export default function RegisterClientScreen() {
  const nav = useNavigation<AuthNavProp>();
  const login = useAuthStore(s => s.login);
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [showSenha, setShowSenha] = useState(false);
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const strength = passwordStrength(senha);
  const canSubmit = nome.trim().length > 0 && email.trim().length > 0 && senha.length >= 8 && acceptedTerms;

  async function handleRegister() {
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/auth/register/client`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nome, email, senha }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao cadastrar');
      const loginRes = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, senha }),
      });
      const loginData = await loginRes.json();
      if (!loginRes.ok) { nav.navigate('Login'); return; }
      login({
        accessToken: loginData.accessToken,
        refreshToken: loginData.refreshToken,
        role: loginData.role,
        userId: loginData.userId,
        nome: loginData.nome,
        email: loginData.email,
      });
    } catch (e: any) {
      setError(e.message ?? 'Erro ao cadastrar. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Back */}
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12} style={styles.back}>
            <Feather name="chevron-left" size={22} color={color.text} />
          </TouchableOpacity>

          <View style={styles.formWrapper}>
            {/* Heading */}
            <View style={styles.headingBlock}>
              <Text style={styles.title}>
                Criar conta{' '}
                <Text style={styles.titleAccent}>cliente</Text>
              </Text>
              <Text style={styles.subtitle}>Leva menos de 1 minuto.</Text>
            </View>

            {/* Form */}
            <View style={styles.form}>
            {/* Nome */}
            <View style={styles.field}>
              <Text style={styles.label}>NOME COMPLETO</Text>
              <TextInput
                style={styles.input}
                placeholder="Lúcia Maria Alves"
                placeholderTextColor={color.textFaint}
                value={nome}
                onChangeText={setNome}
                autoComplete="name"
              />
            </View>

            {/* Email */}
            <View style={styles.field}>
              <Text style={styles.label}>E-MAIL</Text>
              <View style={[styles.inputRow, error && error.toLowerCase().includes('email') && styles.inputError]}>
                <TextInput
                  style={[styles.input, styles.inputFlex]}
                  placeholder="lucia.alves@email.com"
                  placeholderTextColor={color.textFaint}
                  value={email}
                  onChangeText={t => { setEmail(t); setError(''); }}
                  keyboardType="email-address"
                  autoCapitalize="none"
                  autoComplete="email"
                />
                {error && error.toLowerCase().includes('email') && (
                  <Feather name="alert-circle" size={18} color={color.danger} />
                )}
              </View>
              {error && error.toLowerCase().includes('email') && (
                <View style={styles.errorRow}>
                  <Text style={styles.errorText}>{error} </Text>
                  <TouchableOpacity onPress={() => nav.navigate('Login')}>
                    <Text style={styles.errorLink}>Entrar</Text>
                  </TouchableOpacity>
                </View>
              )}
            </View>

            {/* Senha */}
            <View style={styles.field}>
              <Text style={styles.label}>SENHA</Text>
              <View style={styles.inputRow}>
                <TextInput
                  style={[styles.input, styles.inputFlex, senha.length > 0 && { letterSpacing: 3 }]}
                  placeholder="mínimo 8 caracteres"
                  placeholderTextColor={color.textFaint}
                  value={senha}
                  onChangeText={setSenha}
                  secureTextEntry={!showSenha}
                />
                <TouchableOpacity onPress={() => setShowSenha(s => !s)} hitSlop={8}>
                  <Feather name={showSenha ? 'eye-off' : 'eye'} size={20} color={color.textFaint} />
                </TouchableOpacity>
              </View>
              {senha.length > 0 && (
                <View style={styles.strengthRow}>
                  <View style={styles.strengthBars}>
                    {[1, 2, 3, 4].map(i => (
                      <View
                        key={i}
                        style={[styles.strengthBar, { backgroundColor: i <= strength ? STRENGTH_COLORS[strength] : color.lineSoft }]}
                      />
                    ))}
                  </View>
                  <Text style={[styles.strengthLabel, { color: STRENGTH_COLORS[strength] }]}>
                    {STRENGTH_LABELS[strength]}
                  </Text>
                </View>
              )}
              {senha.length > 0 && senha.length < 8 && (
                <Text style={styles.fieldHint}>Mínimo 8 caracteres</Text>
              )}
            </View>

            {/* Termos */}
            <TouchableOpacity
              style={styles.termsRow}
              onPress={() => setAcceptedTerms(v => !v)}
              activeOpacity={0.7}
            >
              <View style={[styles.checkbox, acceptedTerms && styles.checkboxChecked]}>
                {acceptedTerms && <Feather name="check" size={13} color={color.textOnAccent} />}
              </View>
              <Text style={styles.termsText}>
                Li e aceito os{' '}
                <Text
                  style={styles.termsLink}
                  onPress={() => nav.navigate('Legal', { doc: 'terms' })}
                  suppressHighlighting
                >Termos de Uso</Text>
                {' '}e a{' '}
                <Text
                  style={styles.termsLink}
                  onPress={() => nav.navigate('Legal', { doc: 'privacy' })}
                  suppressHighlighting
                >Política de Privacidade</Text>
              </Text>
            </TouchableOpacity>
          </View>

            {/* Generic error */}
            {error && !error.toLowerCase().includes('email') && (
              <Text style={styles.genericError}>{error}</Text>
            )}
          </View>
        </ScrollView>

        {/* Footer */}
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.cta, (!canSubmit || loading) && styles.ctaDisabled]}
            onPress={handleRegister}
            disabled={!canSubmit || loading}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>{loading ? 'Criando...' : 'Criar conta'}</Text>
          </TouchableOpacity>
          <Text style={styles.loginHint}>
            Já tem conta?{' '}
            <Text style={styles.loginLink} onPress={() => nav.navigate('Login')}>Entrar</Text>
          </Text>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[5], paddingBottom: space[4] },

  back: { paddingVertical: space[3] },

  formWrapper: { flex: 1, justifyContent: 'center', gap: space[5] },
  headingBlock: { gap: 6 },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.025 * font.size.h1, marginTop: space[2] },
  titleAccent: { color: color.primary },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft },

  form: { gap: 18 },
  field: { gap: 8 },
  label: { fontSize: font.size.eyebrow, fontWeight: font.weight.semibold, color: color.institutional2, letterSpacing: 0.1 * font.size.eyebrow },

  input: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
    paddingVertical: 15,
    fontSize: 15,
    color: color.text,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
  },
  inputError: { borderColor: color.danger },
  inputFlex: {
    flex: 1,
    backgroundColor: 'transparent',
    borderWidth: 0,
    paddingHorizontal: 0,
  },
  errorRow: { flexDirection: 'row', alignItems: 'center', marginTop: 2 },
  errorText: { fontSize: font.size.caption, color: color.danger, fontWeight: font.weight.semibold },
  errorLink: { fontSize: font.size.caption, color: color.primary, fontWeight: font.weight.semibold },
  genericError: { fontSize: font.size.caption, color: color.danger, textAlign: 'center', marginTop: space[3] },

  strengthRow: { flexDirection: 'row', alignItems: 'center', gap: space[3], marginTop: 2 },
  strengthBars: { flex: 1, flexDirection: 'row', gap: 4 },
  strengthBar: { flex: 1, height: 5, borderRadius: 100 },
  strengthLabel: { fontSize: 12, fontWeight: font.weight.bold },
  fieldHint: { fontSize: font.size.caption, color: color.danger, marginTop: 2 },

  termsRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, marginTop: 4 },
  checkbox: {
    width: 20, height: 20, borderRadius: 5,
    borderWidth: 1.5, borderColor: color.lineSoft,
    backgroundColor: color.surface,
    alignItems: 'center', justifyContent: 'center',
    marginTop: 1,
  },
  checkboxChecked: { backgroundColor: color.primary, borderColor: color.primary },
  termsText: { flex: 1, fontSize: font.size.caption + 0.5, color: color.textSoft, lineHeight: 18 },
  termsLink: { color: color.primary, fontWeight: font.weight.semibold },

  footer: {
    paddingHorizontal: space[5],
    paddingTop: space[3],
    paddingBottom: space[5],
    backgroundColor: color.surface,
    borderTopWidth: 1,
    borderTopColor: color.lineSoft,
    gap: space[3],
  },
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
  ctaDisabled: { opacity: 0.45, elevation: 0, shadowOpacity: 0 },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
  loginHint: { fontSize: font.size.caption + 0.5, color: color.textSoft, textAlign: 'center' },
  loginLink: { color: color.primary, fontWeight: font.weight.bold },
});
