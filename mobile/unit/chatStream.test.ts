import { test } from 'node:test';
import assert from 'node:assert/strict';
import { connectChat, mergeMessages } from '../src/api/chatStream.ts';
import type { ChatEvent, ChatEventName, ChatMessage, ChatTransport, ChatConnectionState } from '../src/api/chatStream.ts';

const message = (id: string, text = id): ChatMessage => ({ id, conteudo: text, remetenteId: 'sender', remetenteNome: 'Pessoa', mascarado: false, createdAt: '2026-10-16T10:00:00Z' });
class Transport implements ChatTransport {
  listeners = new Map<ChatEventName, (event: ChatEvent) => void>();
  closed = false;
  on(type: ChatEventName, listener: (event: ChatEvent) => void) { this.listeners.set(type, listener); }
  close() { this.closed = true; }
  emit(type: ChatEventName, event: ChatEvent = {}) { this.listeners.get(type)?.(event); }
}
const flush = () => new Promise<void>(resolve => setImmediate(resolve));
function setup(fetcher: typeof fetch = async () => new Response('[]')) {
  const transports: Transport[] = [], states: ChatConnectionState[] = [], histories: ChatMessage[][] = [], received: ChatMessage[] = [];
  const subscription = connectChat({
    url: 'https://api.example.org/api/v1/service-requests/request/messages', token: 'synthetic-token',
    createTransport(url, headers) {
      assert.equal(url.includes('synthetic-token'), false);
      assert.equal(headers.Authorization, 'Bearer synthetic-token');
      const transport = new Transport(); transports.push(transport); return transport;
    },
    fetcher, onHistory: history => histories.push(history), onMessage: item => received.push(item), onState: state => states.push(state),
  });
  return { subscription, transports, states, histories, received };
}

test('receives pushes without polling history and merges duplicate ids', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let requests = 0;
  const context = setup(async () => { requests++; return new Response(JSON.stringify([message('1')])); });
  context.transports[0].emit('ready'); await flush();
  context.transports[0].emit('message', { data: JSON.stringify(message('2')) });
  t.mock.timers.tick(5000); await flush();
  assert.equal(requests, 1);
  assert.equal(context.states.at(-1), 'connected');
  assert.deepEqual(mergeMessages(context.histories[0], [...context.received, message('2')]).map(m => m.id), ['1', '2']);
  context.subscription.close();
});

test('reconnect recovers messages missed while disconnected', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let requests = 0;
  const context = setup(async () => new Response(JSON.stringify(++requests === 1 ? [message('1')] : [message('1'), message('2')])));
  context.transports[0].emit('ready'); await flush();
  context.transports[0].emit('error', { xhrStatus: 0 });
  assert.equal(context.transports[0].closed, true);
  t.mock.timers.tick(1000);
  context.transports[1].emit('ready'); await flush();
  assert.deepEqual(context.histories.at(-1)?.map(m => m.id), ['1', '2']);
  context.subscription.close();
});

test('pause and close cancel retries and discard late responses', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let resolve!: (response: Response) => void;
  const context = setup(() => new Promise<Response>(done => { resolve = done; }));
  context.transports[0].emit('ready');
  context.subscription.pause();
  resolve(new Response(JSON.stringify([message('stale')]))); await flush();
  assert.deepEqual(context.histories, []);
  t.mock.timers.tick(60000); assert.equal(context.transports.length, 1);
  context.subscription.resume(); assert.equal(context.transports.length, 2);
  context.transports[1].emit('error', { xhrStatus: 0 });
  context.subscription.close();
  t.mock.timers.tick(60000); assert.equal(context.transports.length, 2);
});

test('session expiry stops automatic retries', t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const context = setup();
  context.transports[0].emit('session-expired');
  t.mock.timers.tick(60000);
  assert.equal(context.states.at(-1), 'unauthorized');
  assert.equal(context.transports.length, 1);
  context.subscription.close();
});

test('authorization failure on history stops and reports unavailable', async () => {
  const context = setup(async () => new Response('{}', { status: 403 }));
  context.transports[0].emit('ready'); await flush();
  assert.equal(context.states.at(-1), 'unavailable');
  assert.equal(context.transports[0].closed, true);
  context.subscription.close();
});

test('silent connection loss is detected by watchdog and retried', t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const context = setup();
  t.mock.timers.tick(45000);
  assert.equal(context.states.at(-1), 'reconnecting');
  assert.equal(context.transports[0].closed, true);
  t.mock.timers.tick(1000);
  assert.equal(context.transports.length, 2);
  context.subscription.close();
});
