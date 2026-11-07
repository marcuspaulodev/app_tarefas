<script setup>
import { computed } from 'vue';
import { calendarDays, formatDate, monthOf, moveMonth } from '../lib/tasks';
import { useTheme } from '../composables/useTheme';
import AppIcon from './AppIcon.vue';

const props = defineProps({ month: String, selected: String, today: String, counts: Object });
const emit = defineEmits(['select', 'month']);
const { theme } = useTheme();
const days = computed(() => calendarDays(props.month));
const heading = computed(() => {
    const label = formatDate(props.month, { month: 'long', year: 'numeric' });
    return label[0].toUpperCase() + label.slice(1);
});
const weekdays = ['Domingo', 'Segunda-feira', 'Terça-feira', 'Quarta-feira', 'Quinta-feira', 'Sexta-feira', 'Sábado'];
</script>

<template>
    <section class="month-calendar" aria-label="Calendário de tarefas">
        <header class="calendar-heading">
            <h2>{{ heading }}</h2>
            <div>
                <button type="button" class="icon-button small" aria-label="Mês anterior" @click="emit('month', moveMonth(month, -1))"><AppIcon name="left" :size="17" /></button>
                <button type="button" class="icon-button small" aria-label="Próximo mês" @click="emit('month', moveMonth(month, 1))"><AppIcon name="right" :size="17" /></button>
            </div>
        </header>
        <div class="calendar-grid">
            <abbr v-for="day in weekdays" :key="day" :title="day" class="weekday">{{ day[0] }}</abbr>
            <button v-for="date in days" :key="date" type="button" class="calendar-day"
                :class="{ outside: monthOf(date) !== monthOf(month), selected: date === selected, today: date === today, some: counts[date] > 0, many: counts[date] >= 3 }"
                :aria-pressed="date === selected" :aria-current="date === today ? 'date' : undefined"
                :aria-label="`${formatDate(date, { dateStyle: 'full' })}: ${counts[date] ?? 0} pendentes`"
                :title="`${counts[date] ?? 0} tarefas pendentes`" @click="emit('select', date)">{{ Number(date.slice(-2)) }}</button>
        </div>
        <p class="calendar-legend">Quanto mais {{ theme === 'dark' ? 'claro' : 'escuro' }} o dia, mais tarefas pendentes.</p>
    </section>
</template>
