import React, { useState } from 'react';
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
import { codigoCompleto, formatarCodigo, redefinirSenha } from '../../api/passwordReset';

/**
 * Recuperação de senha, passo 2 (US35): código recebido por e-mail + nova senha. A mensagem de
 * erro do servidor para código errado, expirado ou já usado é uma só ("Código inválido ou
 * expirado") — a tela não tenta adivinhar o motivo.
 */
export default function ResetPasswordScreen() {
  const nav = useNavigation<AuthNavProp>();
  const { email } = useRoute<RouteProp<AuthStackParams, 'ResetPassword'>>().params;
  const [codigo, setCodigo] = useState('');
  const [senha, setSenha] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  async function enviar() {
    if (!codigoCompleto(codigo)) {
      setError('Digite o código de 8 caracteres que enviamos por e-mail.');
      return;
    }
    if (senha.length < 8) {
      setError('A senha precisa ter ao menos 8 caracteres.');
      return;
    }
    if (senha !== confirmar) {
      setError('As senhas não conferem.');
      return;
    }
    setError('');
    setLoading(true);
    const resultado = await redefinirSenha(email, codigo, senha);
    setLoading(false);
    if (!resultado.ok) {
      setError(resultado.mensagem);
      return;
    }
    setDone(true);
  }

  if (done) {
    return (
      <SafeAreaView style={styles.safe}>
        <View testID="senha-redefinida" style={styles.doneWrap}>
          <View style={styles.doneIcon}>
            <Feather name="check" size={34} color={color.success} accessibilityElementsHidden />
          </View>
          <Text style={styles.doneTitle}>Senha redefinida!</Text>
          <Text style={styles.doneText}>
            Por segurança, encerramos as sessões abertas da sua conta. Entre com a nova senha.
          </Text>
          <TouchableOpacity
            testID="btn-ir-login"
            style={styles.cta}
            // reset (não navigate): no React Navigation 7 navigate EMPILHA outro Login por cima, e o
            // botão voltar levaria de novo a esta tela, com a senha digitada ainda na memória.
            onPress={() => nav.reset({ index: 0, routes: [{ name: 'Login' }] })}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>Entrar</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
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
            Nova <Text style={styles.titleAccent}>senha</Text>
          </Text>
          <Text style={styles.subtitle}>
            Se {email} estiver cadastrado, enviamos um código de 8 caracteres. Digite-o abaixo e
            escolha a nova senha. O código vale por 30 minutos.
          </Text>

          <View style={styles.form}>
            <View style={styles.field}>
              <Text style={styles.label}>CÓDIGO DO E-MAIL</Text>
              <TextInput
                testID="input-codigo-recuperacao"
                style={[styles.input, styles.codigo]}
                value={codigo}
                onChangeText={t => { setCodigo(formatarCodigo(t)); setError(''); }}
                placeholder="ABCD-2345"
                placeholderTextColor={color.textFaint}
                autoCapitalize="characters"
                autoCorrect={false}
                maxLength={9}
              />
            </View>

            <View style={styles.field}>
              <Text style={styles.label}>NOVA SENHA</Text>
              <View style={styles.passWrap}>
                <TextInput
                  testID="input-nova-senha"
                  style={[styles.input, styles.passInput]}
                  value={senha}
                  onChangeText={t => { setSenha(t); setError(''); }}
                  placeholder="mínimo 8 caracteres"
                  placeholderTextColor={color.textFaint}
                  secureTextEntry={!showPass}
                  autoComplete="new-password"
                  maxLength={72}
                />
                <TouchableOpacity
                  style={styles.eyeBtn}
                  onPress={() => setShowPass(v => !v)}
                  hitSlop={8}
                  accessibilityLabel={showPass ? 'Ocultar senha' : 'Mostrar senha'}
                  accessibilityRole="button"
                >
                  <Feather name={showPass ? 'eye-off' : 'eye'} size={18} color={color.textFaint} accessibilityElementsHidden />
                </TouchableOpacity>
              </View>
            </View>

            <View style={styles.field}>
              <Text style={styles.label}>CONFIRMAR NOVA SENHA</Text>
              <TextInput
                testID="input-confirmar-senha"
                style={styles.input}
                value={confirmar}
                onChangeText={t => { setConfirmar(t); setError(''); }}
                placeholder="repita a senha"
                placeholderTextColor={color.textFaint}
                secureTextEntry={!showPass}
                autoComplete="new-password"
                maxLength={72}
              />
            </View>
          </View>

          {error ? (
            <View style={styles.errorRow}>
              <Feather name="alert-circle" size={14} color={color.danger} accessibilityElementsHidden />
              <Text testID="erro-recuperacao" style={styles.errorText}>{error}</Text>
            </View>
          ) : null}

          <TouchableOpacity
            testID="btn-redefinir-senha"
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={enviar}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? <ActivityIndicator color={color.textOnAccent} /> : <Text style={styles.ctaText}>Redefinir senha</Text>}
          </TouchableOpacity>

          <TouchableOpacity testID="link-pedir-novo-codigo" onPress={() => nav.goBack()} style={styles.novoCodigo} hitSlop={8}>
            <Text style={styles.novoCodigoText}>Não recebeu? Pedir outro código</Text>
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
  codigo: { letterSpacing: 3, fontWeight: font.weight.bold },
  passWrap: { position: 'relative' },
  passInput: { paddingRight: 48 },
  eyeBtn: { position: 'absolute', right: 14, top: 0, bottom: 0, justifyContent: 'center' },

  errorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: space[4] },
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
    alignSelf: 'stretch',
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },

  novoCodigo: { alignItems: 'center', paddingVertical: space[4] },
  novoCodigoText: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.primaryInk },

  doneWrap: { flex: 1, paddingHorizontal: space[5], alignItems: 'center', justifyContent: 'center', gap: space[3] },
  doneIcon: {
    width: 72, height: 72, borderRadius: 36, alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.skyTint,
  },
  doneTitle: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.text },
  doneText: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center', lineHeight: 20, marginBottom: space[3] },
});
