import { Alert, Platform } from 'react-native';

/**
 * Confirmação de ação sem volta (cancelar pedido). `Alert.alert` não faz nada no web — e a demo é o build web —, então
 * lá usa `window.confirm`; no celular, o Alert nativo.
 */
export function confirmar(titulo: string, mensagem: string, textoConfirmar: string, aoConfirmar: () => void) {
  if (Platform.OS === 'web') {
    if (globalThis.confirm(`${titulo}\n\n${mensagem}`)) aoConfirmar();
    return;
  }
  Alert.alert(titulo, mensagem, [
    { text: 'Voltar', style: 'cancel' },
    { text: textoConfirmar, style: 'destructive', onPress: aoConfirmar },
  ]);
}
