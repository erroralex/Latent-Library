<script setup>
/**
 * @file MetadataSidebar.vue
 * @description A comprehensive sidebar component for viewing and interacting with image-specific metadata.
 *
 * This component serves as the primary information hub for the selected image. It parses and displays
 * technical generation parameters (Model, Sampler, Seed, etc.), prompts, and file system information.
 *
 * Key functionalities:
 * - Metadata Visualization: Displays parsed AI generation data in a structured grid.
 * - Prompt Management: Provides dedicated sections for positive and negative prompts with copy-to-clipboard support.
 * - Rating System: Allows users to set and view star ratings for the current image.
 * - System Integration: Enables opening the file's physical location in the OS file explorer.
 * - Raw Data View: Includes a modal dialog for inspecting the unparsed, raw metadata string/JSON.
 */
import {useBrowserStore} from '@/stores/browser';
import {computed, ref} from 'vue';
import api from '@/services/api';
import Sidebar from 'primevue/sidebar';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Textarea from 'primevue/textarea';
import Chip from 'primevue/chip';
import Divider from 'primevue/divider';
import Dialog from 'primevue/dialog';
import {useToast} from 'primevue/usetoast';

const store = useBrowserStore();
const toast = useToast();
const meta = computed(() => store.currentMetadata);
const isRawVisible = ref(false);

const fileName = computed(() => {
  if (!store.selectedFile) return 'No Selection';
  return store.selectedFile.split(/[\\/]/).pop();
});

const copyToClipboard = (text) => {
  navigator.clipboard.writeText(text);
  toast.add({severity: 'info', summary: 'Copied', detail: 'Text copied to clipboard', life: 1500});
};

const loras = computed(() => {
  if (meta.value.Loras) return meta.value.Loras.split(',').map(s => s.trim());
  return [];
});

const openFileLocation = async () => {
  if (!store.selectedFile) return;
  try {
    await api.post('/system/show-in-explorer', null, {params: {path: store.selectedFile}});
  } catch (e) {
    // Error handled by api interceptor
  }
};

const formattedRawMeta = computed(() => {
  if (!meta.value.Raw) return 'No raw data available.';
  try {
    const parsed = JSON.parse(meta.value.Raw);
    return JSON.stringify(parsed, null, 2);
  } catch (e) {
    return meta.value.Raw;
  }
});

const resetRating = () => {
  store.setRating(0);
};

const setRating = (rating, event) => {
  store.setRating(rating);
  if (event && event.target) {
    // Attempt to blur the button itself, or its closest button ancestor
    const button = event.target.closest('button');
    if (button) {
      button.blur();
    } else {
      event.target.blur();
    }
  }
};

</script>

<template>
  <div class="metadata-sidebar-glass h-full flex flex-column" style="width: 380px; min-width: 380px;">
    <div class="p-3 border-bottom-1 border-white-alpha-10" style="background: rgba(255,255,255,0.02)">
      <div class="font-bold text-base mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap text-gradient"
           :title="fileName">
        {{ fileName }}
      </div>
      <div class="flex gap-2">
        <Button icon="pi pi-folder-open" class="p-button-sm p-button-text text-white"
                v-tooltip.bottom="'Open File Location'" @click="openFileLocation"/>
        <Button icon="pi pi-code" class="p-button-sm p-button-text text-white" v-tooltip.bottom="'View Raw Metadata'"
                @click="isRawVisible = true"/>
      </div>
    </div>

    <div class="flex-grow-1 overflow-y-auto p-3 custom-scrollbar">
      <div class="flex flex-column align-items-center mb-3">
        <div class="flex justify-content-center gap-1">
          <Button v-for="i in 5" :key="i"
                  :icon="i <= store.currentRating ? 'pi pi-star-fill' : 'pi pi-star'"
                  class="p-button-text p-button-warning p-0 w-2rem h-2rem star-btn"
                  :class="{ 'text-yellow-500': i <= store.currentRating }"
                  @click="(e) => setRating(i, e)"/>
        </div>
        <span class="text-xs text-500 cursor-pointer hover:text-white mt-1" @click="resetRating">Reset</span>
      </div>

      <div class="text-gradient font-bold text-xl mb-3 text-center">Metadata</div>

      <Divider class="border-white-alpha-10"/>

      <div class="mb-3">
        <div class="flex justify-content-between align-items-center mb-1">
          <span class="font-bold text-sm text-500">PROMPT</span>
          <Button icon="pi pi-copy" class="p-button-text p-button-sm p-0 w-2rem h-2rem text-500"
                  v-tooltip.left="'Copy Prompt'" @click="copyToClipboard(meta.Prompt)"/>
        </div>
        <div class="glass-box p-2 border-round text-sm line-height-3 select-text"
             style="max-height: 150px; overflow-y: auto;">
          {{ meta.Prompt || 'No prompt found' }}
        </div>
      </div>

      <div class="mb-3">
        <div class="flex justify-content-between align-items-center mb-1">
          <span class="font-bold text-sm text-red-400">NEGATIVE PROMPT</span>
          <Button icon="pi pi-copy" class="p-button-text p-button-sm p-0 w-2rem h-2rem text-500"
                  v-tooltip.left="'Copy Negative Prompt'" @click="copyToClipboard(meta.Negative)"/>
        </div>
        <div class="glass-box p-2 border-round text-sm line-height-3 select-text"
             style="max-height: 100px; overflow-y: auto;">
          {{ meta.Negative || 'No negative prompt' }}
        </div>
      </div>

      <Divider class="border-white-alpha-10"/>

      <div class="grid grid-nogutter gap-3">
        <div class="col-12">
          <label class="block text-xs text-500 mb-1">Model</label>
          <InputText :value="meta.Model || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="block text-xs text-500 mb-1">Sampler</label>
          <InputText :value="meta.Sampler || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="block text-xs text-500 mb-1">Scheduler</label>
          <InputText :value="meta.Scheduler || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-12">
          <label class="block text-xs text-500 mb-1">Seed</label>
          <InputText :value="meta.Seed || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>

        <div class="col-3">
          <label class="block text-xs text-500 mb-1">CFG</label>
          <InputText :value="meta.CFG || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-3">
          <label class="block text-xs text-500 mb-1">Steps</label>
          <InputText :value="meta.Steps || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-5">
          <label class="block text-xs text-500 mb-1">Resolution</label>
          <InputText :value="meta.Resolution || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-5">
          <label class="block text-xs text-500 mb-1">Size</label>
          <InputText :value="meta.FileSize || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
      </div>

      <Divider class="border-white-alpha-10"/>

      <div class="mb-3">
        <span class="block font-bold text-sm text-500 mb-2">LoRAs</span>
        <div class="flex flex-wrap gap-2">
          <Chip v-for="lora in loras" :key="lora" :label="lora" class="lora-chip text-sm"/>
          <span v-if="loras.length === 0" class="text-500 text-sm italic">None</span>
        </div>
      </div>
    </div>

    <Dialog v-model:visible="isRawVisible" modal header="Raw Metadata" class="glass-dialog w-6"
            :style="{ width: '50vw' }">
      <pre class="raw-meta-pre">{{ formattedRawMeta }}</pre>
      <template #footer>
        <Button label="Copy Text" icon="pi pi-copy" @click="copyToClipboard(formattedRawMeta)"
                class="p-button-secondary"/>
        <Button label="Close" icon="pi pi-times" @click="isRawVisible = false" autofocus/>
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.metadata-sidebar-glass {
  background: var(--bg-sidebar-right);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-left: 1px solid var(--border-light);
  box-shadow: -5px 0 30px rgba(0, 0, 0, 0.3);
}

