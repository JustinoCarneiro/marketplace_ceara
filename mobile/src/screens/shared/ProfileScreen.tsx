import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import Button from '../../components/Button';

export default function ProfileScreen() {
  const { nome, email, role, logout } = useAuthStore(s => ({
    nome: s.nome,
    email: s.email,
    role: s.role,
    logout: s.logout,
  }));

  const isProvider = role === 'ROLE_PROVIDER';

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        {/* Avatar */}
        <View style={styles.header}>
          <View style={styles.avatar}>
            <Text style={{ fontSize: 40 }}>{isProvider ? '👷' : '👤'}</Text>
          </View>
          <Text style={styles.name}>{nome ?? '—'}</Text>
          <Text style={styles.email}>{email ?? '—'}</Text>
          <View style={styles.roleBadge}>
            <Text style={styles.roleText}>{isProvider ? 'Prestador' : 'Cliente'}</Text>
          </View>
        </View>

        {/* Menu */}
        <View style={styles.menu}>
          {[
            { icon: '📋', label: 'Histórico de serviços' },
            { icon: '🔔', label: 'Notificações' },
            { icon: '🔒', label: 'Privacidade e dados (LGPD)' },
            { icon: '❓', label: 'Ajuda e suporte' },
            { icon: '📄', label: 'Termos de uso' },
          ].map((item, i) => (
            <TouchableOpacity key={i} style={styles.menuItem} activeOpacity={0.7}>
              <Text style={{ fontSize: 20 }}>{item.icon}</Text>
              <Text style={styles.menuLabel}>{item.label}</Text>
              <Text style={styles.chevron}>›</Text>
            </TouchableOpacity>
          ))}
        </View>

        {/* Versão */}
        <Text style={styles.version}>Onda · v1.0.0</Text>

        <Button label="Sair da conta" variant="outline" onPress={logout} />
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: color.bg },
  content: { paddingHorizontal: space[5], paddingTop: space[4], paddingBottom: space[7], gap: space[5] },
  header: { alignItems: 'center', gap: space[3], paddingTop: space[4] },
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 3,
    borderColor: color.primary,
  },
  name: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  email: { fontSize: font.size.bodySm, color: color.textSoft },
  roleBadge: {
    backgroundColor: color.primary + '18',
    borderRadius: radius.pill,
    paddingHorizontal: space[4],
    paddingVertical: space[1] + 2,
  },
  roleText: { fontSize: font.size.bodySm, fontWeight: font.weight.bold, color: color.primary },
  menu: {
    backgroundColor: color.surface,
    borderRadius: radius.card,
    borderWidth: 1,
    borderColor: color.lineSoft,
    overflow: 'hidden',
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[4],
    padding: space[4],
    paddingHorizontal: space[5],
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
  },
  menuLabel: { flex: 1, fontSize: font.size.body, color: color.text },
  chevron: { fontSize: 20, color: color.textFaint },
  version: { fontSize: font.size.caption, color: color.textFaint, textAlign: 'center' },
});
