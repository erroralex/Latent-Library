<script setup>
/**
 * @file ComparatorView.vue
 * @description A side-by-side image comparison tool featuring a draggable slider, synchronized zoom, and pan.
 *
 * This view allows users to perform detailed visual and technical inspections between two images.
 * It implements a drag-and-drop interface for loading local files and utilizes the
 * {@link ImageSplitViewer} for interactive comparison. It also integrates with the
 * backend to fetch and display metadata for images that are already part of the library.
 *
 * Key functionalities:
 * - **Drag-and-Drop:** Supports direct file drops into designated slots for quick loading.
 * - **Interactive Comparison:** Hosts the split-viewer component for pixel-perfect
 *   inspections with synchronized zoom and pan.
 * - **Metadata Integration:** Automatically attempts to retrieve generation parameters
 *   from the database for loaded images.
 * - **Memory Management:** Automatically revokes object URLs on unmount to prevent memory leaks.
 * - **Responsive UI:** Features animated drop zones with visual feedback during drag operations.
 */
import {ref, onUnmounted} from 'vue';
import Button from 'primevue/button';
import {useToast} from 'primevue/usetoast';
import ImageSplitViewer from '@/components/ImageSplitViewer.vue';
import ComparisonMetadataPanel from '@/components/ComparisonMetadataPanel.vue';
import api from '@/services/api';

const toast = useToast();

const imageA = ref(null);
const imageB = ref(null);
const pathA = ref(null);
const pathB = ref(null);
const metaA = ref(null);
const metaB = ref(null);

const isDraggingOverA = ref(false);
const isDraggingOverB = ref(false);

onUnmounted(() => {
  if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
  if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
});

const fetchMetadata = async (path, target) => {
  if (!path) return;
  try {
    const res = await api.get('/images/metadata', {params: {path}});
    if (target === 'A') metaA.value = res.data;
    else metaB.value = res.data;
  } catch (e) {
    console.error(`Failed to fetch metadata for ${target}`, e);
    // Metadata fetch failure is non-critical, so we just clear the metadata
    // and optionally log a warning or show a subtle toast if needed.
    // For now, we'll just clear it to avoid stale data.
    if (target === 'A') metaA.value = null;
    else metaB.value = null;
  }
};

const handleFileSelect = (event, target) => {
  const file = event.target.files[0];
  if (!file) return;
  processFile(file, target);
};

const handleDrop = (event, target) => {
  isDraggingOverA.value = false;
  isDraggingOverB.value = false;
  const file = event.dataTransfer.files[0];
  if (!file) return;
  processFile(file, target);
};

const processFile = (file, target) => {
  try {
    const url = URL.createObjectURL(file);
    if (target === 'A') {
      if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
      imageA.value = url;
      pathA.value = file.path; // Note: file.path is non-standard and might only work in Electron/specific envs
      fetchMetadata(file.path, 'A');
    } else {
      if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
      imageB.value = url;
      pathB.value = file.path;
      fetchMetadata(file.path, 'B');
    }
  } catch (e) {
    console.error("Failed to process file", e);
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to load image file', life: 3000});
  }
};

const handleDragLeave = (e, target) => {
  if (!e.currentTarget.contains(e.relatedTarget)) {
    if (target === 'A') isDraggingOverA.value = false;
    else isDraggingOverB.value = false;
  }
};

const reset = () => {
  if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
  if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
  imageA.value = null;
  imageB.value = null;
  pathA.value = null;
  pathB.value = null;
  metaA.value = null;
  metaB.value = null;
};
</script>

<template>
  <div class="comparator-view h-full flex flex-column p-4 overflow-hidden">
    <div class="flex flex-column align-items-center mb-4 flex-shrink-0">
      <h1 class="text-4xl font-bold text-gradient m-0">Comparator</h1>
      <p class="text-gray-400 mt-2 m-0">Compare images by dropping them into the slots below.</p>
    </div>

    <div v-if="imageA && imageB" class="flex-grow-1 flex gap-3 overflow-hidden">
      <ComparisonMetadataPanel :metadata="metaA" :path="pathA" title="Image A"/>

      <div class="flex-grow-1 flex flex-column overflow-hidden">
        <ImageSplitViewer :imageA="imageA" :imageB="imageB"/>
        <div class="flex justify-content-center mt-3">
          <Button label="Reset / Clear" class="p-button-outlined" @click="reset"/>
        </div>
      </div>

      <ComparisonMetadataPanel :metadata="metaB" :path="pathB" title="Image B"/>
    </div>

    <div v-else class="flex-grow-1 flex align-items-center justify-content-center gap-4">
      <div
          class="drop-zone p-4 flex flex-column align-items-center justify-content-center cursor-pointer transition-all transition-duration-300 relative border-round"
          :class="{ 'drop-zone-active': isDraggingOverA }"
          @click="$refs.fileInputA.click()"
          @dragover.prevent
          @dragenter="isDraggingOverA = true"
          @dragleave="handleDragLeave($event, 'A')"
          @drop.prevent="handleDrop($event, 'A')">

        <input type="file" ref="fileInputA" class="hidden" accept="image/*" @change="handleFileSelect($event, 'A')"/>

        <div v-if="imageA"
             class="w-full h-full absolute top-0 left-0 p-3 pointer-events-none flex align-items-center justify-content-center">
          <img :src="imageA" class="max-w-full max-h-full border-round shadow-4" style="object-fit: contain;"/>
        </div>
        <div v-else class="text-center relative z-1 pointer-events-none">
          <i class="pi pi-image text-5xl text-gray-500 mb-3"></i>
          <div class="font-bold text-xl mb-1 text-white">Image A (Left)</div>
          <div class="text-gray-400">Drop or Click</div>
        </div>
      </div>

      <div
          class="drop-zone p-4 flex flex-column align-items-center justify-content-center cursor-pointer transition-all transition-duration-300 relative border-round"
          :class="{ 'drop-zone-active': isDraggingOverB }"
          @click="$refs.fileInputB.click()"
          @dragover.prevent
          @dragenter="isDraggingOverB = true"
          @dragleave="handleDragLeave($event, 'B')"
          @drop.prevent="handleDrop($event, 'B')">

        <input type="file" ref="fileInputB" class="hidden" accept="image/*" @change="handleFileSelect($event, 'B')"/>

        <div v-if="imageB"
             class="w-full h-full absolute top-0 left-0 p-3 pointer-events-none flex align-items-center justify-content-center">
          <img :src="imageB" class="max-w-full max-h-full border-round shadow-4" style="object-fit: contain;"/>
        </div>
        <div v-else class="text-center relative z-1 pointer-events-none">
          <i class="pi pi-image text-5xl text-gray-500 mb-3"></i>
          <div class="font-bold text-xl mb-1 text-white">Image B (Right)</div>
          <div class="text-gray-400">Drop or Click</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comparator-view {
  background: transparent;
}

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.drop-zone {
  width: 300px;
  height: 300px;
  background: var(--bg-card);
  border: 2px solid var(--accent-primary);
  backdrop-filter: blur(10px);
  position: relative;
  z-index: 1;
  opacity: 0.6;
}

.drop-zone::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: var(--grad-hover);
  border-radius: inherit;
  z-index: -2;
  opacity: 0;
  filter: blur(8px);
  transition: opacity 0.3s ease;
}

.drop-zone:hover, .drop-zone-active {
  transform: translateY(-5px);
  opacity: 1;
}

.drop-zone:hover::before, .drop-zone-active::before {
  opacity: 0.8;
}

.drop-zone-active {
  box-shadow: 0 0 30px var(--accent-primary);
}
</style>
