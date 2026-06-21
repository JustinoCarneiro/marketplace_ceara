import React, { useState } from 'react';
import { View, Text, StyleSheet, KeyboardAvoidingView, Platform, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';
import ScreenHeader from '../../components/ScreenHeader';
import Input from '../../components/Input';
import Button from '../../components/Button';

type RouteProps = RouteProp<ClientStackParams, 'PaymentCard'>;

export default function PaymentCardScreen() {
  const nav = useNavigation<ClientNavProp>();
  const route = useRoute<RouteProps>();
  const [numero, setNumero] = useState('');
  const [nome, setNome] = useState('');
  const [validade, setValidade] = useState('');
  const [cvv, setCvv] = useState('');
  const [loading, setLoading] = useState(false);

  function formatCard(v: string) {
    return v.replace(/\D/g, '').replace(/(.{4})/g, '$1 ').trim().substring(0, 19);
  }
  function formatValidade(v: string) {
    return v.replace(/\D/g, '').replace(/(\d{2})(\d)/, '$1/$2').substring(0, 5);
  }

  function pay() {
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      nav.navigate('EscrowConfirmed', { requestId: route.params.requestId });
    }, 1500);
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScreenHeader title="Cartão de crédito" onBack={() => nav.goBack()} />
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">

          {/* Card preview */}
          <View style={styles.cardPreview}>
            <Text style={styles.cardLogo}>💳</Text>
            <Text style={styles.cardNumber}>{numero || '•••• •••• •••• ••••'}</Text>
            <View style={styles.cardBottom}>
              <Text style={styles.cardHolder}>{nome || 'NOME NO CARTÃO'}</Text>
              <Text style={styles.cardExp}>{validade || 'MM/AA'}</Text>
            </View>
          </View>

          <View style={styles.form}>
            <Input
              label="Número do cartão"
              placeholder="0000 0000 0000 0000"
              value={numero}
              onChangeText={v => setNumero(formatCard(v))}
              keyboardType="number-pad"
              maxLength={19}
            />
            <Input
              label="Nome no cartão"
              placeholder="COMO ESTÁ NO CARTÃO"
              value={nome}
              onChangeText={v => setNome(v.toUpperCase())}
              autoCapitalize="characters"
            />
            <View style={styles.row}>
              <View style={{ flex: 1 }}>
                <Input
                  label="Validade"
                  placeholder="MM/AA"
                  value={validade}
                  onChangeText={v => setValidade(formatValidade(v))}
                  keyboardType="number-pad"
                  maxLength={5}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Input
                  label="CVV"
                  placeholder="•••"
                  value={cvv}
                  onChangeText={setCvv}
                  keyboardType="number-pad"
                  maxLength={4}
                  secure
                />
              </View>
            </View>
          </View>

          <View style={styles.secureRow}>
            <Text style={{ fontSize: 16 }}>🔒</Text>
            <Text style={styles.secureText}>Dados criptografados · Processado com segurança</Text>
          </View>

          <Button
            label={`Pagar R$ ${route.params.valor.toFixed(2).replace('.', ',')}`}
            onPress={pay}
            loading={loading}
          />
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingBottom: space[7], gap: space[4] },
  cardPreview: {
    backgroundColor: color.institutional,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[4],
    minHeight: 160,
    justifyContent: 'space-between',
    shadowColor: color.institutional,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.5,
    shadowRadius: 24,
    elevation: 6,
  },
  cardLogo: { fontSize: 28, alignSelf: 'flex-end' },
  cardNumber: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.textOnAccent, letterSpacing: 2 },
  cardBottom: { flexDirection: 'row', justifyContent: 'space-between' },
  cardHolder: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.accentSky },
  cardExp: { fontSize: font.size.bodySm, color: color.accentSky },
  form: { gap: space[4] },
  row: { flexDirection: 'row', gap: space[3] },
  secureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
    justifyContent: 'center',
  },
  secureText: { fontSize: font.size.caption, color: color.textFaint },
});
