<script setup>
import { computed, reactive, ref } from 'vue';
import { parseDate, shownOn } from '../lib/tasks';
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
});
const repetition = ref(props.task?.recurrence === 'DAILY' ? 'daily' : props.task?.recurrence?.startsWith('WEEKLY:') ? 'weekly' : 'none');
const weekdays = ref(props.task?.recurrence?.startsWith('WEEKLY:') ? props.task.recurrence.slice(7).split(',').map(Number) : [parseDate(form.date).getDay() || 7]);
const weekdayLabels = [{ id: 1, name: 'Segunda', short: 'S' }, { id: 2, name: 'Terça', short: 'T' }, { id: 3, name: 'Quarta', short: 'Q' }, { id: 4, name: 'Quinta', short: 'Q' }, { id: 5, name: 'Sexta', short: 'S' }, { id: 6, name: 'Sábado', short: 'S' }, { id: 7, name: 'Domingo', short: 'D' }];
const validWeekly = computed(() => repetition.value !== 'weekly' || weekdays.value.length > 0);

async function submit() {
    if (!validWeekly.value || !form.title.trim()) return;
    busy.value = true;
    error.value = '';
    errors.value = {};
    try {
        await props.save(props.task, {
            ...form, title: form.title.trim(), deadline: form.deadline || null,
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
