import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Feather } from '@expo/vector-icons';
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

type FeatherName = React.ComponentProps<typeof Feather>['name'];

const TAB_ICONS: Record<string, FeatherName> = {
  Home: 'home',
  Search: 'search',
  MyRequests: 'bookmark',
  Profile: 'user',
};

const TAB_LABELS: Record<string, string> = {
  Home: 'Início',
  Search: 'Buscar',
  MyRequests: 'Pedidos',
  Profile: 'Perfil',
};

function ClientTabs() {
  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarIcon: ({ focused, color: iconColor }) => (
          <Feather
            name={TAB_ICONS[route.name]}
            size={22}
            color={iconColor}
            strokeWidth={focused ? 2.2 : 1.8}
          />
        ),
        tabBarLabel: TAB_LABELS[route.name],
        tabBarActiveTintColor: color.primary,
        tabBarInactiveTintColor: color.textFaint,
        tabBarStyle: {
          backgroundColor: color.surface,
          borderTopColor: color.lineSoft,
          borderTopWidth: 1,
          height: 72,
          paddingBottom: 10,
          paddingTop: 6,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: '600',
          marginTop: 2,
        },
      })}
    >
      <Tab.Screen name="Home" component={HomeScreen} />
      <Tab.Screen name="Search" component={ResultsScreen} />
      <Tab.Screen name="MyRequests" component={MyRequestsScreen} />
      <Tab.Screen name="Profile" component={ProfileScreen} />
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
      <Stack.Screen name="PaymentChoice" component={PaymentChoiceScreen} options={{ presentation: 'transparentModal' }} />
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
