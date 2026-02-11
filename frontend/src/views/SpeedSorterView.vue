<script setup>
/**
 * @file SpeedSorterView.vue
 * @description A high-efficiency, keyboard-driven tool for rapid image organization and triage.
 *
 * This view is designed for power users who need to sort through large volumes of images quickly.
 * It implements a "hotkey" system where images can be moved to pre-configured target directories
 * with a single keystroke.
 *
 * Key functionalities:
 * - Keyboard Orchestration: Maps numeric keys (1-5) to target folders and 'X' to deletion.
 * - Rapid Triage: Automatically advances to the next image after an action, minimizing UI interaction time.
 * - Configuration Management: Allows users to define a source "input" folder and up to five "target" folders.
 * - Undo System: Maintains a local history of move actions, allowing users to revert mistakes (Ctrl+Z).
 * - Electron Integration: Leverages native folder selection dialogs when running in an Electron environment.
 */
import {ref, onMounted, onUnmounted, computed} from 'vue';
import axios from 'axios';
import Button from 'primevue/button';
import Toast from 'primevue/toast';
import {useToast} from 'primevue/usetoast';

const toast = useToast();

const inputDir = ref(null);
const targets = ref([]);
const files = ref([]);
const currentIndex = ref(0);
const history = ref([]);

const currentFile = computed(() => files.value[currentIndex.value] || null);
const currentImageUrl = computed(() => currentFile.value ? `http://localhost:8080/api/images/content?path=${encodeURIComponent(currentFile.value)}` : null);
const progress = computed(() => `${currentIndex.value + 1} / ${files.value.length}`);

const loadConfig = async () => {
  try {
    const res = await axios.get('/api/speedsorter/config');
    inputDir.value = res.data.inputDir;
    targets.value = res.data.targets;
    if (inputDir.value) loadFiles();
  } catch (e) {
    console.error("Failed to load config", e);
  }
};

const loadFiles = async () => {
  try {
    const res = await axios.get('/api/speedsorter/files');
    files.value = res.data;
    currentIndex.value = 0;
  } catch (e) {
    console.error("Failed to load files", e);
  }
};

const selectInput = async () => {
  if (window.electronAPI) {
    const path = await window.electronAPI.selectFolder();
    if (path) {
      await axios.post('/api/speedsorter/config/input', null, {params: {path}});
      await loadConfig();
    }
  } else {
    const path = prompt("Enter absolute path to Input Folder:", inputDir.value || "");
    if (path) {
      await axios.post('/api/speedsorter/config/input', null, {params: {path}});
      await loadConfig();
    }
  }
};

const selectTarget = async (index) => {
  if (window.electronAPI) {
    const path = await window.electronAPI.selectFolder();
    if (path) {
      await axios.post('/api/speedsorter/config/target', null, {params: {index, path}});
      await loadConfig();
    }
  } else {
    const current = targets.value.find(t => t.index == index)?.path || "";
    const path = prompt(`Enter absolute path for Target ${index + 1}:`, current);
    if (path) {
      await axios.post('/api/speedsorter/config/target', null, {params: {index, path}});
      await loadConfig();
    }
  }
};

const moveFile = async (targetIndex) => {
  if (!currentFile.value) return;

  const target = targets.value.find(t => t.index == targetIndex);
  if (!target || !target.path) {
    toast.add({
      severity: 'warn',
      summary: 'Target Not Set',
      detail: `Target ${targetIndex + 1} is missing`,
      life: 2000
    });
    return;
  }

  const fileToMove = currentFile.value;
  try {
    const res = await axios.post('/api/speedsorter/move', null, {
      params: {source: fileToMove, targetIndex}
    });

    history.value.push({source: fileToMove, dest: res.data, isDelete: false});

    files.value.splice(currentIndex.value, 1);

    toast.add({
      severity: 'success',
      summary: 'Moved',
      detail: `Moved to ${target.name || 'Target ' + (targetIndex + 1)}`,
      life: 1000
    });
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Move failed', life: 2000});
  }
};

