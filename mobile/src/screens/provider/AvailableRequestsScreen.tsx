import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, ActivityIndicator, RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ProviderNavProp } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';

interface ServiceRequest {
  id: string;
  categoria: string;
  descricao: string;
  status: string;
  clienteNome?: string;
  createdAt: string;
}

export default function AvailableRequestsScreen() {
  const nav = useNavigation<ProviderNavProp>();
  const token = useAuthStore(s => s.accessToken);
  const nome = useAuthStore(s => s.nome);
  const [requests, setRequests] = useState<ServiceRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  async function load() {
    try {
      const res = await fetch('http://10.0.2.2:8080/api/v1/service-requests?status=PENDENTE', {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await res.json();
      setRequests(Array.isArray(data) ? data : []);
    } catch {
      setRequests([]);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }

  useEffect(() => { load(); }, []);

  const firstName = nome?.split(' ')[0] ?? 'você';

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <View>
          <Text style={styles.greeting}>Olá, {firstName} 👷</Text>
          <Text style={styles.subtitle}>Chamados disponíveis na sua área</Text>
        </View>
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color={color.primary} size="large" />
        </View>
      ) : requests.length === 0 ? (
        <View style={styles.center}>
          <Text style={{ fontSize: 48 }}>📭</Text>
          <Text style={styles.emptyTitle}>Nenhum chamado disponível</Text>
          <Text style={styles.emptyBody}>Novos pedidos da sua região aparecerão aqui.</Text>
        </View>
      ) : (
        <FlatList
          data={requests}
          keyExtractor={i => i.id}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: space[3] }} />}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} colors={[color.primary]} />}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.card}
              onPress={() => nav.navigate('SendProposal', { requestId: item.id })}
              activeOpacity={0.85}
            >
              <View style={styles.cardTop}>
                <View style={styles.catChip}>
                  <Text style={styles.catText}>{item.categoria}</Text>
                </View>
                <Text style={styles.date}>{new Date(item.createdAt).toLocaleDateString('pt-BR')}</Text>
              </View>
              <Text style={styles.desc} numberOfLines={3}>{item.descricao}</Text>
              <View style={styles.cardFooter}>
                <Text style={styles.client}>👤 {item.clienteNome ?? 'Cliente'}</Text>
                <TouchableOpacity
                  style={styles.proposeBtn}
                  onPress={() => nav.navigate('SendProposal', { requestId: item.id })}
                >
                  <Text style={styles.proposeBtnText}>Enviar proposta →</Text>
                </TouchableOpacity>
              </View>
            </TouchableOpacity>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  header: {
    paddingHorizontal: space[5],
    paddingTop: space[4],
    paddingBottom: space[3],
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
  },
  greeting: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  subtitle: { fontSize: font.size.bodySm, color: color.textSoft, marginTop: 2 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: space[3], padding: space[5] },
  emptyTitle: { fontSize: font.size.h3, fontWeight: font.weight.bold, color: color.text },
  emptyBody: { fontSize: font.size.bodySm, color: color.textSoft, textAlign: 'center' },
  list: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7] },
  card: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    padding: space[5],
    gap: space[3],
    borderWidth: 1,
    borderColor: color.lineSoft,
    ...shadow.soft,
  },
  cardTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  catChip: {
    backgroundColor: color.primary + '18',
    borderRadius: radius.pill,
    paddingHorizontal: space[3],
    paddingVertical: 4,
  },
  catText: { fontSize: font.size.caption, fontWeight: font.weight.bold, color: color.primary },
  date: { fontSize: font.size.caption, color: color.textFaint },
  desc: { fontSize: font.size.body, color: color.text, lineHeight: font.size.body * font.lineHeight.body },
  cardFooter: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  client: { fontSize: font.size.caption, color: color.textSoft },
  proposeBtn: {
    backgroundColor: color.primary,
    borderRadius: radius.pill,
    paddingHorizontal: space[4],
    paddingVertical: space[2],
  },
  proposeBtnText: { fontSize: font.size.caption, fontWeight: font.weight.bold, color: color.textOnAccent },
});
