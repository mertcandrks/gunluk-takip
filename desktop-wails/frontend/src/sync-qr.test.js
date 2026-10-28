import test from 'node:test';
import assert from 'node:assert/strict';
import { buildSyncQrPayload } from './sync-qr.js';

test('builds the Android-compatible QR payload', () => {
  assert.equal(
    buildSyncQrPayload('192.168.1.40', '12345678'),
    'gunluktakip://sync?v=1&host=192.168.1.40&port=47832&code=12345678',
  );
});

test('rejects unsafe addresses and invalid codes', () => {
  assert.throws(() => buildSyncQrPayload('../host', '12345678'));
  assert.throws(() => buildSyncQrPayload('192.168.1.40', '1234'));
});

test('includes the persistent identity and rejects malformed identities', () => {
  const identity = '0123456789abcdef0123456789abcdef';
  const payload = new URL(buildSyncQrPayload('192.168.1.40', '12345678', identity));
  assert.equal(payload.searchParams.get('id'), identity);
  assert.equal(payload.searchParams.get('code'), '12345678');
  assert.throws(() => buildSyncQrPayload('192.168.1.40', '12345678', 'foreign-device'));
});
