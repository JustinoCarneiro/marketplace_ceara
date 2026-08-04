import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,        // fluxos são sequenciais (cliente/prestador compartilham pedidos)
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: 'http://localhost:8081',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    headless: !!process.env.CI,
  },

  projects: [
    {
      name: 'chromium-mobile',
      use: { ...devices['Pixel 7'], viewport: { width: 430, height: 932 } },
    },
  ],

  // Sobe o preview web do Expo automaticamente antes dos testes. mobile/.env aponta a API
  // pra 8082 (preferência local do dev, porta 8080 colide com outro projeto na máquina dele) —
  // aqui força 8080, o padrão real de backend/CI (mesma porta que mobile-e2e.yml usa).
  webServer: {
    command: 'npm run web',
    url: 'http://localhost:8081',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: { EXPO_PUBLIC_API_URL: process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1' },
  },
});
