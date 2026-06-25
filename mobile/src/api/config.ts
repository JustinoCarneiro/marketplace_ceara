const ENV_URL = process.env.EXPO_PUBLIC_API_URL;

// Emulador Android → 10.0.2.2 aponta para localhost do host
// Dispositivo/Appetize → defina EXPO_PUBLIC_API_URL com a URL pública (ngrok, etc.)
export const API_BASE = ENV_URL ?? 'http://10.0.2.2:8080/api/v1';
