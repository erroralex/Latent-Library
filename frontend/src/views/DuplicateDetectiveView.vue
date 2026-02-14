<script setup>
/**
 * @file DuplicateDetectiveView.vue
 * @description A specialized view for identifying and resolving visual duplicates using dHash.
 *
 * This view provides a dedicated interface for cleaning up the image library by finding
 * visually identical or near-identical images. It utilizes perceptual hashing (dHash)
 * to detect duplicates regardless of minor differences in resolution or compression.
 *
 * Key functionalities:
 * - Visual Comparison: Displays duplicate pairs side-by-side with a draggable slider
 *   for pixel-perfect verification.
 * - Manual Resolution: Allows users to selectively delete the left or right image
 *   of a pair using keyboard shortcuts (1/2) or UI buttons.
 * - Automated Cleanup: Features a "Resolve All" function that automatically keeps
 *   one copy of every duplicate pair and moves the rest to the trash.
 * - Keyboard Orchestration: Supports rapid triage with hotkeys for deletion and skipping.
 * - Real-time Feedback: Integrates with the backend to perform immediate file system
 *   operations and update the local pair list.
 */
import {ref, onMounted, computed, onUnmounted} from 'vue';
import api from '@/services/api';
import Button from 'primevue/button';
import {useToast} from 'primevue/usetoast';
import {useConfirm} from 'primevue/useconfirm';

const toast = useToast();
const confirm = useConfirm();

const pairs = ref([]);
const currentIndex = ref(0);
const sliderPosition = ref(50);
const containerRef = ref(null);

const currentPair = computed(() => pairs.value[currentIndex.value] || null);
const leftImage = computed(() => currentPair.value ? `/api/images/content?path=${encodeURIComponent(currentPair.value.left.path)}` : null);
const rightImage = computed(() => currentPair.value ? `/api/images/content?path=${encodeURIComponent(currentPair.value.right.path)}` : null);

const loadPairs = async () => {
  try {
    const res = await api.get('/duplicates/pairs');
    pairs.value = res.data;
    currentIndex.value = 0;
  } catch (e) {
    console.error("Failed to load duplicate pairs", e);
  }
};

const updateSlider = (event) => {
  if (!containerRef.value) return;
  const rect = containerRef.value.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const percent = (x / rect.width) * 100;
  sliderPosition.value = Math.min(100, Math.max(0, percent));
};

const deleteLeft = async () => {
  if (!currentPair.value) return;
  await deleteFile(currentPair.value.left.path);
  removeCurrentPair();
};

const deleteRight = async () => {
  if (!currentPair.value) return;
  await deleteFile(currentPair.value.right.path);
  removeCurrentPair();
};

const deleteFile = async (path) => {
  try {
    await api.post('/images/batch/delete', [path]);
    toast.add({severity: 'success', summary: 'Deleted', detail: 'File moved to trash', life: 1000});
  } catch (e) {
    // Error handled by api interceptor
  }
};

const removeCurrentPair = () => {
  pairs.value.splice(currentIndex.value, 1);
  if (currentIndex.value >= pairs.value.length) {
    currentIndex.value = Math.max(0, pairs.value.length - 1);
  }
};

const skip = () => {
  if (currentIndex.value < pairs.length - 1) {
    currentIndex.value++;
  } else {
    currentIndex.value = 0;
  }
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
        // Error handled by api interceptor
      }
    }
  });
};

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT') return;
  switch (e.key) {
    case '1':
      deleteLeft();
      break;
    case '2':
      deleteRight();
      break;
    case ' ':
      skip();
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
</script>

<template>
  <div class="duplicate-view h-full flex flex-column p-4 overflow-hidden">
    <div class="flex justify-content-between align-items-center mb-4 flex-shrink-0">
      <div>
        <h1 class="text-3xl font-bold m-0 mb-1 text-gradient">Duplicate Detective</h1>
        <p class="text-gray-400 m-0" v-if="pairs.length > 0">
          Found {{ pairs.length }} potential duplicate pairs.
        </p>
        <p class="text-gray-400 m-0" v-else>No duplicates found.</p>
      </div>
      <Button label="Resolve All (Auto)" icon="pi pi-trash" class="p-button-danger p-button-outlined"
              @click="resolveAll" :disabled="pairs.length === 0"/>
    </div>

    <div v-if="currentPair" class="flex-grow-1 flex flex-column overflow-hidden relative">
      <div
          class="relative flex-grow-1 bg-black-alpha-90 border-round overflow-hidden select-none cursor-crosshair shadow-8"
          ref="containerRef"
          @mousemove="updateSlider"
          @touchmove="updateSlider">

        <img :src="rightImage"
             class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
             style="object-fit: contain;"/>

        <img :src="leftImage"
             class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
             style="object-fit: contain;"
             :style="{ clipPath: `inset(0 ${100 - sliderPosition}% 0 0)` }"/>

        <div class="absolute top-0 bottom-0 w-2px bg-white shadow-4 pointer-events-none"
             :style="{ left: sliderPosition + '%' }">
          <div
              class="slider-handle absolute top-50 left-50 -ml-2 -mt-2 w-2rem h-2rem border-circle flex align-items-center justify-content-center shadow-4">
            <i class="pi pi-arrows-h text-white text-sm"></i>
          </div>
        </div>

        <div class="absolute top-0 left-0 p-3 z-2">
          <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Left (1)</span>
          <div class="text-xs text-white mt-1 bg-black-alpha-50 px-1">{{ currentPair.left.path }}</div>
        </div>
        <div class="absolute top-0 right-0 p-3 z-2 text-right">
          <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Right (2)</span>
          <div class="text-xs text-white mt-1 bg-black-alpha-50 px-1">{{ currentPair.right.path }}</div>
        </div>
      </div>

      <div class="flex justify-content-center gap-3 mt-3 flex-shrink-0">
        <Button label="Delete Left (1)" icon="pi pi-trash" class="p-button-danger" @click="deleteLeft"/>
        <Button label="Skip (Space)" icon="pi pi-forward" class="p-button-secondary" @click="skip"/>
        <Button label="Delete Right (2)" icon="pi pi-trash" class="p-button-danger" @click="deleteRight"/>
      </div>
    </div>

    <div v-else class="flex-grow-1 flex align-items-center justify-content-center">
      <div class="text-center text-gray-500">
        <i class="pi pi-check-circle text-6xl mb-3"></i>
        <div class="text-xl">All clear! No duplicates detected.</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.duplicate-view {
  background: transparent;
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
}
</style>
