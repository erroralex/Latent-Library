<script setup>
/**
 * @file ComparatorView.vue
 * @description A side-by-side image comparison tool featuring a draggable slider.
 *
 * This view allows users to perform detailed visual inspections between two images. It implements
 * a "before-and-after" style slider that reveals portions of each image as it is moved.
 *
 * Key functionalities:
 * - Drag-and-Drop: Supports direct file drops into designated slots for quick loading.
 * - Interactive Slider: Implements a custom clip-path based slider for real-time comparison.
 * - Memory Management: Automatically revokes object URLs on unmount to prevent memory leaks.
 * - Responsive Layout: Adapts the comparison container to the available viewport space.
 */
import {ref, onUnmounted} from 'vue';
import Button from 'primevue/button';

const imageA = ref(null);
const imageB = ref(null);
const sliderPosition = ref(50);
const containerRef = ref(null);

onUnmounted(() => {
  if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
  if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
});

const handleFileSelect = (event, target) => {
  const file = event.target.files[0];
  if (!file) return;

  const url = URL.createObjectURL(file);
  if (target === 'A') {
    if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
    imageA.value = url;
  } else {
    if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
    imageB.value = url;
  }
};

const handleDrop = (event, target) => {
  const file = event.dataTransfer.files[0];
  if (!file) return;

  const url = URL.createObjectURL(file);
  if (target === 'A') {
    if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
    imageA.value = url;
  } else {
    if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
    imageB.value = url;
  }
};

const updateSlider = (event) => {
  if (!containerRef.value) return;
  const rect = containerRef.value.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const percent = (x / rect.width) * 100;
  sliderPosition.value = Math.min(100, Math.max(0, percent));
};

const reset = () => {
  if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
  if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
  imageA.value = null;
  imageB.value = null;
  sliderPosition.value = 50;
};
</script>

<template>
  <div class="comparator-view h-full flex flex-column p-4 overflow-hidden">
    <div class="text-center mb-4 flex-shrink-0">
      <h1 class="text-3xl font-bold m-0 mb-2 text-gradient">Comparator</h1>
      <p class="text-gray-400 m-0">Compare images by dropping them into the slots below.</p>
    </div>

    <div v-if="imageA && imageB" class="flex-grow-1 flex flex-column overflow-hidden relative">
      <div
          class="relative flex-grow-1 bg-black-alpha-90 border-round overflow-hidden select-none cursor-crosshair shadow-8"
          ref="containerRef"
          @mousemove="updateSlider"
          @touchmove="updateSlider">

        <img :src="imageB"
             class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
             style="object-fit: contain;"/>

        <img :src="imageA"
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
      </div>

      <div class="flex justify-content-center mt-3 flex-shrink-0">
        <Button label="Reset" class="p-button-outlined" @click="reset"/>
      </div>
    </div>

    <div v-else class="flex-grow-1 flex align-items-center justify-content-center gap-4">
      <div
          class="drop-zone p-4 flex flex-column align-items-center justify-content-center cursor-pointer transition-all transition-duration-300 relative border-round"
          @click="$refs.fileInputA.click()"
          @dragover.prevent
          @drop.prevent="handleDrop($event, 'A')">

        <input type="file" ref="fileInputA" class="hidden" accept="image/*" @change="handleFileSelect($event, 'A')"/>

        <div v-if="imageA" class="w-full h-full absolute top-0 left-0 p-2">
          <img :src="imageA" class="w-full h-full object-contain border-round"/>
        </div>
        <div v-else class="text-center relative z-1">
          <i class="pi pi-image text-5xl text-gray-500 mb-3"></i>
          <div class="font-bold text-xl mb-1 text-white">Image A (Left)</div>
          <div class="text-gray-400">Drop or Click</div>
        </div>
      </div>

      <div
          class="drop-zone p-4 flex flex-column align-items-center justify-content-center cursor-pointer transition-all transition-duration-300 relative border-round"
          @click="$refs.fileInputB.click()"
          @dragover.prevent
          @drop.prevent="handleDrop($event, 'B')">

        <input type="file" ref="fileInputB" class="hidden" accept="image/*" @change="handleFileSelect($event, 'B')"/>

        <div v-if="imageB" class="w-full h-full absolute top-0 left-0 p-2">
          <img :src="imageB" class="w-full h-full object-contain border-round"/>
        </div>
        <div v-else class="text-center relative z-1">
          <i class="pi pi-image text-5xl text-gray-500 mb-3"></i>
          <div class="font-bold text-xl mb-1 text-white">Image B (Right)</div>
          <div class="text-gray-400">Drop or Click</div>
        </div>
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

.drop-zone {
  width: 300px;
  height: 300px;
  background: rgba(20, 20, 20, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  position: relative;
  z-index: 1;
}

.drop-zone::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: var(--app-grad-hover);
  border-radius: inherit;
  z-index: -2;
  opacity: 0;
  filter: blur(8px);
  transition: opacity 0.3s ease;
}

.drop-zone::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #000000;
  border-radius: inherit;
  z-index: -1;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.drop-zone:hover {
  transform: translateY(-5px);
  border-color: transparent;
}

.drop-zone:hover::before {
  opacity: 0.8;
}

.drop-zone:hover::after {
  opacity: 1;
}

.slider-handle {
  background: linear-gradient(#000, #000) padding-box,
  var(--app-grad-hover) border-box;
  border: 2px solid transparent;
}
</style>