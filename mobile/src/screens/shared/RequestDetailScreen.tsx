import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView,
  ActivityIndicator, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import { useAuthStore } from '../../store/auth';

interface Request {
  id: string;
  status: string;
  categoria: string;
  descricao: string;
  clienteId: string;
  prestadorId?: string;
  prestadorNome?: string;
  createdAt: string;
  updatedAt: string;
  transacao?: {
    statusPagamento: string;
    valorTotal: number;
    valorComissao: number;
  };
}

const COLORS = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#8A989B',
  primary: '#14A8A0',
  primaryDark: '#1B8C84',
  institutional: '#0E3F52',
  institutional2: '#15596E',
  danger: '#C0392B',
  dangerTint: '#FBE6E2',
  line: '#DCD2BC',
  lineSoft: '#E6DDC9',
  escrowBg: '#E2EEF2',
  escrowText: '#0E3F52',
  activeHalo: '#B7E5E1',
  statusActive: '#14A8A0',
  concluido: '#15756E',
  concluidoBg: '#DDF0EC',
};

function statusLabel(st: string): string {
  const map: Record<string, string> = {
    PENDENTE: 'PENDENTE',
    PROPOSTO: 'PROPOSTO',
    ACEITO: 'ACEITO',
    EM_ANDAMENTO: 'EM ANDAMENTO',
    CONCLUIDO: 'CONCLUÍDO',
    EM_DISPUTA: 'EM DISPUTA',
    CANCELADO: 'CANCELADO',
  };
  return map[st] ?? st;
}

function statusBgColor(st: string): string {
  if (st === 'EM_ANDAMENTO') return COLORS.primary;
  if (st === 'CONCLUIDO') return COLORS.concluidoBg;
  if (st === 'EM_DISPUTA') return COLORS.dangerTint;
  if (st === 'CANCELADO') return '#F0E8E8';
  if (st === 'ACEITO') return '#E2EEF2';
  return '#F0EAD6';
}

function statusTextColor(st: string): string {
  if (st === 'EM_ANDAMENTO') return '#fff';
  if (st === 'CONCLUIDO') return COLORS.concluido;
  if (st === 'EM_DISPUTA') return COLORS.danger;
  if (st === 'CANCELADO') return COLORS.danger;
  if (st === 'ACEITO') return COLORS.escrowText;
  return COLORS.textFaint;
}

function statusDotColor(st: string): string {
  if (st === 'EM_ANDAMENTO') return '#fff';
  if (st === 'CONCLUIDO') return COLORS.concluido;
  if (st === 'EM_DISPUTA') return COLORS.danger;
  if (st === 'CANCELADO') return COLORS.danger;
  return COLORS.textFaint;
}

