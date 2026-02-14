<script setup>
/**
 * @file ComparisonMetadataPanel.vue
 * @description A reusable sidebar component for displaying and comparing image metadata.
 *
 * This component is designed for use within comparison-heavy views (like the Comparator
 * or Duplicate Detective). It provides a structured, read-only view of an image's
 * technical generation parameters, including prompts, model info, and physical
 * attributes. It also supports optional action buttons and system-level integration
 * for opening file locations.
 *
 * Key functionalities:
 * - **Metadata Visualization:** Renders technical parameters in a compact grid, with
 *   specialized handling for long text blocks like prompts and negative prompts.
 * - **Path Resolution:** Automatically extracts and displays the filename, parent
 *   folder, and file format from the provided path.
 * - **Interactive Actions:** Supports a customizable primary action button (e.g., "Keep Left")
 *   via the {@code actionLabel} prop and {@code action} event.
 * - **System Integration:** Allows users to reveal the physical file in the OS
 *   explorer by clicking the folder name.
 * - **Visual Feedback:** Displays star ratings and uses theme-consistent styling
 *   for high-contrast readability.
 */
import {computed} from 'vue';
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';
import api from '@/services/api';

const props = defineProps({
  metadata: {type: Object, required: false},
  path: {type: String, required: false},
  title: {type: String, required: false},
  actionLabel: {type: String, required: false},
  rating: {type: Number, default: 0}
});

const emit = defineEmits(['action']);

const fileName = computed(() => props.path ? props.path.split(/[\\/]/).pop() : 'Unknown');
const folderName = computed(() => {
  if (!props.path) return '-';
  const parts = props.path.split(/[\\/]/);
  parts.pop();
  return parts.pop() || 'Root';
});

const fileFormat = computed(() => {
  if (!props.path) return '-';
  const parts = props.path.split('.');
  return parts.length > 1 ? parts.pop().toUpperCase() : 'Unknown';
});

const openFileLocation = async () => {
  if (!props.path) return;
  try {
    await api.post('/system/show-in-explorer', null, {params: {path: props.path}});
  } catch (e) {
    console.error("Failed to open file location", e);
  }
};
</script>

<template>
  <div class="metadata-panel flex flex-column p-3 border-round shadow-4 overflow-y-auto custom-scrollbar">
    <template v-if="metadata">
      <div class="text-gradient font-bold mb-1 text-overflow-ellipsis overflow-hidden white-space-nowrap" :title="path">
        {{ title || fileName }}
      </div>
      <div
          class="text-xs text-500 mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap cursor-pointer hover:text-white transition-colors"
          @click="openFileLocation" title="Click to open folder">
        <i class="pi pi-folder mr-1 text-primary"></i>{{ folderName }}
      </div>

      <div class="flex gap-1 mb-3">
        <i v-for="i in 5" :key="i" class="pi text-xs"
           :class="i <= (rating || metadata.rating || 0) ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
      </div>

      <div class="metadata-grid grid grid-nogutter gap-2">
        <div class="col-12">
          <label class="text-xs text-500">Model</label>
          <InputText :value="metadata.Model || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="text-xs text-500">Sampler</label>
          <InputText :value="metadata.Sampler || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="text-xs text-500">Scheduler</label>
          <InputText :value="metadata.Scheduler || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="text-xs text-500">Steps</label>
          <InputText :value="metadata.Steps || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="text-xs text-500">Resolution</label>
          <InputText :value="metadata.Resolution || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="text-xs text-500">Size</label>
          <InputText :value="metadata.FileSize || '-'" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-6">
          <label class="text-xs text-500">Format</label>
          <InputText :value="fileFormat" readonly class="w-full p-inputtext-sm glass-input"/>
        </div>
        <div class="col-12">
          <label class="text-xs text-500">Prompt</label>
          <div class="glass-box p-2 border-round text-xs line-height-2 select-text overflow-y-auto"
               style="max-height: 100px;">
            {{ metadata.Prompt || 'No prompt' }}
          </div>
        </div>
        <div class="col-12">
          <label class="text-xs text-red-400 font-bold">Negative Prompt</label>
          <div class="glass-box p-2 border-round text-xs line-height-2 select-text overflow-y-auto"
               style="max-height: 80px;">
            {{ metadata.Negative || 'None' }}
          </div>
        </div>
      </div>

      <Button v-if="actionLabel" :label="actionLabel" icon="pi pi-check"
              class="p-button-success p-button-sm mt-auto" @click="emit('action')"/>
    </template>
    <div v-else
         class="flex-grow-1 flex align-items-center justify-content-center text-gray-600 italic text-sm text-center">
      No metadata available
    </div>
  </div>
</template>

<style scoped>
.metadata-panel {
  width: 300px;
  min-width: 300px;
  background: var(--bg-sidebar-right);
  backdrop-filter: var(--glass-blur);
  border: 1px solid var(--border-light);
}

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
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

.text-yellow-500 {
  color: var(--status-warning) !important;
}

.text-500 {
  color: var(--text-secondary) !important;
}

.text-primary {
  color: var(--accent-primary) !important;
}

.text-red-400 {
  color: var(--status-danger) !important;
}
</style>
