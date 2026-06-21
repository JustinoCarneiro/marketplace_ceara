import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import { color, font, space, radius } from '../../theme';
import Button from '../../components/Button';
import StarRating from '../../components/StarRating';

export default function RateConfirmScreen() {
  const nav = useNavigation<any>();
  const route = useRoute<any>();
  const { nota, comentario } = route.params;

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.content}>
        <View style={styles.card}>
          <Text style={{ fontSize: 56 }}>⭐</Text>
          <Text style={styles.title}>Avaliação enviada!</Text>
          <Text style={styles.body}>
            Obrigado pelo feedback. Sua avaliação ajuda a comunidade a encontrar os melhores profissionais.
          </Text>

          <View style={styles.divider} />

          <StarRating value={nota} size={28} readonly />
          {comentario ? (
            <Text style={styles.comentario}>"{comentario}"</Text>
          ) : null}
        </View>

        <Button label="Voltar ao início" onPress={() => nav.popToTop()} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: {
    flex: 1,
    paddingHorizontal: space[5],
    paddingTop: space[7],
    paddingBottom: space[6],
    gap: space[4],
    justifyContent: 'space-between',
  },
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[6],
    alignItems: 'center',
    gap: space[4],
    borderWidth: 1,
    borderColor: color.lineSoft,
  },
  title: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.text },
  body: { fontSize: font.size.body, color: color.textSoft, textAlign: 'center', lineHeight: font.size.body * font.lineHeight.body },
  divider: { width: '100%', height: 1, backgroundColor: color.lineSoft },
  comentario: { fontSize: font.size.bodySm, color: color.textSoft, fontStyle: 'italic', textAlign: 'center' },
});
