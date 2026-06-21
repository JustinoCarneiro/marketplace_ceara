import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { useAuthStore } from '../store/auth';
import type { RootStackParams } from './types';

import AuthNavigator from './AuthNavigator';
import ClientNavigator from './ClientNavigator';
import ProviderNavigator from './ProviderNavigator';

const Stack = createNativeStackNavigator<RootStackParams>();

export default function RootNavigator() {
  const role = useAuthStore(s => s.role);

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {role === 'ROLE_CLIENT' ? (
        <Stack.Screen name="Client" component={ClientNavigator} />
      ) : role === 'ROLE_PROVIDER' ? (
        <Stack.Screen name="Provider" component={ProviderNavigator} />
      ) : (
        <Stack.Screen name="Auth" component={AuthNavigator} />
      )}
    </Stack.Navigator>
  );
}
