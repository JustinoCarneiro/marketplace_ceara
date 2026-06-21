import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { View, Text, StyleSheet } from 'react-native';
import { color } from '../theme';
import type { ProviderStackParams } from './types';

import AvailableRequestsScreen from '../screens/provider/AvailableRequestsScreen';
import SendProposalScreen from '../screens/provider/SendProposalScreen';
import EscrowHeldScreen from '../screens/provider/EscrowHeldScreen';
import ProfileScreen from '../screens/shared/ProfileScreen';
import RequestDetailScreen from '../screens/shared/RequestDetailScreen';
import OpenDisputeScreen from '../screens/shared/OpenDisputeScreen';
import RateScreen from '../screens/shared/RateScreen';
import RateConfirmScreen from '../screens/shared/RateConfirmScreen';
import SosScreen from '../screens/shared/SosScreen';
import SosActiveScreen from '../screens/shared/SosActiveScreen';

const Stack = createNativeStackNavigator<ProviderStackParams>();

export default function ProviderNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ProviderTabs" component={ProviderTabs} />
      <Stack.Screen name="AvailableRequests" component={AvailableRequestsScreen} />
      <Stack.Screen name="SendProposal" component={SendProposalScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="EscrowHeld" component={EscrowHeldScreen} />
      <Stack.Screen name="RequestDetail" component={RequestDetailScreen} />
      <Stack.Screen name="OpenDispute" component={OpenDisputeScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="Rate" component={RateScreen} />
      <Stack.Screen name="RateConfirm" component={RateConfirmScreen} />
      <Stack.Screen name="Sos" component={SosScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="SosActive" component={SosActiveScreen} />
    </Stack.Navigator>
  );
}

const Tab = createBottomTabNavigator();

function TabIcon({ name, focused }: { name: string; focused: boolean }) {
  const icons: Record<string, string> = { Available: '🔍', Active: '📋', Profile: '👤' };
  return (
    <View style={{ marginTop: 4 }}>
      <Text style={{ fontSize: 20, opacity: focused ? 1 : 0.45 }}>{icons[name]}</Text>
    </View>
  );
}

function ProviderTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused }) => <TabIcon name={route.name} focused={focused} />,
        tabBarActiveTintColor: color.primary,
        tabBarInactiveTintColor: color.textFaint,
        tabBarStyle: styles.tabBar,
        tabBarLabelStyle: styles.tabLabel,
      })}
    >
      <Tab.Screen name="Available" component={AvailableRequestsScreen} options={{ tabBarLabel: 'Disponíveis' }} />
      <Tab.Screen name="Active" component={RequestDetailScreen} options={{ tabBarLabel: 'Em Andamento' }} />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ tabBarLabel: 'Perfil' }} />
    </Tab.Navigator>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    backgroundColor: color.surface,
    borderTopColor: color.line,
    borderTopWidth: 1,
    height: 64,
    paddingBottom: 8,
  },
  tabLabel: { fontSize: 11, fontWeight: '600' },
});
