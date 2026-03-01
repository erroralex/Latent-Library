<script setup>
/**
 * @file VirtualGallery.vue
 * @description A high-performance, grid-based image gallery component utilizing windowed rendering and infinite loading.
 *
 * This component is optimized for large datasets by implementing a "render window" (windowing).
 * Instead of mounting all loaded images, it only renders a subset of items around the current
 * scroll position, significantly reducing DOM nodes and improving browser performance.
 *
 * Key functionalities:
 * - **Windowed Rendering:** Only renders ±RENDER_WINDOW items around the scroll anchor to maintain a stable DOM budget.
 * - **Throttled Scroll Tracking:** Uses requestAnimationFrame to update the scroll anchor without blocking the main thread.
 * - **Dynamic Grid:** Automatically adjusts the number of columns based on container width and card size.
 * - **Infinite Loading:** Preserves Intersection Observer logic to trigger `loadMoreImages()` at the bottom of the scrollable area.
 * - **Multi-Selection:** Supports Shift+Click and Ctrl+Click for batch operations.
 */
import {ref, computed, onMounted, onUnmounted, watch, nextTick} from 'vue';
import {useBrowserStore} from '@/stores/browser';
import ImageCard from '@/components/ImageCard.vue';

const RENDER_WINDOW = 200;

const store = useBrowserStore();
const galleryContainer = ref(null);
const observerTarget = ref(null);
const gridCols = ref(4);

const scrollAnchorIndex = ref(0);
let rafHandle = null;

/**
 * Throttled scroll handler to update the scroll anchor index.
 * Uses requestAnimationFrame to ensure smooth performance during fast scrolling.
 */
const onScroll = () => {
  if (rafHandle) return;

  rafHandle = requestAnimationFrame(() => {
    if (galleryContainer.value) {
      const { scrollTop, scrollHeight, clientHeight } = galleryContainer.value;
      if (scrollHeight > clientHeight) {
        const scrollFraction = scrollTop / (scrollHeight - clientHeight);
        scrollAnchorIndex.value = Math.floor(scrollFraction * store.files.length);
      } else {
        scrollAnchorIndex.value = 0;
      }
    }
    rafHandle = null;
  });
};

/**
 * Computes a slice of store.files centered around the scrollAnchorIndex.
 * This prevents the DOM from becoming saturated with thousands of image elements.
 */
const visibleFiles = computed(() => {
  if (!store.files || store.files.length === 0) return [];

  const start = Math.max(0, scrollAnchorIndex.value - RENDER_WINDOW);
  const end = Math.min(store.files.length, scrollAnchorIndex.value + RENDER_WINDOW);

  return store.files.slice(start, end);
});

const chunkedFiles = computed(() => {
  const files = visibleFiles.value;
  if (files.length === 0) return [];

  const chunks = [];
  for (let i = 0; i < files.length; i += gridCols.value) {
    chunks.push(files.slice(i, i + gridCols.value));
  }
  return chunks;
});

const updateGridCols = () => {
  if (!galleryContainer.value) return;
  const containerWidth = galleryContainer.value.clientWidth;
  const cardWidth = store.cardSize + 16;
  const cols = Math.floor(containerWidth / cardWidth) || 1;
  if (gridCols.value !== cols) {
    gridCols.value = cols;
  }
};

watch(() => store.cardSize, () => {
  updateGridCols();
});

let observer;
let resizeObserver;

const setupObserver = () => {
  if (observer) observer.disconnect();

  observer = new IntersectionObserver((entries) => {
    const target = entries[0];
    if (target.isIntersecting && store.hasMore && !store.isFetchingMore && !store.isLoading) {
      store.loadMoreImages();
    }
  }, {
    root: galleryContainer.value,
    rootMargin: '400px',
    threshold: 0.1
  });

  if (observerTarget.value) {
    observer.observe(observerTarget.value);
  }
};

onMounted(() => {
  updateGridCols();

  resizeObserver = new ResizeObserver(() => {
    updateGridCols();
  });
  if (galleryContainer.value) {
    resizeObserver.observe(galleryContainer.value);
  }

  window.addEventListener('resize', updateGridCols);

  nextTick(() => {
    setupObserver();
  });
});

