const KEY = 'onda_admin_token';

export function getToken(): string | null {
  return sessionStorage.getItem(KEY);
}

export function setToken(token: string) {
  sessionStorage.setItem(KEY, token);
}

export function clearToken() {
  sessionStorage.removeItem(KEY);
}

export function isAuthenticated(): boolean {
  return !!getToken();
}
