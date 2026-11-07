import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { addDays, calendarDays, dateGroups, dayGroups, filterByTag, formatDate, isCarried, localDate, monthOf, moveMonth, recurrenceLabel, shownOn } from '../../resources/js/lib/tasks.js';

const fixtures = JSON.parse(readFileSync(new URL('../../../shared/fixtures/rules.json', import.meta.url)));
const today = '2026-11-10';
const pending = { id: 'pending', date: today, original_date: today, completed: false, tag_ids: ['work'] };
const carried = { ...pending, id: 'carried', date: '2026-11-05', original_date: '2026-11-05', tag_ids: ['home'] };
const completed = { ...pending, id: 'completed', completed: true };

test('uses the same shown-day rules as Android and Laravel', () => {
    fixtures.shown_on.forEach(example => assert.equal(shownOn(example, example.today), example.shown_on));
});

test('separates carried, scheduled and completed tasks without losing an item', () => {
    const groups = dayGroups([completed, pending, carried], today);
    assert.deepEqual(groups.map(group => [group.id, group.tasks.map(task => task.id)]), [
        ['carried', ['carried']], ['pending', ['pending']], ['completed', ['completed']],
    ]);
    assert.equal(isCarried({ ...carried, completed: true }, today), false);
});

test('filters by tags and leaves the input list intact', () => {
    const tasks = [pending, carried, completed];
    assert.deepEqual(filterByTag(tasks, 'home'), [carried]);
    assert.deepEqual(filterByTag(tasks, 'unknown'), []);
    assert.deepEqual(filterByTag(tasks, ''), tasks);
    assert.equal(tasks.length, 3);
});

test('groups search results by their displayed day and orders completed days newest first', () => {
    const older = { ...completed, id: 'older', date: '2026-11-04' };
    assert.deepEqual(dateGroups([pending, older, carried], today, true).map(group => [group.id, group.tasks.map(task => task.id)]), [
        [today, ['pending', 'carried']], ['2026-11-04', ['older']],
    ]);
});

test('the calendar starts on Sunday, includes every day and handles leap years and six-week months', () => {
    const november = calendarDays('2026-11-01');
    assert.equal(november.length, 35);
    assert.equal(november[0], '2026-11-01');
    assert.equal(november.at(-1), '2026-12-05');
    assert.equal(calendarDays('2028-02-01').includes('2028-02-29'), true);
    const august = calendarDays('2026-08-01');
    assert.equal(august.length, 42);
    assert.equal(august[0], '2026-07-26');
    assert.equal(august.at(-1), '2026-09-05');
});

test('date arithmetic crosses month and year boundaries without UTC conversion', () => {
    assert.equal(addDays('2026-12-31', 1), '2027-01-01');
    assert.equal(addDays('2028-03-01', -1), '2028-02-29');
    assert.equal(moveMonth('2026-12-31', 1), '2027-01-01');
    assert.equal(moveMonth('2026-01-31', -1), '2025-12-01');
    assert.equal(monthOf('2026-11-10'), '2026-11-01');
    assert.equal(localDate(new Date(2026, 10, 10, 23, 59)), today);
    assert.match(formatDate(today), /10/);
    assert.match(formatDate(today), /nov/);
});

test('recurrence descriptions keep the ISO weekday order and support Sunday', () => {
    assert.equal(recurrenceLabel('DAILY'), 'Repete todo dia');
    assert.equal(recurrenceLabel('WEEKLY:1,3,7'), 'Repete seg, qua, dom');
    assert.equal(recurrenceLabel(null), '');
});