onUnmounted(() => {
  window.removeEventListener('resize', updateGridCols);
  if (resizeObserver) resizeObserver.disconnect();
  if (observer) observer.disconnect();
  if (rafHandle) cancelAnimationFrame(rafHandle);
});

watch(() => store.files.length, () => {
  nextTick(() => {
    if (observerTarget.value && observer) {
      observer.unobserve(observerTarget.value);
      observer.observe(observerTarget.value);
    }
  });
});

const handleGalleryItemClick = (file, event) => {
  const multiSelect = event.ctrlKey || event.metaKey;
  const rangeSelect = event.shiftKey;
  store.selectFile(file, multiSelect, rangeSelect);
};

const handleGalleryItemDoubleClick = (file) => {
  store.selectFile(file);
  store.setViewMode('browser');
  store.setSidebarOpen(true);
};

const emit = defineEmits(['contextmenu']);

const onImageContextMenu = (payload) => {
  if (!store.selectedFiles.has(payload.file.path)) {
    store.selectFile(payload.file);
  }
  emit('contextmenu', payload);
};

watch(() => store.selectedFile, (newPath) => {
  if (!newPath || !galleryContainer.value) return;

  const selectedIndex = store.files.findIndex(f => f.path === newPath);
  if (selectedIndex === -1) return;

  scrollAnchorIndex.value = selectedIndex;

  nextTick(() => {
    const row = Math.floor(selectedIndex / gridCols.value);
    const cardHeight = store.cardSize + 16; // card size + gap
    const targetScrollTop = row * cardHeight;

    const container = galleryContainer.value;
    const itemTop = targetScrollTop;
    const itemBottom = targetScrollTop + cardHeight;
    const visibleTop = container.scrollTop;
    const visibleBottom = container.scrollTop + container.clientHeight;

    if (itemTop < visibleTop) {
      container.scrollTop = itemTop - cardHeight;
    } else if (itemBottom > visibleBottom) {
      container.scrollTop = itemBottom - container.clientHeight + cardHeight;
    }
  });
});

defineExpose({ gridCols });
</script>

<template>
  <div class="h-full overflow-y-auto p-3 gallery-bg custom-scrollbar"
       ref="galleryContainer"
       @scroll="onScroll">

    <div class="flex flex-column gap-2">
      <div v-for="(row, rowIndex) in chunkedFiles" :key="rowIndex" class="flex gap-2 justify-content-center">
        <div v-for="file in row" :key="file.path"
             :style="{ width: store.cardSize + 'px', height: store.cardSize + 'px' }"
             class="image-card-wrapper transition-all transition-duration-100"
             :class="{ 'outline-active': store.selectedFiles.has(file.path) }"
             @click="handleGalleryItemClick(file, $event)"
             @dblclick="handleGalleryItemDoubleClick(file)">

          <ImageCard :file="file" @contextmenu="onImageContextMenu"/>

        </div>
        <div v-if="row.length < gridCols"
             v-for="n in (gridCols - row.length)"
             :key="'spacer-' + n"
             :style="{ width: store.cardSize + 'px' }">
        </div>
      </div>
    </div>

    <div ref="observerTarget" class="h-4rem w-full flex align-items-center justify-content-center mt-4">
      <i v-if="store.isFetchingMore" class="pi pi-spin pi-spinner text-2xl text-gray-500"></i>
      <span v-else-if="!store.hasMore && store.files.length > 0" class="text-gray-600 text-sm">End of library</span>
    </div>

    <div v-if="store.files.length === 0 && !store.isLoading" class="text-center p-5 text-gray-500 h-full flex align-items-center justify-content-center">
      <div class="flex flex-column align-items-center gap-3">
        <i class="pi pi-images text-4xl"></i>
        <span>No images found in this folder.</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gallery-bg {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 10px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.1);
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
  border-radius: 5px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.3);
}

.image-card-wrapper {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
}

.outline-active {
  position: relative;
  z-index: 1;
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
  box-shadow: 0 0 10px rgba(var(--primary-color-rgb), 0.5);
}
</style>
