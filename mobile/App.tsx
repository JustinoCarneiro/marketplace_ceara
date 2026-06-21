import React, { useEffect, useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { StatusBar } from 'expo-status-bar';
import NetInfo from '@react-native-community/netinfo';
import { color } from './src/theme';
import RootNavigator from './src/navigation/RootNavigator';
import OfflineScreen from './src/components/OfflineScreen';

export default function App() {
  const [isOnline, setIsOnline] = useState(true);

  useEffect(() => {
    const unsubscribe = NetInfo.addEventListener(state => {
      setIsOnline(state.isConnected !== false);
    });
    return unsubscribe;
  }, []);

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
