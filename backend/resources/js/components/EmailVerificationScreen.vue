<script setup>
import ThemeSwitcher from './ThemeSwitcher.vue';
defineProps({ verified: Boolean, home: String });
</script>

<template>
    <div class="auth-page">
        <header class="auth-header"><a :href="home" class="brand"><span class="brand-marker"></span>Tarefas</a><ThemeSwitcher /></header>
        <main class="auth-card verification-result" aria-labelledby="verification-title">
            <h1 id="verification-title">{{ verified ? 'E-mail confirmado.' : 'Este link não está disponível.' }}</h1>
            <p class="auth-subtitle">{{ verified ? 'Tudo certo! Você pode voltar ao site ou ao app. O status da sua conta será atualizado quando houver conexão.' : 'O link pode ter expirado ou estar incompleto. Entre na sua conta no site ou no app e solicite outro link de confirmação.' }}</p>
            <a :href="home" class="button primary">Ir para o Tarefas</a>
        </main>
    </div>
</template>
