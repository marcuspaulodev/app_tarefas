<script setup>
import { computed } from 'vue';
import { useAuthForm } from '../composables/useAuthForm';
import { endpoints, initializeCsrf, request } from '../lib/api';
import AppIcon from './AppIcon.vue';
import ThemeSwitcher from './ThemeSwitcher.vue';

const props = defineProps({ authenticate: Function, notice: String, recovery: Object });
async function send(url, body) {
    await initializeCsrf();
    return request(url, { method: 'POST', body });
}
const { mode, busy, error, errors, status, form, registering, forgetting, resetting, changeMode, submit } = useAuthForm({
    authenticate: (...args) => props.authenticate(...args),
    requestRecovery: body => send(endpoints.forgotPassword, body),
    resetPassword: body => send(endpoints.resetPassword, body),
    recovery: props.recovery,
    leaveRecovery: () => window.history.replaceState(null, '', endpoints.home),
});
const title = computed(() => forgetting.value ? 'Esqueceu sua senha?' : resetting.value ? 'Uma nova senha.' : registering.value ? 'Vamos começar?' : 'Bom ter você aqui.');
const subtitle = computed(() => forgetting.value ? 'Informe o e-mail da sua conta para receber um link de recuperação.'
    : resetting.value ? 'Escolha uma senha com pelo menos 8 caracteres para entrar no site e no app.'
        : registering.value ? 'Crie sua conta e organize o primeiro dia.' : 'Entre para continuar de onde parou.');
const submitLabel = computed(() => forgetting.value ? 'Enviar link' : resetting.value ? 'Salvar nova senha' : registering.value ? 'Criar minha conta' : 'Entrar');
const message = computed(() => status.value || (mode.value === 'login' ? props.notice : ''));
</script>

<template>
    <div class="auth-page">
        <header class="auth-header">
            <a href="/" class="brand" aria-label="Tarefas, início"><span class="brand-marker"></span>Tarefas</a>
            <ThemeSwitcher />
        </header>
        <main class="auth-layout">
            <section class="auth-intro">
                <span class="eyebrow">UM DIA DE CADA VEZ</span>
                <h1>Espaço para<br>o que <span>importa.</span></h1>
                <p>Organize as tarefas, encontre seu ritmo e deixe cada dia um pouco mais leve.</p>
                <div class="auth-illustration" aria-hidden="true">
                    <div class="sample-day">Seu dia, com clareza.</div>
                    <div class="sample-task"><span class="sample-checkbox done"><AppIcon name="check" :size="16" /></span><s>Dar o primeiro passo</s></div>
                    <div class="sample-task"><span class="sample-checkbox"></span><span class="sample-highlight">Tirar uma ideia do papel</span></div>
                    <div class="sample-task"><span class="sample-checkbox"></span>Reservar um tempo para você</div>
                    <span class="sample-note">Tudo no seu tempo.</span>
                </div>
            </section>
            <section class="auth-card" aria-labelledby="auth-title">
                <div v-if="!forgetting && !resetting" class="auth-tabs" role="group" aria-label="Acesso à conta">
                    <button type="button" :aria-pressed="!registering" :disabled="busy" @click="changeMode('login')">Entrar</button>
                    <button type="button" :aria-pressed="registering" :disabled="busy" @click="changeMode('register')">Criar conta</button>
                </div>
                <h2 id="auth-title">{{ title }}</h2>
                <p class="auth-subtitle">{{ subtitle }}</p>
                <p v-if="message && !error" class="form-notice" role="status">{{ message }}</p>
                <p v-if="error" class="form-error" role="alert">{{ error }}</p>
                <form class="form-stack" @submit.prevent="submit">
                    <label v-if="registering" class="field">Nome
                        <input v-model="form.name" name="name" autocomplete="name" required maxlength="255" :disabled="busy" :aria-invalid="!!errors.name" aria-describedby="name-error">
                        <span v-if="errors.name" id="name-error" class="field-error">{{ errors.name[0] }}</span>
                    </label>
                    <label class="field">E-mail
                        <input v-model="form.email" type="email" name="email" autocomplete="username" required maxlength="255" :disabled="busy" :aria-invalid="!!errors.email" aria-describedby="email-error" placeholder="voce@exemplo.com">
                        <span v-if="errors.email" id="email-error" class="field-error">{{ errors.email[0] }}</span>
                    </label>
                    <label v-if="!forgetting" class="field">{{ resetting ? 'Nova senha' : 'Senha' }}
                        <input v-model="form.password" type="password" name="password" :autocomplete="registering || resetting ? 'new-password' : 'current-password'" required :minlength="registering || resetting ? 8 : undefined" :disabled="busy" :aria-invalid="!!errors.password" aria-describedby="password-error" :placeholder="registering || resetting ? 'Pelo menos 8 caracteres' : 'Sua senha'">
                        <span v-if="errors.password" id="password-error" class="field-error">{{ errors.password[0] }}</span>
                    </label>
                    <label v-if="registering || resetting" class="field">Confirmar senha
                        <input v-model="form.password_confirmation" type="password" name="password_confirmation" autocomplete="new-password" required minlength="8" :disabled="busy" :aria-invalid="!!errors.password_confirmation" aria-describedby="confirmation-error">
                        <span v-if="errors.password_confirmation" id="confirmation-error" class="field-error">{{ errors.password_confirmation[0] }}</span>
                    </label>
                    <button type="submit" class="button primary auth-submit" :disabled="busy">{{ busy ? 'Aguarde…' : submitLabel }}<AppIcon v-if="!busy" name="arrow" :size="19" /></button>
                </form>
                <div class="auth-links">
                    <button v-if="mode === 'login' || resetting" type="button" class="text-button" :disabled="busy" @click="changeMode('forgot')">{{ resetting ? 'Solicitar outro link' : 'Esqueci minha senha' }}</button>
                    <button v-if="forgetting || resetting" type="button" class="text-button" :disabled="busy" @click="changeMode('login')">Voltar para entrar</button>
                </div>
            </section>
        </main>
    </div>
</template>
