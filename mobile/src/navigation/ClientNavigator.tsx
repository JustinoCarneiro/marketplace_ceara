import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { View, Text, StyleSheet } from 'react-native';
import { color } from '../theme';
import type { ClientStackParams, ClientTabParams } from './types';

import HomeScreen from '../screens/client/HomeScreen';
import MyRequestsScreen from '../screens/client/MyRequestsScreen';
import ProfileScreen from '../screens/shared/ProfileScreen';
import ResultsScreen from '../screens/client/ResultsScreen';
import FiltersSheet from '../screens/client/FiltersScreen';
import ProviderProfileScreen from '../screens/client/ProviderProfileScreen';
import NewRequestScreen from '../screens/client/NewRequestScreen';
import AiAssistantScreen from '../screens/client/AiAssistantScreen';
import RequestCreatedScreen from '../screens/client/RequestCreatedScreen';
import CompareProposalsScreen from '../screens/client/CompareProposalsScreen';
import PaymentChoiceScreen from '../screens/client/PaymentChoiceScreen';
import PaymentPixScreen from '../screens/client/PaymentPixScreen';
import PaymentCardScreen from '../screens/client/PaymentCardScreen';
import EscrowConfirmedScreen from '../screens/client/EscrowConfirmedScreen';
import RequestDetailScreen from '../screens/shared/RequestDetailScreen';
import OpenDisputeScreen from '../screens/shared/OpenDisputeScreen';
import RateScreen from '../screens/shared/RateScreen';
import RateConfirmScreen from '../screens/shared/RateConfirmScreen';
import SosScreen from '../screens/shared/SosScreen';
import SosActiveScreen from '../screens/shared/SosActiveScreen';

const Tab = createBottomTabNavigator<ClientTabParams>();
const Stack = createNativeStackNavigator<ClientStackParams>();

const TAB_ICONS: Record<string, { active: string; inactive: string }> = {
  Home:       { active: '⌂',  inactive: '⌂' },
  Search:     { active: '⊙',  inactive: '⊙' },
  MyRequests: { active: '❑',  inactive: '❑' },
  Profile:    { active: '◉',  inactive: '◉' },
};

function TabIcon({ name, focused }: { name: string; focused: boolean }) {
  const label: Record<string, string> = {
    Home: '🏠', Search: '🔍', MyRequests: '📋', Profile: '👤',
  };
  return (
    <View style={styles.tabIcon}>
      <Text style={{ fontSize: 22, opacity: focused ? 1 : 0.4 }}>{label[name]}</Text>
    </View>
  );
}

function ClientTabs() {
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
      <Tab.Screen name="Home"       component={HomeScreen}       options={{ tabBarLabel: 'Início' }} />
      <Tab.Screen name="Search"     component={ResultsScreen}    options={{ tabBarLabel: 'Buscar' }} />
      <Tab.Screen name="MyRequests" component={MyRequestsScreen} options={{ tabBarLabel: 'Pedidos' }} />
      <Tab.Screen name="Profile"    component={ProfileScreen}    options={{ tabBarLabel: 'Perfil' }} />
    </Tab.Navigator>
  );
}

export default function ClientNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="ClientTabs" component={ClientTabs} />
      <Stack.Screen name="Results" component={ResultsScreen} />
      <Stack.Screen name="Filters" component={FiltersSheet} options={{ presentation: 'modal' }} />
      <Stack.Screen name="ProviderProfile" component={ProviderProfileScreen} />
      <Stack.Screen name="NewRequest" component={NewRequestScreen} />
      <Stack.Screen name="AiAssistant" component={AiAssistantScreen} />
      <Stack.Screen name="RequestCreated" component={RequestCreatedScreen} />
      <Stack.Screen name="CompareProposals" component={CompareProposalsScreen} />
      <Stack.Screen name="PaymentChoice" component={PaymentChoiceScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="PaymentPix" component={PaymentPixScreen} />
      <Stack.Screen name="PaymentCard" component={PaymentCardScreen} />
      <Stack.Screen name="EscrowConfirmed" component={EscrowConfirmedScreen} />
      <Stack.Screen name="RequestDetail" component={RequestDetailScreen} />
      <Stack.Screen name="OpenDispute" component={OpenDisputeScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="Rate" component={RateScreen} />
      <Stack.Screen name="RateConfirm" component={RateConfirmScreen} />
      <Stack.Screen name="Sos" component={SosScreen} options={{ presentation: 'modal' }} />
      <Stack.Screen name="SosActive" component={SosActiveScreen} />
    </Stack.Navigator>
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
  tabIcon: { marginTop: 4 },
});
