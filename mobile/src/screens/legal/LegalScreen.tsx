/**
 * ⚠️  ALERTA LEGAL — RASCUNHO INTERNO
 * Este texto é um rascunho mínimo para uso em homologação/testes.
 * DEVE ser revisado e aprovado por advogado especializado em LGPD e
 * direito do consumidor ANTES do lançamento para usuários reais.
 * Ticket: alinhar com assessoria jurídica — termos e política de privacidade.
 */

import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation, useRoute, type RouteProp } from '@react-navigation/native';
import type { AuthStackParams } from '../../navigation/types';
import { color, font, space, radius } from '../../theme';

type LegalRoute = RouteProp<AuthStackParams, 'Legal'>;

const TERMS_CONTENT = {
  title: 'Termos de Uso',
  sections: [
    {
      heading: '1. Das Partes',
      body:
        'A plataforma Onda ("Onda", "nós") é operada por [razão social a definir], CNPJ [a definir], com sede no Ceará. ' +
        '"Cliente" é o usuário que contrata serviços. "Prestador" é o profissional que os executa.',
    },
    {
      heading: '2. Objeto',
      body:
        'A Onda é um marketplace de intermediação. Conectamos Clientes a Prestadores de serviços residenciais ' +
        'e gerenciamos o pagamento em custódia (escrow) até a conclusão do serviço. Não somos parte contratante ' +
        'do serviço prestado.',
    },
    {
      heading: '3. Cadastro',
      body:
        'O usuário deve ter no mínimo 18 anos, fornecer dados verídicos e manter as credenciais em sigilo. ' +
        'A Onda pode cancelar contas com dados falsos ou que violem estas regras.',
    },
    {
      heading: '4. Pagamento e Escrow',
      body:
        'O valor do serviço é retido pela Onda no ato da contratação e liberado ao Prestador após confirmação ' +
        'de conclusão pelo Cliente. Em caso de disputa, a Onda pode reter o valor até resolução. ' +
        'A comissão da plataforma é descontada no repasse.',
    },
    {
      heading: '5. Responsabilidades',
      body:
        'A Onda não garante a qualidade dos serviços, apenas facilita a conexão e a segurança do pagamento. ' +
        'O Prestador é responsável pela execução do serviço. O Cliente é responsável por fornecer acesso adequado.',
    },
    {
      heading: '6. Cancelamento',
      body:
        'Cancelamentos antes do início do serviço resultam em reembolso integral. Após o início, ' +
        'aplicam-se as regras de disputa previstas na plataforma.',
    },
    {
      heading: '7. Foro',
      body:
        'Fica eleito o foro da Comarca de Fortaleza/CE para dirimir quaisquer controvérsias decorrentes ' +
        'destes Termos.',
    },
  ],
};

const PRIVACY_CONTENT = {
  title: 'Política de Privacidade',
  sections: [
    {
      heading: '1. Dados Coletados',
      body:
        'Coletamos: nome completo, e-mail e senha (hash) para Clientes; nome, e-mail, CPF (criptografado) ' +
        'e dados de localização para Prestadores. Durante o uso, coletamos localização aproximada para ' +
        'geobusca de profissionais próximos.',
    },
    {
      heading: '2. Finalidade',
      body:
        'Os dados são usados exclusivamente para: autenticação, exibição de prestadores próximos, ' +
        'verificação de identidade do Prestador (background check assíncrono), e processamento de pagamentos. ' +
        'Nunca vendemos dados a terceiros.',
    },
    {
      heading: '3. Compartilhamento',
      body:
        'Dados mínimos são compartilhados com: gateway de pagamento (para processar transações) e ' +
        'serviço de verificação de identidade (CPF do Prestador). Todos sob contrato de sigilo.',
    },
    {
      heading: '4. Retenção',
      body:
        'Dados de conta são mantidos enquanto a conta estiver ativa. Após exclusão de conta, ' +
        'dados financeiros são retidos por 5 anos conforme obrigação legal. ' +
        'CPF é excluído após o período mínimo legal.',
    },
    {
      heading: '5. Seus Direitos (LGPD)',
      body:
        'Você pode a qualquer momento: acessar seus dados, corrigir dados incorretos, solicitar exclusão ' +
        'de dados não obrigatórios por lei, e revogar o consentimento. ' +
        'Solicitações pelo e-mail: privacidade@[domínio a definir].',
    },
    {
      heading: '6. Segurança',
      body:
        'CPF e dados sensíveis são criptografados em repouso. Comunicações via HTTPS/TLS. ' +
        'Acesso restrito por autenticação JWT com expiração curta e refresh token.',
    },
    {
      heading: '7. Contato',
      body: 'Encarregado de Dados (DPO): [nome a definir] — privacidade@[domínio a definir]',
    },
  ],
};

export default function LegalScreen() {
  const nav = useNavigation();
  const route = useRoute<LegalRoute>();
  const content = route.params.doc === 'terms' ? TERMS_CONTENT : PRIVACY_CONTENT;

  return (
    <SafeAreaView style={styles.safe}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => nav.goBack()} hitSlop={12}
          accessibilityLabel="Voltar" accessibilityRole="button">
          <Feather name="chevron-left" size={22} color={color.text} accessibilityElementsHidden />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{content.title}</Text>
        <View style={{ width: 22 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* Alerta de rascunho */}
        <View style={styles.draftBanner}>
          <Feather name="alert-triangle" size={15} color={color.warning} />
          <Text style={styles.draftText}>
            Rascunho interno — revisão jurídica pendente antes do lançamento.
          </Text>
        </View>

        {content.sections.map((s, i) => (
          <View key={i} style={styles.section}>
            <Text style={styles.sectionHeading}>{s.heading}</Text>
            <Text style={styles.sectionBody}>{s.body}</Text>
          </View>
        ))}

        <Text style={styles.lastUpdated}>Versão preliminar · Junho 2026</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },

  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: space[5],
    paddingVertical: space[4],
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
    backgroundColor: color.surface,
  },
  headerTitle: {
    fontSize: font.size.body,
    fontWeight: font.weight.bold,
    color: color.text,
  },

  scroll: { paddingHorizontal: space[5], paddingVertical: space[5], gap: space[5] },

  draftBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
    backgroundColor: '#FFF8E6',
    borderWidth: 1,
    borderColor: color.warning,
    borderRadius: radius.card,
    padding: space[4],
  },
  draftText: {
    flex: 1,
    fontSize: font.size.caption,
    color: '#7A5800',
    lineHeight: 18,
    fontWeight: font.weight.semibold,
  },

  section: { gap: 6 },
  sectionHeading: {
    fontSize: font.size.body,
    fontWeight: font.weight.bold,
    color: color.text,
  },
  sectionBody: {
    fontSize: font.size.bodySm,
    color: color.textSoft,
    lineHeight: font.size.bodySm * 1.6,
  },

  lastUpdated: {
    fontSize: font.size.caption,
    color: color.textFaint,
    textAlign: 'center',
    marginTop: space[3],
    paddingBottom: space[5],
  },
});
