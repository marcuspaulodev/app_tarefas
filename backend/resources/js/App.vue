<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useWorkspace } from './composables/useWorkspace';
import { endpoints } from './lib/api';
import { addDays, dateGroups, dayGroups, dayTitle, filterByTag, formatDate, isCarried } from './lib/tasks';
import AppDialog from './components/AppDialog.vue';
import AppIcon from './components/AppIcon.vue';
import AuthScreen from './components/AuthScreen.vue';
import MonthCalendar from './components/MonthCalendar.vue';
import TagManager from './components/TagManager.vue';
import TaskForm from './components/TaskForm.vue';
import TaskItem from './components/TaskItem.vue';
import ThemeSwitcher from './components/ThemeSwitcher.vue';

const workspace = useWorkspace();
const recovery = ref(endpoints.passwordRecovery);

async function authenticate(mode, credentials) {
    await workspace.authenticate(mode, credentials);
    recovery.value = null;
    window.history.replaceState(null, '', endpoints.home);
}
const {
    user, booting, notice, today, selectedDate, month, view, searchInput, searchTerm, selectedTag,
    tasks, tags, counts, loading, loadError, pendingTasks, toast, todayCount, upcomingCount,
} = workspace;
const menuOpen = ref(false);
const taskFormOpen = ref(false);
const editingTask = ref(null);
const tagsOpen = ref(false);
const deletingTask = ref(null);
const deleting = ref(false);
const deleteError = ref('');
const loggingOut = ref(false);
const searchField = ref(null);
const visibleTasks = computed(() => filterByTag(tasks.value, selectedTag.value));
const groups = computed(() => {
    if (searchTerm.value || view.value !== 'day') return dateGroups(visibleTasks.value, today.value, view.value === 'completed' || !!searchTerm.value);
    return dayGroups(visibleTasks.value, today.value).map(group => ({
        ...group,
        title: group.id === 'pending' && selectedDate.value === today.value ? 'Para hoje'
            : group.id === 'completed' && selectedDate.value === today.value ? 'Concluídas hoje' : group.title,
    }));
});
const title = computed(() => searchTerm.value ? 'Encontrar uma tarefa' : view.value === 'upcoming' ? 'Próximos dias' : view.value === 'completed' ? 'Concluídas' : dayTitle(selectedDate.value));
const subtitle = computed(() => {
    if (searchTerm.value) return `${visibleTasks.value.length} resultado(s) para “${searchTerm.value}”, em todas as datas`;
    if (view.value === 'upcoming') return `Pendentes de ${formatDate(addDays(today.value, 1))} a ${formatDate(addDays(today.value, 7))}`;
    if (view.value === 'completed') return 'Cada tarefa concluída é um passo a mais.';
    const pending = visibleTasks.value.filter(task => !task.completed).length;
    const carried = visibleTasks.value.filter(task => isCarried(task, today.value)).length;
    return `${pending} ${pending === 1 ? 'pendente' : 'pendentes'}${carried ? `, ${carried} ${carried === 1 ? 'dela vinda' : 'delas vindas'} de outros dias` : ''}`;
});

function selectDay(date) {
    workspace.selectDay(date);
    menuOpen.value = false;
}

function selectView(value) {
    if (value === 'today') workspace.selectToday();
    else workspace.selectView(value);
    menuOpen.value = false;
}

function openTask(task = null) {
    editingTask.value = task;
    taskFormOpen.value = true;
}

function taskSaved(message) {
    taskFormOpen.value = false;
    workspace.notify(message);
}

async function confirmDelete() {
    deleting.value = true;
    deleteError.value = '';
    try {
        await workspace.deleteTask(deletingTask.value);
        deletingTask.value = null;
        workspace.notify('Tarefa excluída.');
    } catch (error) {
        deleteError.value = error.message;
    } finally {
        deleting.value = false;
    }
}

async function logout() {
    loggingOut.value = true;
    await workspace.logout();
    loggingOut.value = false;
    menuOpen.value = false;
    taskFormOpen.value = false;
    tagsOpen.value = false;
    deletingTask.value = null;
}

function shortcut(event) {
    if (recovery.value || !user.value || event.metaKey || event.ctrlKey || event.altKey || document.querySelector('dialog[open]')) return;
    if (event.target.closest('input, textarea, select, [contenteditable="true"]')) return;
    if (event.key.toLowerCase() === 'n') {
        event.preventDefault();
        openTask();
    } else if (event.key === '/') {
        event.preventDefault();
        searchField.value?.focus();
    }
}

