import { ref, watch } from 'vue';

const theme = ref(document.documentElement.dataset.theme ?? 'light');

watch(theme, value => {
    document.documentElement.dataset.theme = value;
    document.querySelector('meta[name="theme-color"]').content = value === 'dark' ? '#131A25' : '#E9EDF2';
    try { localStorage.setItem('tarefas.theme', value); } catch { /* A preferência continua válida nesta página. */ }
}, { immediate: true });

export function useTheme() {
    return { theme };
}
