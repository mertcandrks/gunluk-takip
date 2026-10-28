import test from 'node:test';
import assert from 'node:assert/strict';
import { readParts, progressPercent, appendPendingPart } from './agenda-parts.js';

test('save includes the pending title without duplicating it on retry', () => {
  const existing = [{id:'first', title:'First', progress:100}];
  const saved = appendPendingPart(existing, '  Second  ');
  assert.deepEqual(saved[0], existing[0]);
  assert.equal(saved[1].title, 'Second');
  assert.equal(saved[1].progress, 0);
  assert.equal(existing.length, 1);
  assert.equal(appendPendingPart([], 'First').length, 1);
  assert.strictEqual(appendPendingPart(saved, '  '), saved);
  const full = Array.from({length:100}, (_,id) => ({id:String(id),title:'Step',progress:0}));
  assert.strictEqual(appendPendingPart(full, ''), full);
  assert.throws(() => appendPendingPart(full, 'Extra'));
  assert.throws(() => appendPendingPart([], 'x'.repeat(201)));
});

test('project progress follows parts and rejects invalid data', () => {
  const parts = readParts('[{"id":"front","title":"Frontend","progress":50},{"id":"back","title":"Backend","progress":0}]');
  assert.equal(progressPercent(parts), 25);
  assert.equal(progressPercent(parts.map(part => ({...part, progress:100}))), 100);
  assert.equal(progressPercent(parts.slice(0,1)), 50);
  assert.equal(progressPercent(readParts('')), 0);
  for (const bad of ['null', '{}', '[{"id":"a","title":"","progress":0}]', '[{"id":"a","title":"a","progress":101}]', JSON.stringify([parts[0],parts[0]])]) {
    assert.throws(() => readParts(bad));
  }
});
