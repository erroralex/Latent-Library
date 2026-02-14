<script setup>
/**
 * @file ComparatorView.vue
 * @description A side-by-side image comparison tool featuring a draggable slider, synchronized zoom, and pan.
 *
 * This implementation eliminates GPU compositing artifacts (ghost lines) by scaling images
 * via layout dimensions (width/height) instead of CSS transforms. This ensures both images
 * share the same pixel grid and avoids texture resampling mismatches at the split boundary.
 */
import {ref, onMounted, onUnmounted, computed, nextTick} from 'vue';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import api from '@/services/api';

const imageA = ref(null);
const imageB = ref(null);
const pathA = ref(null);
const pathB = ref(null);
const metaA = ref(null);
const metaB = ref(null);

// Image Dimensions
const naturalWidth = ref(0);
const naturalHeight = ref(0);

const splitX = ref(0); // Single source of truth: Divider position in image-space pixels
const containerRef = ref(null);
const contentRef = ref(null);

// Zoom & Pan State
const zoom = ref(1);
const panX = ref(0);
const panY = ref(0);
const isDragging = ref(false);
const lastMouseX = ref(0);
const lastMouseY = ref(0);

// Drag-and-Drop Feedback State
const isDraggingOverA = ref(false);
const isDraggingOverB = ref(false);

onUnmounted(() => {
  if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
  if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
  window.removeEventListener('keydown', handleKeyDown);
});

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown);
});

const handleKeyDown = (e) => {
  if (e.key === 'Escape') {
    resetZoom();
  }
};

