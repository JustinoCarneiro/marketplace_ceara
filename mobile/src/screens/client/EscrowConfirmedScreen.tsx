import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';

type RouteProps = RouteProp<ClientStackParams, 'EscrowConfirmed'>;

export default function EscrowConfirmedScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.container}>
        <View style={styles.center}>
          {/* Shield icon */}
          <View style={styles.shieldWrap}>
            <Feather name="shield" size={52} color={color.textOnAccent} />
            <View style={styles.checkBadge}>
              <Feather name="check" size={12} color={color.textOnAccent} />
            </View>
          </View>

          {/* Heading + body */}
          <View style={styles.textBlock}>
            <Text style={styles.heading}>Pagamento retido com segurança</Text>
            <Text style={styles.body}>
              Seu dinheiro fica retido na Onda e só é liberado para o profissional quando você{' '}
              <Text style={styles.bodyBold}>confirmar a conclusão</Text> do serviço.
            </Text>
          </View>

          {/* Status chips */}
          <View style={styles.chipsRow}>
            <View style={styles.chipGhost}>
              <View style={styles.dot} />
              <Text style={styles.chipGhostText}>PEDIDO ACEITO</Text>
            </View>
            <View style={styles.chipWhite}>
              <Feather name="lock" size={13} color={color.institutional} />
              <Text style={styles.chipWhiteText}>R$ 242 RETIDO</Text>
            </View>
          </View>

          {/* Provider mini card */}
          <View style={styles.providerCard}>
            <View style={styles.providerAvatar}>
              <Text style={styles.providerAvatarText}>JW</Text>
            </View>
            <View style={styles.providerInfo}>
              <Text style={styles.providerName}>José Wagner</Text>
              <Text style={styles.providerSub}>Instalação elétrica · prazo 2 dias</Text>
            </View>
          </View>
        </View>

        {/* CTA */}
        <TouchableOpacity
          style={styles.cta}
          onPress={() => nav.navigate('RequestDetail', { requestId: route.params.requestId })}
          activeOpacity={0.85}
        >
          <Text style={styles.ctaText}>Acompanhar pedido</Text>
          <Feather name="arrow-right" size={18} color={color.textOnAccent} />
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.institutional },
  container: { flex: 1, paddingHorizontal: space[5], paddingVertical: space[5] },

  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 22 },

  shieldWrap: {
    width: 104,
    height: 104,
    borderRadius: 34,
    backgroundColor: color.institutional2,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkBadge: {
    position: 'absolute',
    bottom: 14,
    right: 14,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: color.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },

  textBlock: { alignItems: 'center', gap: space[3] },
  heading: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.textOnAccent,
    textAlign: 'center',
    letterSpacing: -0.025 * font.size.h1,
    lineHeight: font.size.h1 * 1.1,
  },
  body: {
    fontSize: font.size.body,
    color: '#B7DCE3',
    textAlign: 'center',
    lineHeight: font.size.body * 1.6,
    maxWidth: 300,
  },
  bodyBold: { color: color.textOnAccent, fontWeight: font.weight.bold },

  chipsRow: { flexDirection: 'row', gap: space[3] },
  chipGhost: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: 'rgba(255,255,255,0.1)',
    borderRadius: radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#B7DCE3' },
  chipGhostText: { fontSize: 12, fontWeight: font.weight.black, color: color.textOnAccent, letterSpacing: 0.5 },
  chipWhite: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: color.textOnAccent,
    borderRadius: radius.pill,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  chipWhiteText: { fontSize: 12, fontWeight: font.weight.black, color: color.institutional, letterSpacing: 0.5 },

  providerCard: {
    width: '100%',
    backgroundColor: color.institutional2,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
    padding: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
  },
  providerAvatar: {
    width: 44,
    height: 44,
    borderRadius: 13,
    backgroundColor: color.warmTerra,
    alignItems: 'center',
    justifyContent: 'center',
  },
  providerAvatarText: { fontSize: 15, fontWeight: font.weight.black, color: color.textOnAccent },
  providerInfo: { flex: 1 },
  providerName: { fontSize: 15, fontWeight: font.weight.bold, color: color.textOnAccent },
  providerSub: { fontSize: font.size.caption, color: '#B7DCE3', marginTop: 2 },

  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[3],
    shadowColor: color.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.6,
    shadowRadius: 24,
    elevation: 6,
  },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },
});
