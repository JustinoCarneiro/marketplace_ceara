import { API_BASE } from '../../api/config';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TextInput, TouchableOpacity, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import { useAuthStore } from '../../store/auth';

const C = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#606E71',
  primary: '#10847D',
  institutional2: '#15596E',
  lineSoft: '#E6DDC9',
  bubbleMine: '#10847D',
  bubbleTheirs: '#FCF8EE',
};

interface Message {
  id: string;
  remetenteId: string;
  remetenteNome: string;
  conteudo: string;
  mascarado: boolean;
  createdAt: string;
}

// Chat pré-transação (docs/BOAS_PRATICAS_UX.md §1) — telefone/e-mail digitados são
// mascarados pelo backend antes de persistir (anti-desintermediação, protege a comissão
// que sustenta o MVP). Sem poll de tempo real no resto do app (nem WebSocket, nem SSE);
// aqui um intervalo simples enquanto a tela está em foco é suficiente pro caso de uso.
const POLL_MS = 4000;

export default function ChatScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const token = useAuthStore(s => s.accessToken);
  const myUserId = useAuthStore(s => s.userId);
  const requestId = route.params?.requestId;

  const [messages, setMessages] = useState<Message[]>([]);
  const [loading, setLoading] = useState(true);
  const [texto, setTexto] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const listRef = useRef<FlatList>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/service-requests/${requestId}/messages`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        const data = await res.json();
        setMessages(Array.isArray(data) ? data : []);
      }
    } catch {
      // Poll silencioso — falha de rede aqui não deve travar a tela, só não atualiza agora.
    } finally {
      setLoading(false);
    }
  }, [requestId, token]);

  useFocusEffect(useCallback(() => {
    load();
    const interval = setInterval(() => load(true), POLL_MS);
    return () => clearInterval(interval);
  }, [load]));

  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 100);
    }
  }, [messages.length]);

  async function enviar() {
    const conteudo = texto.trim();
    if (!conteudo) return;
    setSending(true);
    setError('');
    try {
      const res = await fetch(`${API_BASE}/service-requests/${requestId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ conteudo }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        throw new Error(data.message ?? 'Erro ao enviar mensagem.');
      }
      setTexto('');
      await load(true);
    } catch (e: any) {
      setError(e.message ?? 'Erro ao enviar mensagem.');
    } finally {
      setSending(false);
    }
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => nav.goBack()} activeOpacity={0.7} hitSlop={14}
          accessibilityLabel="Voltar" accessibilityRole="button">
          <Feather name="chevron-left" size={22} color={C.text} accessibilityElementsHidden />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Conversa</Text>
      </View>

      <View style={styles.banner}>
        <Feather name="shield" size={15} color={C.institutional2} style={{ flexShrink: 0 }} />
        <Text style={styles.bannerText}>
          Seus dados ficam protegidos enquanto o pagamento está retido.
        </Text>
      </View>

      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={m => m.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => {
            const mine = item.remetenteId === myUserId;
            return (
              <View style={[styles.bubbleRow, mine && styles.bubbleRowMine]}>
                <View style={[styles.bubble, mine ? styles.bubbleMine : styles.bubbleTheirs]}>
                  {!mine && <Text style={styles.bubbleSender}>{item.remetenteNome}</Text>}
                  <Text style={mine ? styles.bubbleTextMine : styles.bubbleTextTheirs}>
                    {item.conteudo}
                  </Text>
                  {item.mascarado && (
                    <View style={styles.maskedRow}>
                      <Feather name="eye-off" size={11} color={mine ? '#CFEAE7' : C.textFaint} />
                      <Text style={[styles.maskedText, { color: mine ? '#CFEAE7' : C.textFaint }]}>
                        contato removido por segurança
                      </Text>
                    </View>
                  )}
                </View>
              </View>
            );
          }}
          ListEmptyComponent={!loading ? (
            <View style={styles.empty}>
              <Feather name="message-circle" size={28} color={C.textFaint} />
              <Text style={styles.emptyText}>Nenhuma mensagem ainda. Diga oi!</Text>
            </View>
          ) : null}
        />

        {error ? (
          <View style={styles.errorRow}>
            <Feather name="alert-circle" size={14} color="#C0392B" accessibilityElementsHidden />
            <Text style={styles.error}>{error}</Text>
          </View>
        ) : null}

        <View style={styles.inputRow}>
          <TextInput
            testID="input-chat-mensagem"
            style={styles.input}
            placeholder="Escreva sua mensagem…"
            placeholderTextColor={C.textFaint}
            value={texto}
            onChangeText={setTexto}
            multiline
            maxLength={2000}
          />
          <TouchableOpacity
            testID="btn-enviar-mensagem"
            style={[styles.sendBtn, (sending || !texto.trim()) && { opacity: 0.5 }]}
            onPress={enviar}
            disabled={sending || !texto.trim()}
            activeOpacity={0.8}
          >
            <Feather name="send" size={18} color="#fff" />
          </TouchableOpacity>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 8,
    paddingBottom: 12,
  },
  headerTitle: { fontSize: 20, fontWeight: '800', letterSpacing: -0.4, color: C.text },

  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginHorizontal: 20,
    marginBottom: 10,
    backgroundColor: '#E2EEF2',
    borderRadius: 10,
    padding: 10,
  },
  bannerText: { flex: 1, fontSize: 12, color: C.institutional2, lineHeight: 12 * 1.4 },

  list: { paddingHorizontal: 16, paddingBottom: 12, gap: 8, flexGrow: 1 },

  bubbleRow: { flexDirection: 'row', justifyContent: 'flex-start' },
  bubbleRowMine: { justifyContent: 'flex-end' },
  bubble: {
    maxWidth: '80%',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  bubbleMine: { backgroundColor: C.bubbleMine, borderBottomRightRadius: 4 },
  bubbleTheirs: {
    backgroundColor: C.bubbleTheirs,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderBottomLeftRadius: 4,
  },
  bubbleSender: { fontSize: 11, fontWeight: '700', color: C.institutional2, marginBottom: 2 },
  bubbleTextMine: { fontSize: 14.5, color: '#fff', lineHeight: 14.5 * 1.4 },
  bubbleTextTheirs: { fontSize: 14.5, color: C.text, lineHeight: 14.5 * 1.4 },

  maskedRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 4 },
  maskedText: { fontSize: 10.5, fontStyle: 'italic' },

  empty: { alignItems: 'center', justifyContent: 'center', gap: 10, paddingTop: 80 },
  emptyText: { fontSize: 13.5, color: C.textFaint },

  errorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingVertical: 6 },
  error: { fontSize: 12, color: '#C0392B', textAlign: 'center' },

  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
    paddingHorizontal: 16,
    paddingVertical: 10,
    paddingBottom: 18,
    backgroundColor: C.surface,
    borderTopWidth: 1,
    borderTopColor: C.lineSoft,
  },
  input: {
    flex: 1,
    maxHeight: 100,
    backgroundColor: C.bg,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 20,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 14.5,
    color: C.text,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: C.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
