<script setup>
/**
 * @file SingleImageViewer.vue
 * @description A high-fidelity image viewing component featuring programmatic loading, advanced zoom/pan, and filmstrip navigation.
 *
 * This component provides a professional-grade image viewing experience. It utilizes a programmatic
 * image loading strategy to ensure reliable state transitions and spinner management, bypassing
 * common race conditions associated with DOM-based load events.
 *
 * Key features:
 * - **Programmatic Loader:** Uses `new Image()` to track loading progress, ensuring the UI remains responsive and the "ready" state is accurate.
 * - **Advanced Zoom & Pan:** Implements cursor-relative zooming and smooth panning for detailed image inspection.
 * - **Dual-Layer Rendering:** Displays a low-resolution thumbnail as a placeholder while the high-resolution source loads to improve perceived performance.
 * - **Keyboard & Mouse Integration:** Supports mouse wheel zooming, click-to-toggle sidebar, and escape-to-reset zoom.
 * - **Navigation Controls:** Integrated directional arrows and a bottom filmstrip for rapid library traversal.
 */
import {computed, onMounted, onUnmounted, ref, watch} from 'vue';
import {useBrowserStore} from '@/stores/browser';
import FilmstripView from '@/components/FilmstripView.vue';

const store = useBrowserStore();
const viewerContainer = ref(null);

const scale = ref(1);
const translate = ref({x: 0, y: 0});
const isPanning = ref(false);
const isDragging = ref(false);
const startPan = ref({x: 0, y: 0});
const startTranslate = ref({x: 0, y: 0});

const isHighResReady = ref(false);
const hasLoadError = ref(false);
const loadRequestId = ref(0);

const mainImageUrl = computed(() => {
  if (!store.selectedFile) return null;
  return `/api/images/content?path=${encodeURIComponent(store.selectedFile)}`;
});

const thumbnailImageUrl = computed(() => {
  if (!store.selectedFile) return null;
  return `/api/images/thumbnail?path=${encodeURIComponent(store.selectedFile)}`;
});

const imageStyle = computed(() => ({
  transform: `translate(${translate.value.x}px, ${translate.value.y}px) scale(${scale.value})`,
  transition: isPanning.value ? 'none' : 'transform 0.1s ease-out',
  cursor: scale.value > 1 ? (isPanning.value ? 'grabbing' : 'grab') : 'pointer',
  width: '100%',
  height: '100%',
  objectFit: 'contain'
}));

const resetZoom = () => {
  scale.value = 1;
  translate.value = {x: 0, y: 0};
  isPanning.value = false;
  isDragging.value = false;
};

// Programmatic image loading to prevent spinner race conditions
watch(mainImageUrl, (newUrl) => {
  resetZoom();
  isHighResReady.value = false;
  hasLoadError.value = false;

  if (!newUrl) return;

  // Use a request ID to ignore callbacks from stale requests (rapid navigation)
  const requestId = ++loadRequestId.value;

  const img = new Image();
  img.onload = () => {
    if (loadRequestId.value === requestId) {
      isHighResReady.value = true;
    }
  };
  img.onerror = () => {
    if (loadRequestId.value === requestId) {
      hasLoadError.value = true;
      isHighResReady.value = true; // Stop spinner even on error
    }
  };
  img.src = newUrl;
}, { immediate: true });


const onWheel = (e) => {
  e.preventDefault();
  if (!viewerContainer.value) return;

  const rect = viewerContainer.value.getBoundingClientRect();
  const centerX = rect.width / 2;
  const centerY = rect.height / 2;
  const mouseX = e.clientX - rect.left;
  const mouseY = e.clientY - rect.top;

  const ZOOM_SENSITIVITY = 0.001;
  const MIN_SCALE = 0.5;
  const MAX_SCALE = 20;

  const delta = -e.deltaY;
  const factor = Math.exp(delta * ZOOM_SENSITIVITY);

  let newScale = scale.value * factor;
  if (newScale < MIN_SCALE) newScale = MIN_SCALE;
  if (newScale > MAX_SCALE) newScale = MAX_SCALE;

  const effectiveFactor = newScale / scale.value;

  const newTranslateX = (mouseX - centerX) * (1 - effectiveFactor) + translate.value.x * effectiveFactor;
  const newTranslateY = (mouseY - centerY) * (1 - effectiveFactor) + translate.value.y * effectiveFactor;

  scale.value = newScale;
  translate.value = {x: newTranslateX, y: newTranslateY};
};

