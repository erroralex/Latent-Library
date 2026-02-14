<script setup>
/**
 * @file ImageSplitViewer.vue
 * @description A high-precision, reusable component for side-by-side image comparison with height-matched scaling.
 *
 * This component provides a professional-grade visual comparison interface. It implements
 * "Height-Matched Scaling" to automatically normalize images of different resolutions,
 * ensuring they are vertically aligned and centered within a unified virtual canvas.
 * It features a draggable divider, advanced zoom/pan capabilities, and layout-based
 * cropping to reveal portions of each image.
 *
 * Key functionalities:
 * - **Height Normalization:** Automatically scales the smaller image to match the height
 *   of the larger one, preventing vertical desync during comparison.
 * - **Unified Canvas:** Calculates a virtual bounding box that encompasses both
 *   normalized images, serving as the coordinate system for zoom and pan.
 * - **Interactive Divider:** Implements a pixel-perfect slider that controls the
 *   visibility of the top image via layout-based cropping.
 * - **Advanced Zoom & Pan:** Supports cursor-relative zooming and smooth panning
 *   across the entire comparison canvas.
 * - **Responsive Layout:** Adapts the initial zoom level to fit the container dimensions
 *   while maintaining aspect ratios.
 */
import {ref, onMounted, onUnmounted, computed, watch, nextTick} from 'vue';

const props = defineProps({
  imageA: {type: String, required: false},
  imageB: {type: String, required: false}
});

const containerRef = ref(null);
const contentRef = ref(null);

const widthA = ref(0);
const heightA = ref(0);
const widthB = ref(0);
const heightB = ref(0);

const normalizationScaleA = computed(() => {
  if (heightA.value === 0 || heightB.value === 0) return 1;
  return heightB.value > heightA.value ? (heightB.value / heightA.value) : 1;
});

const normalizationScaleB = computed(() => {
  if (heightA.value === 0 || heightB.value === 0) return 1;
  return heightA.value > heightB.value ? (heightA.value / heightB.value) : 1;
});

const effectiveWidthA = computed(() => widthA.value * normalizationScaleA.value);
const effectiveHeightA = computed(() => heightA.value * normalizationScaleA.value);
const effectiveWidthB = computed(() => widthB.value * normalizationScaleB.value);
const effectiveHeightB = computed(() => heightB.value * normalizationScaleB.value);

const canvasWidth = computed(() => Math.max(effectiveWidthA.value, effectiveWidthB.value));
const canvasHeight = computed(() => Math.max(effectiveHeightA.value, effectiveHeightB.value));

const splitX = ref(0);

const zoom = ref(1);
const panX = ref(0);
const panY = ref(0);
const isDragging = ref(false);
const lastMouseX = ref(0);
const lastMouseY = ref(0);

const containerWidth = computed(() => containerRef.value?.getBoundingClientRect().width ?? 0);

const offsetA = computed(() => ({
  x: (canvasWidth.value - effectiveWidthA.value) / 2,
  y: (canvasHeight.value - effectiveHeightA.value) / 2
}));

const offsetB = computed(() => ({
  x: (canvasWidth.value - effectiveWidthB.value) / 2,
  y: (canvasHeight.value - effectiveHeightB.value) / 2
}));

const loadAllDimensions = () => {
  const promiseA = new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      widthA.value = img.naturalWidth;
      heightA.value = img.naturalHeight;
      resolve();
    };
    img.src = props.imageA;
  });

  const promiseB = new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      widthB.value = img.naturalWidth;
      heightB.value = img.naturalHeight;
      resolve();
    };
    img.src = props.imageB;
  });

  Promise.all([promiseA, promiseB]).then(() => {
    splitX.value = canvasWidth.value / 2;
    nextTick(resetZoom);
  });
};

const updateSlider = (event) => {
  if (isDragging.value || !contentRef.value) return;
  const rect = contentRef.value.getBoundingClientRect();
  const mouseX = event.clientX - rect.left;
  const imageX = mouseX / zoom.value;
  splitX.value = Math.max(0, Math.min(canvasWidth.value, imageX));
};

