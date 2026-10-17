import test from 'node:test';
import assert from 'node:assert/strict';
import { serializePush } from '../src/api/pushCoordinator.ts';

test('profile activation, cleanup and next account binding share one queue', async () => {
  const events: string[] = [];
  let release!: () => void;
  const first = serializePush(async () => {
    events.push('A:put-start');
    await new Promise<void>(resolve => { release = resolve; });
    events.push('A:put-end');
  });
  await Promise.resolve();
  const cleanup = serializePush(async () => { events.push('A:revoke'); });
  const second = serializePush(async () => { events.push('B:put'); });
  assert.deepEqual(events, ['A:put-start']);
  release(); await first; await cleanup; await second;
  assert.deepEqual(events, ['A:put-start', 'A:put-end', 'A:revoke', 'B:put']);
});

test('failed registration does not block next revocation', async () => {
  await assert.rejects(serializePush(async () => { throw new Error('fixture'); }));
  assert.equal(await serializePush(async () => 'revoked'), 'revoked');
});
