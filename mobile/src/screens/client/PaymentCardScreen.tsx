import React, { useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TouchableOpacity,
  TextInput, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { ClientNavProp, ClientStackParams } from '../../navigation/types';
import { Feather } from '@expo/vector-icons';

type RouteProps = RouteProp<ClientStackParams, 'PaymentCard'>;

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

  const displayNumber = numero || '5102  ••••  ••••  4821';
  const displayNome = nome || 'LÚCIA M ALVES';
  const displayVal = validade || '08/29';

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <View style={styles.screen}>
          <View style={styles.header}>
            <TouchableOpacity onPress={() => nav.goBack()} activeOpacity={0.7}>
              <Feather name="chevron-left" size={22} color={C.text} />
            </TouchableOpacity>
            <Text style={styles.headerTitle}>Cartão de crédito</Text>
          </View>

          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {/* Card preview */}
            <View style={styles.cardPreview}>
              <View style={styles.cardTop}>
                <View style={styles.chip} />
                <View style={styles.brandCircles}>
                  <View style={[styles.circle, { backgroundColor: '#DA6A32', opacity: 0.9, marginRight: -10 }]} />
                  <View style={[styles.circle, { backgroundColor: '#F2B015', opacity: 0.75 }]} />
                </View>
              </View>
              <Text style={styles.cardNumber}>{displayNumber}</Text>
              <View style={styles.cardBottom}>
                <View>
                  <Text style={styles.cardLabel}>TITULAR</Text>
                  <Text style={styles.cardValue}>{displayNome}</Text>
                </View>
                <View>
                  <Text style={styles.cardLabel}>VALIDADE</Text>
                  <Text style={styles.cardValue}>{displayVal}</Text>
                </View>
              </View>
            </View>

            {/* Form fields */}
            <View style={styles.form}>
              <View style={styles.field}>
                <Text style={styles.fieldLabel}>NÚMERO DO CARTÃO</Text>
                <TextInput
                  style={styles.fieldInput}
                  value={numero}
                  onChangeText={v => setNumero(formatCard(v))}
                  placeholder="5102 4830 1192 4821"
                  placeholderTextColor={C.textFaint}
                  keyboardType="number-pad"
                  maxLength={19}
                />
              </View>

              <View style={styles.row}>
                <View style={[styles.field, { flex: 1 }]}>
                  <Text style={styles.fieldLabel}>VALIDADE</Text>
                  <TextInput
                    style={styles.fieldInput}
                    value={validade}
                    onChangeText={v => setValidade(formatValidade(v))}
                    placeholder="08/29"
                    placeholderTextColor={C.textFaint}
                    keyboardType="number-pad"
                    maxLength={5}
                  />
                </View>
                <View style={[styles.field, { flex: 1 }]}>
                  <Text style={styles.fieldLabel}>CVV</Text>
                  <TextInput
                    style={styles.fieldInput}
                    value={cvv}
                    onChangeText={setCvv}
                    placeholder="•••"
                    placeholderTextColor={C.textFaint}
                    keyboardType="number-pad"
                    maxLength={4}
                    secureTextEntry
                  />
                </View>
              </View>
            </View>
          </ScrollView>

          <View style={styles.footer}>
            <TouchableOpacity
              style={[styles.btnPrimary, loading && styles.btnLoading]}
              onPress={pay}
              activeOpacity={0.85}
              disabled={loading}
            >
              {loading && <View style={styles.spinner} />}
              <Text style={styles.btnPrimaryText}>
                {loading
                  ? 'Processando pagamento…'
                  : `Pagar R$ ${route.params.valor.toFixed(2).replace('.', ',')}`}
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>
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
    gap: 14,
  },
  cardPreview: {
    backgroundColor: C.institutional,
    borderRadius: 24,
    padding: 22,
    gap: 24,
    shadowColor: C.institutional,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 8,
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  chip: {
    width: 40,
    height: 28,
    borderRadius: 6,
    backgroundColor: '#F2B015',
    opacity: 0.9,
  },
  brandCircles: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  circle: {
    width: 28,
    height: 28,
    borderRadius: 14,
  },
  cardNumber: {
    fontSize: 18,
    fontWeight: '700',
    letterSpacing: 2,
    color: '#fff',
    fontFamily: 'monospace',
  },
  cardBottom: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  cardLabel: {
    fontSize: 12,
    color: C.accentSky,
    letterSpacing: 1,
  },
  cardValue: {
    fontSize: 13,
    fontWeight: '600',
    color: '#fff',
    marginTop: 2,
  },
  form: {
    gap: 14,
  },
  field: {
    gap: 7,
  },
  fieldLabel: {
    fontSize: 12,
    fontWeight: '600',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: C.institutional2,
  },
  fieldInput: {
    backgroundColor: C.surface,
    borderWidth: 1,
    borderColor: C.lineSoft,
    borderRadius: 12,
    padding: 14,
    paddingHorizontal: 16,
    fontSize: 15,
    color: C.text,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
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
  btnLoading: {
    opacity: 0.85,
  },
  spinner: {
    width: 16,
    height: 16,
    borderRadius: 8,
    borderWidth: 2.5,
    borderColor: '#fff',
    borderTopColor: 'transparent',
  },
  btnPrimaryText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#fff',
  },
});
