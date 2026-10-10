import { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, ScrollView, TextInput,
  TouchableOpacity, ActivityIndicator, KeyboardAvoidingView, Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ParamListBase } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Feather } from '@expo/vector-icons';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import { excluirConta } from '../../api/deleteAccount';

/**
 * Exclusão de conta (US36, LGPD art. 18, VI — também exigida pela App Store e pela Play Store).
 * A senha confirma o pedido. O servidor recusa com a mensagem do que resolver quando há pedido em
 * andamento ou pagamento a receber/reembolsar; a tela só a mostra.
 *
 * Depois de excluída, a sessão já não vale no servidor: a tela fica na confirmação até o usuário
 * tocar em "Concluir" (ou voltar), que encerra a sessão local e leva ao início.
 */
export default function DeleteAccountScreen() {
  const nav = useNavigation<NativeStackNavigationProp<ParamListBase>>();
  const token = useAuthStore(s => s.accessToken);
  const papeis = useAuthStore(s => s.papeis);
  const logout = useAuthStore(s => s.logout);
  // a exclusão é da CONTA, que pode ter os dois papéis: o texto vale para a conta, não para o modo em uso
  const isProvider = papeis.includes('ROLE_PROVIDER');

  const [senha, setSenha] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  // Excluída, voltar levaria a uma sessão morta (a conta já não existe): voltar = concluir. Já deslogado,
  // deixa sair — senão o próprio logout (que remove esta tela) ficaria preso aqui.
  useEffect(() => {
    if (!done) return;
    return nav.addListener('beforeRemove', e => {
      if (useAuthStore.getState().accessToken === null) return;
      e.preventDefault();
      logout();
    });
  }, [done, nav, logout]);

  async function excluir() {
    if (!senha) {
      setError('Digite sua senha para confirmar.');
      return;
    }
    setError('');
    setLoading(true);
    const resultado = await excluirConta(token ?? '', senha);
    setLoading(false);
    if (!resultado.ok) {
      setError(resultado.mensagem);
      return;
    }
    setSenha('');
    setDone(true);
  }

  if (done) {
    return (
      <SafeAreaView style={styles.safe}>
        <View testID="conta-excluida" style={styles.doneWrap}>
          <View style={styles.doneIcon}>
            <Feather name="check" size={34} color={color.success} accessibilityElementsHidden />
          </View>
          <Text style={styles.doneTitle}>Conta excluída</Text>
          <Text style={styles.doneText}>
            Seu nome e e-mail saíram de tudo que você usava no app. O histórico de pagamentos e avaliações
            fica, sem o seu nome. Obrigado por ter usado o Onda.
          </Text>
          <TouchableOpacity testID="btn-concluir-exclusao" style={styles.cta} onPress={logout} activeOpacity={0.85}>
            <Text style={styles.ctaText}>Concluir</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>

          <TouchableOpacity onPress={() => nav.goBack()} hitSlop={14} style={styles.back}
            accessibilityLabel="Voltar" accessibilityRole="button">
            <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
          </TouchableOpacity>

          <Text style={styles.title}>Excluir conta</Text>
          <Text style={styles.subtitle}>Veja o que acontece com os seus dados antes de confirmar.</Text>

          <View style={styles.card}>
            <Text style={styles.item}>
              Seus dados pessoais (nome, e-mail, CPF, localização, fotos e áudios dos pedidos) são apagados.
            </Text>
            {isProvider && (
              <Text style={styles.item}>Seu perfil de prestador e sua chave Pix também são apagados.</Text>
            )}
            <Text style={styles.item}>
              {isProvider
                ? 'Suas propostas abertas são encerradas e os pedidos seus que ainda não têm serviço contratado são cancelados.'
                : 'Pedidos que ainda não têm serviço contratado são cancelados.'}
            </Text>
            <Text style={styles.item}>
              Suas mensagens do chat e os comentários que você escreveu nas avaliações são removidos.
            </Text>
            <Text style={styles.item}>
              O histórico de pagamentos e as notas das avaliações ficam, sem o seu nome. Por exigência legal,
              o registro de aceite dos termos (com o IP de quando você aceitou) e eventuais alertas de
              segurança ou denúncias também continuam.
            </Text>
            <Text style={styles.item}>
              Não dá para desfazer. Depois, você pode criar uma nova conta com o mesmo e-mail.
            </Text>
          </View>

          <View style={styles.notice}>
            <Feather name="info" size={16} color={color.institutional2} accessibilityElementsHidden />
            <Text style={styles.noticeText}>
              {isProvider
                ? 'Serviços em andamento (como prestador ou como cliente), repasse ainda por receber ou reembolso a caminho precisam ser concluídos antes — senão o dinheiro ficaria sem destino.'
                : 'Pedidos em andamento ou reembolso ainda a caminho precisam ser concluídos antes.'}
            </Text>
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>CONFIRME COM SUA SENHA</Text>
            <View style={styles.passWrap}>
              <TextInput
                testID="input-senha-exclusao"
                style={[styles.input, styles.passInput]}
                value={senha}
                onChangeText={t => { setSenha(t); setError(''); }}
                placeholder="sua senha atual"
                placeholderTextColor={color.textFaint}
                secureTextEntry={!showPass}
                autoComplete="current-password"
                autoCapitalize="none"
                autoCorrect={false}
                maxLength={256}
              />
              <TouchableOpacity
                style={styles.eyeBtn}
                onPress={() => setShowPass(v => !v)}
                hitSlop={8}
                accessibilityLabel={showPass ? 'Ocultar senha' : 'Mostrar senha'}
                accessibilityRole="button"
              >
                <Feather name={showPass ? 'eye-off' : 'eye'} size={18} color={color.textFaint} accessibilityElementsHidden />
              </TouchableOpacity>
            </View>
          </View>

          {error ? (
            <View style={styles.errorRow}>
              <Feather name="alert-circle" size={14} color={color.danger} accessibilityElementsHidden />
              <Text testID="erro-exclusao" accessibilityRole="alert" style={styles.errorText}>{error}</Text>
            </View>
          ) : null}

          <TouchableOpacity
            testID="btn-excluir-conta"
            style={[styles.cta, styles.ctaDanger, loading && { opacity: 0.7 }]}
            onPress={excluir}
            disabled={loading}
            activeOpacity={0.85}
          >
            {loading ? <ActivityIndicator color={color.textOnAccent} /> : <Text style={styles.ctaText}>Excluir minha conta</Text>}
          </TouchableOpacity>

          <TouchableOpacity onPress={() => nav.goBack()} style={styles.cancelar} hitSlop={8}>
            <Text style={styles.cancelarText}>Cancelar</Text>
          </TouchableOpacity>

        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  scroll: { flexGrow: 1, paddingHorizontal: space[5], paddingTop: space[5], paddingBottom: space[4] },

  back: { paddingVertical: space[3] },
  title: {
    fontSize: font.size.h1,
    fontWeight: font.weight.black,
    color: color.text,
    letterSpacing: -0.025 * font.size.h1,
    marginTop: space[2],
  },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft, marginTop: space[2], marginBottom: space[5], lineHeight: 20 },

  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    padding: space[5],
    gap: space[3],
  },
  item: { fontSize: font.size.bodySm, color: color.text, lineHeight: 20 },

  notice: { flexDirection: 'row', alignItems: 'flex-start', gap: space[2], marginTop: space[4], marginBottom: space[5] },
  noticeText: { flex: 1, fontSize: font.size.caption, color: color.textSoft, lineHeight: 18 },

  field: { gap: 7 },
  label: { fontSize: font.size.eyebrow, fontWeight: font.weight.semibold, color: color.institutional2, letterSpacing: 0.1 * font.size.eyebrow },
  input: {
    backgroundColor: color.surface,
    borderWidth: 1,
    borderColor: color.lineSoft,
    borderRadius: radius.field,
    paddingHorizontal: space[4],
    paddingVertical: 14,
    fontSize: 15,
    color: color.text,
  },
  passWrap: { position: 'relative' },
  passInput: { paddingRight: 48 },
  eyeBtn: { position: 'absolute', right: 14, top: 0, bottom: 0, justifyContent: 'center' },

  errorRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: space[4] },
  errorText: { fontSize: font.size.caption, color: color.danger, textAlign: 'center', flexShrink: 1 },

  cta: {
    height: 56,
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: space[5],
    alignSelf: 'stretch',
  },
  ctaDanger: { backgroundColor: color.danger },
  ctaText: { fontSize: font.size.body, fontWeight: font.weight.bold, color: color.textOnAccent },

  cancelar: { alignItems: 'center', paddingVertical: space[4] },
  cancelarText: { fontSize: font.size.bodySm, fontWeight: font.weight.semibold, color: color.primaryInk },

  doneWrap: { flex: 1, paddingHorizontal: space[5], alignItems: 'center', justifyContent: 'center', gap: space[3] },
  doneIcon: {
    width: 72, height: 72, borderRadius: 36, alignItems: 'center', justifyContent: 'center',
    backgroundColor: color.skyTint,
  },
  doneTitle: { fontSize: font.size.h1, fontWeight: font.weight.black, color: color.text },
  doneText: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center', lineHeight: 20, marginBottom: space[3] },
});