const fetchMetadata = async (path, target) => {
  if (!path) return;
  try {
    const res = await api.get('/images/metadata', { params: { path } });
    if (target === 'A') metaA.value = res.data;
    else metaB.value = res.data;
  } catch (e) {
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
  const url = URL.createObjectURL(file);

  const img = new Image();
  img.onload = () => {
      if (target === 'A' || naturalWidth.value === 0) {
          naturalWidth.value = img.naturalWidth;
          naturalHeight.value = img.naturalHeight;

          splitX.value = naturalWidth.value / 2;

          if (containerRef.value) {
              const rect = containerRef.value.getBoundingClientRect();
              const zoomW = rect.width / naturalWidth.value;
              const zoomH = rect.height / naturalHeight.value;
              zoom.value = Math.min(zoomW, zoomH, 1);

              panX.value = (rect.width - (naturalWidth.value * zoom.value)) / 2;
              panY.value = (rect.height - (naturalHeight.value * zoom.value)) / 2;
          }
      }
  };
  img.src = url;

  if (target === 'A') {
    if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
    imageA.value = url;
    pathA.value = file.path;
    fetchMetadata(file.path, 'A');
  } else {
    if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
    imageB.value = url;
    pathB.value = file.path;
    fetchMetadata(file.path, 'B');
  }
};

const handleDragEnter = (e, target) => {
    e.preventDefault();
    if (target === 'A') isDraggingOverA.value = true;
    else isDraggingOverB.value = true;
};

const handleDragLeave = (e, target) => {
    if (!e.currentTarget.contains(e.relatedTarget)) {
        if (target === 'A') isDraggingOverA.value = false;
        else isDraggingOverB.value = false;
    }
};

/**
 * Updates the split position based on mouse coordinates.
 */
const updateSlider = (event) => {
  if (isDragging.value || !contentRef.value) return;

  const rect = contentRef.value.getBoundingClientRect();
  const mouseX = event.clientX - rect.left;

  // Convert screen -> image space
  const imageX = mouseX / zoom.value;
  splitX.value = Math.max(0, Math.min(naturalWidth.value, imageX));
};

const handleWheel = (e) => {
  if (!imageA.value || !imageB.value || !containerRef.value) return;
  e.preventDefault();

  const rect = containerRef.value.getBoundingClientRect();
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  const delta = e.deltaY > 0 ? 0.9 : 1.1;
  const nextZoom = Math.min(20, Math.max(0.01, zoom.value * delta));

  const zoomFactor = nextZoom / zoom.value;
  panX.value = mouseX - (mouseX - panX.value) * zoomFactor;
  panY.value = mouseY - (mouseY - panY.value) * zoomFactor;

  zoom.value = nextZoom;
};

const startDrag = (e) => {
    isDragging.value = true;
    lastMouseX.value = e.clientX;
    lastMouseY.value = e.clientY;
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
  if (containerRef.value && naturalWidth.value > 0) {
      const rect = containerRef.value.getBoundingClientRect();
      const zoomW = rect.width / naturalWidth.value;
      const zoomH = rect.height / naturalHeight.value;
      zoom.value = Math.min(zoomW, zoomH, 1);
      panX.value = (rect.width - (naturalWidth.value * zoom.value)) / 2;
      panY.value = (rect.height - (naturalHeight.value * zoom.value)) / 2;
      splitX.value = naturalWidth.value / 2;
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
  naturalWidth.value = 0;
  naturalHeight.value = 0;
  zoom.value = 1;
  panX.value = 0;
  panY.value = 0;
};

const getFileName = (path) => path ? path.split(/[\\/]/).pop() : 'Unknown';
const getFolderName = (path) => {
    if (!path) return '-';
    const parts = path.split(/[\\/]/);
    parts.pop();
    return parts.pop() || 'Root';
};
</script>

<template>
  <div class="comparator-view h-full flex flex-column p-4 overflow-hidden">
    <div class="flex flex-column align-items-center mb-4 flex-shrink-0">
      <h1 class="text-4xl font-bold text-gradient m-0">Comparator</h1>
      <p class="text-gray-400 mt-2 m-0">Compare images by dropping them into the slots below.</p>
    </div>

    <div v-if="imageA && imageB" class="flex-grow-1 flex gap-3 overflow-hidden">

      <!-- Left Metadata Panel -->
      <div class="metadata-panel flex flex-column p-3 border-round shadow-4 overflow-y-auto custom-scrollbar">
          <template v-if="metaA">
              <div class="text-gradient font-bold mb-1 text-overflow-ellipsis overflow-hidden white-space-nowrap" :title="pathA">
                  {{ getFileName(pathA) }}
              </div>
              <div class="text-xs text-500 mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap">
                  <i class="pi pi-folder mr-1"></i>{{ getFolderName(pathA) }}
              </div>
              <div class="flex gap-1 mb-3">
                  <i v-for="i in 5" :key="i" class="pi text-xs" :class="i <= metaA.rating ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
              </div>
              <div class="metadata-grid grid grid-nogutter gap-2">
                  <div class="col-12"><label class="text-xs text-500">Model</label><InputText :value="metaA.Model || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Sampler</label><InputText :value="metaA.Sampler || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Steps</label><InputText :value="metaA.Steps || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Resolution</label><InputText :value="metaA.Resolution || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Size</label><InputText :value="metaA.FileSize || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-12">
                      <label class="text-xs text-500">Prompt</label>
                      <div class="glass-box p-2 border-round text-xs line-height-2 select-text overflow-y-auto" style="max-height: 120px;">{{ metaA.Prompt || 'No prompt' }}</div>
                  </div>
              </div>
          </template>
          <div v-else class="flex-grow-1 flex align-items-center justify-content-center text-gray-600 italic text-sm text-center">
              No library metadata available for Image A
          </div>
      </div>

      <!-- Comparison Area -->
      <div class="flex-grow-1 flex flex-column overflow-hidden relative">
        <div
            class="relative flex-grow-1 border-round overflow-hidden select-none shadow-8"
            :class="{ 'cursor-move': zoom > 1, 'cursor-crosshair': zoom === 1 }"
            ref="containerRef"
            @mousemove="updateSlider"
            @wheel="handleWheel"
            @mousedown="startDrag"
            @mousemove.capture="onDrag"
            @mouseup="stopDrag"
            @mouseleave="stopDrag">

          <!-- Zoomable/Pannable Content (Scaled via Layout, Panned via Translate) -->
          <div ref="contentRef"
               class="absolute origin-top-left"
               :style="{
                  transform: `translate(${panX}px, ${panY}px)`,
                  width: (naturalWidth * zoom) + 'px',
                  height: (naturalHeight * zoom) + 'px'
               }">

            <!-- Bottom Image (Image B) -->
            <img :src="imageB" class="absolute top-0 left-0 select-none pointer-events-none"
                 :style="{ width: (naturalWidth * zoom) + 'px', height: (naturalHeight * zoom) + 'px' }"/>

            <!-- Top Image (Image A) with Layout-based Cropping -->
            <div class="absolute top-0 left-0 overflow-hidden"
                 :style="{
                    width: (splitX * zoom) + 'px',
                    height: (naturalHeight * zoom) + 'px'
                 }">
              <img :src="imageA" class="absolute top-0 left-0 select-none pointer-events-none"
                   :style="{
                      width: (naturalWidth * zoom) + 'px',
                      height: (naturalHeight * zoom) + 'px'
                   }"/>
            </div>

            <!-- Divider Line (Now inside the same coordinate space as images) -->
            <div class="absolute top-0 bottom-0 z-5 pointer-events-none"
                 :style="{
                    left: (splitX * zoom) + 'px',
                    width: '2px',
                    backgroundColor: 'var(--accent-primary)',
                    boxShadow: '0 0 4px rgba(0,0,0,0.5)'
                 }">
              <div class="slider-handle absolute top-50 left-50 border-circle flex align-items-center justify-content-center shadow-4"
                   style="width: 32px; height: 32px; margin-left: -16px; margin-top: -16px; border-width: 2px;">
                <i class="pi pi-arrows-h text-white" style="font-size: 14px;"></i>
              </div>
            </div>
          </div>

          <div class="absolute top-0 left-0 p-3 z-2 pointer-events-none">
            <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Left</span>
          </div>
          <div class="absolute top-0 right-0 p-3 z-2 text-right pointer-events-none">
            <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Right</span>
          </div>
        </div>

        <div class="flex justify-content-center gap-3 mt-3 flex-shrink-0">
          <Button label="Reset" class="p-button-outlined" @click="reset"/>
          <span v-if="naturalWidth > 0" class="text-xs text-gray-500 flex align-items-center">
              Zoom: {{ Math.round(zoom * 100) }}% (Esc to reset)
          </span>
        </div>
      </div>

      <!-- Right Metadata Panel -->
      <div class="metadata-panel flex flex-column p-3 border-round shadow-4 overflow-y-auto custom-scrollbar">
          <template v-if="metaB">
              <div class="text-gradient font-bold mb-1 text-overflow-ellipsis overflow-hidden white-space-nowrap" :title="pathB">
                  {{ getFileName(pathB) }}
              </div>
              <div class="text-xs text-500 mb-2 text-overflow-ellipsis overflow-hidden white-space-nowrap">
                  <i class="pi pi-folder mr-1"></i>{{ getFolderName(pathB) }}
              </div>
              <div class="flex gap-1 mb-3">
                  <i v-for="i in 5" :key="i" class="pi text-xs" :class="i <= metaB.rating ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
              </div>
              <div class="metadata-grid grid grid-nogutter gap-2">
                  <div class="col-12"><label class="text-xs text-500">Model</label><InputText :value="metaB.Model || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Sampler</label><InputText :value="metaB.Sampler || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Steps</label><InputText :value="metaB.Steps || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Resolution</label><InputText :value="metaB.Resolution || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-6"><label class="text-xs text-500">Size</label><InputText :value="metaB.FileSize || '-'" readonly class="w-full p-inputtext-sm glass-input"/></div>
                  <div class="col-12">
                      <label class="text-xs text-500">Prompt</label>
                      <div class="glass-box p-2 border-round text-xs line-height-2 select-text overflow-y-auto" style="max-height: 120px;">{{ metaB.Prompt || 'No prompt' }}</div>
                  </div>
              </div>
          </template>
          <div v-else class="flex-grow-1 flex align-items-center justify-content-center text-gray-600 italic text-sm text-center">
              No library metadata available for Image B
          </div>
      </div>

    </div>

    <div v-else class="flex-grow-1 flex align-items-center justify-content-center gap-4">
      <!-- Drop Zone A -->
      <div
          class="drop-zone p-4 flex flex-column align-items-center justify-content-center cursor-pointer transition-all transition-duration-300 relative border-round"
          :class="{ 'drop-zone-active': isDraggingOverA }"
          @click="$refs.fileInputA.click()"
          @dragover.prevent
          @dragenter="handleDragEnter($event, 'A')"
          @dragleave="handleDragLeave($event, 'A')"
          @drop.prevent="handleDrop($event, 'A')">

        <input type="file" ref="fileInputA" class="hidden" accept="image/*" @change="handleFileSelect($event, 'A')"/>

        <div v-if="imageA" class="w-full h-full absolute top-0 left-0 p-2 pointer-events-none">
          <img :src="imageA" class="w-full h-full object-contain border-round"/>
        </div>
        <div v-else class="text-center relative z-1 pointer-events-none">
          <i class="pi pi-image text-5xl text-gray-500 mb-3"></i>
          <div class="font-bold text-xl mb-1 text-white">Image A (Left)</div>
          <div class="text-gray-400">Drop or Click</div>
        </div>
      </div>

      <!-- Drop Zone B -->
      <div
          class="drop-zone p-4 flex flex-column align-items-center justify-content-center cursor-pointer transition-all transition-duration-300 relative border-round"
          :class="{ 'drop-zone-active': isDraggingOverB }"
          @click="$refs.fileInputB.click()"
          @dragover.prevent
          @dragenter="handleDragEnter($event, 'B')"
          @dragleave="handleDragLeave($event, 'B')"
          @drop.prevent="handleDrop($event, 'B')">

        <input type="file" ref="fileInputB" class="hidden" accept="image/*" @change="handleFileSelect($event, 'B')"/>

        <div v-if="imageB" class="w-full h-full absolute top-0 left-0 p-2 pointer-events-none">
          <img :src="imageB" class="w-full h-full object-contain border-round"/>
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

.drop-zone {
  width: 300px;
  height: 300px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  backdrop-filter: blur(10px);
  position: relative;
  z-index: 1;
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
  border-color: transparent;
}

.drop-zone:hover::before, .drop-zone-active::before {
  opacity: 0.8;
}

.drop-zone-active {
    box-shadow: 0 0 30px var(--accent-primary);
    border-color: var(--accent-primary) !important;
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

.cursor-move {
    cursor: move !important;
}

img {
  image-rendering: auto;
  backface-visibility: hidden;
}
</style>
