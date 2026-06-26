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

const CATEGORIES = ['Elétrica', 'Hidráulica', 'Limpeza', 'Pintura', 'Reforma', 'Jardinagem', 'Geral'];

export default function RegisterProviderScreen() {
  const nav = useNavigation<AuthNavProp>();
  const login = useAuthStore(s => s.login);
  const [nome, setNome] = useState('');
  const [cpf, setCpf] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [bio, setBio] = useState('');
  const [selectedCats, setSelectedCats] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function toggleCat(cat: string) {
    setSelectedCats(prev =>
      prev.includes(cat) ? prev.filter(c => c !== cat) : [...prev, cat],
    );
  }

  async function handleRegister() {
    setError('');
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/auth/register/provider`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nome, cpf, email, senha, bio, categorias: selectedCats }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao cadastrar');
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
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Back */}
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12} style={styles.back}>
            <Feather name="chevron-left" size={22} color={color.text} />
          </TouchableOpacity>

          <Text style={styles.title}>Cadastro de prestador</Text>

          <View style={styles.form}>
            {/* Nome */}
            <View style={styles.field}>
              <Text style={styles.label}>NOME COMPLETO</Text>
              <TextInput
                style={styles.input}
                placeholder="José Wagner Ferreira"
                placeholderTextColor={color.textFaint}
                value={nome}
                onChangeText={setNome}
              />
            </View>

            {/* CPF */}
            <View style={styles.field}>
              <Text style={styles.label}>CPF</Text>
              <TextInput
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

            {/* E-mail */}
            <View style={styles.field}>
              <Text style={styles.label}>E-MAIL</Text>
              <TextInput
                style={styles.input}
                placeholder="jose@email.com"
                placeholderTextColor={color.textFaint}
                value={email}
                onChangeText={setEmail}
                keyboardType="email-address"
                autoCapitalize="none"
              />
            </View>

            {/* Senha */}
            <View style={styles.field}>
              <Text style={styles.label}>SENHA</Text>
              <TextInput
                style={styles.input}
                placeholder="mínimo 8 caracteres"
                placeholderTextColor={color.textFaint}
                value={senha}
                onChangeText={setSenha}
                secureTextEntry
              />
            </View>

            {/* Categorias */}
            <View style={styles.field}>
              <Text style={styles.label}>CATEGORIA PRINCIPAL</Text>
              <View style={styles.chips}>
                {CATEGORIES.map(cat => {
                  const active = selectedCats.includes(cat);
                  return (
                    <TouchableOpacity
                      key={cat}
                      style={[styles.chip, active && styles.chipActive]}
                      onPress={() => toggleCat(cat)}
                      activeOpacity={0.8}
                    >
                      <Text style={[styles.chipText, active && styles.chipTextActive]}>{cat}</Text>
                    </TouchableOpacity>
                  );
                })}
              </View>
            </View>

            {/* Bio */}
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

            {/* Localização */}
            <View style={styles.field}>
              <Text style={styles.label}>LOCALIZAÇÃO DE ATENDIMENTO</Text>
              <View style={styles.locRow}>
                <Feather name="map-pin" size={17} color={color.primary} />
                <Text style={styles.locText}>Aldeota · raio 10 km</Text>
              </View>
            </View>
          </View>

          {error ? <Text style={styles.errorText}>{error}</Text> : null}
        </ScrollView>

        {/* Footer */}
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={handleRegister}
            disabled={loading}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>{loading ? 'Enviando...' : 'Enviar para verificação'}</Text>
          </TouchableOpacity>
          <Text style={styles.verifyNote}>
            Depois de enviar, seu cadastro fica{' '}
            <Text style={styles.verifyBadge}>EM VERIFICAÇÃO</Text>.
          </Text>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingHorizontal: space[5], paddingBottom: space[4] },

  back: { paddingVertical: space[3] },
  title: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.025 * font.size.h1,
    marginBottom: space[5],
    marginTop: space[2],
  },

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
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    backgroundColor: color.skyTint,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  lgpdText: { flex: 1, fontSize: 12, color: color.institutional2, lineHeight: 12 * 1.4 },

  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: radius.pill,
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  chipActive: { backgroundColor: color.sunTint, borderWidth: 1.5, borderColor: color.warmSun },
  chipText: { fontSize: font.size.caption, fontWeight: font.weight.semibold, color: color.textSoft },
  chipTextActive: { fontWeight: font.weight.bold, color: color.text },

  locRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    padding: space[3] + 2,
  },
  locText: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.text },

  errorText: { fontSize: font.size.caption, color: color.danger, textAlign: 'center', marginTop: space[3] },

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
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
  verifyNote: { fontSize: 12, color: color.textFaint, textAlign: 'center' },
  verifyBadge: { fontWeight: font.weight.bold, color: color.sunInk },
});