export default function RequestDetailScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const token = useAuthStore(s => s.accessToken);
  const role = useAuthStore(s => s.role);
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
      setActionLoading(false);
    }
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
      setActionLoading(false);
    }
  }

  const isProvider = role === 'ROLE_PROVIDER';
  const isClient = role === 'ROLE_CLIENT';
  const st = request?.status ?? '';

  type TimelineStep = {
    label: string;
    sublabel: string;
    state: 'done' | 'active' | 'pending';
  };

  const TIMELINE_STEPS: TimelineStep[] = [
    {
      label: 'Pedido criado',
      sublabel: `PENDENTE · ${request ? new Date(request.createdAt).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : ''}`,
      state: 'done',
    },
    {
      label: 'Proposta aceita',
      sublabel: 'PROPOSTO → ACEITO',
      state: ['ACEITO', 'EM_ANDAMENTO', 'CONCLUIDO', 'EM_DISPUTA'].includes(st) ? 'done' : 'pending',
    },
    {
      label: 'Pagamento retido',
      sublabel: 'Escrow RETIDO',
      state: ['ACEITO', 'EM_ANDAMENTO', 'CONCLUIDO', 'EM_DISPUTA'].includes(st) ? 'done' : 'pending',
    },
    {
      label: 'Serviço em andamento',
      sublabel: 'EM_ANDAMENTO',
      state: st === 'EM_ANDAMENTO' ? 'active' : ['CONCLUIDO', 'EM_DISPUTA'].includes(st) ? 'done' : 'pending',
    },
    {
      label: 'Conclusão',
      sublabel: st === 'CONCLUIDO' ? 'CONCLUÍDO' : 'CONCLUÍDO · aguardando',
      state: st === 'CONCLUIDO' ? 'done' : 'pending',
    },
  ];

  if (loading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.topBar}>
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={8}>
            <Feather name="chevron-left" size={22} color={COLORS.text} />
          </TouchableOpacity>
        </View>
        <View style={styles.center}>
          <ActivityIndicator color={COLORS.primary} size="large" />
        </View>
      </SafeAreaView>
    );
  }

  if (!request) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.topBar}>
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={8}>
            <Feather name="chevron-left" size={22} color={COLORS.text} />
          </TouchableOpacity>
        </View>
        <View style={styles.center}>
          <Text style={styles.emptyText}>Chamado não encontrado.</Text>
        </View>
      </SafeAreaView>
    );
  }

  const showConfirmCompletion = isClient && st === 'EM_ANDAMENTO';
  const showStartService = isProvider && st === 'ACEITO';
  const showRate = st === 'CONCLUIDO';
  const showCancel = ['ACEITO', 'EM_ANDAMENTO'].includes(st);
  const showDispute = ['ACEITO', 'EM_ANDAMENTO'].includes(st);

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: 0 }} showsVerticalScrollIndicator={false}>
        <View style={styles.topBar}>
          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={8} style={styles.backRow}>
            <Feather name="chevron-left" size={22} color={COLORS.text} />
            <View style={{ marginLeft: 12 }}>
              <Text style={styles.headerTitle}>Chamado #{request.id.slice(-4)}</Text>
              <Text style={styles.headerSub}>{request.categoria}{request.prestadorNome ? ` · ${request.prestadorNome}` : ''}</Text>
            </View>
          </TouchableOpacity>
        </View>

        <View style={styles.statusRow}>
          <View style={[styles.statusBadge, { backgroundColor: statusBgColor(st) }]}>
            <View style={[styles.statusDot, { backgroundColor: statusDotColor(st) }]} />
            <Text style={[styles.statusText, { color: statusTextColor(st) }]}>{statusLabel(st)}</Text>
          </View>
          {request.transacao && (
            <View style={styles.escrowBadge}>
              <Feather name="lock" size={13} color={COLORS.escrowText} />
              <Text style={styles.escrowText}>R$ {request.transacao.valorTotal.toFixed(2).replace('.', ',')} RETIDO</Text>
            </View>
          )}
        </View>

        <View style={styles.timeline}>
          {TIMELINE_STEPS.map((step, i) => {
            const isLast = i === TIMELINE_STEPS.length - 1;
            const dotDone = step.state === 'done';
            const dotActive = step.state === 'active';
            const lineDone = i < TIMELINE_STEPS.length - 1 && (TIMELINE_STEPS[i].state === 'done');
            return (
              <View key={i} style={styles.timelineRow}>
                <View style={styles.timelineLeft}>
                  <View style={[
                    styles.timelineDot,
                    dotDone && styles.timelineDotDone,
                    dotActive && styles.timelineDotActive,
                  ]}>
                    {dotDone && <Feather name="check" size={13} color="#fff" />}
                  </View>
                  {!isLast && (
                    <View style={[styles.timelineLine, lineDone && styles.timelineLineDone]} />
                  )}
                </View>
                <View style={[styles.timelineContent, isLast && { paddingBottom: 0 }]}>
                  <Text style={[styles.timelineLabel, (dotDone || dotActive) && styles.timelineLabelActive]}>
                    {step.label}
                  </Text>
                  <Text style={[
                    styles.timelineSub,
                    dotActive && { color: COLORS.primary, fontWeight: '600' },
                  ]}>
                    {step.sublabel}
                  </Text>
                </View>
              </View>
            );
          })}
        </View>
      </ScrollView>

      <View style={styles.footer}>
        {showConfirmCompletion && (
          <TouchableOpacity
            style={[styles.btnPrimary, actionLoading && { opacity: 0.6 }]}
            onPress={confirmCompletion}
            disabled={actionLoading}
            activeOpacity={0.85}
          >
            <Feather name="check" size={18} color="#fff" />
            <Text style={styles.btnPrimaryText}>{actionLoading ? 'Aguarde…' : 'Confirmar conclusão'}</Text>
          </TouchableOpacity>
        )}
        {showStartService && (
          <TouchableOpacity
            style={[styles.btnPrimary, actionLoading && { opacity: 0.6 }]}
            onPress={startService}
            disabled={actionLoading}
            activeOpacity={0.85}
          >
            <Feather name="play" size={18} color="#fff" />
            <Text style={styles.btnPrimaryText}>{actionLoading ? 'Aguarde…' : 'Iniciar serviço'}</Text>
          </TouchableOpacity>
        )}
        {showRate && (
          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => nav.navigate('Rate', {
              requestId,
              avaliadoId: isClient ? request.prestadorId : request.clienteId,
              avaliadoNome: isClient ? (request.prestadorNome ?? 'o prestador') : 'o cliente',
            })}
            activeOpacity={0.85}
          >
            <Feather name="star" size={18} color="#fff" />
            <Text style={styles.btnPrimaryText}>Avaliar</Text>
          </TouchableOpacity>
        )}
        <View style={styles.footerRow}>
          {showCancel && (
            <TouchableOpacity
              style={styles.btnGhost}
              onPress={() => nav.goBack()}
              activeOpacity={0.75}
            >
              <Text style={styles.btnGhostText}>Cancelar</Text>
            </TouchableOpacity>
          )}
          {showDispute && (
            <TouchableOpacity
              style={styles.btnSos}
              onPress={() => nav.navigate('Sos', { requestId })}
              activeOpacity={0.85}
            >
              <Feather name="alert-triangle" size={17} color="#fff" />
              <Text style={styles.btnSosText}>SOS</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: 14, color: COLORS.textSoft },
  topBar: {
    paddingHorizontal: 20,
    paddingVertical: 8,
    backgroundColor: COLORS.bg,
  },
  backRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: COLORS.text,
  },
  headerSub: {
    fontSize: 13,
    color: COLORS.textSoft,
    marginTop: 1,
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 20,
    paddingBottom: 16,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingVertical: 9,
    paddingHorizontal: 16,
    borderRadius: 100,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  statusText: {
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  escrowBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: COLORS.escrowBg,
    paddingVertical: 9,
    paddingHorizontal: 14,
    borderRadius: 100,
  },
  escrowText: {
    fontSize: 12,
    fontWeight: '700',
    color: COLORS.escrowText,
  },
  timeline: {
    paddingHorizontal: 20,
    paddingTop: 4,
    paddingBottom: 20,
  },
  timelineRow: {
    flexDirection: 'row',
    gap: 14,
  },
  timelineLeft: {
    alignItems: 'center',
    width: 26,
  },
  timelineDot: {
    width: 26,
    height: 26,
    borderRadius: 13,
    borderWidth: 2,
    borderColor: COLORS.line,
    backgroundColor: COLORS.bg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  timelineDotDone: {
    borderColor: COLORS.primaryDark,
    backgroundColor: COLORS.primaryDark,
  },
  timelineDotActive: {
    borderColor: COLORS.activeHalo,
    backgroundColor: COLORS.primary,
    borderWidth: 3,
  },
  timelineLine: {
    width: 2,
    flex: 1,
    backgroundColor: COLORS.line,
    minHeight: 26,
  },
  timelineLineDone: {
    backgroundColor: COLORS.primaryDark,
  },
  timelineContent: {
    paddingBottom: 18,
    flex: 1,
  },
  timelineLabel: {
    fontSize: 15,
    fontWeight: '700',
    color: COLORS.textFaint,
  },
  timelineLabelActive: {
    color: COLORS.text,
  },
  timelineSub: {
    fontSize: 12.5,
    color: COLORS.textFaint,
    marginTop: 2,
  },
  footer: {
    backgroundColor: COLORS.surface,
    borderTopWidth: 1,
    borderTopColor: COLORS.lineSoft,
    paddingHorizontal: 20,
    paddingTop: 14,
    paddingBottom: 20,
    gap: 12,
  },
  btnPrimary: {
    width: '100%',
    height: 54,
    borderRadius: 100,
    backgroundColor: COLORS.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 6,
  },
  btnPrimaryText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#fff',
  },
  footerRow: {
    flexDirection: 'row',
    gap: 10,
  },
  btnGhost: {
    flex: 1,
    height: 50,
    borderRadius: 100,
    borderWidth: 1.5,
    borderColor: COLORS.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnGhostText: {
    fontSize: 14,
    fontWeight: '700',
    color: COLORS.textSoft,
  },
  btnSos: {
    flex: 1,
    height: 50,
    borderRadius: 100,
    backgroundColor: COLORS.danger,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    shadowColor: COLORS.danger,
    shadowOffset: { width: 0, height: 14 },
    shadowOpacity: 0.8,
    shadowRadius: 24,
    elevation: 5,
  },
  btnSosText: {
    fontSize: 14,
    fontWeight: '800',
    color: '#fff',
  },
});