.glass-box {
  background: var(--bg-input);
  border: 1px solid var(--border-input);
}

.glass-input {
  background: var(--bg-input) !important;
  border: 1px solid var(--border-input) !important;
  color: var(--text-primary);
}

.glass-input:enabled:focus {
  box-shadow: none !important;
  outline: none !important;
  border-color: transparent !important;
  border-image: var(--grad-hover) 1 !important;
}

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.raw-meta-pre {
  background-color: var(--bg-input);
  border: 1px solid var(--border-input);
  border-radius: 6px;
  padding: 1rem;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 60vh;
  overflow-y: auto;
  color: var(--text-secondary);
}

.lora-chip {
  background: transparent !important;
  color: var(--text-primary) !important;
  border: none !important;
  position: relative;
  z-index: 1;
  overflow: visible !important;
}

.lora-chip::before {
  content: '';
  position: absolute;
  inset: -1px;
  background: var(--grad-hover);
  border-radius: 16px;
  z-index: -2;
}

.lora-chip::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--bg-btn-inner); /* Use theme variable */
  border-radius: 16px;
  z-index: -1;
}

:deep(.glass-dialog) {
  background: var(--bg-panel-opaque) !important;
  border: 1px solid var(--border-input) !important;
  box-shadow: 0 0 40px rgba(0, 0, 0, 0.8) !important;
  backdrop-filter: var(--glass-blur) !important;
  color: var(--text-primary) !important;
}

:deep(.glass-dialog .p-dialog-header) {
  background: transparent !important;
  color: var(--text-primary) !important;
  border-bottom: 1px solid var(--border-input) !important;
  padding: 1.5rem !important;
}

:deep(.glass-dialog .p-dialog-content) {
  background: transparent !important;
  color: var(--text-primary) !important;
  padding: 1.5rem !important;
}

:deep(.glass-dialog .p-dialog-footer) {
  background: transparent !important;
  border-top: 1px solid var(--border-input) !important;
  padding: 1.5rem !important;
}

:deep(.glass-dialog .p-dialog-header-icon) {
  color: var(--text-secondary) !important;
}

:deep(.glass-dialog .p-dialog-header-icon:hover) {
  background: rgba(255, 255, 255, 0.1) !important;
  color: var(--text-primary) !important;
}

.text-yellow-500 {
  color: var(--status-warning) !important;
}

.text-red-400 {
  color: var(--status-danger) !important;
}

.text-500 {
  color: var(--text-secondary) !important;
}

.text-white {
  color: var(--text-primary) !important;
}

.border-white-alpha-10 {
  border-color: var(--border-light) !important;
}

/* Explicitly remove focus outline for star buttons */
.star-btn:focus {
  box-shadow: none !important;
  outline: none !important;
}
</style>
