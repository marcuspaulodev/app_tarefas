<script setup>
import { computed, reactive, ref } from 'vue';
import { MAX_ITEMS, MAX_TEXT, moveItem, newItem } from '../lib/checklist';
import { parseDate, shownOn } from '../lib/tasks';
import AppIcon from './AppIcon.vue';
import AppDialog from './AppDialog.vue';

const props = defineProps({ task: Object, date: String, today: String, tags: Array, save: Function });
const emit = defineEmits(['close', 'saved']);
const busy = ref(false);
const error = ref('');
const errors = ref({});
const form = reactive({
    title: props.task?.title ?? '', description: props.task?.description ?? '',
    date: props.task ? shownOn(props.task, props.today) : props.date,
    deadline: props.task?.deadline ?? '', priority: props.task?.priority ?? 'MEDIUM',
    tag_ids: [...(props.task?.tag_ids ?? [])],
    checklist: (props.task?.checklist ?? []).map(item => ({ ...item })),
});
const newItemText = ref('');
const canAddItem = computed(() => !!newItemText.value.trim() && form.checklist.length < MAX_ITEMS);

function addItem() {
    if (!canAddItem.value) return;
    form.checklist.push(newItem(newItemText.value));
    newItemText.value = '';
}

function move(index, offset) {
    form.checklist = moveItem(form.checklist, index, offset);
}
const repetition = ref(props.task?.recurrence === 'DAILY' ? 'daily' : props.task?.recurrence?.startsWith('WEEKLY:') ? 'weekly' : 'none');
const weekdays = ref(props.task?.recurrence?.startsWith('WEEKLY:') ? props.task.recurrence.slice(7).split(',').map(Number) : [parseDate(form.date).getDay() || 7]);
const weekdayLabels = [{ id: 1, name: 'Segunda', short: 'S' }, { id: 2, name: 'Terça', short: 'T' }, { id: 3, name: 'Quarta', short: 'Q' }, { id: 4, name: 'Quinta', short: 'Q' }, { id: 5, name: 'Sexta', short: 'S' }, { id: 6, name: 'Sábado', short: 'S' }, { id: 7, name: 'Domingo', short: 'D' }];
const validWeekly = computed(() => repetition.value !== 'weekly' || weekdays.value.length > 0);

async function submit() {
    if (!validWeekly.value || !form.title.trim()) return;
    addItem();
    busy.value = true;
    error.value = '';
    errors.value = {};
    try {
        await props.save(props.task, {
            ...form, title: form.title.trim(), deadline: form.deadline || null,
            checklist: form.checklist.filter(item => item.text.trim()).map(item => ({ ...item, text: item.text.trim() })),
            recurrence: repetition.value === 'daily' ? 'DAILY' : repetition.value === 'weekly' ? `WEEKLY:${[...weekdays.value].sort().join(',')}` : null,
        });
        emit('saved', props.task ? 'Tarefa atualizada.' : 'Tarefa criada.');
    } catch (failure) {
        error.value = failure.message;
        errors.value = failure.errors ?? {};
    } finally {
        busy.value = false;
    }
}
</script>

<template>
    <AppDialog :title="task ? 'Editar tarefa' : 'Nova tarefa'" :busy="busy" @close="emit('close')">
        <form class="form-stack" @submit.prevent="submit">
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <fieldset class="form-fields" :disabled="busy">
                <label class="field">O que precisa ser feito?
                    <input v-model="form.title" name="title" required maxlength="255" autofocus :aria-invalid="!!errors.title" placeholder="Dê um nome à tarefa">
                    <span v-if="errors.title" class="field-error">{{ errors.title[0] }}</span>
                </label>
                <label class="field">Descrição <span class="optional">opcional</span>
                    <textarea v-model="form.description" name="description" rows="3" maxlength="5000" placeholder="Algum detalhe para lembrar?"></textarea>
                </label>
                <fieldset class="checklist-editor">
                    <legend>Checklist <span class="optional">opcional</span></legend>
                    <ul v-if="form.checklist.length">
                        <li v-for="(item, index) in form.checklist" :key="item.id">
                            <input v-model="item.done" type="checkbox" :aria-label="`Marcar ${item.text || 'item'}`">
                            <input v-model="item.text" :maxlength="MAX_TEXT" :aria-label="`Item ${index + 1} do checklist`" placeholder="Item sem texto será removido">
                            <button type="button" class="icon-button small" :aria-label="`Subir ${item.text}`" :disabled="index === 0" @click="move(index, -1)"><AppIcon name="up" :size="16" /></button>
                            <button type="button" class="icon-button small" :aria-label="`Descer ${item.text}`" :disabled="index === form.checklist.length - 1" @click="move(index, 1)"><AppIcon name="down" :size="16" /></button>
                            <button type="button" class="icon-button small" :aria-label="`Remover ${item.text}`" @click="form.checklist.splice(index, 1)"><AppIcon name="close" :size="16" /></button>
                        </li>
                    </ul>
                    <div class="checklist-add">
                        <input v-model="newItemText" :maxlength="MAX_TEXT" :disabled="form.checklist.length >= MAX_ITEMS" :placeholder="form.checklist.length >= MAX_ITEMS ? `Limite de ${MAX_ITEMS} itens` : 'Novo item, Enter para adicionar'" aria-label="Novo item do checklist" @keydown.enter.prevent="addItem">
                        <button type="button" class="button secondary small" :disabled="!canAddItem" @click="addItem">Adicionar</button>
                    </div>
                    <span v-if="errors.checklist" class="field-error">{{ errors.checklist[0] }}</span>
                </fieldset>
                <div class="form-columns">
                    <label class="field">Dia da tarefa<input v-model="form.date" type="date" name="date" required min="1900-01-01" max="9999-12-31"></label>
                    <label class="field">Prazo <span class="optional">opcional</span><input v-model="form.deadline" type="date" name="deadline" min="1900-01-01" max="9999-12-31"></label>
                </div>
                <div class="form-columns">
                    <label class="field">Prioridade
                        <select v-model="form.priority" name="priority"><option value="LOW">Baixa</option><option value="MEDIUM">Média</option><option value="HIGH">Alta</option></select>
                    </label>
                    <label class="field">Repetição
                        <select v-model="repetition" name="repetition"><option value="none">Não repetir</option><option value="daily">Todo dia</option><option value="weekly">Dias da semana</option></select>
                    </label>
                </div>
                <fieldset v-if="repetition === 'weekly'" class="weekday-picker">
                    <legend>Repetir em</legend>
                    <label v-for="day in weekdayLabels" :key="day.id" :title="day.name"><input v-model="weekdays" type="checkbox" :value="day.id" :aria-label="day.name"><span>{{ day.short }}</span></label>
                    <p v-if="!validWeekly" class="field-error">Escolha pelo menos um dia.</p>
                </fieldset>
                <fieldset v-if="tags.length" class="tag-picker">
                    <legend>Tags <span class="optional">opcional</span></legend>
                    <label v-for="tag in tags" :key="tag.id" :style="{ '--tag-color': tag.color_hex }"><input v-model="form.tag_ids" type="checkbox" :value="tag.id"><span class="tag-label">{{ tag.name }}</span></label>
                </fieldset>
            </fieldset>
            <footer class="dialog-actions">
                <button type="button" class="button secondary" :disabled="busy" @click="emit('close')">Cancelar</button>
                <button type="submit" class="button primary" :disabled="busy || !validWeekly || !form.title.trim()">{{ busy ? 'Salvando…' : 'Salvar tarefa' }}</button>
            </footer>
        </form>
    </AppDialog>
</template>
