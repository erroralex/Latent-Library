<script setup>
/**
 * @file DuplicateDetectiveView.vue
 * @description A specialized view for identifying and resolving visual and exact duplicates using dHash and SHA-256.
 *
 * This view provides a dedicated interface for cleaning up the image library by finding
 * visually identical or near-identical images. It utilizes perceptual hashing (dHash)
 * to detect duplicates regardless of minor differences in resolution or compression,
 * and cryptographic hashing for exact byte-for-byte matches.
 *
 * Key functionalities:
 * - **Visual Comparison:** Displays duplicate pairs side-by-side with a draggable slider
 *   and advanced zoom/pan controls for pixel-perfect verification.
 * - **Metadata Inspection:** Provides detailed metadata panels for both images in a pair,
 *   allowing users to compare generation parameters before deciding which to keep.
 * - **Manual Resolution:** Allows users to selectively keep the left or right image
 *   using keyboard shortcuts (1/2) or UI buttons.
 * - **Automated Cleanup:** Features a "Resolve All" function that automatically keeps
 *   one copy of every duplicate set and moves the rest to the trash.
 * - **Hash Repair:** Includes a manual scan trigger to backfill missing hash data for
 *   images that haven't been fully indexed.
 */
import {ref, onMounted, computed, onUnmounted} from 'vue';
import api from '@/services/api';
import Button from 'primevue/button';
import Divider from 'primevue/divider';
import InputText from 'primevue/inputtext';
import {useToast} from 'primevue/usetoast';
import {useConfirm} from 'primevue/useconfirm';

const toast = useToast();
const confirm = useConfirm();

const pairs = ref([]);
const currentIndex = ref(0);
const sliderPosition = ref(50);
const containerRef = ref(null);
const status = ref({missingHashes: 0, totalImages: 0});
const isScanning = ref(false);

const zoom = ref(1);
const panX = ref(0);
const panY = ref(0);
const isDragging = ref(false);
const lastMouseX = ref(0);
const lastMouseY = ref(0);

const currentPair = computed(() => pairs.value[currentIndex.value] || null);
const leftImage = computed(() => currentPair.value ? `/api/images/content?path=${encodeURIComponent(currentPair.value.left.path)}` : null);
const rightImage = computed(() => currentPair.value ? `/api/images/content?path=${encodeURIComponent(currentPair.value.right.path)}` : null);

const loadStatus = async () => {
  try {
    const res = await api.get('/duplicates/status');
    status.value = res.data;
  } catch (e) {
    console.error("Failed to load duplicate status", e);
  }
};

const loadPairs = async () => {
  try {
    const res = await api.get('/duplicates/pairs');
    pairs.value = res.data;
    currentIndex.value = 0;
    resetZoom();
    await loadStatus();
  } catch (e) {
    console.error("Failed to load duplicate pairs", e);
  }
};

const scanHashes = async () => {
  isScanning.value = true;
  try {
    await api.post('/duplicates/scan');
    toast.add({severity: 'info', summary: 'Scan Complete', detail: 'Finished calculating image hashes', life: 3000});
    await loadPairs();
  } catch (e) {
  } finally {
    isScanning.value = false;
  }
};

const updateSlider = (event) => {
  if (isDragging.value || !containerRef.value) return;
  const rect = containerRef.value.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const percent = (x / rect.width) * 100;
  sliderPosition.value = Math.min(100, Math.max(0, percent));
};

const handleWheel = (e) => {
  e.preventDefault();
  const delta = e.deltaY > 0 ? 0.9 : 1.1;
  const newZoom = zoom.value * delta;
  zoom.value = Math.min(10, Math.max(1, newZoom));
  if (zoom.value === 1) {
    panX.value = 0;
    panY.value = 0;
  }
};

const startDrag = (e) => {
  if (zoom.value > 1) {
    isDragging.value = true;
    lastMouseX.value = e.clientX;
    lastMouseY.value = e.clientY;
  }
};

const onDrag = (e) => {
  if (isDragging.value) {
    const dx = e.clientX - lastMouseX.value;
    const dy = e.clientY - lastMouseY.value;
    panX.value += dx;
    panY.value += dy;
    lastMouseX.value = e.clientX;
    lastMouseY.value = e.clientY;
  }
};

const stopDrag = () => {
  isDragging.value = false;
};

const resetZoom = () => {
  zoom.value = 1;
  panX.value = 0;
  panY.value = 0;
};

const keepLeft = async () => {
  if (!currentPair.value) return;
  await deleteFile(currentPair.value.right.path);
  removeCurrentPair();
};

const keepRight = async () => {
  if (!currentPair.value) return;
  await deleteFile(currentPair.value.left.path);
  removeCurrentPair();
};

const deleteFile = async (path) => {
  try {
    await api.post('/images/batch/delete', [path]);
    toast.add({severity: 'success', summary: 'Resolved', detail: 'Duplicate removed', life: 1000});
  } catch (e) {
  }
};