const deleteFile = async () => {
  if (!currentFile.value) return;
  const fileToDelete = currentFile.value;

  try {
    await axios.post('/api/speedsorter/delete', null, {params: {path: fileToDelete}});

    history.value.push({source: fileToDelete, dest: null, isDelete: true});
    files.value.splice(currentIndex.value, 1);

    toast.add({severity: 'error', summary: 'Deleted', detail: 'Moved to Recycle Bin', life: 1000});
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Delete failed', life: 2000});
  }
};

const undo = async () => {
  if (history.value.length === 0) return;

  const lastAction = history.value.pop();
  if (lastAction.isDelete) {
    toast.add({severity: 'warn', summary: 'Cannot Undo', detail: 'Cannot undo delete from Recycle Bin', life: 2000});
    return;
  }

  try {
    await axios.post('/api/speedsorter/undo', null, {
      params: {source: lastAction.dest, original: lastAction.source}
    });

    files.value.splice(currentIndex.value, 0, lastAction.source);
    toast.add({severity: 'info', summary: 'Undone', detail: 'File restored', life: 1000});
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Undo failed', life: 2000});
  }
};

const next = () => {
  if (currentIndex.value < files.value.length - 1) currentIndex.value++;
};

const prev = () => {
  if (currentIndex.value > 0) currentIndex.value--;
};

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT') return;

  if (e.ctrlKey && e.key === 'z') {
    undo();
    return;
  }

  switch (e.key) {
    case '1':
      moveFile(0);
      break;
    case '2':
      moveFile(1);
      break;
    case '3':
      moveFile(2);
      break;
    case '4':
      moveFile(3);
      break;
    case '5':
      moveFile(4);
      break;
    case 'Delete':
    case 'x':
    case 'X':
      deleteFile();
      break;
    case 'ArrowRight':
    case ' ':
      next();
      break;
    case 'ArrowLeft':
      prev();
      break;
  }
};

onMounted(() => {
  loadConfig();
  window.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<template>
  <div class="flex flex-column h-full overflow-hidden p-3">
    <Toast position="bottom-center"/>

    <div class="flex align-items-center justify-content-between mb-3 glass-panel p-3 border-round">
      <div class="flex align-items-center gap-3">
        <span class="text-xl font-bold text-gradient">Speed Sorter</span>
        <Button label="Select Input" icon="pi pi-folder-open" @click="selectInput" class="p-button-sm"/>
        <span class="text-sm text-gray-400 font-italic">{{ inputDir || 'No input folder selected' }}</span>
      </div>
      <span class="text-lg font-bold text-white">{{ progress }}</span>
    </div>

    <div class="flex-grow-1 flex gap-3 overflow-hidden">
      <div
          class="flex-grow-1 glass-panel border-round flex align-items-center justify-content-center relative overflow-hidden">
        <img v-if="currentImageUrl" :src="currentImageUrl" class="max-w-full max-h-full shadow-8"
             style="object-fit: contain;"/>
        <div v-else class="text-2xl text-gray-500">
          {{ inputDir ? 'No images found or all processed' : 'Select an input folder to start' }}
        </div>
      </div>
    </div>

    <div class="mt-3 glass-panel p-3 border-round flex justify-content-center gap-4">
      <div v-for="(target, i) in targets" :key="i" class="flex flex-column align-items-center gap-1">
        <span class="text-gradient font-bold">Key [{{ i + 1 }}]</span>
        <Button :label="target.name || 'Set Folder'"
                @click="selectTarget(i)"
                class="p-button-sm w-10rem text-overflow-ellipsis"
                :class="{ 'p-button-outlined': !target.path }"
                :title="target.path"/>
      </div>

      <div class="border-left-1 border-white-alpha-10 mx-2"></div>

      <div class="flex flex-column gap-1 text-xs text-gray-400 justify-content-center">
        <span><strong class="text-white">DEL / X</strong> : Recycle Bin</span>
        <span><strong class="text-white">Ctrl+Z</strong> : Undo</span>
        <span><strong class="text-white">SPACE</strong> : Skip</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.text-gradient {
  background: var(--app-grad-text, linear-gradient(90deg, #66fcf1, #d870ff));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.glass-panel {
  background: rgba(20, 20, 20, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}
</style>