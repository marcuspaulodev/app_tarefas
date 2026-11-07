<script setup>
import { reactive, ref } from 'vue';
import AppDialog from './AppDialog.vue';
import AppIcon from './AppIcon.vue';

const props = defineProps({ tags: Array, save: Function, remove: Function });
const emit = defineEmits(['close', 'saved']);
const form = reactive({ name: '', color_hex: '#3D5AFE' });
const editing = ref(null);
const deleting = ref(null);
const busy = ref(false);
const error = ref('');
const palette = ['#3D5AFE', '#00C853', '#FF6D00', '#D500F9', '#F50057', '#00B8D4', '#FFAB00', '#6D4C41'];

function edit(tag) {
    editing.value = tag;
    deleting.value = null;
    form.name = tag.name;
    form.color_hex = tag.color_hex;
    error.value = '';
}

function reset() {
    editing.value = null;
    form.name = '';
    form.color_hex = '#3D5AFE';
}

async function save() {
    if (!form.name.trim()) return;
    busy.value = true;
    error.value = '';
    try {
        await props.save(editing.value, { name: form.name.trim(), color_hex: form.color_hex });
        emit('saved', editing.value ? 'Tag atualizada.' : 'Tag criada.');
        reset();
    } catch (failure) {
        error.value = failure.message;
    } finally {
        busy.value = false;
    }
}

async function remove(tag) {
    busy.value = true;
    error.value = '';
    try {
        await props.remove(tag);
        if (editing.value?.id === tag.id) reset();
        deleting.value = null;
        emit('saved', 'Tag excluída.');
    } catch (failure) {
        error.value = failure.message;
    } finally {
        busy.value = false;
    }
}
</script>

<template>
    <AppDialog title="Suas tags" :busy="busy" @close="emit('close')">
        <p class="dialog-description">Um jeito simples de separar trabalho, casa e o que mais fizer sentido.</p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <ul class="tag-manager-list">
            <li v-for="tag in tags" :key="tag.id">
                <div class="tag-manager-row">
                    <span class="tag-label" :style="{ '--tag-color': tag.color_hex }">{{ tag.name }}</span>
                    <div>
                        <button type="button" class="icon-button" :aria-label="`Editar tag ${tag.name}`" :disabled="busy" @click="edit(tag)"><AppIcon name="edit" :size="17" /></button>
                        <button type="button" class="icon-button" :aria-label="`Excluir tag ${tag.name}`" :disabled="busy" @click="deleting = tag.id"><AppIcon name="trash" :size="17" /></button>
                    </div>
                </div>
                <div v-if="deleting === tag.id" class="tag-delete-confirm">
                    <p>Excluir “{{ tag.name }}”? As tarefas serão mantidas e perderão esta tag.</p>
                    <button type="button" class="button secondary small" :disabled="busy" @click="deleting = null">Cancelar</button>
                    <button type="button" class="button danger small" :disabled="busy" @click="remove(tag)">Excluir tag</button>
                </div>
            </li>
        </ul>
        <p v-if="!tags.length" class="muted">Você ainda não tem tags. Crie a primeira abaixo.</p>
        <form class="form-stack tag-editor" @submit.prevent="save">
            <h3>{{ editing ? 'Editar tag' : 'Nova tag' }}</h3>
            <label class="field">Nome da tag<input v-model="form.name" name="tag_name" required maxlength="50" :disabled="busy" placeholder="Ex.: Pessoal"></label>
            <fieldset class="color-picker" :disabled="busy">
                <legend>Cor</legend>
                <label v-for="color in palette" :key="color" :style="{ '--swatch': color }" :title="color"><input v-model="form.color_hex" type="radio" name="tag_color" :value="color" :aria-label="`Cor ${color}`"><span><AppIcon v-if="form.color_hex.toLowerCase() === color.toLowerCase()" name="check" :size="17" /></span></label>
                <label class="custom-color" title="Escolher outra cor"><input v-model="form.color_hex" type="color" aria-label="Cor personalizada"><span>Outra</span></label>
            </fieldset>
            <footer class="dialog-actions">
                <button v-if="editing" type="button" class="button secondary" :disabled="busy" @click="reset">Cancelar edição</button>
                <button type="submit" class="button primary" :disabled="busy || !form.name.trim()">{{ busy ? 'Salvando…' : editing ? 'Salvar tag' : 'Criar tag' }}</button>
            </footer>
        </form>
    </AppDialog>
</template>
