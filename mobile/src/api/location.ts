import * as Location from 'expo-location';

export interface Coords {
  lat: number;
  lng: number;
}

// Centro de Fortaleza — usado só como fallback se a permissão for negada ou o GPS falhar,
// nunca como valor padrão. Sem isso a busca/pedido travaria para quem nega a permissão.
const FALLBACK: Coords = { lat: -3.7319, lng: -38.5267 };

export async function getCurrentCoords(): Promise<Coords> {
  try {
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') return FALLBACK;

    const position = await Location.getCurrentPositionAsync({
      accuracy: Location.Accuracy.Balanced,
    });
    return { lat: position.coords.latitude, lng: position.coords.longitude };
  } catch {
    return FALLBACK;
  }
}
