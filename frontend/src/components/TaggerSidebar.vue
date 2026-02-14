<script setup>
/**
 * @file TaggerSidebar.vue
 * @description Sidebar component for AI Auto-Tagging controls using the WD14 model.
 *
 * This component provides a user interface for managing the AI-based image interrogation
 * process. It handles model status monitoring, download orchestration, and the execution
 * of tagging tasks for both individual images and entire directories.
 *
 * Key functionalities:
 * - **Model Management:** Monitors the readiness of the WD14 ONNX model and provides
 *   a guided download experience with progress tracking.
 * - **Confidence Control:** Features a slider to adjust the confidence threshold for
 *   predicted tags, allowing users to balance precision and recall.
 * - **Task Execution:** Triggers asynchronous tagging operations on the backend,
 *   supporting both single-image and batch-folder processing.
 * - **Real-time Feedback:** Polls the backend for model status and provides visual
 *   loading states during inference tasks.
 * - **Metadata Integration:** Automatically refreshes the browser's metadata view
 *   after a single image has been successfully tagged.
 */
import {ref, onMounted, onUnmounted, computed} from 'vue';
import api from '@/services/api';
import Button from 'primevue/button';
import ProgressBar from 'primevue/progressbar';
import Slider from 'primevue/slider';
import {useToast} from 'primevue/usetoast';
import {useBrowserStore} from '@/stores/browser';

const store = useBrowserStore();
const toast = useToast();

const modelStatus = ref({
  ready: false,
  downloading: false,
  progress: 0
});

const threshold = ref(35);
const isTagging = ref(false);
let pollInterval = null;

const checkStatus = async () => {
  try {
    const res = await api.get('/tagger/status');
    modelStatus.value = res.data;
  } catch (e) {
    console.error("Failed to check tagger status", e);
  }
};

const downloadModel = async () => {
  try {
    await api.post('/tagger/download');
    modelStatus.value.downloading = true;
    toast.add({severity: 'info', summary: 'Download Started', detail: 'Downloading WD14 model...', life: 3000});
  } catch (e) {
  }
};

const startTagging = async (single = false) => {
  const path = single ? store.selectedFile : store.lastFolderPath;

  if (!path) {
    toast.add({severity: 'warn', summary: 'No Target', detail: 'Please select a file or folder.', life: 3000});
    return;
  }

  isTagging.value = true;
  try {
    const res = await api.post('/tagger/tag-folder', null, {
      params: {
        path: path,
        threshold: threshold.value / 100.0
      }
    });

    toast.add({severity: 'success', summary: 'Started', detail: res.data, life: 3000});

    if (single) {
      setTimeout(async () => {
        await store.fetchMetadata(path);
        isTagging.value = false;
        toast.add({severity: 'success', summary: 'Done', detail: 'AI Tags updated', life: 2000});
      }, 3000);
    } else {
      setTimeout(() => {
        isTagging.value = false;
        toast.add({severity: 'success', summary: 'Completed', detail: 'Folder tagging finished', life: 3000});
      }, 8000);
    }
  } catch (e) {
    isTagging.value = false;
  }
};

onMounted(() => {
  checkStatus();
  pollInterval = setInterval(checkStatus, 2000);
});

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval);
});
</script>

<template>
  <div class="tagger-sidebar-glass h-full flex flex-column p-3" style="width: 380px; min-width: 380px;">
    <div class="text-gradient font-bold text-xl mb-4 text-center">AI Auto-Tagger</div>

    <div v-if="!modelStatus.ready"
         class="flex-grow-1 flex flex-column align-items-center justify-content-center text-center gap-4">
      <i class="pi pi-cloud-download text-5xl text-primary"></i>
      <h2 class="text-lg font-bold text-white">Model Required</h2>
      <p class="text-sm text-gray-400">The WD14 ONNX model (~150MB) is needed.</p>

      <div v-if="modelStatus.downloading" class="w-full px-4">
        <ProgressBar :value="modelStatus.progress" class="h-1rem mb-2"></ProgressBar>
        <span class="text-xs text-gray-400">Downloading... {{ modelStatus.progress }}%</span>
      </div>

      <Button v-else label="Download Model" icon="pi pi-download" @click="downloadModel" class="p-button-sm"/>
    </div>

    <div v-else class="flex flex-column gap-5">
      <div>
        <label class="block text-xs text-500 font-bold mb-3 uppercase tracking-wider">Confidence Threshold: {{
            threshold
          }}%</label>
        <Slider v-model:modelValue="threshold" :min="10" :max="90"/>
      </div>

      <div class="flex flex-column gap-3">
        <label class="block text-xs text-500 font-bold uppercase tracking-wider">Actions</label>
        <Button label="Tag Current Image" icon="pi pi-tag"
                @click="startTagging(true)" :loading="isTagging" :disabled="!store.selectedFile"/>

        <Button label="Tag Entire Folder" icon="pi pi-tags" class="p-button-outlined"
                @click="startTagging(false)" :loading="isTagging" :disabled="!store.lastFolderPath"/>
      </div>

      <div class="mt-4 p-3 glass-box border-round">
        <div class="text-xs text-500 font-bold mb-2 uppercase">Current Target</div>
        <div class="text-sm text-white text-overflow-ellipsis overflow-hidden white-space-nowrap">
          <i class="pi pi-folder mr-1 text-primary"></i>
          {{ store.lastFolderPath || 'None' }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tagger-sidebar-glass {
  background: var(--bg-sidebar-left);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-right: 1px solid var(--border-light);
  box-shadow: 5px 0 30px rgba(0, 0, 0, 0.3);
}

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.glass-box {
  background: var(--bg-input);
  border: 1px solid var(--border-input);
}

.text-500 {
  color: var(--text-secondary) !important;
}
</style>
