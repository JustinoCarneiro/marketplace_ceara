import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import Button from '../../components/Button';

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

  useEffect(() => {
    // Busca sugestão da IA — fallback manual se falhar
    (async () => {
      try {
        const res = await fetch(
          `${API_BASE}/service-requests/${route.params.requestId}/ai-suggestion`,
          { headers: { Authorization: `Bearer ${token}` } }
        );
        if (res.ok) {
          const data = await res.json();
          setSuggestion(data);
        } else {
          setSuggestion(null); // fallback manual
        }
      } catch {
        setSuggestion(null); // fallback manual
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  function proceed() {
    nav.navigate('RequestCreated', { requestId: route.params.requestId });
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="Assistente IA" onBack={() => nav.goBack()} />
      <ScrollView contentContainerStyle={styles.content}>

        {loading ? (
          <View style={styles.loadingCard}>
            <ActivityIndicator color={color.primary} size="large" />
            <Text style={styles.loadingTitle}>Analisando seu pedido…</Text>
            <Text style={styles.loadingBody}>Nossa IA está processando as informações para sugerir o melhor orçamento.</Text>
          </View>
        ) : suggestion?.descricaoSugerida ? (
          <>
            <View style={styles.aiCard}>
              <View style={styles.aiHeader}>
                <Text style={{ fontSize: 24 }}>✨</Text>
                <Text style={styles.aiTitle}>Sugestão da IA</Text>
              </View>

              <View style={styles.suggestionBlock}>
                <Text style={styles.suggestionLabel}>DESCRIÇÃO SUGERIDA</Text>
                <Text style={styles.suggestionText}>{suggestion.descricaoSugerida}</Text>
              </View>

              {(suggestion.faixaMin && suggestion.faixaMax) ? (
                <View style={styles.priceBlock}>
                  <Text style={styles.suggestionLabel}>FAIXA DE ORÇAMENTO ESTIMADA</Text>
                  <Text style={styles.priceRange}>
                    R$ {suggestion.faixaMin.toFixed(0)} — R$ {suggestion.faixaMax.toFixed(0)}
                  </Text>
                  <Text style={styles.priceNote}>Estimativa baseada em serviços similares na sua região.</Text>
                </View>
              ) : null}
            </View>

            <View style={styles.fallbackNote}>
              <Text style={{ fontSize: 16 }}>ℹ️</Text>
              <Text style={styles.fallbackText}>
                A sugestão é apenas uma referência. O prestador enviará a proposta real com o valor definitivo.
              </Text>
            </View>
          </>
        ) : (
          // Fallback manual — IA indisponível
          <View style={styles.fallbackCard}>
            <Text style={{ fontSize: 32 }}>📋</Text>
            <Text style={styles.fallbackTitle}>Pedido registrado!</Text>
            <Text style={styles.fallbackBody}>
              A sugestão automática não está disponível agora, mas seu pedido foi criado com sucesso.
              Os prestadores da sua região já podem visualizá-lo e enviar propostas.
            </Text>
          </View>
        )}

        <Button label="Ver meu pedido" onPress={proceed} />
        <Button label="Voltar ao início" variant="ghost" onPress={() => nav.popToTop()} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[4] },

  loadingCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[6],
    alignItems: 'center',
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  loadingTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  loadingBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center', lineHeight: font.size.bodySm * 1.6 },

  aiCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[4],
    borderWidth: 1,
    borderColor: color.primary + '40',
  },
  aiHeader: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  aiTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },

  suggestionBlock: { gap: space[2] },
  suggestionLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  suggestionText: { fontSize: font.size.body, color: color.text, lineHeight: font.size.body * font.lineHeight.body },

  priceBlock: { gap: space[2] },
  priceRange: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.primary },
  priceNote: { fontSize: font.size.caption, color: color.textFaint },

  fallbackNote: {
    flexDirection: 'row',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
  },
  fallbackText: { flex: 1, fontSize: font.size.caption, color: color.textSoft, lineHeight: font.size.caption * 1.55 },

  fallbackCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[6],
    alignItems: 'center',
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  fallbackTitle: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  fallbackBody: { fontSize: font.size.body, color: color.textSoft, textAlign: 'center', lineHeight: font.size.body * font.lineHeight.body },
});
