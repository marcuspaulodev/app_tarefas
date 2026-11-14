import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { completesChecklist, forNextOccurrence, moveItem, newItem, progress, toggleItem, uuid } from '../../resources/js/lib/checklist.js';

const fixtures = JSON.parse(readFileSync(new URL('../../../shared/fixtures/rules.json', import.meta.url)));
const a = { id: 'a', text: 'Aquecer', done: true };
const b = { id: 'b', text: 'Treino', done: false };
const c = { id: 'c', text: 'Alongar', done: false };

test('uses the same next-occurrence checklist as Android and Laravel', () => {
    fixtures.next_checklist.forEach(example => assert.deepEqual(forNextOccurrence(example.checklist), example.next));
});

test('creates trimmed pending items with unique ids', () => {
    const first = newItem('  Comprar pão ');
    assert.deepEqual({ ...first, id: undefined }, { id: undefined, text: 'Comprar pão', done: false });
    assert.notEqual(first.id, newItem('Outro').id);
    assert.match(uuid(), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

test('counts, toggles and moves items without changing the input', () => {
    const list = [a, b, c];
    assert.deepEqual(progress(list), { done: 1, total: 3 });
    assert.deepEqual(progress(), { done: 0, total: 0 });
    assert.deepEqual(toggleItem(list, 'b').map(item => item.done), [true, true, false]);
    assert.deepEqual(moveItem(list, 2, -1).map(item => item.id), ['a', 'c', 'b']);
    assert.equal(moveItem(list, 0, -1), list);
    assert.deepEqual(list, [a, b, c]);
});

test('asks to complete only when the last pending item of an open task is checked', () => {
    const task = { completed: false, checklist: [a, b] };
    assert.equal(completesChecklist(task, 'b'), true);
    assert.equal(completesChecklist(task, 'a'), false);
    assert.equal(completesChecklist({ ...task, checklist: [a, b, c] }, 'b'), false);
    assert.equal(completesChecklist({ ...task, completed: true }, 'b'), false);
});
