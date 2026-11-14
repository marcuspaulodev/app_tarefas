/** Itens do checklist de uma tarefa: [{ id, text, done }], como na API e no app. */

export const MAX_ITEMS = 50;
export const MAX_TEXT = 255;

/** UUID v4 com getRandomValues: randomUUID só existe em HTTPS, e o site também abre por http na rede local. */
export function uuid() {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    const hex = [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
    return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export function newItem(text) {
    return { id: uuid(), text: text.trim(), done: false };
}

export function progress(checklist = []) {
    return { done: checklist.filter(item => item.done).length, total: checklist.length };
}

export function toggleItem(checklist, id) {
    return checklist.map(item => item.id === id ? { ...item, done: !item.done } : item);
}

export function moveItem(checklist, index, offset) {
    const target = index + offset;
    if (target < 0 || target >= checklist.length) return checklist;
    const items = [...checklist];
    [items[index], items[target]] = [items[target], items[index]];
    return items;
}

/** Marcar este item completa o checklist de uma tarefa ainda aberta: hora de perguntar se quer concluir. */
export function completesChecklist(task, id) {
    if (task.completed) return false;
    const item = task.checklist.find(entry => entry.id === id);
    return !!item && !item.done && task.checklist.every(entry => entry.done || entry.id === id);
}

/** Próxima ocorrência de uma recorrente: só os pendentes (mesma regra do servidor e do app). */
export function forNextOccurrence(checklist = []) {
    return checklist.filter(item => !item.done).map(item => ({ ...item, done: false }));
}
