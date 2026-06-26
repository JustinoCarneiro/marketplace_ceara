import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { Feather } from '@expo/vector-icons';

const COLORS = {
  bg: '#F3ECDC',
  surface: '#FCF8EE',
  text: '#0E2A33',
  textSoft: '#4C636A',
  primary: '#14A8A0',
  lineSoft: '#E6DDC9',
  concluido: '#15756E',
  concluidoBg: '#DDF0EC',
  warmSun: '#F2B015',
};

export default function RateConfirmScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const { nota } = route.params;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <View style={styles.body}>
          <View style={styles.starsRow}>
            {[1, 2, 3, 4, 5].map(i => (
              <Feather
                key={i}
                name="star"
                size={30}
                color={i <= (nota ?? 5) ? COLORS.warmSun : '#DCD2BC'}
                fill={i <= (nota ?? 5) ? COLORS.warmSun : 'none'}
              />
            ))}
          </View>

          <View style={styles.checkCircle}>
            <Feather name="check" size={50} color={COLORS.concluido} />
          </View>

          <View style={styles.textBlock}>
            <Text style={styles.title}>Obrigado!</Text>
            <Text style={styles.bodyText}>
              Sua avaliação ajuda toda a comunidade a encontrar bons profissionais.
            </Text>
          </View>
        </View>

        <TouchableOpacity
          style={styles.homeBtn}
          onPress={() => nav.popToTop()}
          activeOpacity={0.85}
        >
          <Text style={styles.homeBtnText}>Voltar ao início</Text>
        </TouchableOpacity>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: COLORS.bg },
  content: {
    flex: 1,
    paddingHorizontal: 28,
    paddingTop: 28,
    paddingBottom: 28,
    flexDirection: 'column',
  },
  body: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 22,
  },
  starsRow: {
    flexDirection: 'row',
    gap: 4,
  },
  checkCircle: {
    width: 104,
    height: 104,
    borderRadius: 34,
    backgroundColor: COLORS.concluidoBg,
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
    color: COLORS.text,
  },
  bodyText: {
    fontSize: 16,
    lineHeight: 25.6,
    color: COLORS.textSoft,
    textAlign: 'center',
    maxWidth: 300,
  },
  homeBtn: {
    width: '100%',
    height: 56,
    borderRadius: 100,
    backgroundColor: COLORS.primary,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: COLORS.primary,
    shadowOffset: { width: 0, height: 16 },
    shadowOpacity: 0.85,
    shadowRadius: 26,
    elevation: 6,
  },
  homeBtnText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#fff',
  },
});
