import { API_BASE } from '../../api/config';
import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';

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
    <SafeAreaView style={styles.safe}>
      <View style={styles.handle} />
      <View style={styles.content}>
        <View style={styles.icon}>
          <Text style={{ fontSize: 56 }}>🆘</Text>
        </View>
        <Text style={styles.title}>Botão de Emergência</Text>
        <Text style={styles.body}>
          Use apenas em situação de risco real. Nossa equipe e os serviços de emergência serão acionados imediatamente.
        </Text>

        <View style={styles.divider} />

        <TouchableOpacity
          style={[styles.sosBtn, loading && styles.sosBtnDisabled]}
          onPress={triggerSos}
          disabled={loading}
          activeOpacity={0.85}
        >
          <Text style={styles.sosBtnIcon}>🆘</Text>
          <Text style={styles.sosBtnText}>{loading ? 'Acionando…' : 'ACIONAR SOS'}</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.cancelBtn} onPress={() => nav.goBack()}>
          <Text style={styles.cancelText}>Cancelar — estou bem</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  handle: { width: 40, height: 4, borderRadius: 2, backgroundColor: color.line, alignSelf: 'center', marginTop: space[3] },
  content: {
    flex: 1,
    paddingHorizontal: space[5],
    paddingTop: space[5],
    paddingBottom: space[6],
    alignItems: 'center',
    gap: space[4],
  },
  icon: {
    width: 120,
    height: 120,
    borderRadius: 60,
    backgroundColor: color.dangerTint,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: space[4],
  },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.danger, textAlign: 'center' },
  body: {
    fontSize: font.size.body,
    color: color.textSoft,
    textAlign: 'center',
    lineHeight: font.size.body * font.lineHeight.body,
    maxWidth: 300,
  },
  divider: { width: '100%', height: 1, backgroundColor: color.lineSoft },
  sosBtn: {
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: color.danger,
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[2],
    shadowColor: color.danger,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.55,
    shadowRadius: 32,
    elevation: 10,
    marginTop: space[3],
  },
  sosBtnDisabled: { opacity: 0.6 },
  sosBtnIcon: { fontSize: 48 },
  sosBtnText: { fontSize: font.size.bodySm, fontWeight: font.weight.black, color: color.textOnAccent, letterSpacing: 1 },
  cancelBtn: { marginTop: space[2] },
  cancelText: { fontSize: font.size.body, color: color.textSoft, fontWeight: font.weight.medium },
});
