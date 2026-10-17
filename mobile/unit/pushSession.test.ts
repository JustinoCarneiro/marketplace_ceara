import test from 'node:test';
import assert from 'node:assert/strict';
import { createPushSession } from '../src/api/pushSession.ts';

test('session closed during registration revokes device after response', async () => {
  let finish!: (ok: boolean) => void;
  const deleted: string[] = [];
  const session = createPushSession(() => new Promise(resolve => { finish = resolve; }), async t => { deleted.push(t); return true; });
  const registering = session.update('fixture-token');
  await Promise.resolve();
  const closing = session.close();
  finish(true);
  await registering; await closing;
  assert.ok(deleted.includes('fixture-token'));
});

test('closed session never registers a late token', async () => {
  let calls = 0;
  const session = createPushSession(async () => { calls++; return true; }, async () => true);
  await session.close(); await session.update('fixture-late-token');
  assert.equal(calls, 0);
});

test('rotation revokes previous token before registering replacement', async () => {
  const events: string[] = [];
  const session = createPushSession(async t => { events.push(`put:${t}`); return true; }, async t => { events.push(`delete:${t}`); return true; });
  await session.update('old'); await session.update('new');
  assert.deepEqual(events, ['put:old', 'delete:old', 'put:new']);
});

test('failed revocation postpones replacement until next retry', async () => {
  let revoke = false;
  const registered: string[] = [];
  const session = createPushSession(async t => { registered.push(t); return true; }, async () => revoke);
  await session.update('old'); await session.update('new');
  assert.deepEqual(registered, ['old']);
  revoke = true; await session.update('new');
  assert.deepEqual(registered, ['old', 'new']);
});
