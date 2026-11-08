import { computed, reactive, ref } from 'vue';

export function useAuthForm({ authenticate, requestRecovery, resetPassword, recovery = null, leaveRecovery = () => {} }) {
    const mode = ref(recovery?.mode ?? 'login');
    const busy = ref(false);
    const error = ref('');
    const errors = ref({});
    const status = ref('');
    const form = reactive({ name: '', email: recovery?.email ?? '', password: '', password_confirmation: '' });
    let token = recovery?.token ?? '';
    const registering = computed(() => mode.value === 'register');
    const forgetting = computed(() => mode.value === 'forgot');
    const resetting = computed(() => mode.value === 'reset');

    function changeMode(value) {
        mode.value = value;
        error.value = '';
        errors.value = {};
        status.value = '';
        form.password = '';
        form.password_confirmation = '';
        if (value !== 'reset' && token) {
            token = '';
            leaveRecovery();
        }
    }

    async function submit() {
        if (busy.value) return;
        busy.value = true;
        error.value = '';
        errors.value = {};
        status.value = '';
        try {
            const credentials = { ...form, name: form.name.trim(), email: form.email.trim() };
            if (forgetting.value) {
                const response = await requestRecovery({ email: credentials.email });
                status.value = response.message;
            } else if (resetting.value) {
                const response = await resetPassword({
                    email: credentials.email, token,
                    password: credentials.password, password_confirmation: credentials.password_confirmation,
                });
                changeMode('login');
                status.value = response.message;
            } else {
                await authenticate(mode.value, credentials);
                form.password = '';
                form.password_confirmation = '';
            }
        } catch (failure) {
            errors.value = failure.errors ?? {};
            error.value = errors.value.token?.[0] ?? failure.message;
        } finally {
            busy.value = false;
        }
    }

    return { mode, busy, error, errors, status, form, registering, forgetting, resetting, changeMode, submit };
}
