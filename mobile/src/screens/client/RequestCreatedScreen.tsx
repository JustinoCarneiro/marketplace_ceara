import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { Feather } from '@expo/vector-icons';
import { API_BASE } from '../../api/config';
import { useAuthStore } from '../../store/auth';

type RouteProps = RouteProp<ClientStackParams, 'RequestCreated'>;

const C = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#606E71',
  primary: '#10847D',
  institutional: '#0E3F52',
  lineSoft: '#E6DDC9',
  warmSun: '#F2B015',
  sunTint: '#FDF3D6',
};

// Mesmos ícones/cores por categoria usados em NewRequestScreen — mantém o card de
// confirmação consistente com o que o cliente escolheu, em vez do ícone fixo de Elétrica.
const CATEGORY_ICON: Record<string, { icon: React.ComponentProps<typeof Feather>['name']; color: string; bg: string }> = {
  'Elétrica':   { icon: 'zap',     color: '#8E6508', bg: '#FDF3D6' },
  'Hidráulica': { icon: 'droplet', color: '#15596E', bg: '#E2EEF2' },
  'Limpeza':    { icon: 'edit-2',  color: '#15756E', bg: '#DDF0EC' },
  'Pintura':    { icon: 'edit-3',  color: '#A94C25', bg: '#F7E3D6' },
  'Reforma':    { icon: 'tool',    color: '#244C86', bg: '#E8EEFA' },
  'Jardinagem': { icon: 'sun',     color: '#3C7A4E', bg: '#E2F0E6' },
  'Geral':      { icon: 'grid',    color: '#4C636A', bg: '#FCF8EE' },
};

export default function RequestCreatedScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const token = useAuthStore(s => s.accessToken);
  const [summary, setSummary] = useState<{ categoria: string; descricao: string } | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const res = await fetch(`${API_BASE}/service-requests/${route.params.requestId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (res.ok) {
          const data = await res.json();
          setSummary({ categoria: data.categoria, descricao: data.descricao });
        }
      } catch {
        // Card do pedido só não aparece — o essencial (navegar pra "Meus pedidos") não depende disso.
      }
    })();
  }, [route.params.requestId]);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.screen}>
        <ScrollView
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.hero}>
            <View style={styles.iconWrap}>
              <Feather name="check" size={50} color="#15756E" />
            </View>

            <View style={styles.textBlock}>
              <Text style={styles.title}>Pedido criado!</Text>
              <Text style={styles.body}>
                Os profissionais da sua região já podem enviar propostas. Avisamos assim que a primeira chegar.
              </Text>
            </View>

            <View style={styles.statusBadge}>
              <View style={styles.statusDot} />
              <Text style={styles.statusText}>PENDENTE · AGUARDANDO PROPOSTAS</Text>
            </View>

            {summary && (
              <View style={styles.serviceCard}>
                <View style={[styles.serviceIconWrap, { backgroundColor: (CATEGORY_ICON[summary.categoria] ?? CATEGORY_ICON.Geral).bg }]}>
                  <Feather
                    name={(CATEGORY_ICON[summary.categoria] ?? CATEGORY_ICON.Geral).icon}
                    size={22}
                    color={(CATEGORY_ICON[summary.categoria] ?? CATEGORY_ICON.Geral).color}
                  />
                </View>
                <View style={styles.serviceInfo}>
                  <Text style={styles.serviceTitle} numberOfLines={1}>{summary.descricao}</Text>
                  <Text style={styles.serviceSubtitle}>{summary.categoria}</Text>
                </View>
              </View>
            )}
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => nav.navigate('CompareProposals', { requestId: route.params.requestId })}
            activeOpacity={0.85}
          >
            <Text style={styles.btnPrimaryText}>Ver meus pedidos</Text>
            <Feather name="arrow-right" size={18} color="#fff" />
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.bg },
  screen: { flex: 1, backgroundColor: C.bg },
  content: {
    flexGrow: 1,
    padding: 28,
    justifyContent: 'center',
    alignItems: 'center',
    gap: 22,
  },
  hero: {
    flex: 1,
    alignItems: 'center',
    gap: 22,
    width: '100%',
  },
  iconWrap: {
    width: 104,
    height: 104,
    borderRadius: 34,
    backgroundColor: '#DDF0EC',
    alignItems: 'center',
    justifyContent: 'center',
  },
  textBlock: {
    alignItems: 'center',
    gap: 12,
  },
  title: {
    fontSize: 26,
    fontWeight: '800',
    letterSpacing: -0.5,
    color: C.text,
    textAlign: 'center',
  },
  body: {
    fontSize: 16,
    lineHeight: 16 * 1.6,
    color: C.textSoft,
    textAlign: 'center',
    maxWidth: 300,
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    backgroundColor: '#F6EEDC',
    borderWidth: 1,
    borderColor: C.lineSoft,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 100,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: C.textFaint,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.6,
    color: C.textFaint,
  },
  serviceCard: {
    width: '100%',
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 24,
    padding: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
  },
  serviceIconWrap: {
    width: 46,
    height: 46,
    borderRadius: 12,
    backgroundColor: C.sunTint,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  serviceInfo: {
    flex: 1,
  },
  serviceTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: C.text,
  },
  serviceSubtitle: {
    fontSize: 13,
    color: C.textSoft,
    marginTop: 2,
  },
  footer: {
    padding: 14,
    paddingBottom: 20,
    backgroundColor: C.surface,
    borderTopWidth: 1,
    borderTopColor: C.lineSoft,
  },
  btnPrimary: {
    height: 56,
    borderRadius: 100,
    backgroundColor: C.primary,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    shadowColor: C.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.5,
    shadowRadius: 16,
    elevation: 6,
  },
  btnPrimaryText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#fff',
  },
});
