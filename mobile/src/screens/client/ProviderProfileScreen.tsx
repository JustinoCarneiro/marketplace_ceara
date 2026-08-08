import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, Modal, TextInput, Alert,
  ActivityIndicator, TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ClientStackParams, 'ProviderProfile'>;

interface Review { id: string; autorNome: string; nota: number; comentario: string; }
interface ProviderProfile {
  userId: string;
  nome: string;
  categoria: string;
  bio?: string;
  notaMedia: number | null;
  totalAvaliacoes?: number;
  totalServicos?: number;
  tempoRespostaMin?: number;
  pontualidadePct?: number;
  precoMin?: number;
  precoMax?: number;
  statusVerificacao: string;
  avaliacoes?: Review[];
}

function initials(nome: string) {
  return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
}

const AVATAR_COLORS = [color.warmTerra, color.catHidraulica, color.catLimpeza, color.catReforma];
function avatarBg(nome: string) { return AVATAR_COLORS[nome.charCodeAt(0) % AVATAR_COLORS.length]; }

const REVIEW_AVATAR_COLORS = [color.institutional2, color.catJardinagem, color.warmTerra, color.catLimpeza];
function reviewAvatarBg(nome: string) { return REVIEW_AVATAR_COLORS[nome.charCodeAt(0) % REVIEW_AVATAR_COLORS.length]; }

const MOTIVOS_DENUNCIA = ['Perfil falso', 'Comportamento inadequado', 'Conteúdo ofensivo', 'Golpe ou fraude', 'Outro'];

