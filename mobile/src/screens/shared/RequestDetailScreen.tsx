import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  ActivityIndicator, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import ScreenHeader from '../../components/ScreenHeader';
import StatusBadge from '../../components/StatusBadge';
import Button from '../../components/Button';

interface Request {
  id: string;
  status: string;
  categoria: string;
  descricao: string;
  clienteId: string;
  prestadorId?: string;
  createdAt: string;
  updatedAt: string;
  transacao?: {
    statusPagamento: string;
    valorTotal: number;
    valorComissao: number;
  };
}

export default function RequestDetailScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const token = useAuthStore(s => s.accessToken);
  const role = useAuthStore(s => s.role);
  const userId = useAuthStore(s => s.userId);
  const [request, setRequest] = useState<Request | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const requestId = route.params?.requestId ?? '';

  async function load() {
    if (!requestId) { setLoading(false); return; }
    try {
      const res = await fetch(`${API_BASE}/service-requests/${requestId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setRequest(res.ok ? await res.json() : null);
    } catch {
      setRequest(null);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, [requestId]);

  async function startService() {
    setActionLoading(true);
    try {
      await fetch(`${API_BASE}/service-requests/${requestId}/start`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      load();
    } finally {
      setActionLoading(false); }
  }

  async function confirmCompletion() {
    setActionLoading(true);
    try {
      await fetch(`${API_BASE}/service-requests/${requestId}/confirm-completion`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      });
      load();
    } finally {
      setActionLoading(false); }
  }

  const isProvider = role === 'ROLE_PROVIDER';
  const isClient = role === 'ROLE_CLIENT';
  const st = request?.status;

  const TIMELINE_STEPS = [
    { label: 'Pedido criado', done: true },
    { label: 'Proposta aceita', done: ['ACEITO','EM_ANDAMENTO','CONCLUIDO','EM_DISPUTA'].includes(st ?? '') },
    { label: 'Em andamento',   done: ['EM_ANDAMENTO','CONCLUIDO'].includes(st ?? '') },
    { label: 'Concluído',      done: st === 'CONCLUIDO' },
  ];

  if (loading) return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="Chamado" onBack={() => nav.goBack()} />
      <View style={styles.center}><ActivityIndicator color={color.primary} size="large" /></View>
    </SafeAreaView>
  );

  if (!request) return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="Chamado" onBack={() => nav.goBack()} />
      <View style={styles.center}><Text style={styles.emptyText}>Chamado não encontrado.</Text></View>
    </SafeAreaView>
  );

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader
        title="Detalhe do chamado"
        onBack={() => nav.goBack()}
        right={
          <TouchableOpacity onPress={() => nav.navigate('Sos', { requestId })} hitSlop={8}>
            <Text style={{ fontSize: 22 }}>🆘</Text>
          </TouchableOpacity>
        }
      />
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* Status */}
        <View style={styles.statusRow}>
          <StatusBadge status={request.status as any} />
          <Text style={styles.date}>{new Date(request.updatedAt).toLocaleDateString('pt-BR')}</Text>
        </View>

        {/* Descrição */}
        <View style={styles.card}>
          <Text style={styles.cardLabel}>CATEGORIA</Text>
          <Text style={styles.cardValue}>{request.categoria}</Text>
          <Text style={styles.cardLabel} key="desc-label">DESCRIÇÃO</Text>
          <Text style={styles.desc}>{request.descricao}</Text>
        </View>

        {/* Timeline */}
        <View style={styles.card}>
          <Text style={styles.cardLabel}>PROGRESSO</Text>
          {TIMELINE_STEPS.map((step, i) => (
            <View key={i} style={styles.timelineRow}>
              <View style={[styles.timelineDot, step.done && styles.timelineDotDone]} />
              {i < TIMELINE_STEPS.length - 1 && (
                <View style={[styles.timelineLine, step.done && styles.timelineLineDone]} />
              )}
              <Text style={[styles.timelineLabel, step.done && styles.timelineLabelDone]}>{step.label}</Text>
            </View>
          ))}
        </View>

        {/* Transação */}
        {request.transacao && (
          <View style={[styles.card, styles.escrowCard]}>
            <View style={styles.escrowRow}>
              <Text style={{ fontSize: 20 }}>🔒</Text>
              <View style={{ flex: 1 }}>
                <Text style={styles.escrowTitle}>Escrow</Text>
                <StatusBadge status={request.transacao.statusPagamento as any} size="sm" />
              </View>
              <Text style={styles.escrowValue}>
                R$ {request.transacao.valorTotal.toFixed(2).replace('.', ',')}
              </Text>
            </View>
          </View>
        )}

        {/* Ações */}
        {isProvider && st === 'ACEITO' && (
          <Button label="Iniciar serviço" onPress={startService} loading={actionLoading} />
        )}
        {isClient && st === 'EM_ANDAMENTO' && (
          <Button label="Confirmar conclusão" onPress={confirmCompletion} loading={actionLoading} />
        )}
        {st === 'CONCLUIDO' && (
          <Button
            label="Avaliar"
            onPress={() => nav.navigate('Rate', {
              requestId,
              avaliadoId: isClient ? request.prestadorId : request.clienteId,
              avaliadoNome: isClient ? 'o prestador' : 'o cliente',
            })}
          />
        )}
        {['ACEITO','EM_ANDAMENTO'].includes(st ?? '') && (
          <Button
            label="Abrir disputa"
            variant="outline"
            onPress={() => nav.navigate('OpenDispute', { requestId })}
          />
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: font.size.bodySm, color: color.textSoft },
  content: { paddingHorizontal: space[5], paddingBottom: space[7], gap: space[4] },
  statusRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingTop: space[2] },
  date: { fontSize: font.size.caption, color: color.textFaint },
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
    ...shadow.soft,
  },
  cardLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  cardValue: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.text },
  desc: { fontSize: font.size.body, color: color.text, lineHeight: font.size.body * font.lineHeight.body },
  timelineRow: { flexDirection: 'row', alignItems: 'center', gap: space[3], paddingVertical: 2 },
  timelineDot: {
    width: 16,
    height: 16,
    borderRadius: 8,
    borderWidth: 2,
    borderColor: color.line,
    backgroundColor: color.bg,
    flexShrink: 0,
  },
  timelineDotDone: { borderColor: color.success, backgroundColor: color.success },
  timelineLine: { position: 'absolute', left: 7, top: 20, width: 2, height: 20, backgroundColor: color.line },
  timelineLineDone: { backgroundColor: color.success },
  timelineLabel: { fontSize: font.size.bodySm, color: color.textFaint },
  timelineLabelDone: { color: color.text, fontWeight: font.weight.semibold },
  escrowCard: { borderColor: color.institutional + '40' },
  escrowRow: { flexDirection: 'row', alignItems: 'center', gap: space[3] },
  escrowTitle: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.institutional, marginBottom: 4 },
  escrowValue: { fontSize: font.size.h3, fontWeight: font.weight.black, color: color.primary },
});
