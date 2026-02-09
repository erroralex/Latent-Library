<script setup>
/**
 * MetadataSidebar.vue
 *
 * Displays detailed metadata for the currently selected image.
 * Shows prompts, negative prompts, generation parameters (Model, Seed, CFG, etc.),
 * and allows users to rate the image.
 * Includes copy-to-clipboard functionality for prompts.
 */
import { useBrowserStore } from '@/stores/browser';
import { computed } from 'vue';
import Sidebar from 'primevue/sidebar';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Chip from 'primevue/chip';
import Divider from 'primevue/divider';

const store = useBrowserStore();
const meta = computed(() => store.currentMetadata);

const fileName = computed(() => {
    if (!store.selectedFile) return 'No Selection';
    return store.selectedFile.split(/[\\/]/).pop();
});

const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
};

const loras = computed(() => {
    if (meta.value.Loras) return meta.value.Loras.split(',').map(s => s.trim());
    return [];
});
</script>

<template>
    <div class="metadata-sidebar h-full flex flex-column surface-card border-left-1 surface-border" style="width: 380px; min-width: 380px;">
        <div class="p-3 border-bottom-1 surface-border">
            <div class="font-bold text-lg mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap" :title="fileName">
                {{ fileName }}
            </div>
            <div class="flex gap-2">
                <Button icon="pi pi-folder-open" class="p-button-sm p-button-secondary" tooltip="Open Location" />
                <Button icon="pi pi-code" class="p-button-sm p-button-secondary" tooltip="Raw Metadata" />
            </div>
        </div>

        <div class="flex-grow-1 overflow-y-auto p-3">
            <div class="flex justify-content-center gap-1 mb-3">
                <Button v-for="i in 5" :key="i"
                        :icon="i <= store.currentRating ? 'pi pi-star-fill' : 'pi pi-star'"
                        class="p-button-text p-button-warning p-0 w-2rem h-2rem"
                        @click="store.setRating(i)" />
            </div>

            <Divider />

            <div class="mb-3">
                <div class="flex justify-content-between align-items-center mb-1">
                    <span class="font-bold text-sm text-500">PROMPT</span>
                    <Button icon="pi pi-copy" class="p-button-text p-button-sm p-0 w-2rem h-2rem" @click="copyToClipboard(meta.Prompt)" />
                </div>
                <div class="surface-ground p-2 border-round text-sm line-height-3 select-text" style="max-height: 150px; overflow-y: auto;">
                    {{ meta.Prompt || 'No prompt found' }}
                </div>
            </div>

            <div class="mb-3">
                <div class="flex justify-content-between align-items-center mb-1">
                    <span class="font-bold text-sm text-500">NEGATIVE PROMPT</span>
                    <Button icon="pi pi-copy" class="p-button-text p-button-sm p-0 w-2rem h-2rem" @click="copyToClipboard(meta.Negative)" />
                </div>
                <div class="surface-ground p-2 border-round text-sm line-height-3 select-text" style="max-height: 100px; overflow-y: auto;">
                    {{ meta.Negative || 'No negative prompt' }}
                </div>
            </div>

            <Divider />

            <div class="grid grid-nogutter gap-3">
                <div class="col-12">
                    <label class="block text-xs text-500 mb-1">Model</label>
                    <InputText :value="meta.Model || '-'" readonly class="w-full p-inputtext-sm" />
                </div>
                <div class="col-6">
                    <label class="block text-xs text-500 mb-1">Sampler</label>
                    <InputText :value="meta.Sampler || '-'" readonly class="w-full p-inputtext-sm" />
                </div>
                <div class="col-6">
                    <label class="block text-xs text-500 mb-1">Scheduler</label>
                    <InputText :value="meta.Scheduler || '-'" readonly class="w-full p-inputtext-sm" />
                </div>
                <div class="col-6">
                    <label class="block text-xs text-500 mb-1">Seed</label>
                    <InputText :value="meta.Seed || '-'" readonly class="w-full p-inputtext-sm" />
                </div>
                <div class="col-6">
                    <label class="block text-xs text-500 mb-1">CFG / Steps</label>
                    <InputText :value="`${meta.CFG || '-'} / ${meta.Steps || '-'}`" readonly class="w-full p-inputtext-sm" />
                </div>
            </div>

            <Divider />

            <div class="mb-3">
                <span class="block font-bold text-sm text-500 mb-2">RESOURCES / LoRAs</span>
                <div class="flex flex-wrap gap-2">
                    <Chip v-for="lora in loras" :key="lora" :label="lora" class="text-xs" />
                    <span v-if="loras.length === 0" class="text-500 text-sm italic">None</span>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.metadata-sidebar {
    background: var(--surface-card);
}
</style>
