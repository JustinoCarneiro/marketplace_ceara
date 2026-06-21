import React, { useState } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ActivityIndicator } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import ScreenHeader from '../../components/ScreenHeader';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'PaymentPix'>;

export default function PaymentPixScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const [copied, setCopied] = useState(false);

  const pixKey = '00020126580014BR.GOV.BCB.PIX0136onda-marketplace-escrow520400005303986540';

  function copyPix() {
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScreenHeader title="Pagamento via PIX" onBack={() => nav.goBack()} />
      <View style={styles.content}>

        {/* QR Code placeholder */}
        <View style={styles.qrCard}>
          <View style={styles.qrPlaceholder}>
            <Text style={{ fontSize: 64 }}>⬛</Text>
            <Text style={styles.qrLabel}>QR Code PIX</Text>
          </View>
          <Text style={styles.valor}>
            R$ {route.params.valor.toFixed(2).replace('.', ',')}
          </Text>
          <Text style={styles.qrSub}>Escaneie com o app do seu banco</Text>
        </View>

        {/* Copia e cola */}
        <View style={styles.copySection}>
          <Text style={styles.copyLabel}>PIX COPIA E COLA</Text>
          <View style={styles.copyRow}>
            <Text style={styles.pixKey} numberOfLines={1}>{pixKey.substring(0, 30)}…</Text>
            <TouchableOpacity style={styles.copyBtn} onPress={copyPix}>
              <Text style={styles.copyBtnText}>{copied ? '✓ Copiado' : 'Copiar'}</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Instruções */}
        <View style={styles.steps}>
          {[
            'Abra o app do seu banco',
            'Acesse a área PIX',
            'Escaneie o QR Code ou use o Copia e Cola',
            'Confirme o pagamento de R$ ' + route.params.valor.toFixed(2).replace('.', ','),
          ].map((step, i) => (
            <View key={i} style={styles.stepRow}>
              <View style={styles.stepNum}><Text style={styles.stepNumText}>{i + 1}</Text></View>
              <Text style={styles.stepText}>{step}</Text>
            </View>
          ))}
        </View>

        {/* Status */}
        <View style={styles.waitBanner}>
          <ActivityIndicator color={color.institutional} size="small" />
          <Text style={styles.waitText}>Aguardando confirmação do pagamento…</Text>
        </View>

        <Button
          label="Já paguei — verificar"
          onPress={() => nav.navigate('EscrowConfirmed', { requestId: route.params.requestId })}
        />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { flex: 1, paddingHorizontal: space[5], paddingBottom: space[6], gap: space[4] },
  qrCard: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    alignItems: 'center',
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  qrPlaceholder: {
    width: 180,
    height: 180,
    backgroundColor: color.bgAlt,
    borderRadius: radius.field,
    alignItems: 'center',
    justifyContent: 'center',
    gap: space[2],
  },
  qrLabel: { fontSize: font.size.caption, color: color.textFaint },
  valor: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.primary },
  qrSub: { fontSize: font.size.caption, color: color.textSoft },
  copySection: { gap: space[2] },
  copyLabel: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  copyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    backgroundColor: color.surface,
    borderRadius: radius.field,
    borderWidth: 1,
    borderColor: color.line,
    padding: space[4],
  },
  pixKey: { flex: 1, fontSize: font.size.bodySm, color: color.textSoft, fontFamily: 'monospace' },
  copyBtn: {
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    paddingHorizontal: space[4],
    paddingVertical: space[2],
  },
  copyBtnText: { fontSize: font.size.caption, fontWeight: font.weight.bold, color: color.textOnAccent },
  steps: { gap: space[3] },
  stepRow: { flexDirection: 'row', alignItems: 'flex-start', gap: space[3] },
  stepNum: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: color.institutional,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  stepNumText: { fontSize: 12, fontWeight: font.weight.bold, color: color.textOnAccent },
  stepText: { flex: 1, fontSize: font.size.bodySm, color: color.text, lineHeight: font.size.bodySm * 1.6, paddingTop: 3 },
  waitBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[3],
    backgroundColor: color.skyTint,
    borderRadius: radius.field,
    padding: space[4],
  },
  waitText: { flex: 1, fontSize: font.size.bodySm, color: color.institutional, fontWeight: font.weight.medium },
});
