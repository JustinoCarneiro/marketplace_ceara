import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  KeyboardAvoidingView, Platform, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { AuthNavProp } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import Button from '../../components/Button';
import Input from '../../components/Input';
import ScreenHeader from '../../components/ScreenHeader';

const CATEGORIES = [
  { slug: 'eletrica',    label: '⚡ Elétrica',       color: color.catEletrica },
  { slug: 'hidraulica',  label: '🔧 Hidráulica',     color: color.catHidraulica },
  { slug: 'limpeza',     label: '🧹 Limpeza',        color: color.catLimpeza },
  { slug: 'pintura',     label: '🎨 Pintura',        color: color.catPintura },
  { slug: 'reforma',     label: '🏗️ Reforma',        color: color.catReforma },
  { slug: 'jardinagem',  label: '🌱 Jardinagem',     color: color.catJardinagem },
  { slug: 'geral',       label: '🔩 Serviços Gerais', color: color.catGeral },
];

export default function RegisterProviderScreen() {
  const nav = useNavigation<AuthNavProp>();
  const login = useAuthStore(s => s.login);
  const [nome, setNome] = useState('');
  const [email, setEmail] = useState('');
  const [cpf, setCpf] = useState('');
  const [senha, setSenha] = useState('');
  const [categoria, setCategoria] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function handleRegister() {
    if (!categoria) { setError('Selecione uma categoria de serviço.'); return; }
    setError('');
    setLoading(true);
    try {
      const res = await fetch('${API_BASE}/auth/register/provider', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nome, email, cpf, senha, categoria }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao cadastrar');
      const loginRes = await fetch('${API_BASE}/auth/login', {
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
      nav.navigate('VerificationPending', { status: 'EM_VERIFICACAO' });
    } catch (e: any) {
      setError(e.message ?? 'Erro ao cadastrar. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScreenHeader title="Criar conta — Prestador" onBack={() => nav.goBack()} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.form}>
            <Input label="Nome completo" placeholder="João Silva" value={nome} onChangeText={setNome} autoComplete="name" />
            <Input label="E-mail" placeholder="seu@email.com" value={email} onChangeText={setEmail} keyboardType="email-address" />
            <Input
              label="CPF"
              placeholder="000.000.000-00"
              value={cpf}
              onChangeText={t => setCpf(t.replace(/\D/g, '').replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4'))}
              keyboardType="numeric"
              maxLength={14}
              hint="Usado para verificação de identidade (LGPD)"
            />
            <Input label="Senha" placeholder="mínimo 8 caracteres" value={senha} onChangeText={setSenha} secure />

            <View style={styles.catSection}>
              <Text style={styles.catLabel}>ESPECIALIDADE</Text>
              <View style={styles.catGrid}>
                {CATEGORIES.map(cat => (
                  <TouchableOpacity
                    key={cat.slug}
                    onPress={() => setCategoria(cat.slug)}
                    style={[styles.catChip, categoria === cat.slug && { borderColor: cat.color, backgroundColor: cat.color + '18' }]}
                    activeOpacity={0.7}
                  >
                    <Text style={[styles.catText, categoria === cat.slug && { color: cat.color, fontWeight: font.weight.bold }]}>
                      {cat.label}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>
          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button label="Criar conta" onPress={handleRegister} loading={loading} />
          <Text style={styles.note}>
            Após o cadastro, sua identidade será verificada em até 24h antes de você aparecer nas buscas.
          </Text>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  form: { gap: space[4] },
  catSection: { gap: space[2] },
  catLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  catGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  catChip: {
    paddingHorizontal: space[3],
    paddingVertical: space[2],
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.surface,
  },
  catText: { fontSize: font.size.bodySm, color: color.textSoft },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
  note: {
    fontSize: font.size.caption,
    color: color.textFaint,
    textAlign: 'center',
    lineHeight: font.size.caption * 1.5,
  },
});
