import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  TouchableOpacity, TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'NewRequest'>;

const CATEGORIES = ['Elétrica', 'Hidráulica', 'Limpeza', 'Pintura', 'Reforma', 'Jardinagem', 'Serviços Gerais'];

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
      const res = await fetch('http://10.0.2.2:8080/api/v1/service-requests', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          categoria,
          descricao,
          lat: -3.7319,
          lng: -38.5267,
        }),
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

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScreenHeader title="Novo pedido" onBack={() => nav.goBack()} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">

          {/* Categoria */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>CATEGORIA DO SERVIÇO</Text>
            <View style={styles.chipRow}>
              {CATEGORIES.map(c => (
                <TouchableOpacity
                  key={c}
                  style={[styles.chip, categoria === c && styles.chipActive]}
                  onPress={() => setCategoria(c)}
                >
                  <Text style={[styles.chipText, categoria === c && styles.chipTextActive]}>{c}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>

          {/* Descrição */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>DESCREVA O PROBLEMA</Text>
            <View style={styles.textAreaWrap}>
              <TextInput
                style={styles.textArea}
                placeholder="Ex: Torneira da cozinha vazando há 2 dias, necessário substituição do reparo…"
                placeholderTextColor={color.textFaint}
                value={descricao}
                onChangeText={setDescricao}
                multiline
                numberOfLines={5}
                textAlignVertical="top"
              />
            </View>
            <Text style={styles.charCount}>{descricao.length}/500</Text>
          </View>

          {/* Mídia (placeholder) */}
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>FOTOS / ÁUDIO (OPCIONAL)</Text>
            <View style={styles.mediaRow}>
              <TouchableOpacity style={styles.mediaBtn}>
                <Text style={{ fontSize: 24 }}>📸</Text>
                <Text style={styles.mediaBtnText}>Foto</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.mediaBtn}>
                <Text style={{ fontSize: 24 }}>🎤</Text>
                <Text style={styles.mediaBtnText}>Áudio</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.mediaBtn}>
                <Text style={{ fontSize: 24 }}>📹</Text>
                <Text style={styles.mediaBtnText}>Vídeo</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* IA info */}
          <View style={styles.aiInfo}>
            <Text style={{ fontSize: 18 }}>✨</Text>
            <Text style={styles.aiText}>
              Nossa IA vai sugerir a descrição e faixa de orçamento após você criar o pedido.
            </Text>
          </View>

          {error ? <Text style={styles.error}>{error}</Text> : null}
          <Button label="Criar pedido" onPress={createRequest} loading={loading} />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  section: { gap: space[3] },
  sectionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  chipRow: { flexDirection: 'row', flexWrap: 'wrap', gap: space[2] },
  chip: {
    paddingHorizontal: space[3],
    paddingVertical: space[2],
    borderRadius: radius.pill,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.surface,
  },
  chipActive: { borderColor: color.primary, backgroundColor: '#DFF5F3' },
  chipText: { fontSize: font.size.bodySm, color: color.textSoft },
  chipTextActive: { color: color.primary, fontWeight: font.weight.bold },
  textAreaWrap: {
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.surface,
    padding: space[4],
    minHeight: 130,
  },
  textArea: { fontSize: font.size.body, color: color.text, flex: 1, fontFamily: font.family },
  charCount: { fontSize: font.size.caption, color: color.textFaint, textAlign: 'right' },
  mediaRow: { flexDirection: 'row', gap: space[3] },
  mediaBtn: {
    flex: 1,
    alignItems: 'center',
    gap: space[2],
    backgroundColor: color.surface,
    borderRadius: radius.field,
    padding: space[4],
    borderWidth: 1.5,
    borderColor: color.line,
    borderStyle: 'dashed',
  },
  mediaBtnText: { fontSize: font.size.caption, color: color.textSoft, fontWeight: font.weight.medium },
  aiInfo: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
  },
  aiText: { flex: 1, fontSize: font.size.caption, color: color.textSoft, lineHeight: font.size.caption * 1.55 },
  error: { fontSize: font.size.caption, color: color.danger, textAlign: 'center' },
});