const removeCurrentPair = () => {
  pairs.value.splice(currentIndex.value, 1);
  if (currentIndex.value >= pairs.value.length) {
    currentIndex.value = Math.max(0, pairs.value.length - 1);
  }
  resetZoom();
};

const skip = () => {
  if (currentIndex.value < pairs.value.length - 1) {
    currentIndex.value++;
  } else {
    currentIndex.value = 0;
  }
  resetZoom();
};

const resolveAll = () => {
  confirm.require({
    message: 'Are you sure you want to delete all duplicates? This will keep one copy and delete the rest.',
    header: 'Resolve All Duplicates',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        const res = await api.post('/duplicates/resolve-all');
        toast.add({severity: 'success', summary: 'Resolved', detail: res.data, life: 3000});
        await loadPairs();
      } catch (e) {
      }
    }
  });
};

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT') return;
  switch (e.key) {
    case '1':
      keepLeft();
      break;
    case '2':
      keepRight();
      break;
    case ' ':
      skip();
      break;
    case 'Escape':
      resetZoom();
      break;
  }
};

onMounted(() => {
  loadPairs();
  window.addEventListener('keydown', handleKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});

const getFileName = (path) => path.split(/[\\/]/).pop();
const getFolderName = (path) => {
  if (!path) return '-';
  const parts = path.split(/[\\/]/);
  parts.pop();
  return parts.pop() || 'Root';
};
const getFileFormat = (path) => {
  if (!path) return '-';
  const parts = path.split('.');
  return parts.length > 1 ? parts.pop().toUpperCase() : 'Unknown';
};

</script>

<template>
  <div class="duplicate-view h-full flex flex-column p-4 overflow-hidden">
    <div class="flex justify-content-between align-items-center mb-4 flex-shrink-0">
      <div>
        <h1 class="text-3xl font-bold m-0 mb-1 text-gradient">Duplicate Detective</h1>
        <div class="flex align-items-center gap-3">
          <p class="text-gray-400 m-0" v-if="pairs.length > 0">
            Found {{ pairs.length }} potential duplicate pairs.
          </p>
          <p class="text-gray-400 m-0" v-else>No duplicates found.</p>

          <span v-if="status.missingHashes > 0" class="text-xs bg-yellow-900 text-yellow-200 px-2 py-1 border-round">
                {{ status.missingHashes }} images missing hashes
            </span>
        </div>
      </div>
      <div class="flex gap-2">
        <Button label="Scan for Duplicates" icon="pi pi-search"
                class="p-button-secondary p-button-outlined"
                @click="scanHashes" :loading="isScanning"/>
        <Button label="Resolve All (Auto)" icon="pi pi-trash" class="p-button-danger p-button-outlined"
                @click="resolveAll" :disabled="pairs.length === 0"/>
      </div>
    </div>

    <div v-if="currentPair" class="flex-grow-1 flex gap-3 overflow-hidden">

      <div class="metadata-panel flex flex-column p-3 border-round shadow-4 overflow-y-auto custom-scrollbar">
        <div class="text-gradient font-bold mb-1 text-overflow-ellipsis overflow-hidden white-space-nowrap"
             :title="currentPair.left.path">
          {{ getFileName(currentPair.left.path) }}
        </div>
        <div class="text-xs text-500 mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap"
             :title="currentPair.left.path">
          <i class="pi pi-folder mr-1"></i>{{ getFolderName(currentPair.left.path) }}
        </div>

        <div class="flex gap-1 mb-3">
          <i v-for="i in 5" :key="i" class="pi text-xs"
             :class="i <= currentPair.left.rating ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
        </div>

        <div class="metadata-grid grid grid-nogutter gap-2">
          <div class="col-12">
            <label class="text-xs text-500">Model</label>
            <InputText :value="currentPair.left.metadata.Model || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Sampler</label>
            <InputText :value="currentPair.left.metadata.Sampler || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Steps</label>
            <InputText :value="currentPair.left.metadata.Steps || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Resolution</label>
            <InputText :value="currentPair.left.metadata.Resolution || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Size</label>
            <InputText :value="currentPair.left.metadata.FileSize || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-12">
            <label class="text-xs text-500">Format</label>
            <InputText :value="getFileFormat(currentPair.left.path)" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-12">
            <label class="text-xs text-500">Prompt</label>
            <div class="glass-box p-2 border-round text-xs line-height-2 select-text overflow-y-auto"
                 style="max-height: 120px;">
              {{ currentPair.left.metadata.Prompt || 'No prompt' }}
            </div>
          </div>
        </div>
        <Button label="Keep Left (1)" icon="pi pi-check" class="p-button-success p-button-sm mt-auto"
                @click="keepLeft"/>
      </div>

      <div class="flex-grow-1 flex flex-column overflow-hidden relative">
        <div
            class="relative flex-grow-1 bg-black-alpha-90 border-round overflow-hidden select-none shadow-8"
            :class="{ 'cursor-move': zoom > 1, 'cursor-crosshair': zoom === 1 }"
            ref="containerRef"
            @mousemove="updateSlider"
            @wheel="handleWheel"
            @mousedown="startDrag"
            @mousemove.capture="onDrag"
            @mouseup="stopDrag"
            @mouseleave="stopDrag">

          <div class="w-full h-full transition-transform transition-duration-100"
               :style="{ transform: `scale(${zoom}) translate(${panX/zoom}px, ${panY/zoom}px)` }">

            <img :src="rightImage"
                 class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
                 style="object-fit: contain;"/>

            <img :src="leftImage"
                 class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
                 style="object-fit: contain;"
                 :style="{ clipPath: `inset(0 ${100 - sliderPosition}% 0 0)` }"/>
          </div>

          <div class="absolute top-0 bottom-0 w-2px bg-primary shadow-4 z-5"
               :style="{ left: sliderPosition + '%', backgroundColor: 'var(--accent-primary)' }">
            <div
                class="slider-handle absolute top-50 left-50 -ml-2 -mt-2 w-2rem h-2rem border-circle flex align-items-center justify-content-center shadow-4">
              <i class="pi pi-arrows-h text-white text-sm"></i>
            </div>
          </div>

          <div class="absolute top-0 left-0 p-3 z-2">
            <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Left (1)</span>
          </div>
          <div class="absolute top-0 right-0 p-3 z-2 text-right">
            <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Right (2)</span>
          </div>
        </div>

        <div class="flex justify-content-center gap-3 mt-3 flex-shrink-0">
          <Button label="Keep Left (1)" icon="pi pi-check" class="p-button-success" @click="keepLeft"/>
          <Button label="Skip (Space)" icon="pi pi-forward" class="p-button-secondary" @click="skip"/>
          <Button label="Keep Right (2)" icon="pi pi-check" class="p-button-success" @click="keepRight"/>
        </div>
      </div>

      <div class="metadata-panel flex flex-column p-3 border-round shadow-4 overflow-y-auto custom-scrollbar">
        <div class="text-gradient font-bold mb-1 text-overflow-ellipsis overflow-hidden white-space-nowrap"
             :title="currentPair.right.path">
          {{ getFileName(currentPair.right.path) }}
        </div>
        <div class="text-xs text-500 mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap"
             :title="currentPair.right.path">
          <i class="pi pi-folder mr-1"></i>{{ getFolderName(currentPair.right.path) }}
        </div>

        <div class="flex gap-1 mb-3">
          <i v-for="i in 5" :key="i" class="pi text-xs"
             :class="i <= currentPair.right.rating ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
        </div>

        <div class="metadata-grid grid grid-nogutter gap-2">
          <div class="col-12">
            <label class="text-xs text-500">Model</label>
            <InputText :value="currentPair.right.metadata.Model || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Sampler</label>
            <InputText :value="currentPair.right.metadata.Sampler || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Steps</label>
            <InputText :value="currentPair.right.metadata.Steps || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Resolution</label>
            <InputText :value="currentPair.right.metadata.Resolution || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-6">
            <label class="text-xs text-500">Size</label>
            <InputText :value="currentPair.right.metadata.FileSize || '-'" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-12">
            <label class="text-xs text-500">Format</label>
            <InputText :value="getFileFormat(currentPair.right.path)" readonly
                       class="w-full p-inputtext-sm glass-input"/>
          </div>
          <div class="col-12">
            <label class="text-xs text-500">Prompt</label>
            <div class="glass-box p-2 border-round text-xs line-height-2 select-text overflow-y-auto"
                 style="max-height: 120px;">
              {{ currentPair.right.metadata.Prompt || 'No prompt' }}
            </div>
          </div>
        </div>
        <Button label="Keep Right (2)" icon="pi pi-check" class="p-button-success p-button-sm mt-auto"
                @click="keepRight"/>
      </div>

    </div>

    <div v-else class="flex-grow-1 flex align-items-center justify-content-center">
      <div class="text-center text-gray-500">
        <i class="pi pi-check-circle text-6xl mb-3"></i>
        <div class="text-xl">All clear! No duplicates detected.</div>
        <p v-if="status.missingHashes > 0" class="mt-3">
          Note: {{ status.missingHashes }} images are missing hashes. Click "Scan for Duplicates" to process them.
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.duplicate-view {
  background: transparent;
}

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

.slider-handle {
  background: linear-gradient(#000, #000) padding-box,
  var(--grad-hover) border-box;
  border: 2px solid transparent;
  z-index: 10;
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

.w-2px {
  width: 2px !important;
}

.cursor-move {
  cursor: move !important;
}
</style>
