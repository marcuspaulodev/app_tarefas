<script setup>
import { useTheme } from '../composables/useTheme';
import AppIcon from './AppIcon.vue';

const { theme } = useTheme();
</script>

<template>
    <div class="theme-switch" role="group" aria-label="Aparência">
        <button type="button" :aria-pressed="theme === 'light'" @click="theme = 'light'"><AppIcon name="sun" :size="17" />Claro</button>
        <button type="button" :aria-pressed="theme === 'dark'" @click="theme = 'dark'"><AppIcon name="moon" :size="17" />Escuro</button>
    </div>
</template>