const onMouseDown = (e) => {
  isDragging.value = false;
  if (scale.value > 1 || e.button === 1) {
    e.preventDefault();
    isPanning.value = true;
    startPan.value = {x: e.clientX, y: e.clientY};
    startTranslate.value = {...translate.value};
  }
};

const onMouseMove = (e) => {
  if (isPanning.value) {
    isDragging.value = true;
    const dx = e.clientX - startPan.value.x;
    const dy = e.clientY - startPan.value.y;
    translate.value = {
      x: startTranslate.value.x + dx,
      y: startTranslate.value.y + dy
    };
  }
};

const onMouseUp = () => {
  isPanning.value = false;
};

const handleImageClick = () => {
  if (isDragging.value) return;
  store.toggleSidebar();
};

const onKeyDown = (e) => {
  if (e.key === 'Escape') {
    if (scale.value > 1) {
      e.preventDefault();
      e.stopPropagation();
      resetZoom();
    }
  }
};

const emit = defineEmits(['contextmenu']);

const onRightClick = (event) => {
  if (store.selectedFile) {
    // Find the full file object from the store if possible, or construct a partial one
    const fileObj = store.files.find(f => f.path === store.selectedFile) || { path: store.selectedFile };
    emit('contextmenu', {event, file: fileObj});
  }
};

onMounted(() => {
  window.addEventListener('keydown', onKeyDown, {capture: true});
});

onUnmounted(() => {
  window.removeEventListener('keydown', onKeyDown, {capture: true});
});
</script>

<template>
  <div class="relative h-full w-full image-viewer-glass" @contextmenu.prevent="onRightClick">
    <div
        class="absolute top-0 left-0 right-0 overflow-hidden flex align-items-center justify-content-center"
        style="bottom: 10rem"
        ref="viewerContainer"
        @wheel="onWheel"
        @mousedown="onMouseDown"
        @mousemove="onMouseMove"
        @mouseup="onMouseUp"
        @mouseleave="onMouseUp"
    >
      <!-- Thumbnail: Removed !isHighResReady check so it stays visible until covered by high-res -->
      <img v-if="thumbnailImageUrl"
           :src="thumbnailImageUrl"
           class="absolute inset-0 z-0"
           :style="imageStyle"
           decoding="async"
           draggable="false"/>

      <img v-if="mainImageUrl"
           :key="mainImageUrl"
           :src="mainImageUrl"
           class="absolute inset-0 z-1 shadow-8"
           :class="{ 'opacity-100': isHighResReady, 'opacity-0': !isHighResReady }"
           style="transition: opacity 0.3s ease;"
           :style="imageStyle"
           @click="handleImageClick"
           draggable="false"/>

      <div v-if="hasLoadError" class="absolute z-2 flex flex-column align-items-center text-red-400">
        <i class="pi pi-exclamation-triangle text-4xl mb-2"></i>
        <span>Failed to load image</span>
      </div>

      <div v-if="!mainImageUrl" class="text-white text-xl z-2">No image selected</div>

      <div
          class="absolute left-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200 z-2"
          @click="store.navigate(-1)">
        <i class="pi pi-chevron-left text-4xl text-white-alpha-50"></i>
      </div>
      <div
          class="absolute right-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200 z-2"
          @click="store.navigate(1)">
        <i class="pi pi-chevron-right text-4xl text-white-alpha-50"></i>
      </div>
    </div>

    <FilmstripView class="absolute bottom-0 left-0 right-0 w-full z-3" style="height: 10rem;"/>
  </div>
</template>

<style scoped>
.image-viewer-glass {
  background: var(--bg-overlay);
}

.text-red-400 {
  color: var(--status-danger) !important;
}

.text-white {
  color: var(--text-primary) !important;
}

.opacity-0 {
  opacity: 0;
}

.opacity-100 {
  opacity: 1;
}
</style>
