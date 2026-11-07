import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { endpoints, initializeCsrf, request } from '../lib/api';
import { addDays, calendarDays, localDate, monthOf } from '../lib/tasks';

export function useWorkspace() {
    const user = ref(null);
    const booting = ref(true);
    const notice = ref('');
    const today = ref(localDate());
    const selectedDate = ref(today.value);
    const month = ref(monthOf(today.value));
    const view = ref('day');
    const searchInput = ref('');
    const searchTerm = ref('');
    const selectedTag = ref('');
    const tasks = ref([]);
    const tags = ref([]);
    const counts = ref({});
    const loading = ref(false);
    const loadError = ref('');
    const pendingTasks = ref(new Set());
    const toast = ref('');
    let sequence = 0;
    let controller;
    let searchTimer;
    let toastTimer;
    let dayTimer;

    const todayCount = computed(() => counts.value[today.value] ?? 0);
    const upcomingCount = computed(() => Array.from({ length: 7 }, (_, index) => counts.value[addDays(today.value, index + 1)] ?? 0).reduce((sum, count) => sum + count, 0));

    function notify(message) {
        toast.value = message;
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => { toast.value = ''; }, 5000);
    }

    function handleError(error) {
        if (error.status === 401 || error.status === 419) {
            notice.value = error.message;
            user.value = null;
        } else {
            notify(error.message);
        }
    }

    async function initialize() {
        booting.value = true;
        try {
            user.value = (await request(endpoints.user)).data;
        } catch (error) {
            if (error.status !== 401) notice.value = error.message;
        } finally {
            booting.value = false;
        }
    }

    async function authenticate(mode, credentials) {
        await initializeCsrf();
        const response = await request(endpoints[mode], { method: 'POST', body: credentials });
        notice.value = '';
        selectToday();
        user.value = response.data;
    }

    async function logout() {
        try {
            await request(endpoints.logout, { method: 'POST' });
            notice.value = '';
            user.value = null;
        } catch (error) {
            handleError(error);
        }
    }

    function clearSearch() {
        clearTimeout(searchTimer);
        searchInput.value = '';
        searchTerm.value = '';
    }

    function selectDay(date) {
        clearSearch();
        selectedDate.value = date;
        month.value = monthOf(date);
        view.value = 'day';
    }

    function selectToday() {
        selectDay(today.value);
    }

    function selectView(value) {
        clearSearch();
        view.value = value;
    }

    async function reload({ background = false } = {}) {
        const current = ++sequence;
        controller?.abort();
        if (!user.value) {
            tasks.value = [];
            tags.value = [];
            counts.value = {};
            selectedTag.value = '';
            loading.value = false;
            return;
        }

        controller = new AbortController();
        const { signal } = controller;
        if (!background) loading.value = true;
        loadError.value = '';
        const days = calendarDays(month.value);
        const start = [days[0], today.value].sort()[0];
        const end = [days.at(-1), addDays(today.value, 7)].sort().at(-1);
        const query = searchTerm.value
            ? { search: searchTerm.value }
            : view.value === 'upcoming'
                ? { start: addDays(today.value, 1), end: addDays(today.value, 7), today: today.value, completed: 0 }
                : view.value === 'completed'
                    ? { completed: 1 }
                    : { date: selectedDate.value, today: today.value };

        try {
            const [taskResponse, tagResponse, countResponse] = await Promise.all([
                request(endpoints.tasks, { query, signal }),
                request(endpoints.tags, { signal }),
                request(endpoints.counts, { query: { start, end, today: today.value }, signal }),
            ]);
            if (current !== sequence) return;
            tasks.value = taskResponse.data;
            tags.value = tagResponse.data;
            counts.value = Object.fromEntries(countResponse.data.map(item => [item.date, item.count]));
            if (selectedTag.value && !tags.value.some(tag => tag.id === selectedTag.value)) selectedTag.value = '';
        } catch (error) {
            if (error.name === 'AbortError' || current !== sequence) return;
            loadError.value = error.message;
            if (error.status === 401 || error.status === 419) handleError(error);
        } finally {
            if (current === sequence) loading.value = false;
        }
    }

    async function mutate(url, options) {
        try {
            const response = await request(url, options);
            await reload({ background: true });
            return response;
        } catch (error) {
            if (error.status === 401 || error.status === 419) handleError(error);
            throw error;
        }
    }

    async function saveTask(task, attributes) {
        return mutate(task ? `${endpoints.tasks}/${task.id}` : endpoints.tasks, {
            method: task ? 'PATCH' : 'POST',
            body: { ...attributes, ...(task ? { today: today.value } : {}) },
        });
    }

    async function toggleTask(task) {
        if (pendingTasks.value.has(task.id)) return;
        pendingTasks.value.add(task.id);
        try {
            await mutate(`${endpoints.tasks}/${task.id}/toggle`, { method: 'POST', body: { today: today.value } });
            notify(task.completed ? 'Tarefa reaberta.' : 'Tarefa concluída.');
        } catch (error) {
            handleError(error);
        } finally {
            pendingTasks.value.delete(task.id);
        }
    }

    function saveTag(tag, attributes) {
        return mutate(tag ? `${endpoints.tags}/${tag.id}` : endpoints.tags, { method: tag ? 'PATCH' : 'POST', body: attributes });
    }

    function deleteTag(tag) {
        return mutate(`${endpoints.tags}/${tag.id}`, { method: 'DELETE' });
    }

    function deleteTask(task) {
        return mutate(`${endpoints.tasks}/${task.id}`, { method: 'DELETE' });
    }

    function updateDay() {
        const date = localDate();
        if (date === today.value) return;
        if (view.value === 'day' && selectedDate.value === today.value) selectDay(date);
        today.value = date;
    }

    watch(searchInput, value => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(() => { searchTerm.value = value.trim(); }, 300);
    });
    watch([() => user.value?.id, selectedDate, view, searchTerm, month, today], () => reload());

    onMounted(() => {
        initialize();
        dayTimer = setInterval(updateDay, 60000);
        window.addEventListener('focus', updateDay);
    });
    onUnmounted(() => {
        controller?.abort();
        clearTimeout(searchTimer);
        clearTimeout(toastTimer);
        clearInterval(dayTimer);
        window.removeEventListener('focus', updateDay);
    });

    return {
        user, booting, notice, today, selectedDate, month, view, searchInput, searchTerm, selectedTag,
        tasks, tags, counts, loading, loadError, pendingTasks, toast, todayCount, upcomingCount,
        authenticate, logout, selectDay, selectToday, selectView, clearSearch, reload, notify, handleError,
        saveTask, toggleTask, deleteTask, saveTag, deleteTag,
    };
}
