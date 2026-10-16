/** Transporte do chat: uma conexão SSE e uma consulta de recuperação por conexão. */
export interface ChatMessage {
  id: string;
  remetenteId: string;
  remetenteNome: string;
  conteudo: string;
  mascarado: boolean;
  createdAt: string;
}
export type ChatConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'paused' | 'unauthorized' | 'unavailable';
export type ChatEventName = 'open' | 'ready' | 'message' | 'heartbeat' | 'reconnect' | 'session-expired' | 'access-revoked' | 'error';
export interface ChatEvent { type?: string; data?: string | null; xhrStatus?: number }
export interface ChatTransport {
  on: (type: ChatEventName, listener: (event: ChatEvent) => void) => void;
  close: () => void;
}
interface Options {
  url: string;
  token: string;
  createTransport: (url: string, headers: Record<string, string>) => ChatTransport;
  fetcher?: typeof fetch;
  onHistory: (messages: ChatMessage[]) => void;
  onMessage: (message: ChatMessage) => void;
  onState: (state: ChatConnectionState) => void;
}

// Instant do backend usa UTC e pode omitir zeros finais; normalizar preserva a ordem submilissegundo.
function timestampKey(value: string): string {
  return value.replace(/(?:\.(\d+))?Z$/, (_match, fraction: string | undefined) => `.${(fraction ?? '').padEnd(9, '0')}Z`);
}

export function mergeMessages(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const byId = new Map(current.map(message => [message.id, message]));
  for (const message of incoming) byId.set(message.id, message);
  return [...byId.values()].sort((one, two) => timestampKey(one.createdAt).localeCompare(timestampKey(two.createdAt)) || one.id.localeCompare(two.id));
}

function isMessage(value: unknown): value is ChatMessage {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<ChatMessage>;
  return typeof item.id === 'string' && typeof item.remetenteId === 'string' && typeof item.remetenteNome === 'string'
    && typeof item.conteudo === 'string' && typeof item.mascarado === 'boolean' && typeof item.createdAt === 'string';
}

export function connectChat(options: Options) {
  const fetcher = options.fetcher ?? fetch;
  const headers = { Authorization: `Bearer ${options.token}` };
  let disposed = false, paused = false, fatal = false, generation = 0, failures = 0;
  let transport: ChatTransport | undefined;
  let history: AbortController | undefined;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let watchdog: ReturnType<typeof setTimeout> | undefined;

  function disconnect() {
    generation++;
    if (retry) clearTimeout(retry);
    if (watchdog) clearTimeout(watchdog);
    retry = undefined; watchdog = undefined;
    history?.abort(); history = undefined;
    transport?.close(); transport = undefined;
  }
  function stop(state: 'unauthorized' | 'unavailable') {
    fatal = true; disconnect(); options.onState(state);
  }
  function reconnect(delay?: number) {
    if (disposed || paused || fatal || retry) return;
    disconnect();
    options.onState('reconnecting');
    const wait = delay ?? Math.min(30000, 1000 * 2 ** Math.min(failures++, 5));
    retry = setTimeout(() => { retry = undefined; open(); }, wait);
  }
  function open() {
    if (disposed || paused || fatal) return;
    const attempt = ++generation;
    let ready = false;
    const active = () => !disposed && !paused && !fatal && generation === attempt;
    const touch = () => {
      if (watchdog) clearTimeout(watchdog);
      watchdog = setTimeout(() => { if (active()) reconnect(); }, 45000);
    };
    options.onState(failures ? 'reconnecting' : 'connecting');
    touch();
    try {
      const connection = options.createTransport(`${options.url}/stream`, headers);
      transport = connection;
      connection.on('open', () => { if (active()) touch(); });
      connection.on('heartbeat', () => { if (active()) touch(); });
      connection.on('ready', async () => {
        if (!active() || ready) return;
        ready = true; touch();
        const controller = new AbortController(); history = controller;
        try {
          const response = await fetcher(options.url, { headers, signal: controller.signal });
          if (!active()) return;
          if (response.status === 401) return stop('unauthorized');
          if ([403, 404, 422].includes(response.status)) return stop('unavailable');
          if (!response.ok) throw new Error('history unavailable');
          const data: unknown = await response.json();
          if (!active()) return;
          if (!Array.isArray(data) || !data.every(isMessage)) throw new Error('invalid history');
          options.onHistory(data);
          failures = 0; options.onState('connected');
        } catch {
          if (active()) reconnect();
        }
      });
      connection.on('message', event => {
        if (!active()) return;
        touch();
        try {
          const message: unknown = JSON.parse(event.data ?? '');
          if (!isMessage(message)) throw new Error('invalid message');
          options.onMessage(message);
        } catch { reconnect(); }
      });
      connection.on('reconnect', () => { if (active()) reconnect(0); });
      connection.on('session-expired', () => { if (active()) stop('unauthorized'); });
      connection.on('access-revoked', () => { if (active()) stop('unavailable'); });
      connection.on('error', event => {
        if (!active()) return;
        if (event.xhrStatus === 401) stop('unauthorized');
        else if ([403, 404, 422].includes(event.xhrStatus ?? 0)) stop('unavailable');
        else reconnect(event.xhrStatus === 429 ? 30000 : undefined);
      });
    } catch { reconnect(); }
  }
  open();
  return {
    close() { disposed = true; disconnect(); },
    pause() { if (disposed || paused || fatal) return; paused = true; disconnect(); options.onState('paused'); },
    resume() { if (disposed || !paused || fatal) return; paused = false; failures = 0; open(); },
  };
}
