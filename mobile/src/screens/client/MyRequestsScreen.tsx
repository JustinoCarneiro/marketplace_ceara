import { API_BASE } from '../../api/config';
import React, { useEffect, useState } from 'react';
import {
  View, Text, StyleSheet, FlatList,
  TouchableOpacity, ActivityIndicator, RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import type { ClientNavProp } from '../../navigation/types';
import { color, font, space, radius, shadow } from '../../theme';
import { useAuthStore } from '../../store/auth';
import StatusBadge from '../../components/StatusBadge';

interface Request {
  id: string;
  categoria: string;
  descricao: string;
  status: string;
  updatedAt: string;
}

export default function MyRequestsScreen() {
  const nav = useNavigation<ClientNavProp>();
  const token = useAuthStore(s => s.accessToken);
  const [requests, setRequests] = useState<Request[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  async function load() {
    try {
      const res = await fetch('${API_BASE}/service-requests/my', {
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

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.header}>
        <Text style={styles.title}>Meus pedidos</Text>
      </View>

      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color={color.primary} size="large" />
        </View>
      ) : requests.length === 0 ? (
        <View style={styles.center}>
          <Text style={{ fontSize: 48 }}>📋</Text>
          <Text style={styles.emptyTitle}>Nenhum pedido ainda</Text>
          <Text style={styles.emptyBody}>Seus chamados aparecerão aqui.</Text>
        </View>
      ) : (
        <FlatList
          data={requests}
          keyExtractor={r => r.id}
          contentContainerStyle={styles.list}
          ItemSeparatorComponent={() => <View style={{ height: space[3] }} />}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => { setRefreshing(true); load(); }} colors={[color.primary]} />}
          renderItem={({ item }) => (
            <TouchableOpacity
              style={styles.card}
              onPress={() => nav.navigate('RequestDetail', { requestId: item.id })}
              activeOpacity={0.85}
            >
              <View style={styles.cardTop}>
                <Text style={styles.categoria}>{item.categoria}</Text>
                <StatusBadge status={item.status as any} size="sm" />
              </View>
              <Text style={styles.desc} numberOfLines={2}>{item.descricao}</Text>
              <Text style={styles.date}>{new Date(item.updatedAt).toLocaleDateString('pt-BR')}</Text>
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
  title: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
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
  categoria: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.text },
  desc: { fontSize: font.size.bodySm, color: color.textSoft, lineHeight: font.size.bodySm * 1.6 },
  date: { fontSize: font.size.caption, color: color.textFaint },
});
