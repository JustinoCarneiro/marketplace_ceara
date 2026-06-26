import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import { useAuthStore } from '../../store/auth';

const COLORS = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#8A989B',
  danger: '#C0392B',
  dangerTint: '#FBE6E2',
  line: '#DCD2BC',
  lineSoft: '#E6DDC9',
  overlayBg: 'rgba(14,42,51,0.6)',
};

export default function SosScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const token = useAuthStore(s => s.accessToken);
  const [loading, setLoading] = useState(false);

  async function triggerSos() {
    setLoading(true);
    try {
      const res = await fetch(`${API_BASE}/sos`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({
          serviceRequestId: route.params.requestId,
          latitude: -3.7319,
          longitude: -38.5267,
        }),
      });
      const data = await res.json();
      if (res.ok) {
        nav.replace('SosActive', { alertId: data.id });
      }
    } catch {
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={styles.overlay}>
      <SafeAreaView style={styles.safeOverlay} edges={['top']}>
        <View style={styles.bgContent}>
          <View style={styles.bgStatusBadge}>
            <Text style={styles.bgStatusText}>EM ANDAMENTO</Text>
          </View>
          <View style={styles.bgLine} />
        </View>
      </SafeAreaView>

      <View style={styles.sheet}>
        <View style={styles.handle} />

        <View style={styles.sheetBody}>
          <View style={styles.iconCircle}>
            <Feather name="alert-triangle" size={36} color={COLORS.danger} />
          </View>

          <Text style={styles.sheetTitle}>Acionar emergência?</Text>

          <Text style={styles.sheetDesc}>
            Vamos registrar sua <Text style={styles.sheetDescBold}>localização</Text> e{' '}
            <Text style={styles.sheetDescBold}>horário</Text> e acionar nosso canal de emergência.
            Use apenas em situação de risco real.
          </Text>
        </View>

        <View style={styles.actions}>
          <TouchableOpacity
            style={[styles.acionarBtn, loading && { opacity: 0.6 }]}
            onPress={triggerSos}
            disabled={loading}
            activeOpacity={0.85}
          >
            <Feather name="alert-triangle" size={18} color="#fff" />
            <Text style={styles.acionarText}>{loading ? 'Acionando…' : 'Acionar agora'}</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.cancelBtn}
            onPress={() => nav.goBack()}
            activeOpacity={0.75}
          >
            <Text style={styles.cancelText}>Cancelar</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.auditRow}>
          <Feather name="lock" size={13} color={COLORS.textFaint} />
          <Text style={styles.auditText}>Este acionamento fica auditável.</Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: COLORS.overlayBg,
    justifyContent: 'flex-end',
  },
  safeOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
  bgContent: {
    paddingHorizontal: 22,
    paddingTop: 22,
    gap: 10,
    opacity: 0.45,
  },
  bgStatusBadge: {
    alignSelf: 'flex-start',
    backgroundColor: '#14A8A0',
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 100,
  },
  bgStatusText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#fff',
  },
  bgLine: {
    height: 14,
    width: '65%',
    backgroundColor: '#F3ECDC',
    borderRadius: 8,
  },
  sheet: {
    backgroundColor: COLORS.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingHorizontal: 22,
    paddingTop: 14,
    paddingBottom: 24,
    gap: 18,
    shadowColor: '#0E2A33',
    shadowOffset: { width: 0, height: -20 },
    shadowOpacity: 0.5,
    shadowRadius: 50,
    elevation: 20,
  },
  handle: {
    width: 44,
    height: 5,
    borderRadius: 100,
    backgroundColor: COLORS.line,
    alignSelf: 'center',
  },
  sheetBody: {
    alignItems: 'center',
    gap: 14,
  },
  iconCircle: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: COLORS.dangerTint,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sheetTitle: {
    fontSize: 24,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: COLORS.text,
    textAlign: 'center',
  },
  sheetDesc: {
    fontSize: 15,
    lineHeight: 24,
    color: COLORS.textSoft,
    textAlign: 'center',
    maxWidth: 300,
  },
  sheetDescBold: {
    color: COLORS.text,
    fontWeight: '700',
  },
  actions: {
    gap: 10,
  },
  acionarBtn: {
    width: '100%',
    height: 56,
    borderRadius: 100,
    backgroundColor: COLORS.danger,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    shadowColor: COLORS.danger,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 28,
    elevation: 8,
  },
  acionarText: {
    fontSize: 16,
    fontWeight: '800',
    color: '#fff',
  },
  cancelBtn: {
    width: '100%',
    height: 52,
    borderRadius: 100,
    borderWidth: 1.5,
    borderColor: COLORS.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelText: {
    fontSize: 15,
    fontWeight: '700',
    color: COLORS.textSoft,
  },
  auditRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
  },
  auditText: {
    fontSize: 12,
    color: COLORS.textFaint,
  },
});
