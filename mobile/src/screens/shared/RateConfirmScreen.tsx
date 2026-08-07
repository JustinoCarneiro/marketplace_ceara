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
  textFaint: '#8A989B',
  primary: '#14A8A0',
  institutional2: '#15596E',
  lineSoft: '#E6DDC9',
  concluido: '#15756E',
  concluidoBg: '#DDF0EC',
  skyTint: '#E2EEF2',
  skyLine: '#C8DDE4',
  warmSun: '#F2B015',
};

function formatarPrazo(iso?: string | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'long' });
}

export default function RateConfirmScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const { nota, revelada, prazoRevelacao, avaliadoNome } = route.params;

  const prazo = formatarPrazo(prazoRevelacao);
  const outraParte = typeof avaliadoNome === 'string' && avaliadoNome.trim()
    ? avaliadoNome.split(' ')[0]
    : 'a outra parte';

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <View style={styles.body}>
          {/* Glifo ★ (mesmo da RateScreen): o ícone "star" do Feather é um contorno e
              não tem preenchimento — a prop `fill` não existe em ícone de fonte, então
              a nota do próprio usuário aparecia como 5 estrelas vazias. */}
          <View
            style={styles.starsRow}
            accessibilityLabel={`Você deu ${nota ?? 5} de 5 estrelas`}
          >
            {[1, 2, 3, 4, 5].map(i => (
              <Text
                key={i}
                style={[styles.star, { color: i <= (nota ?? 5) ? COLORS.warmSun : '#DCD2BC' }]}
                accessibilityElementsHidden
              >
                ★
              </Text>
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

          {/* Double-blind: as notas só aparecem quando as duas partes avaliam —
              ninguém escreve olhando o que o outro escreveu. */}
          <View style={styles.revealCard} accessibilityLiveRegion="polite">
            <View style={styles.revealHeader}>
              <Feather
                name={revelada ? 'eye' : 'eye-off'}
                size={15}
                color={COLORS.institutional2}
                accessibilityElementsHidden
              />
              <Text style={styles.revealHeaderText}>
                {revelada ? 'Avaliações publicadas' : 'Aguardando a outra avaliação'}
              </Text>
            </View>
            <Text style={styles.revealBody}>
              {revelada
                ? `${outraParte} também avaliou — as duas avaliações ficaram visíveis ao mesmo tempo.`
                : `Sua nota fica visível quando ${outraParte} também avaliar${prazo ? `, ou em ${prazo}` : ''}.`}
            </Text>
            {!revelada && (
              <Text style={styles.revealFootnote}>
                Nenhuma das partes vê a nota da outra antes de avaliar.
              </Text>
            )}
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
  star: {
    fontSize: 30,
    lineHeight: 34,
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
  revealCard: {
    width: '100%',
    backgroundColor: COLORS.skyTint,
    borderWidth: 1,
    borderColor: COLORS.skyLine,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    gap: 6,
  },
  revealHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
  },
  revealHeaderText: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.3,
    textTransform: 'uppercase',
    color: COLORS.institutional2,
  },
  revealBody: {
    fontSize: 13.5,
    lineHeight: 13.5 * 1.5,
    color: COLORS.textSoft,
  },
  revealFootnote: {
    fontSize: 12,
    lineHeight: 12 * 1.45,
    color: COLORS.textFaint,
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
