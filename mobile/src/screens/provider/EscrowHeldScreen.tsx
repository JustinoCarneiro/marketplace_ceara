import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';

const C = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  textFaint: '#8A989B',
  primary: '#14A8A0',
  institutional: '#0E3F52',
  institutional2: '#15596E',
  lineSoft: '#E6DDC9',
  accentSky: '#B7DCE3',
};

export default function EscrowHeldScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();

  const valor = route.params?.valor ?? 220;
  const clienteNome = route.params?.clienteNome ?? 'Lúcia Alves';
  const endereco = route.params?.endereco ?? 'Aldeota · 1,4 km';
  const prazo = route.params?.prazo ?? '2 dias';
  const requestId = route.params?.requestId;
  const chamadoId = route.params?.chamadoId ?? '1042';
  const serviceTitle = route.params?.serviceTitle ?? 'Troca de tomada';

  const valorFormatted = `R$ ${Number(valor).toFixed(2).replace('.', ',')}`;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.screen}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => nav.goBack()} activeOpacity={0.7}>
            <Feather name="chevron-left" size={22} color={C.text} />
          </TouchableOpacity>
          <View>
            <Text style={styles.headerTitle}>Chamado #{chamadoId}</Text>
            <Text style={styles.headerSubtitle}>{serviceTitle} · {clienteNome}</Text>
          </View>
        </View>

        <ScrollView
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}
        >
          {/* Dark escrow card */}
          <View style={styles.escrowCard}>
            <View style={styles.escrowCardTop}>
              <View style={styles.shieldIconWrap}>
                <Feather name="shield" size={26} color="#fff" />
              </View>
              <View style={styles.escrowCardTopText}>
                <Text style={styles.escrowCardTitle}>Pago e retido</Text>
                <Text style={styles.escrowCardSubtitle}>Pode iniciar sem risco</Text>
              </View>
            </View>

            <Text style={styles.escrowCardBody}>
              O valor já está <Text style={styles.escrowCardBodyBold}>retido na Onda</Text>. Quando o cliente confirmar a conclusão, ele é liberado para você.
            </Text>

            <View style={styles.retidoBadge}>
              <View style={styles.retidoDot} />
              <Text style={styles.retidoLabel}>RETIDO</Text>
              <Text style={styles.retidoValor}>{valorFormatted}</Text>
            </View>
          </View>

          {/* Service details */}
          <View style={styles.detailsCard}>
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Cliente</Text>
              <Text style={styles.detailValue}>{clienteNome}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Endereço</Text>
              <Text style={styles.detailValue}>{endereco}</Text>
            </View>
            <View style={styles.detailRow}>
              <Text style={styles.detailLabel}>Prazo combinado</Text>
              <Text style={styles.detailValue}>{prazo}</Text>
            </View>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => nav.navigate('RequestDetail', { requestId })}
            activeOpacity={0.85}
          >
            <Text style={styles.btnPrimaryText}>Iniciar serviço</Text>
            <Feather name="play" size={18} color="#fff" />
          </TouchableOpacity>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: C.bg },
  screen: { flex: 1, backgroundColor: C.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 20,
    paddingVertical: 8,
    paddingBottom: 16,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: C.text,
  },
  headerSubtitle: {
    fontSize: 13,
    color: C.textSoft,
    marginTop: 1,
  },
  content: {
    paddingHorizontal: 20,
    paddingBottom: 24,
    gap: 12,
  },
  escrowCard: {
    backgroundColor: C.institutional,
    borderRadius: 24,
    padding: 22,
    gap: 16,
  },
  escrowCardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  shieldIconWrap: {
    width: 52,
    height: 52,
    borderRadius: 12,
    backgroundColor: C.institutional2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  escrowCardTopText: {
    flex: 1,
  },
  escrowCardTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: '#fff',
  },
  escrowCardSubtitle: {
    fontSize: 13,
    color: C.accentSky,
    marginTop: 2,
  },
  escrowCardBody: {
    fontSize: 14.5,
    lineHeight: 14.5 * 1.55,
    color: C.accentSky,
  },
  escrowCardBodyBold: {
    color: '#fff',
    fontWeight: '700',
  },
  retidoBadge: {
    backgroundColor: C.institutional2,
    borderRadius: 12,
    padding: 14,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  retidoDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: C.accentSky,
  },
  retidoLabel: {
    flex: 1,
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.8,
    color: '#fff',
  },
  retidoValor: {
    fontSize: 20,
    fontWeight: '800',
    color: '#fff',
  },
  detailsCard: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 12,
    padding: 16,
    gap: 10,
  },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  detailLabel: {
    fontSize: 14,
    color: C.textSoft,
  },
  detailValue: {
    fontSize: 14,
    fontWeight: '700',
    color: C.text,
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
