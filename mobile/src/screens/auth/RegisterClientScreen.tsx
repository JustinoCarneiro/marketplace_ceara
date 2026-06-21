import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space } from '../../theme';
import { useAuthStore } from '../../store/auth';
import Button from '../../components/Button';
import Input from '../../components/Input';
import ScreenHeader from '../../components/ScreenHeader';

export default function RegisterClientScreen() {
  const nav = useNavigation<AuthNavProp>();
  const login = useAuthStore(s => s.login);
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [cpf, setCpf] = useState('');
  const [senha, setSenha] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleRegister() {
    setError('');
    setLoading(true);
    try {
      const res = await fetch('http://10.0.2.2:8080/api/v1/auth/register/client', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nome, email, cpf, senha }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao cadastrar');
      // Auto-login após cadastro
      const loginRes = await fetch('http://10.0.2.2:8080/api/v1/auth/login', {
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
        <ScreenHeader title="Criar conta — Cliente" onBack={() => nav.goBack()} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.form}>
            <Input label="Nome completo" placeholder="Maria da Silva" value={nome} onChangeText={setNome} autoComplete="name" />
            <Input label="E-mail" placeholder="seu@email.com" value={email} onChangeText={setEmail} keyboardType="email-address" autoComplete="email" />
            <Input
              label="CPF"
              placeholder="000.000.000-00"
              value={cpf}
              onChangeText={t => setCpf(t.replace(/\D/g, '').replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4'))}
              keyboardType="numeric"
              maxLength={14}
              hint="Seus dados são criptografados (LGPD)"
            />
            <Input label="Senha" placeholder="mínimo 8 caracteres" value={senha} onChangeText={setSenha} secure />
          </View>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button label="Criar conta" onPress={handleRegister} loading={loading} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  form: { gap: space[4] },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
});
