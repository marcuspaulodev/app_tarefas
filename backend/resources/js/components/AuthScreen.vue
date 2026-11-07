<script setup>
import { computed, reactive, ref } from 'vue';
import AppIcon from './AppIcon.vue';
import ThemeSwitcher from './ThemeSwitcher.vue';

const props = defineProps({ authenticate: Function, notice: String });
const mode = ref('login');
const busy = ref(false);
const error = ref('');
const errors = ref({});
const form = reactive({ name: '', email: '', password: '', password_confirmation: '' });
const registering = computed(() => mode.value === 'register');

function changeMode(value) {
    mode.value = value;
    error.value = '';
    errors.value = {};
    form.password = '';
    form.password_confirmation = '';
}

async function submit() {
    busy.value = true;
    error.value = '';
    errors.value = {};
    try {
        await props.authenticate(mode.value, { ...form, name: form.name.trim(), email: form.email.trim() });
    } catch (failure) {
        error.value = failure.message;
        errors.value = failure.errors ?? {};
    } finally {
        busy.value = false;
    }
}
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
                <div class="auth-tabs" role="group" aria-label="Acesso à conta">
                    <button type="button" :aria-pressed="!registering" :disabled="busy" @click="changeMode('login')">Entrar</button>
                    <button type="button" :aria-pressed="registering" :disabled="busy" @click="changeMode('register')">Criar conta</button>
                </div>
                <h2 id="auth-title">{{ registering ? 'Vamos começar?' : 'Bom ter você aqui.' }}</h2>
                <p class="auth-subtitle">{{ registering ? 'Crie sua conta e organize o primeiro dia.' : 'Entre para continuar de onde parou.' }}</p>
                <p v-if="notice && !error" class="form-notice" role="status">{{ notice }}</p>
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
                    <label class="field">Senha
                        <input v-model="form.password" type="password" name="password" :autocomplete="registering ? 'new-password' : 'current-password'" required :minlength="registering ? 8 : undefined" :disabled="busy" :aria-invalid="!!errors.password" aria-describedby="password-error" :placeholder="registering ? 'Pelo menos 8 caracteres' : 'Sua senha'">
                        <span v-if="errors.password" id="password-error" class="field-error">{{ errors.password[0] }}</span>
                    </label>
                    <label v-if="registering" class="field">Confirmar senha
                        <input v-model="form.password_confirmation" type="password" name="password_confirmation" autocomplete="new-password" required minlength="8" :disabled="busy">
                    </label>
                    <button type="submit" class="button primary auth-submit" :disabled="busy">{{ busy ? 'Aguarde…' : registering ? 'Criar minha conta' : 'Entrar' }}<AppIcon v-if="!busy" name="arrow" :size="19" /></button>
                </form>
            </section>
        </main>
    </div>
</template>
