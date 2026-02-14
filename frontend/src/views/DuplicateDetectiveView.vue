<script setup>
/**
 * @file DuplicateDetectiveView.vue
 * @description A specialized view for identifying and resolving visual and exact duplicates using dHash and SHA-256.
 *
 * This view provides a dedicated interface for cleaning up the image library by finding
 * visually identical or near-identical images. It utilizes perceptual hashing (dHash)
 * to detect duplicates regardless of minor differences in resolution or compression,
 * and cryptographic hashing for exact byte-for-byte matches. It leverages the
 * {@link ImageSplitViewer} for interactive verification and {@link ComparisonMetadataPanel}
 * for technical data comparison.
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
import api, {authenticatedUrl} from '@/services/api';
import Button from 'primevue/button';
import {useToast} from 'primevue/usetoast';
import {useConfirm} from 'primevue/useconfirm';
import ImageSplitViewer from '@/components/ImageSplitViewer.vue';
import ComparisonMetadataPanel from '@/components/ComparisonMetadataPanel.vue';

const toast = useToast();
const confirm = useConfirm();
const splitViewerRef = ref(null);

const pairs = ref([]);
const currentIndex = ref(0);
const status = ref({missingHashes: 0, totalImages: 0});
const isScanning = ref(false);

const currentPair = computed(() => pairs.value[currentIndex.value] || null);
const leftImage = computed(() => currentPair.value ? authenticatedUrl(`/api/images/content?path=${encodeURIComponent(currentPair.value.left.path)}`) : null);
const rightImage = computed(() => currentPair.value ? authenticatedUrl(`/api/images/content?path=${encodeURIComponent(currentPair.value.right.path)}`) : null);

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
  splitViewerRef.value?.resetZoom();
};

const skip = () => {
  if (currentIndex.value < pairs.value.length - 1) {
    currentIndex.value++;
  } else {
    currentIndex.value = 0;
  }
  splitViewerRef.value?.resetZoom();
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
      splitViewerRef.value?.resetZoom();
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

      <ComparisonMetadataPanel
          :metadata="currentPair.left.metadata"
          :path="currentPair.left.path"
          :rating="currentPair.left.rating"
          title="Left (1)"
          actionLabel="Keep Left (1)"
          @action="keepLeft"/>

      <div class="flex-grow-1 flex flex-column overflow-hidden">
        <ImageSplitViewer ref="splitViewerRef" :imageA="leftImage" :imageB="rightImage"/>
        <div class="flex justify-content-center gap-3 mt-3">
          <Button label="Keep Left (1)" icon="pi pi-check" class="p-button-success" @click="keepLeft"/>
          <Button label="Skip (Space)" icon="pi pi-forward" class="p-button-secondary" @click="skip"/>
          <Button label="Keep Right (2)" icon="pi pi-check" class="p-button-success" @click="keepRight"/>
        </div>
      </div>

      <ComparisonMetadataPanel
          :metadata="currentPair.right.metadata"
          :path="currentPair.right.path"
          :rating="currentPair.right.rating"
          title="Right (2)"
          actionLabel="Keep Right (2)"
          @action="keepRight"/>

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

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}
</style>
