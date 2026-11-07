<script setup>
import { onBeforeUnmount, onMounted, ref, useId } from 'vue';
import AppIcon from './AppIcon.vue';

const props = defineProps({ title: String, busy: Boolean, wide: Boolean });
const emit = defineEmits(['close']);
const dialog = ref(null);
const titleId = useId();
onMounted(() => dialog.value.showModal());
onBeforeUnmount(() => dialog.value.close());

function cancel(event) {
    event.preventDefault();
    if (!props.busy) emit('close');
}
</script>

<template>
    <dialog ref="dialog" class="app-dialog" :class="{ wide }" :aria-labelledby="titleId" @cancel="cancel">
        <header class="dialog-header">
            <h2 :id="titleId">{{ title }}</h2>
            <button type="button" class="icon-button" aria-label="Fechar janela" :disabled="busy" @click="emit('close')"><AppIcon name="close" /></button>
        </header>
        <slot />
    </dialog>
</template>
