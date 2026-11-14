<script setup>
import { computed, ref } from 'vue';
import { progress } from '../lib/checklist';
import { formatDate, isCarried, recurrenceLabel, shownOn } from '../lib/tasks';
import AppIcon from './AppIcon.vue';

const props = defineProps({ task: Object, today: String, tags: Array, busy: Boolean, showDate: Boolean });
const emit = defineEmits(['toggle', 'edit', 'delete', 'check-item']);
const taskTags = computed(() => props.tags.filter(tag => props.task.tag_ids.includes(tag.id)));
const priorityLabels = { HIGH: 'Alta', MEDIUM: 'Média', LOW: 'Baixa' };
const checklistOpen = ref(false);
const checklistProgress = computed(() => progress(props.task.checklist));

function toggle(event) {
    event.target.checked = props.task.completed;
    emit('toggle', props.task);
}

function checkItem(event, item) {
    event.target.checked = item.done;
    emit('check-item', props.task, item.id);
}
</script>

<template>
    <article class="task-row" :class="{ carried: isCarried(task, today), completed: task.completed, 'is-busy': busy }" :data-task-id="task.id">
        <label class="task-completion" :title="`Prioridade ${priorityLabels[task.priority].toLowerCase()}`">
            <input type="checkbox" :checked="task.completed" :disabled="busy" :aria-label="`${task.completed ? 'Reabrir' : 'Concluir'} ${task.title}`" :class="`priority-${task.priority.toLowerCase()}`" @change="toggle">
            <AppIcon v-if="task.completed" name="check" :size="17" />
        </label>
        <div class="task-main">
            <button type="button" class="task-content" :disabled="busy" :aria-label="`Editar ${task.title}`" @click="emit('edit', task)">
                <span class="task-title">{{ task.title }}</span>
                <span v-if="task.description" class="task-description">{{ task.description }}</span>
                <span class="task-meta">
                    <span v-if="isCarried(task, today)" class="carried-since">Desde {{ formatDate(task.original_date, { weekday: 'short', day: 'numeric', month: 'short' }) }}</span>
                    <span v-if="showDate" class="task-date">{{ formatDate(shownOn(task, today), { day: 'numeric', month: 'short', year: 'numeric' }) }}</span>
                    <span v-for="tag in taskTags" :key="tag.id" class="tag-label" :style="{ '--tag-color': tag.color_hex }">{{ tag.name }}</span>
                    <span v-if="task.recurrence" class="recurrence"><AppIcon name="repeat" :size="13" />{{ recurrenceLabel(task.recurrence) }}</span>
                </span>
            </button>
            <button v-if="checklistProgress.total" type="button" class="checklist-toggle" :class="{ complete: checklistProgress.done === checklistProgress.total }" :aria-expanded="checklistOpen" :aria-label="`Checklist: ${checklistProgress.done} de ${checklistProgress.total} itens marcados`" @click="checklistOpen = !checklistOpen">
                <AppIcon name="checklist" :size="15" />{{ checklistProgress.done }}/{{ checklistProgress.total }}<AppIcon :name="checklistOpen ? 'up' : 'down'" :size="14" />
            </button>
            <ul v-if="checklistOpen && checklistProgress.total" class="task-checklist">
                <li v-for="item in task.checklist" :key="item.id" :class="{ done: item.done }">
                    <label><input type="checkbox" :checked="item.done" :disabled="busy" @change="checkItem($event, item)"><span>{{ item.text }}</span></label>
                </li>
            </ul>
        </div>
        <div class="task-trailing">
            <span v-if="task.deadline" class="deadline" :class="{ overdue: !task.completed && task.deadline < today }">Prazo {{ formatDate(task.deadline) }}</span>
            <button type="button" class="icon-button delete-task" :aria-label="`Excluir ${task.title}`" :disabled="busy" @click="emit('delete', task)"><AppIcon name="trash" :size="17" /></button>
        </div>
    </article>
</template>
