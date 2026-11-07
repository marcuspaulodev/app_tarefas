export function localDate(value = new Date()) {
    return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

export function parseDate(value) {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day, 12);
}

export function addDays(value, days) {
    const date = parseDate(value);
    date.setDate(date.getDate() + days);
    return localDate(date);
}

export function monthOf(value) {
    return `${value.slice(0, 7)}-01`;
}

export function moveMonth(value, offset) {
    const date = parseDate(monthOf(value));
    date.setMonth(date.getMonth() + offset);
    return localDate(date);
}

export function calendarDays(month) {
    const first = parseDate(monthOf(month));
    const next = parseDate(moveMonth(month, 1));
    next.setDate(0);
    const length = Math.ceil((first.getDay() + next.getDate()) / 7) * 7;
    const start = addDays(localDate(first), -first.getDay());
    return Array.from({ length }, (_, index) => addDays(start, index));
}

export function formatDate(value, options = { day: 'numeric', month: 'short' }) {
    return new Intl.DateTimeFormat('pt-BR', options).format(parseDate(value));
}

export function dayTitle(value) {
    const title = formatDate(value, { weekday: 'short', day: 'numeric', month: 'long' }).replace('.', '');
    return title[0].toUpperCase() + title.slice(1);
}

export function shownOn(task, today) {
    return !task.completed && task.date < today ? today : task.date;
}

export function isCarried(task, today) {
    return !task.completed && task.original_date < today;
}

export function filterByTag(tasks, tagId) {
    return tagId ? tasks.filter(task => task.tag_ids.includes(tagId)) : tasks;
}

export function dayGroups(tasks, today) {
    return [
        { id: 'carried', title: 'Vieram de outros dias', hint: 'ainda por fazer', tasks: tasks.filter(task => isCarried(task, today)) },
        { id: 'pending', title: 'Para este dia', tasks: tasks.filter(task => !task.completed && !isCarried(task, today)) },
        { id: 'completed', title: 'Concluídas no dia', tasks: tasks.filter(task => task.completed) },
    ].filter(group => group.tasks.length);
}

export function dateGroups(tasks, today, descending = false) {
    const groups = new Map();
    tasks.forEach(task => {
        const date = shownOn(task, today);
        if (!groups.has(date)) groups.set(date, { id: date, title: dayTitle(date), tasks: [] });
        groups.get(date).tasks.push(task);
    });
    return [...groups.values()].sort((a, b) => descending ? b.id.localeCompare(a.id) : a.id.localeCompare(b.id));
}

export function recurrenceLabel(recurrence) {
    if (recurrence === 'DAILY') return 'Repete todo dia';
    if (!recurrence?.startsWith('WEEKLY:')) return '';
    const labels = { 1: 'seg', 2: 'ter', 3: 'qua', 4: 'qui', 5: 'sex', 6: 'sáb', 7: 'dom' };
    return `Repete ${recurrence.slice(7).split(',').map(day => labels[Number(day)]).join(', ')}`;
}
