<script setup>
/**
 * @file FilmstripView.vue
 * @description A horizontal, carousel-style list of image thumbnails. This component
 * keeps the currently selected image centered in the view, with other images scrolling
 * horizontally around it.
 */
import { useBrowserStore } from '@/stores/browser';
import { ref, computed, onMounted, onUnmounted } from 'vue';

const store = useBrowserStore();
const containerRef = ref(null);
const containerWidth = ref(0);

// --- Carousel Logic ---
const ITEM_WIDTH = 120; // From CSS width
const ITEM_GAP = 8;    // From `gap-2` class (0.5rem)
const TOTAL_ITEM_WIDTH = ITEM_WIDTH + ITEM_GAP;

// This computed property is the core of the carousel logic.
// It calculates the exact `translateX` offset needed to center the selected item.
const carouselOffset = computed(() => {
  if (!store.selectedFile || containerWidth.value === 0) {
    return 0;
  }

  const selectedIndex = store.files.indexOf(store.selectedFile);
  if (selectedIndex === -1) {
    return 0;
  }

  // Calculate the position of the center of the selected item
  const selectedItemCenter = (selectedIndex * TOTAL_ITEM_WIDTH) + (TOTAL_ITEM_WIDTH / 2);

  // Calculate the position of the center of the filmstrip container
  const containerCenter = containerWidth.value / 2;

  // The offset is the difference, which will shift the items container
  // so that the two centers align.
  return containerCenter - selectedItemCenter;
});

// We need to know the width of the container to center things correctly.
// A ResizeObserver is the most robust way to do this.
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
  <!-- The outer container clips the content -->
  <div ref="containerRef" class="filmstrip-view filmstrip-glass h-10rem flex align-items-center overflow-hidden">
    <!-- The inner container is moved with a CSS transform -->
    <div
        class="flex flex-nowrap gap-2 px-2 transition-transform duration-500 ease-in-out"
        :style="{ transform: `translateX(${carouselOffset}px)` }"
    >
      <div v-for="file in store.files" :key="file"
           class="filmstrip-item flex-shrink-0 cursor-pointer border-round"
           :class="{ 'selected-item': store.selectedFile === file }"
           @click="store.selectFile(file)">

        <div class="relative border-round overflow-hidden flex align-items-center justify-content-center" :style="{ width: `${ITEM_WIDTH}px`, height: `${ITEM_WIDTH}px` }">
          <img :src="`http://localhost:8080/api/images/content?path=${encodeURIComponent(file)}`"
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

/* Helper for smooth transitions on the transform property */
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