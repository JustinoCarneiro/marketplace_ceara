import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TextInput,
  TouchableOpacity, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

export default function LoginScreen() {
  const nav = useNavigation<AuthNavProp>();
  const login = useAuthStore(s => s.login);
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleLogin() {
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, senha }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Credenciais inválidas');
      login({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        role: data.role,
        userId: data.userId,
        nome: data.nome,
        email: data.email,
      });
    } catch (e: any) {
      setError(e.message ?? 'Credenciais inválidas. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">

          {/* Back button */}
          <TouchableOpacity style={styles.backBtn} onPress={() => nav.goBack()} hitSlop={12}>
            <Feather name="arrow-left" size={22} color={color.text} />
          </TouchableOpacity>

          {/* Wave logo */}
          <View style={styles.logo}>
            <Text style={styles.logoWave}>∿</Text>
          </View>

          {/* Hero text */}
          <View style={styles.headerText}>
            <Text style={styles.title}>Bom te ver{'\n'}de novo</Text>
            <Text style={styles.subtitle}>Entre com seu e-mail para continuar.</Text>
          </View>

          {/* Form */}
          <View style={styles.form}>
            <View style={styles.fieldGroup}>
              <Text style={styles.fieldLabel}>E-mail</Text>
              <TextInput
                style={[styles.field, !!error && styles.fieldError]}
                value={email}
                onChangeText={t => { setEmail(t); setError(''); }}
                placeholder="seu@email.com"
                placeholderTextColor={color.textFaint}
                keyboardType="email-address"
                autoCapitalize="none"
                autoComplete="email"
              />
            </View>

            <View style={styles.fieldGroup}>
              <View style={styles.labelRow}>
                <Text style={styles.fieldLabel}>Senha</Text>
                <TouchableOpacity hitSlop={8}>
                  <Text style={styles.forgotLink}>Esqueci a senha</Text>
                </TouchableOpacity>
              </View>
              <View style={styles.passWrap}>
                <TextInput
                  style={[styles.field, styles.passInput, !!error && styles.fieldError]}
                  value={senha}
                  onChangeText={t => { setSenha(t); setError(''); }}
                  placeholder="••••••••"
                  placeholderTextColor={color.textFaint}
                  secureTextEntry={!showPass}
                  autoComplete="current-password"
                />
                <TouchableOpacity
                  style={styles.eyeBtn}
                  onPress={() => setShowPass(v => !v)}
                  hitSlop={8}
                >
                  <Feather name={showPass ? 'eye-off' : 'eye'} size={18} color={color.textFaint} />
                </TouchableOpacity>
              </View>
              {error ? (
                <View style={styles.errorRow}>
                  <Feather name="alert-circle" size={13} color={color.danger} />
                  <Text style={styles.errorText}>{error}</Text>
                </View>
              ) : null}
            </View>
          </View>

          {/* CTA */}
          <TouchableOpacity
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={handleLogin}
            disabled={loading}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>{loading ? 'Entrando…' : 'Entrar'}</Text>
          </TouchableOpacity>

          {/* Register link */}
          <View style={styles.registerRow}>
            <Text style={styles.registerHint}>Ainda não tem conta? </Text>
            <TouchableOpacity onPress={() => nav.navigate('Splash')}>
              <Text style={styles.registerLink}>Cadastrar</Text>
            </TouchableOpacity>
          </View>

        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingHorizontal: space[5], paddingBottom: space[7] },
  backBtn: { marginTop: 8, marginBottom: space[5] },
  logo: {
    width: 60,
    height: 60,
    borderRadius: 20,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space[5],
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 14 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 6,
  },
  logoWave: { fontSize: 28, color: color.textOnAccent },
  headerText: { gap: 8, marginBottom: space[6] },
  title: {
    fontSize: font.size.display,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: font.tracking.display * font.size.display,
    lineHeight: font.size.display * font.lineHeight.tight,
  },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft },
  form: { gap: space[4], marginBottom: space[5] },
  fieldGroup: { gap: 7 },
  labelRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  fieldLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.institutional2,
    letterSpacing: 0.1 * font.size.eyebrow,
    textTransform: 'uppercase',
  },
  forgotLink: {
    fontSize: font.size.caption,
    fontWeight: font.weight.semibold,
    color: color.primary,
  },
  field: {
    height: 52,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: 16,
    fontSize: font.size.body,
    color: color.text,
  },
  fieldError: { borderColor: color.danger, borderWidth: 1.5 },
  passWrap: { position: 'relative' },
  passInput: { paddingRight: 48 },
  eyeBtn: { position: 'absolute', right: 14, top: 0, bottom: 0, justifyContent: 'center' },
  errorRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 4 },
  errorText: { fontSize: font.size.caption, color: color.danger, flex: 1 },
  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: space[4],
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
  registerRow: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  registerHint: { fontSize: font.size.bodySm, color: color.textSoft },
  registerLink: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.primary },
});
