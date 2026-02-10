<script setup>
/**
 * @file FilmstripView.vue
 * @description A horizontal, carousel-style navigation component that displays a "filmstrip" of image thumbnails.
 *
 * This component is designed to provide quick visual context and navigation within the current image set.
 * It features a dynamic centering mechanism that automatically scrolls the selected image into the center
 * of the view using CSS transforms.
 *
 * Key functionalities:
 * - Reactive centering: Calculates the necessary translateX offset based on the selected image's index and container width.
 * - Smooth transitions: Uses CSS transitions for fluid movement when the selection changes.
 * - Responsive design: Utilizes ResizeObserver to adapt centering logic to container size changes.
 * - Interactive thumbnails: Allows users to select images directly from the strip.
 */
import { useBrowserStore } from '@/stores/browser';
import { ref, computed, onMounted, onUnmounted } from 'vue';

const store = useBrowserStore();
const containerRef = ref(null);
const containerWidth = ref(0);

const ITEM_WIDTH = 120;
const ITEM_GAP = 8;
const TOTAL_ITEM_WIDTH = ITEM_WIDTH + ITEM_GAP;

/**
 * Calculates the exact `translateX` offset needed to center the selected item within the container.
 * This is the core logic for the carousel's positioning.
 */
const carouselOffset = computed(() => {
  if (!store.selectedFile || containerWidth.value === 0) {
    return 0;
  }

  const selectedIndex = store.files.findIndex(f => f.path === store.selectedFile);
  if (selectedIndex === -1) {
    return 0;
  }

  const selectedItemCenter = (selectedIndex * TOTAL_ITEM_WIDTH) + (TOTAL_ITEM_WIDTH / 2);
  const containerCenter = containerWidth.value / 2;

  return containerCenter - selectedItemCenter;
});

let resizeObserver;
onMounted(() => {
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(entries => {
      if (entries[0]) {
        containerWidth.value = entries[0].contentRect.width;
      }
    });
    resizeObserver.observe(containerRef.value);
  }
});

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
});

</script>

<template>
  <div ref="containerRef" class="filmstrip-view filmstrip-glass h-10rem flex align-items-center overflow-hidden">
    <div
        class="flex flex-nowrap gap-2 px-2 transition-transform duration-500 ease-in-out"
        :style="{ transform: `translateX(${carouselOffset}px)` }"
    >
      <div v-for="file in store.files" :key="file.path"
           class="filmstrip-item flex-shrink-0 cursor-pointer border-round"
           :class="{ 'selected-item': store.selectedFile === file.path }"
           @click="store.selectFile(file)">

        <div class="relative border-round overflow-hidden flex align-items-center justify-content-center" :style="{ width: `${ITEM_WIDTH}px`, height: `${ITEM_WIDTH}px` }">
          <img :src="`http://localhost:8080/api/images/content?path=${encodeURIComponent(file.path)}`"
               loading="lazy"
               class="w-full h-full"
               style="object-fit: contain;" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filmstrip-glass {
  background: var(--app-bg-panel, rgba(20, 25, 35, 0.6));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 -5px 30px rgba(0,0,0,0.3);
}

.filmstrip-item {
  opacity: 0.7;
  border: 2px solid transparent;
  background: rgba(255, 255, 255, 0.05);
  position: relative;
  z-index: 0;
  transition: all 0.3s ease;
}

.filmstrip-item:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.1);
  transform: scale(0.95);
}

.filmstrip-item.selected-item {
  opacity: 1;
  background: transparent;
  transform: scale(1.05);
  z-index: 1;
  border-color: transparent;
}

.filmstrip-item.selected-item::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: var(--app-grad-hover);
  border-radius: inherit;
  z-index: -2;
  filter: blur(2px);
}

.filmstrip-item.selected-item::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #000000;
  border-radius: inherit;
  z-index: -1;
}

.transition-transform {
    transition-property: transform;
}
.duration-500 {
    transition-duration: 500ms;
}
.ease-in-out {
    transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
}
</style>