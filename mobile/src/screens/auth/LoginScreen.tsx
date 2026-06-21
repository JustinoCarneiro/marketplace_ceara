import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space } from '../../theme';
import { useAuthStore } from '../../store/auth';
import Button from '../../components/Button';
import Input from '../../components/Input';
import ScreenHeader from '../../components/ScreenHeader';

export default function LoginScreen() {
  const nav = useNavigation<AuthNavProp>();
  const login = useAuthStore(s => s.login);
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleLogin() {
    setError('');
    setLoading(true);
    try {
      const res = await fetch('http://10.0.2.2:8080/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, senha }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao entrar');
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
        <ScreenHeader title="Entrar" onBack={() => nav.goBack()} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.form}>
            <Input
              label="E-mail"
              placeholder="seu@email.com"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoComplete="email"
            />
            <Input
              label="Senha"
              placeholder="••••••••"
              value={senha}
              onChangeText={setSenha}
              secure
            />
            {error ? <Text style={styles.error}>{error}</Text> : null}
          </View>

          <View style={styles.actions}>
            <Button label="Entrar" onPress={handleLogin} loading={loading} />
            <View style={styles.row}>
              <Text style={styles.hint}>Ainda não tem conta? </Text>
              <TouchableOpacity onPress={() => nav.navigate('Splash')}>
                <Text style={styles.link}>Cadastrar</Text>
              </TouchableOpacity>
            </View>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[5], paddingBottom: space[7], gap: space[6] },
  form: { gap: space[4] },
  actions: { gap: space[4] },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
  row: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center' },
  hint: { fontSize: font.size.bodySm, color: color.textSoft },
  link: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.primary },
});
