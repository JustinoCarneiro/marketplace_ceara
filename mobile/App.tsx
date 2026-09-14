import React, { useEffect, useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import { useFonts } from 'expo-font';
import NetInfo from '@react-native-community/netinfo';
import { color } from './src/theme';
import RootNavigator from './src/navigation/RootNavigator';
import OfflineScreen from './src/components/OfflineScreen';

export default function App() {
  const [isOnline, setIsOnline] = useState(true);
  // theme/index.ts declara family: 'Manrope' em todo o app — sem carregar aqui, o RN cai
  // silenciosamente pra fonte de sistema (nunca quebra, só nunca fica certo). `error` também
  // libera o render: uma falha de carregamento não pode travar o app na tela em branco.
  const [fontsLoaded, fontError] = useFonts({
    Manrope: require('./assets/fonts/Manrope-VariableFont_wght.ttf'),
  });

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener(state => {
      setIsOnline(state.isConnected !== false);
    });
    return unsubscribe;
  }, []);

  if (!fontsLoaded && !fontError) {
    return null;
  }

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        {isOnline ? (
          <NavigationContainer>
            <RootNavigator />
          </NavigationContainer>
        ) : (
          <OfflineScreen onRetry={() => NetInfo.fetch().then(s => setIsOnline(s.isConnected !== false))} />
        )}
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
