import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, ActivityIndicator,
  TouchableOpacity, TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ClientStackParams, 'AiAssistant'>;

interface AiSuggestion {
  descricaoSugerida?: string;
  faixaMin?: number;
  faixaMax?: number;
}

export default function AiAssistantScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [suggestion, setSuggestion] = useState<AiSuggestion | null>(null);
  const [loading, setLoading] = useState(true);
  const [descricao, setDescricao] = useState('');
  const [publishing, setPublishing] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(
          `${API_BASE}/service-requests/${route.params.requestId}/ai-suggestion`,
          { headers: { Authorization: `Bearer ${token}` } }
        );
        if (res.ok) {
          const data: AiSuggestion = await res.json();
          setSuggestion(data);
          setDescricao(data.descricaoSugerida ?? '');
        } else {
          setSuggestion(null);
        }
      } catch {
        setSuggestion(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  async function publish() {
    setPublishing(true);
    try {
      await fetch(`${API_BASE}/service-requests/${route.params.requestId}/publish`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ descricao }),
      });
    } catch { /* best effort */ } finally {
      setPublishing(false);
      nav.navigate('RequestCreated', { requestId: route.params.requestId });
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          {/* Header */}
          <View style={styles.header}>
            <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12}>
              <Feather name="chevron-left" size={22} color={color.text} />
            </TouchableOpacity>
            <Text style={styles.headerTitle}>Revisar com a IA</Text>
          </View>

          {loading ? (
            <View style={styles.loadingBlock}>
              <ActivityIndicator color={color.primary} size="large" />
              <Text style={styles.loadingTitle}>Analisando seu pedido…</Text>
              <Text style={styles.loadingBody}>Nossa IA está processando as informações para sugerir o melhor orçamento.</Text>
            </View>
          ) : (
            <View style={styles.form}>
              {/* AI badge */}
              <View style={styles.aiBadge}>
                <Feather name="sun" size={16} color="#fff" />
                <Text style={styles.aiBadgeText}>Sugestão da IA — você confirma</Text>
              </View>

              {/* Descrição sugerida */}
              <View style={styles.field}>
                <Text style={styles.label}>
                  Descrição sugerida{' '}
                  <Text style={styles.labelSoft}>· editável</Text>
                </Text>
                <View style={[styles.textAreaWrap, suggestion?.descricaoSugerida && styles.textAreaAi]}>
                  <TextInput
                    style={styles.textArea}
                    value={descricao}
                    onChangeText={setDescricao}
                    multiline
                    numberOfLines={4}
                    textAlignVertical="top"
                    placeholder="Descreva o problema…"
                    placeholderTextColor={color.textFaint}
                  />
                </View>
              </View>

              {/* Orçamento estimado */}
              {(suggestion?.faixaMin != null && suggestion?.faixaMax != null) && (
                <View style={styles.field}>
                  <Text style={styles.label}>
                    Orçamento estimado{' '}
                    <Text style={styles.labelSoft}>· editável</Text>
                  </Text>
                  <View style={styles.rangeCard}>
                    <View style={styles.rangeRow}>
                      <Text style={styles.rangeCaption}>Mínimo</Text>
                      <Text style={styles.rangeCaption}>Máximo</Text>
                    </View>
                    <View style={styles.rangeRow}>
                      <Text style={styles.rangeValue}>R$ {suggestion.faixaMin?.toFixed(0)}</Text>
                      <Text style={styles.rangeValue}>R$ {suggestion.faixaMax?.toFixed(0)}</Text>
                    </View>
                    {/* Visual slider (non-interactive decoration) */}
                    <View style={styles.sliderTrack}>
                      <View style={styles.sliderFill} />
                      <View style={[styles.sliderThumb, { left: '14%' }]} />
                      <View style={[styles.sliderThumb, { left: '88%' }]} />
                    </View>
                  </View>
                </View>
              )}

              {/* Fallback / AI warning */}
              <View style={styles.fallbackNotice}>
                <Feather name="alert-triangle" size={17} color={color.terraInk} style={{ flexShrink: 0, marginTop: 1 }} />
                <Text style={styles.fallbackText}>
                  Se a IA estiver indisponível, é só{' '}
                  <Text style={styles.fallbackBold}>continuar preenchendo manualmente</Text>
                  {' '}— o pedido não trava.
                </Text>
              </View>
            </View>
          )}
        </ScrollView>

        {/* Footer CTA */}
        <View style={styles.footer}>
          <TouchableOpacity
            style={[styles.cta, publishing && { opacity: 0.7 }]}
            onPress={publish}
            disabled={publishing || loading}
            activeOpacity={0.85}
          >
            <Text style={styles.ctaText}>{publishing ? 'Publicando...' : 'Confirmar e publicar pedido'}</Text>
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
    paddingTop: 8,
    paddingBottom: 18,
  },
  headerTitle: { fontSize: font.size.h2, fontWeight: font.weight.black, color: color.text, letterSpacing: -0.02 * font.size.h2 },

  loadingBlock: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[4],
    padding: space[6],
  },
  loadingTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  loadingBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center', lineHeight: font.size.bodySm * 1.6 },

  form: { paddingHorizontal: space[5], gap: 16 },

  aiBadge: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: color.institutional,
    borderRadius: radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  aiBadgeText: { fontSize: 12.5, fontWeight: font.weight.bold, color: '#fff', letterSpacing: 0.04 * 12.5 },

  field: { gap: 8 },
  label: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.institutional2,
    letterSpacing: 0.1 * font.size.eyebrow,
    textTransform: 'uppercase',
  },
  labelSoft: { textTransform: 'none', letterSpacing: 0, fontWeight: '400', color: color.textFaint },

  textAreaWrap: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    padding: 14,
    minHeight: 88,
  },
  textAreaAi: { borderWidth: 1.5, borderColor: '#B7DCE3' },
  textArea: { fontSize: 14.5, color: color.text, lineHeight: 14.5 * 1.6 },

  rangeCard: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    padding: 18,
    gap: 14,
  },
  rangeRow: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between' },
  rangeCaption: { fontSize: 13, color: color.textSoft },
  rangeValue: { fontSize: 24, fontWeight: font.weight.black, color: color.text },

  sliderTrack: {
    height: 8,
    borderRadius: 100,
    backgroundColor: color.lineSoft,
    position: 'relative',
  },
  sliderFill: {
    position: 'absolute',
    left: '14%',
    right: '12%',
    top: 0,
    bottom: 0,
    borderRadius: 100,
    backgroundColor: color.primary,
  },
  sliderThumb: {
    position: 'absolute',
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#fff',
    borderWidth: 3,
    borderColor: color.primary,
    top: -5,
    marginLeft: -9,
  },

  fallbackNotice: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 9,
    backgroundColor: color.terraTint,
    borderWidth: 1,
    borderColor: color.terraTintLine,
    borderRadius: radius.field,
    padding: 12,
  },
  fallbackText: { flex: 1, fontSize: 12.5, lineHeight: 12.5 * 1.5, color: color.terraInkDeep },
  fallbackBold: { fontWeight: font.weight.bold },

  footer: {
    paddingHorizontal: space[5],
    paddingTop: 14,
    paddingBottom: 20,
    backgroundColor: color.surface,
    borderTopWidth: 1,
    borderTopColor: color.lineSoft,
  },
  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
});
