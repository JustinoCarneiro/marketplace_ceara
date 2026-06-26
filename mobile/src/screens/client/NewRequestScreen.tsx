import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ClientStackParams, 'NewRequest'>;

const CATEGORIES: { label: string; icon: React.ComponentProps<typeof Feather>['name']; color: string; bg: string; border: string }[] = [
  { label: 'Elétrica',   icon: 'zap',       color: '#B5810A', bg: color.sunTint,     border: color.warmSun },
  { label: 'Hidráulica', icon: 'droplet',   color: color.institutional2, bg: color.skyTint, border: color.institutional2 },
  { label: 'Limpeza',    icon: 'edit-2',    color: '#15756E', bg: color.successTint, border: color.success },
  { label: 'Pintura',    icon: 'edit-3',    color: color.terraInk, bg: color.terraTint, border: color.warmTerra },
  { label: 'Reforma',    icon: 'tool',      color: '#244C86', bg: '#E8EEFA', border: '#244C86' },
  { label: 'Jardinagem', icon: 'sun',       color: '#3C7A4E', bg: '#E2F0E6', border: '#3C7A4E' },
  { label: 'Geral',      icon: 'grid',      color: color.textSoft, bg: color.surface, border: color.lineSoft },
];

export default function NewRequestScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [categoria, setCategoria] = useState(route.params?.categoria ?? '');
  const [descricao, setDescricao] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function createRequest() {
    if (!categoria) { setError('Selecione uma categoria.'); return; }
    if (!descricao.trim()) { setError('Descreva o problema.'); return; }
    setError('');
    setLoading(true);
    try {
      const idempotencyKey = `req-${Date.now()}`;
      const res = await fetch(`${API_BASE}/service-requests`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({ categoria, descricao, lat: -3.7319, lng: -38.5267 }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message ?? 'Erro ao criar pedido');
      nav.navigate('AiAssistant', { requestId: data.id });
    } catch (e: any) {
      setError(e.message ?? 'Erro ao criar pedido.');
    } finally {
      setLoading(false);
    }
  }

  const selectedCat = CATEGORIES.find(c => c.label === categoria);

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Header */}
          <View style={styles.header}>
            <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12}>
              <Feather name="chevron-left" size={22} color={color.text} />
            </TouchableOpacity>
            <Text style={styles.headerTitle}>Novo pedido</Text>
          </View>

          <View style={styles.form}>
            {/* Categoria */}
            <View style={styles.field}>
              <Text style={styles.label}>CATEGORIA</Text>
              {selectedCat ? (
                <TouchableOpacity
                  style={[styles.catSelected, { backgroundColor: selectedCat.bg, borderColor: selectedCat.border }]}
                  onPress={() => setCategoria('')}
                  activeOpacity={0.8}
                >
                  <Feather name={selectedCat.icon} size={16} color={selectedCat.color} />
                  <Text style={[styles.catSelectedText, { color: color.text }]}>{selectedCat.label}</Text>
                </TouchableOpacity>
              ) : (
                <View style={styles.catGrid}>
                  {CATEGORIES.map(c => (
                    <TouchableOpacity
                      key={c.label}
                      style={[styles.catChip, { backgroundColor: c.bg, borderColor: c.border }]}
                      onPress={() => setCategoria(c.label)}
                      activeOpacity={0.8}
                    >
                      <Feather name={c.icon} size={14} color={c.color} />
                      <Text style={[styles.catChipText, { color: c.color }]}>{c.label}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              )}
            </View>

            {/* Descrição */}
            <View style={styles.field}>
              <Text style={styles.label}>DESCRIÇÃO</Text>
              <View style={styles.textAreaWrap}>
                <TextInput
                  style={styles.textArea}
                  placeholder="A tomada da cozinha solta faísca quando ligo a air fryer. Preciso trocar com segurança."
                  placeholderTextColor={color.textFaint}
                  value={descricao}
                  onChangeText={setDescricao}
                  multiline
                  numberOfLines={4}
                  textAlignVertical="top"
                />
              </View>
            </View>

            {/* Anexos */}
            <View style={styles.field}>
              <Text style={styles.label}>ANEXOS</Text>
              <View style={styles.anexosRow}>
                <TouchableOpacity style={styles.anexoBtn}>
                  <Feather name="camera" size={22} color={color.primary} />
                  <Text style={styles.anexoBtnText}>Foto</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.anexoBtn}>
                  <Feather name="mic" size={22} color={color.primary} />
                  <Text style={styles.anexoBtnText}>Áudio</Text>
                </TouchableOpacity>
              </View>
            </View>

            {/* Localização */}
            <View style={styles.field}>
              <Text style={styles.label}>LOCALIZAÇÃO</Text>
              <View style={styles.locRow}>
                <Feather name="map-pin" size={18} color={color.primary} />
                <Text style={styles.locText}>Aldeota, Fortaleza-CE</Text>
              </View>
            </View>

            {/* Permissões */}
            <View style={styles.permNotice}>
              <Feather name="info" size={16} color={color.institutional2} style={{ flexShrink: 0, marginTop: 1 }} />
              <Text style={styles.permText}>
                Vamos pedir acesso à <Text style={styles.permBold}>câmera</Text> e ao{' '}
                <Text style={styles.permBold}>microfone</Text> só quando você anexar.
              </Text>
            </View>
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}
        </ScrollView>

        {/* Footer CTA */}
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.cta, loading && { opacity: 0.7 }]}
            onPress={createRequest}
            disabled={loading}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>{loading ? 'Criando...' : 'Continuar'}</Text>
            {!loading && <Feather name="arrow-right" size={18} color={color.textOnAccent} />}
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingBottom: space[3] },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    paddingHorizontal: space[5],
    paddingVertical: space[3],
  },
  headerTitle: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.02 * font.size.h2 },

  form: { paddingHorizontal: space[5], gap: 18 },
  field: { gap: 8 },
  label: { fontSize: font.size.eyebrow, fontWeight: font.weight.semibold, color: color.institutional2, letterSpacing: 0.1 * font.size.eyebrow },

  catSelected: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 9,
  },
  catSelectedText: { fontSize: 14, fontWeight: font.weight.bold },
  catGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  catChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingHorizontal: 12,
    paddingVertical: 7,
  },
  catChipText: { fontSize: 13, fontWeight: font.weight.semibold },

  textAreaWrap: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    padding: space[3] + 2,
    minHeight: 96,
  },
  textArea: { fontSize: font.size.bodySm, color: color.text, lineHeight: font.size.bodySm * 1.55 },

  anexosRow: { flexDirection: 'row', gap: space[3] },
  anexoBtn: {
    flex: 1,
    height: 80,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    backgroundColor: color.surface,
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: '#B7DCE3',
    borderStyle: 'dashed',
  },
  anexoBtnText: { fontSize: 12, fontWeight: font.weight.semibold, color: color.textSoft },

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

  permNotice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: 12,
  },
  permText: { flex: 1, fontSize: font.size.caption, color: color.institutional2, lineHeight: font.size.caption * 1.5 },
  permBold: { fontWeight: font.weight.bold },

  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center', paddingHorizontal: space[5] },

  footer: {
    paddingHorizontal: space[5],
    paddingTop: space[3],
    paddingBottom: space[5],
    backgroundColor: color.surface,
    borderTopWidth: 1,
    borderTopColor: color.lineSoft,
  },
  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[3],
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.55,
    shadowRadius: 24,
    elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
});