watch(() => user.value?.id, () => {
    taskFormOpen.value = false;
    editingTask.value = null;
    tagsOpen.value = false;
    deletingTask.value = null;
    deleteError.value = '';
    menuOpen.value = false;
});

onMounted(() => window.addEventListener('keydown', shortcut));
onUnmounted(() => window.removeEventListener('keydown', shortcut));
</script>

<template>
    <div v-if="booting" class="boot-screen" role="status"><span class="brand"><span class="brand-marker"></span>Tarefas</span><p>Preparando seu espaço…</p></div>
    <AuthScreen v-else-if="!user || recovery" :authenticate="authenticate" :notice="notice" :recovery="recovery" />
    <div v-else class="workspace">
        <a href="#main-content" class="skip-link">Pular para as tarefas</a>
        <aside class="sidebar">
            <header class="sidebar-header">
                <button type="button" class="brand" @click="selectView('today')"><span class="brand-marker"></span>Tarefas</button>
                <button type="button" class="icon-button menu-toggle" aria-label="Menu de navegação" :aria-expanded="menuOpen" aria-controls="sidebar-body" @click="menuOpen = !menuOpen"><AppIcon :name="menuOpen ? 'close' : 'menu'" /></button>
            </header>
            <div id="sidebar-body" class="sidebar-body" :class="{ 'is-open': menuOpen }">
                <nav class="main-nav" aria-label="Navegação principal">
                    <button type="button" :class="{ active: view === 'day' && selectedDate === today && !searchTerm }" :aria-current="view === 'day' && selectedDate === today && !searchTerm ? 'page' : undefined" @click="selectView('today')">Hoje<span>{{ todayCount }}</span></button>
                    <button type="button" :class="{ active: view === 'upcoming' && !searchTerm }" :aria-current="view === 'upcoming' && !searchTerm ? 'page' : undefined" @click="selectView('upcoming')">Próximos dias<span>{{ upcomingCount }}</span></button>
                    <button type="button" @click="tagsOpen = true; menuOpen = false">Tags<AppIcon name="tag" :size="16" /></button>
                    <button type="button" :class="{ active: view === 'completed' && !searchTerm }" :aria-current="view === 'completed' && !searchTerm ? 'page' : undefined" @click="selectView('completed')">Concluídas<AppIcon name="check" :size="16" /></button>
                </nav>
                <MonthCalendar :month="month" :selected="view === 'day' && !searchTerm ? selectedDate : ''" :today="today" :counts="counts" @select="selectDay" @month="month = $event" />
                <div class="sidebar-footer">
                    <span class="appearance-label">Aparência</span>
                    <ThemeSwitcher />
                    <div class="account">
                        <span class="avatar" aria-hidden="true">{{ user.name.slice(0, 1).toUpperCase() }}</span>
                        <div><strong>{{ user.name }}</strong><span>{{ user.email }}</span></div>
                        <button type="button" class="icon-button" :aria-label="loggingOut ? 'Saindo' : 'Sair da conta'" :disabled="loggingOut || pendingTasks.size > 0" @click="logout"><AppIcon name="logout" :size="18" /></button>
                    </div>
                </div>
            </div>
        </aside>

        <main id="main-content" class="sheet" tabindex="-1">
            <header class="workspace-header">
                <div><h1>{{ title }}</h1><p class="page-subtitle" aria-live="polite">{{ loading ? 'Atualizando suas tarefas…' : subtitle }}</p></div>
                <form class="search-field" role="search" @submit.prevent="searchField?.blur()">
                    <AppIcon name="search" :size="17" />
                    <input ref="searchField" v-model="searchInput" type="search" name="search" maxlength="100" placeholder="Buscar tarefas" aria-label="Buscar tarefas em todas as datas">
                    <button v-if="searchInput" type="button" class="icon-button small" aria-label="Limpar busca" @click="workspace.clearSearch()"><AppIcon name="close" :size="15" /></button>
                </form>
            </header>

            <button type="button" class="quick-add" @click="openTask()"><AppIcon name="plus" :size="21" /><span>Adicionar tarefa{{ view === 'day' && !searchTerm ? selectedDate === today ? ' para hoje' : ' para este dia' : '' }}</span><kbd>N</kbd></button>

            <nav v-if="tags.length" class="tag-filters" aria-label="Filtrar por tag">
                <button type="button" :aria-pressed="!selectedTag" @click="selectedTag = ''">Todas</button>
                <button v-for="tag in tags" :key="tag.id" type="button" :aria-pressed="selectedTag === tag.id" :style="{ '--tag-color': tag.color_hex }" @click="selectedTag = selectedTag === tag.id ? '' : tag.id"><span class="tag-label">{{ tag.name }}</span></button>
            </nav>

            <div class="task-list" :aria-busy="loading">
                <div v-if="loading" class="loading-state" role="status"><span class="loading-line"></span><span class="loading-line"></span><span class="loading-line"></span><span class="sr-only">Carregando tarefas</span></div>
                <div v-else-if="loadError" class="empty-state" role="alert"><h2>Não conseguimos carregar as tarefas.</h2><p>{{ loadError }}</p><button type="button" class="button secondary" @click="workspace.reload()">Tentar novamente</button></div>
                <template v-else>
                    <section v-for="group in groups" :key="group.id" class="task-group" :aria-labelledby="`group-${group.id}`">
                        <h2 :id="`group-${group.id}`">{{ group.title }}<span v-if="group.hint">{{ group.hint }}</span><span v-if="view !== 'day' || searchTerm">{{ group.id.slice(0, 4) }}</span></h2>
                        <TaskItem v-for="task in group.tasks" :key="task.id" :task="task" :today="today" :tags="tags" :busy="pendingTasks.has(task.id)" :show-date="false" @edit="openTask" @toggle="workspace.toggleTask" @delete="deletingTask = $event; deleteError = ''" />
                    </section>
                    <div v-if="!visibleTasks.length" class="empty-state">
                        <span class="empty-symbol"><AppIcon :name="searchTerm ? 'search' : view === 'completed' ? 'check' : 'calendar'" :size="32" /></span>
                        <h2>{{ searchTerm ? 'Nenhuma tarefa encontrada.' : selectedTag ? 'Nenhuma tarefa com esta tag.' : view === 'completed' ? 'Um passo de cada vez.' : 'Um pouco de espaço no seu dia.' }}</h2>
                        <p>{{ searchTerm ? 'Tente outro termo ou retire o filtro de tag.' : selectedTag ? 'Escolha outra tag ou veja todas as tarefas.' : view === 'completed' ? 'As tarefas que você concluir vão aparecer aqui.' : view === 'upcoming' ? 'Não há pendências para os próximos 7 dias.' : 'Não há tarefas para este dia. Que tal planejar a próxima?' }}</p>
                        <button v-if="selectedTag" type="button" class="button secondary" @click="selectedTag = ''">Ver todas as tags</button>
                        <button v-else-if="!searchTerm && view !== 'completed'" type="button" class="button secondary" @click="openTask()">Criar uma tarefa</button>
                    </div>
                </template>
            </div>
        </main>

        <TaskForm v-if="taskFormOpen" :task="editingTask" :date="view === 'day' && !searchTerm ? selectedDate : today" :today="today" :tags="tags" :save="workspace.saveTask" @close="taskFormOpen = false" @saved="taskSaved" />
        <TagManager v-if="tagsOpen" :tags="tags" :save="workspace.saveTag" :remove="workspace.deleteTag" @close="tagsOpen = false" @saved="workspace.notify" />
        <AppDialog v-if="deletingTask" title="Excluir tarefa?" :busy="deleting" @close="deletingTask = null">
            <p class="dialog-description">“{{ deletingTask.title }}” será excluída. As outras tarefas serão mantidas.</p>
            <p v-if="deleteError" class="form-error" role="alert">{{ deleteError }}</p>
            <footer class="dialog-actions"><button type="button" class="button secondary" :disabled="deleting" @click="deletingTask = null">Cancelar</button><button type="button" class="button danger" :disabled="deleting" @click="confirmDelete">{{ deleting ? 'Excluindo…' : 'Excluir tarefa' }}</button></footer>
        </AppDialog>
    </div>
    <div v-if="toast" class="toast" role="status">{{ toast }}<button type="button" class="icon-button small" aria-label="Dispensar mensagem" @click="toast = ''"><AppIcon name="close" :size="16" /></button></div>
</template>
