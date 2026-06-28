import React from 'react';
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Linking, Alert } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Feather } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { color, font, space, radius } from '../../theme';
import { useAuthStore } from '../../store/auth';
import Button from '../../components/Button';

type FeatherName = React.ComponentProps<typeof Feather>['name'];

function initials(nome: string) {
  return nome.split(' ').slice(0, 2).map(n => n[0]).join('').toUpperCase();
}

export default function ProfileScreen() {
  const nav    = useNavigation<any>();
  const nome   = useAuthStore(s => s.nome);
  const email  = useAuthStore(s => s.email);
  const role   = useAuthStore(s => s.role);
  const logout = useAuthStore(s => s.logout);

  const isProvider = role === 'ROLE_PROVIDER';

  const MENU_ITEMS: { icon: FeatherName; label: string; onPress: () => void }[] = [
    {
      icon: 'clock',
      label: 'Histórico de serviços',
      onPress: () => Alert.alert('Em breve', 'Histórico de serviços estará disponível na próxima versão.'),
    },
    {
      icon: 'bell',
      label: 'Notificações',
      onPress: () => Alert.alert('Em breve', 'Central de notificações estará disponível na próxima versão.'),
    },
    {
      icon: 'shield',
      label: 'Privacidade e dados (LGPD)',
      onPress: () => nav.navigate('Legal', { doc: 'privacy' }),
    },
    {
      icon: 'help-circle',
      label: 'Ajuda e suporte',
      onPress: () => Linking.openURL('mailto:suporte@onda.app?subject=Ajuda%20-%20Onda'),
    },
    {
      icon: 'file-text',
      label: 'Termos de uso',
      onPress: () => nav.navigate('Legal', { doc: 'terms' }),
    },
  ];

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>

        {/* Avatar */}
        <View style={styles.header}>
          <View style={styles.avatar}>
            <Text style={styles.avatarText}>{nome ? initials(nome) : '?'}</Text>
          </View>
          <Text style={styles.name}>{nome ?? '—'}</Text>
          <Text style={styles.email}>{email ?? '—'}</Text>
          <View style={styles.roleBadge}>
            <Feather
              name={isProvider ? 'tool' : 'user'}
              size={12}
              color={color.primary}
            />
            <Text style={styles.roleText}>{isProvider ? 'Prestador' : 'Cliente'}</Text>
          </View>
        </View>

        {/* Menu */}
        <View style={styles.menu}>
          {MENU_ITEMS.map((item, i) => (
            <TouchableOpacity
              key={i}
              style={[styles.menuItem, i === MENU_ITEMS.length - 1 && styles.menuItemLast]}
              activeOpacity={0.7}
              onPress={item.onPress}
            >
              <View style={styles.menuIconWrap}>
                <Feather name={item.icon} size={18} color={color.institutional2} />
              </View>
              <Text style={styles.menuLabel}>{item.label}</Text>
              <Feather name="chevron-right" size={18} color={color.textFaint} />
            </TouchableOpacity>
          ))}
        </View>

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
    backgroundColor: color.institutional,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 3,
    borderColor: color.primary,
  },
  avatarText: {
    fontSize: font.size.h2,
    fontWeight: font.weight.black,
    color: color.textOnAccent,
    letterSpacing: 1,
  },
  name: { fontSize: font.size.h2, fontWeight: font.weight.bold, color: color.text },
  email: { fontSize: font.size.bodySm, color: color.textSoft },
  roleBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
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
    paddingVertical: space[4],
    paddingHorizontal: space[5],
    borderBottomWidth: 1,
    borderBottomColor: color.lineSoft,
  },
  menuItemLast: { borderBottomWidth: 0 },
  menuIconWrap: {
    width: 36,
    height: 36,
    borderRadius: radius.field,
    backgroundColor: color.bgAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  menuLabel: { flex: 1, fontSize: font.size.body, color: color.text },

  version: { fontSize: font.size.caption, color: color.textFaint, textAlign: 'center' },
});