const handleWheel = (e) => {
  if (!props.imageA || !props.imageB || !containerRef.value) return;
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
  if (!props.imageA || !props.imageB) return;
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
  if (containerRef.value && canvasWidth.value > 0) {
    const rect = containerRef.value.getBoundingClientRect();
    const zoomW = rect.width / canvasWidth.value;
    const zoomH = rect.height / canvasHeight.value;
    zoom.value = Math.min(zoomW, zoomH, 1);
    panX.value = (rect.width - (canvasWidth.value * zoom.value)) / 2;
    panY.value = (rect.height - (canvasHeight.value * zoom.value)) / 2;
    splitX.value = canvasWidth.value / 2;
  }
};

const handleKeyDown = (e) => {
  if (e.key === 'Escape') {
    resetZoom();
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown, true);

  watch(() => [props.imageA, props.imageB], () => {
    if (props.imageA && props.imageB) {
      loadAllDimensions();
    }
  }, {immediate: true});
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeyDown, true);
});

defineExpose({resetZoom});
</script>

<template>
  <div class="image-split-viewer relative flex-grow-1 border-round overflow-hidden select-none shadow-8"
       :class="{ 'cursor-move': zoom > 1, 'cursor-crosshair': zoom === 1 }"
       ref="containerRef"
       @mousemove="updateSlider"
       @wheel="handleWheel"
       @mousedown="startDrag"
       @mousemove.capture="onDrag"
       @mouseup="stopDrag"
       @mouseleave="stopDrag">

    <div v-if="imageA && imageB && canvasWidth > 0" ref="contentRef"
         class="absolute origin-top-left"
         :style="{
            transform: `translate(${panX}px, ${panY}px)`,
            width: (canvasWidth * zoom) + 'px',
            height: (canvasHeight * zoom) + 'px'
         }">

      <img :src="imageB" class="absolute select-none pointer-events-none"
           :style="{
              left: (offsetB.x * zoom) + 'px',
              top: (offsetB.y * zoom) + 'px',
              width: (effectiveWidthB * zoom) + 'px',
              height: (effectiveHeightB * zoom) + 'px'
           }"/>

      <div class="absolute top-0 left-0 overflow-hidden"
           :style="{
              width: (splitX * zoom) + 'px',
              height: (canvasHeight * zoom) + 'px'
           }">
        <img :src="imageA" class="absolute select-none pointer-events-none"
             :style="{
                left: (offsetA.x * zoom) + 'px',
                top: (offsetA.y * zoom) + 'px',
                width: (effectiveWidthA * zoom) + 'px',
                height: (effectiveHeightA * zoom) + 'px'
             }"/>
      </div>

      <div class="absolute top-0 bottom-0 z-5 pointer-events-none"
           :style="{
              left: (splitX * zoom) + 'px',
              width: '2px',
              backgroundColor: 'var(--accent-primary)',
              boxShadow: '0 0 4px rgba(0,0,0,0.5)'
           }">
        <div
            class="slider-handle absolute top-50 left-50 border-circle flex align-items-center justify-content-center shadow-4"
            style="width: 32px; height: 32px; margin-left: -16px; margin-top: -16px; border-width: 2px;">
          <i class="pi pi-arrows-h text-white" style="font-size: 14px;"></i>
        </div>
      </div>
    </div>

    <div v-if="imageA && imageB" class="absolute top-0 left-0 p-3 z-2 pointer-events-none">
      <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Left</span>
    </div>
    <div v-if="imageA && imageB" class="absolute top-0 right-0 p-3 z-2 text-right pointer-events-none">
      <span class="bg-black-alpha-70 text-white px-2 py-1 border-round font-bold">Right</span>
    </div>

    <div v-if="!imageA || !imageB" class="h-full flex align-items-center justify-content-center text-gray-500 italic">
      Waiting for images...
    </div>
  </div>
</template>

<style scoped>
.image-split-viewer {
  background: transparent;
}

.slider-handle {
  background: linear-gradient(#000, #000) padding-box,
  var(--grad-hover) border-box;
  border: 2px solid transparent;
  z-index: 10;
}

.cursor-move {
  cursor: move !important;
}

img {
  image-rendering: auto;
  backface-visibility: hidden;
}
</style>