export default function ProviderProfileScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [profile, setProfile] = useState<ProviderProfile | null>(null);
  const [loading, setLoading] = useState(true);

  const [reportAlvo, setReportAlvo] = useState<{ tipo: 'PRESTADOR' | 'AVALIACAO'; alvoId: string } | null>(null);
  const [reportMotivo, setReportMotivo] = useState('');
  const [reportDetalhes, setReportDetalhes] = useState('');
  const [reportEnviando, setReportEnviando] = useState(false);
  const [reportError, setReportError] = useState('');

  function abrirDenuncia(tipo: 'PRESTADOR' | 'AVALIACAO', alvoId: string) {
    setReportAlvo({ tipo, alvoId });
    setReportMotivo('');
    setReportDetalhes('');
    setReportError('');
  }

  async function enviarDenuncia() {
    if (!reportAlvo) return;
    if (!reportMotivo) { setReportError('Selecione um motivo.'); return; }
    setReportEnviando(true);
    setReportError('');
    try {
      const res = await fetch(`${API_BASE}/denuncias`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          tipo: reportAlvo.tipo,
          alvoId: reportAlvo.alvoId,
          motivo: reportMotivo,
          detalhes: reportDetalhes.trim() || null,
        }),
      });
      if (!res.ok) throw new Error('Erro ao enviar denúncia');
      setReportAlvo(null);
      Alert.alert('Denúncia enviada', 'Nossa equipe vai analisar. Obrigado por ajudar a manter o Onda seguro.');
    } catch {
      setReportError('Não foi possível enviar a denúncia. Tente de novo.');
    } finally {
      setReportEnviando(false);
    }
  }

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(
          `${API_BASE}/providers/${route.params.providerId}`,
          { headers: { Authorization: `Bearer ${token}` } },
        );
        const data = await res.json();
        setProfile(res.ok ? data : null);
      } catch {
        setProfile(null);
      } finally {
        setLoading(false);
      }
    })();
  }, [route.params.providerId]);

  if (loading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.center}><ActivityIndicator color={color.primary} size="large" /></View>
      </SafeAreaView>
    );
  }

  if (!profile) {
    return (
      <SafeAreaView style={styles.safe}>
        <TouchableOpacity onPress={() => nav.goBack()} style={styles.backPad} hitSlop={14}
          accessibilityLabel="Voltar" accessibilityRole="button">
          <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
        </TouchableOpacity>
        <View style={styles.center}><Text style={styles.emptyText}>Perfil não encontrado.</Text></View>
      </SafeAreaView>
    );
  }

  const bg = avatarBg(profile.nome);
  const init = initials(profile.nome);
  const nota = profile.notaMedia ?? 0;
  const isVerified = profile.statusVerificacao === 'VERIFICADO';
  const precoStr = profile.precoMin && profile.precoMax
    ? `R$ ${profile.precoMin} – R$ ${profile.precoMax}`
    : null;

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <TouchableOpacity onPress={() => nav.goBack()} style={styles.backPad} hitSlop={14}
          accessibilityLabel="Voltar" accessibilityRole="button">
          <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
        </TouchableOpacity>

        <View style={styles.hero}>
          <View style={[styles.avatar, { backgroundColor: bg }]}>
            <Text style={styles.avatarText}>{init}</Text>
          </View>
          <View style={styles.heroInfo}>
            <Text style={styles.name}>{profile.nome}</Text>
            {isVerified && (
              <View style={styles.verifiedBadge}>
                <Feather name="shield" size={13} color="#fff" />
                <Text style={styles.verifiedBadgeText}>PRESTADOR VERIFICADO</Text>
              </View>
            )}
            <View style={styles.ratingMeta}>
              <View style={styles.starRow}>
                <Feather name="star" size={16} color={color.warmSun} />
                <Text style={styles.ratingVal}>{nota > 0 ? nota.toFixed(1) : 'Novo'}</Text>
              </View>
              <Text style={styles.metaText}>
                {profile.totalAvaliacoes ? `· ${profile.totalAvaliacoes} avaliações · ` : '· '}
                {profile.categoria}
                {profile.tempoRespostaMin ? ` · ~${profile.tempoRespostaMin} min` : ''}
              </Text>
            </View>
            <TouchableOpacity
              onPress={() => abrirDenuncia('PRESTADOR', profile.userId)}
              hitSlop={8}
              accessibilityRole="button"
              accessibilityLabel="Reportar prestador"
            >
              <Text style={styles.reportLink}>Reportar prestador</Text>
            </TouchableOpacity>
          </View>
        </View>

        <View style={styles.statsRow}>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>{profile.totalServicos ?? 0}+</Text>
            <Text style={styles.statLabel}>serviços</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={styles.statValue}>
              {profile.tempoRespostaMin ? `~${profile.tempoRespostaMin} min` : '—'}
            </Text>
            <Text style={styles.statLabel}>resposta</Text>
          </View>
          <View style={styles.statBox}>
            <Text style={[styles.statValue, { color: color.success }]}>
              {profile.pontualidadePct ? `${profile.pontualidadePct}%` : '—'}
            </Text>
            <Text style={styles.statLabel}>no prazo</Text>
          </View>
        </View>

        {precoStr && (
          <View style={styles.priceRow}>
            <View style={styles.priceChip}>
              <Text style={styles.priceLabel}>Faixa de preço</Text>
              <Text style={styles.priceValue}>{precoStr}</Text>
            </View>
          </View>
        )}

        {profile.avaliacoes && profile.avaliacoes.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Avaliações recentes</Text>
            <View style={styles.reviewList}>
              {profile.avaliacoes.slice(0, 3).map((r, i) => {
                const rInit = r.autorNome.split(' ').slice(0, 2).map(n => n[0]).join('').slice(0, 2).toUpperCase();
                const rBg = reviewAvatarBg(r.autorNome);
                return (
                  <View key={i} style={styles.reviewCard}>
                    <View style={styles.reviewHeader}>
                      <View style={[styles.reviewAvatar, { backgroundColor: rBg }]}>
                        <Text style={styles.reviewAvatarText}>{rInit}</Text>
                      </View>
                      <Text style={styles.reviewAuthor}>{r.autorNome}</Text>
                      <View style={styles.reviewStars}>
                        {Array.from({ length: 5 }).map((_, si) => (
                          <Text
                            key={si}
                            style={{ fontSize: 13, color: si < r.nota ? color.warmSun : color.lineSoft }}
                          >
                            ★
                          </Text>
                        ))}
                      </View>
                      <TouchableOpacity
                        onPress={() => abrirDenuncia('AVALIACAO', r.id)}
                        hitSlop={8}
                        accessibilityRole="button"
                        accessibilityLabel="Reportar avaliação"
                      >
                        <Feather name="flag" size={14} color={color.textFaint} />
                      </TouchableOpacity>
                    </View>
                    <Text style={styles.reviewText}>{r.comentario}</Text>
                  </View>
                );
              })}
            </View>
          </View>
        )}

        <View style={{ height: 100 }} />
      </ScrollView>

      <View style={styles.footer}>
        <TouchableOpacity
          style={styles.ctaBtn}
          onPress={() => nav.navigate('NewRequest', { providerId: profile.userId, categoria: profile.categoria })}
          activeOpacity={0.85}
        >
          <Text style={styles.ctaBtnText}>Solicitar serviço</Text>
          <Feather name="arrow-right" size={18} color="#fff" />
        </TouchableOpacity>
      </View>

      <Modal visible={!!reportAlvo} transparent animationType="fade" onRequestClose={() => setReportAlvo(null)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>
              {reportAlvo?.tipo === 'PRESTADOR' ? 'Reportar prestador' : 'Reportar avaliação'}
            </Text>
            <Text style={styles.modalSubtitle}>Motivo</Text>
            <View style={styles.motivoGrid}>
              {MOTIVOS_DENUNCIA.map(m => (
                <TouchableOpacity
                  key={m}
                  style={[styles.motivoChip, reportMotivo === m && styles.motivoChipAtivo]}
                  onPress={() => setReportMotivo(m)}
                  activeOpacity={0.8}
                >
                  <Text style={[styles.motivoChipText, reportMotivo === m && styles.motivoChipTextAtivo]}>{m}</Text>
                </TouchableOpacity>
              ))}
            </View>
            <Text style={styles.modalSubtitle}>Detalhes (opcional)</Text>
            <TextInput
              style={styles.modalInput}
              placeholder="Conte o que aconteceu…"
              placeholderTextColor={color.textFaint}
              value={reportDetalhes}
              onChangeText={setReportDetalhes}
              multiline
              numberOfLines={3}
              maxLength={500}
            />
            {reportError ? <Text style={styles.modalError}>{reportError}</Text> : null}
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalCancelBtn} onPress={() => setReportAlvo(null)} disabled={reportEnviando}>
                <Text style={styles.modalCancelText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalSubmitBtn, reportEnviando && { opacity: 0.7 }]}
                onPress={enviarDenuncia}
                disabled={reportEnviando}
              >
                <Text style={styles.modalSubmitText}>{reportEnviando ? 'Enviando…' : 'Enviar denúncia'}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { fontSize: font.size.bodySm, color: color.textSoft },
  scroll: { paddingBottom: space[4] },

  backPad: {
    paddingHorizontal: 20,
    paddingTop: 8,
    paddingBottom: 0,
  },

  hero: {
    paddingHorizontal: 20,
    paddingTop: 16,
    paddingBottom: 20,
    alignItems: 'center',
    gap: 12,
    textAlign: 'center',
  },
  avatar: { width: 88, height: 88, borderRadius: 28, alignItems: 'center', justifyContent: 'center' },
  avatarText: { fontSize: 30, fontWeight: font.weight.black, color: color.textOnAccent },
  heroInfo: { alignItems: 'center', gap: 8 },
  name: {
    fontSize: 26,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.02 * 26,
    textAlign: 'center',
  },
  verifiedBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: color.institutional,
    borderRadius: radius.pill,
    paddingLeft: 9,
    paddingRight: 12,
    paddingVertical: 5,
  },
  verifiedBadgeText: {
    fontSize: 12,
    fontWeight: font.weight.bold,
    color: color.textOnAccent,
    letterSpacing: 0.5,
  },
  ratingMeta: { flexDirection: 'row', alignItems: 'center', gap: 4, flexWrap: 'wrap', justifyContent: 'center' },
  starRow: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  ratingVal: { fontSize: 15, fontWeight: font.weight.black, color: color.text },
  metaText: { fontSize: 14, color: color.textSoft },
  reportLink: { fontSize: 12.5, color: color.textFaint, textDecorationLine: 'underline' },

  statsRow: {
    flexDirection: 'row',
    marginHorizontal: 20,
    marginBottom: 20,
    gap: 10,
  },
  statBox: {
    flex: 1,
    backgroundColor: color.surface,
    borderRadius: radius.field,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: 14,
    alignItems: 'center',
    gap: 2,
  },
  statValue: { fontSize: 20, fontWeight: font.weight.black, color: color.text },
  statLabel: { fontSize: 12, color: color.textSoft, marginTop: 2 },

  priceRow: { paddingHorizontal: 20, marginBottom: 16 },
  priceChip: {
    backgroundColor: color.skyTint,
    borderWidth: 1,
    borderColor: color.accentSky,
    borderRadius: radius.field,
    padding: 14,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  priceLabel: { fontSize: 13, fontWeight: font.weight.semibold, color: color.institutional2 },
  priceValue: { fontSize: 16, fontWeight: font.weight.black, color: color.institutional },

  section: { paddingHorizontal: 20, marginBottom: 20, gap: 12 },
  sectionTitle: {
    fontSize: 16,
    fontWeight: font.weight.black,
    color: color.text,
  },
  reviewList: { gap: 12 },
  reviewCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: 14,
    gap: 6,
  },
  reviewHeader: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  reviewAvatar: {
    width: 34,
    height: 34,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  reviewAvatarText: { fontSize: 13, fontWeight: font.weight.bold, color: color.textOnAccent },
  reviewAuthor: { flex: 1, fontSize: 14, fontWeight: font.weight.bold, color: color.text },
  reviewStars: { flexDirection: 'row', gap: 1 },
  reviewText: { fontSize: 13.5, color: color.textSoft, lineHeight: 13.5 * 1.55 },

  footer: {
    padding: 14,
    paddingHorizontal: 20,
    paddingBottom: 20,
    backgroundColor: color.surface,
    borderTopWidth: 1,
    borderTopColor: color.lineSoft,
  },
  ctaBtn: {
    width: '100%',
    height: 56,
    borderRadius: radius.pill,
    backgroundColor: color.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    shadowColor: 'rgba(20,168,160,0.85)',
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 8,
  },
  ctaBtnText: { fontSize: 16, fontWeight: font.weight.bold, color: color.textOnAccent },

  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(14,42,51,0.5)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: space[5],
  },
  modalCard: {
    width: '100%',
    maxWidth: 420,
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: 10,
  },
  modalTitle: { fontSize: 18, fontWeight: font.weight.black, color: color.text },
  modalSubtitle: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.institutional2,
    marginTop: 6,
  },
  motivoGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  motivoChip: {
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.pill,
    paddingHorizontal: 12,
    paddingVertical: 7,
    backgroundColor: color.bg,
  },
  motivoChipAtivo: { borderColor: color.primary, backgroundColor: color.skyTint },
  motivoChipText: { fontSize: 12.5, color: color.textSoft, fontWeight: font.weight.semibold },
  motivoChipTextAtivo: { color: color.institutional },
  modalInput: {
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    padding: 12,
    minHeight: 72,
    fontSize: font.size.bodySm,
    color: color.text,
    textAlignVertical: 'top',
  },
  modalError: { fontSize: font.size.caption, color: color.danger },
  modalActions: { flexDirection: 'row', gap: 10, marginTop: 8 },
  modalCancelBtn: {
    flex: 1,
    height: 48,
    borderRadius: radius.pill,
    borderWidth: 1,
    borderColor: color.lineSoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  modalCancelText: { fontSize: 14, fontWeight: font.weight.bold, color: color.textSoft },
  modalSubmitBtn: {
    flex: 1,
    height: 48,
    borderRadius: radius.pill,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  modalSubmitText: { fontSize: 14, fontWeight: font.weight.bold, color: color.textOnAccent },
});
