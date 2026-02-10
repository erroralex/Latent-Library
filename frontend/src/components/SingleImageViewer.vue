<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue';
import { useBrowserStore } from '@/stores/browser';
import FilmstripView from '@/components/FilmstripView.vue';

const store = useBrowserStore();
const viewerContainer = ref(null);

// Zoom & Pan State
const scale = ref(1);
const translate = ref({ x: 0, y: 0 });
const isPanning = ref(false);
const isDragging = ref(false);
const startPan = ref({ x: 0, y: 0 });
const startTranslate = ref({ x: 0, y: 0 });

const mainImageUrl = computed(() => {
  if (!store.selectedFile) return null;
  return `http://localhost:8080/api/images/content?path=${encodeURIComponent(store.selectedFile)}`;
});

const imageStyle = computed(() => ({
    transform: `translate(${translate.value.x}px, ${translate.value.y}px) scale(${scale.value})`,
    transition: isPanning.value ? 'none' : 'transform 0.1s ease-out',
    cursor: scale.value > 1 ? (isPanning.value ? 'grabbing' : 'grab') : 'pointer'
}));

const resetZoom = () => {
    scale.value = 1;
    translate.value = { x: 0, y: 0 };
    isPanning.value = false;
    isDragging.value = false;
};

// Reset zoom when image changes
watch(() => store.selectedFile, () => {
    resetZoom();
});

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

    // Calculate new translation to zoom towards mouse pointer
    // Formula: T_new = (M - C) * (1 - factor) + T_old * factor
    // Note: We use the effective factor (newScale / oldScale) to be precise
    const effectiveFactor = newScale / scale.value;

    const newTranslateX = (mouseX - centerX) * (1 - effectiveFactor) + translate.value.x * effectiveFactor;
    const newTranslateY = (mouseY - centerY) * (1 - effectiveFactor) + translate.value.y * effectiveFactor;

    scale.value = newScale;
    translate.value = { x: newTranslateX, y: newTranslateY };
};

const onMouseDown = (e) => {
    isDragging.value = false;
    // Allow pan if zoomed in or middle mouse button
    if (scale.value > 1 || e.button === 1) {
        e.preventDefault();
        isPanning.value = true;
        startPan.value = { x: e.clientX, y: e.clientY };
        startTranslate.value = { ...translate.value };
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
            e.stopPropagation(); // Stop propagation to prevent switching back to gallery
            resetZoom();
        }
    }
};

onMounted(() => {
    window.addEventListener('keydown', onKeyDown, { capture: true });
});

onUnmounted(() => {
    window.removeEventListener('keydown', onKeyDown, { capture: true });
});
</script>

<template>
    <div class="relative h-full">
      <!-- The image viewer is locked to the top and fills all space DOWN TO the filmstrip's height. -->
      <div class="absolute top-0 left-0 right-0 image-viewer-glass overflow-hidden flex align-items-center justify-content-center"
           style="bottom: 10rem;"
           ref="viewerContainer"
           @wheel="onWheel"
           @mousedown="onMouseDown"
           @mousemove="onMouseMove"
           @mouseup="onMouseUp"
           @mouseleave="onMouseUp"
      >
        <img v-if="mainImageUrl" :src="mainImageUrl"
             class="max-w-full max-h-full object-contain shadow-8"
             :style="imageStyle"
             @click="handleImageClick"
             draggable="false" />

        <div v-else class="text-white text-xl">No image selected</div>

        <!-- Navigation Arrows -->
        <div class="absolute left-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200 z-2"
             @click="store.navigate(-1)">
          <i class="pi pi-chevron-left text-4xl text-white-alpha-50"></i>
        </div>
        <div class="absolute right-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200 z-2"
             @click="store.navigate(1)">
          <i class="pi pi-chevron-right text-4xl text-white-alpha-50"></i>
        </div>
      </div>

      <!-- The filmstrip is locked to the bottom with a fixed height matching its internal class `h-10rem`. -->
      <FilmstripView class="absolute bottom-0 left-0 right-0 w-full" style="height: 10rem;" />
    </div>
</template>

<style scoped>
.image-viewer-glass {
  background: rgba(0, 0, 0, 0.2);
}
</style>