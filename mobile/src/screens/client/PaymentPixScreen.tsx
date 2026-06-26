import React, { useState } from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { Feather } from '@expo/vector-icons';

type RouteProps = RouteProp<ClientStackParams, 'PaymentPix'>;

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
  warmSun: '#F2B015',
  sunTint: '#FDF3D6',
};

const PIX_KEY = '00020126360014BR.GOV.BCB.PIX0114+5585...';

export default function PaymentPixScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const [copied, setCopied] = useState(false);

  function copyPix() {
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  const valorFormatted = `R$ ${route.params.valor.toFixed(2).replace('.', ',')}`;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.screen}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => nav.goBack()} activeOpacity={0.7}>
            <Feather name="chevron-left" size={22} color={C.text} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Pagar com Pix</Text>
        </View>

        <ScrollView
          contentContainerStyle={styles.content}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.qrCard}>
            <Text style={styles.qrHint}>Escaneie o QR Code no seu banco</Text>

            <View style={styles.qrBox}>
              <View style={styles.qrCornerTL} />
              <View style={styles.qrCornerTR} />
              <View style={styles.qrCornerBL} />
              <View style={styles.qrPattern} />
            </View>

            <Text style={styles.valor}>{valorFormatted}</Text>
          </View>

          <TouchableOpacity style={styles.copyRow} onPress={copyPix} activeOpacity={0.7}>
            <Text style={styles.pixKeyText} numberOfLines={1}>{PIX_KEY}</Text>
            <View style={styles.copyBtn}>
              <Feather name="copy" size={15} color={C.primary} />
              <Text style={styles.copyBtnText}>{copied ? 'Copiado' : 'Copiar'}</Text>
            </View>
          </TouchableOpacity>

          <View style={styles.waitBanner}>
            <View style={styles.spinner} />
            <Text style={styles.waitText}>Aguardando confirmação do pagamento…</Text>
          </View>

          <View style={styles.secureRow}>
            <Feather name="shield" size={14} color={C.institutional2} />
            <Text style={styles.secureText}>O valor será retido com segurança após a confirmação.</Text>
          </View>
        </ScrollView>

        <View style={styles.footer}>
          <TouchableOpacity
            style={styles.btnPrimary}
            onPress={() => nav.navigate('EscrowConfirmed', { requestId: route.params.requestId })}
            activeOpacity={0.85}
          >
            <Text style={styles.btnPrimaryText}>Paguei</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.btnGhost}
            onPress={() => nav.goBack()}
            activeOpacity={0.7}
          >
            <Text style={styles.btnGhostText}>Cancelar</Text>
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
    fontSize: 21,
    fontWeight: '800',
    letterSpacing: -0.4,
    color: C.text,
  },
  content: {
    paddingHorizontal: 20,
    paddingBottom: 24,
    alignItems: 'center',
    gap: 18,
  },
  qrCard: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 24,
    padding: 22,
    alignItems: 'center',
    gap: 16,
    width: '100%',
    shadowColor: C.text,
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.12,
    shadowRadius: 20,
    elevation: 4,
  },
  qrHint: {
    fontSize: 13,
    color: C.textSoft,
  },
  qrBox: {
    width: 200,
    height: 200,
    backgroundColor: '#fff',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: C.lineSoft,
    padding: 14,
    position: 'relative',
    overflow: 'hidden',
  },
  qrPattern: {
    position: 'absolute',
    top: 14,
    left: 14,
    right: 14,
    bottom: 14,
    opacity: 0.92,
    borderRadius: 4,
    backgroundColor: '#fff',
    backgroundImage: undefined,
  },
  qrCornerTL: {
    position: 'absolute',
    top: 14,
    left: 14,
    width: 46,
    height: 46,
    borderRadius: 8,
    borderWidth: 7,
    borderColor: C.text,
    backgroundColor: '#fff',
    zIndex: 2,
  },
  qrCornerTR: {
    position: 'absolute',
    top: 14,
    right: 14,
    width: 46,
    height: 46,
    borderRadius: 8,
    borderWidth: 7,
    borderColor: C.text,
    backgroundColor: '#fff',
    zIndex: 2,
  },
  qrCornerBL: {
    position: 'absolute',
    bottom: 14,
    left: 14,
    width: 46,
    height: 46,
    borderRadius: 8,
    borderWidth: 7,
    borderColor: C.text,
    backgroundColor: '#fff',
    zIndex: 2,
  },
  valor: {
    fontSize: 24,
    fontWeight: '800',
    color: C.text,
  },
  copyRow: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 12,
    padding: 14,
  },
  pixKeyText: {
    flex: 1,
    fontSize: 12.5,
    color: C.textSoft,
    fontFamily: 'monospace',
  },
  copyBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
  },
  copyBtnText: {
    fontSize: 13,
    fontWeight: '700',
    color: C.primary,
  },
  waitBanner: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: C.sunTint,
    borderWidth: 1,
    borderColor: C.warmSun,
    borderRadius: 12,
    padding: 13,
    justifyContent: 'center',
  },
  spinner: {
    width: 14,
    height: 14,
    borderRadius: 7,
    borderWidth: 2.5,
    borderColor: C.warmSun,
    borderTopColor: 'transparent',
  },
  waitText: {
    fontSize: 13.5,
    fontWeight: '700',
    color: '#B5810A',
  },
  secureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  secureText: {
    fontSize: 12.5,
    color: C.institutional2,
  },
  footer: {
    padding: 14,
    paddingBottom: 20,
    backgroundColor: C.surface,
    borderTopWidth: 1,
    borderTopColor: C.lineSoft,
    gap: 10,
  },
  btnPrimary: {
    height: 56,
    borderRadius: 100,
    backgroundColor: C.primary,
    alignItems: 'center',
    justifyContent: 'center',
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
  btnGhost: {
    height: 52,
    borderRadius: 100,
    borderWidth: 1.5,
    borderColor: '#DCD2BC',
    alignItems: 'center',
    justifyContent: 'center',
  },
  btnGhostText: {
    fontSize: 15,
    fontWeight: '700',
    color: C.textSoft,
  },
});
