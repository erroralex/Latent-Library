<script setup>
/**
 * @file VirtualGallery.vue
 * @description A high-performance, grid-based image gallery component utilizing virtual scrolling.
 *
 * This component is responsible for rendering large sets of images efficiently by only mounting
 * elements currently visible in the viewport. It dynamically calculates grid columns based on
 * the container width and user-defined card size.
 *
 * Key functionalities:
 * - Virtual Scrolling: Uses PrimeVue's VirtualScroller to handle thousands of items with minimal DOM overhead.
 * - Dynamic Grid: Automatically adjusts the number of columns when the window is resized or the card size changes.
 * - Smart Scrolling: Monitors selection changes to ensure the active image is always scrolled into view.
 * - Infinite Loading: Triggers pagination/lazy-loading when the user nears the bottom of the list.
 */
import {ref, computed, onMounted, onUnmounted, watch, nextTick} from 'vue';
import {useBrowserStore} from '@/stores/browser';
import ImageCard from '@/components/ImageCard.vue';
import VirtualScroller from 'primevue/virtualscroller';

const store = useBrowserStore();
const galleryContainer = ref(null);
const scrollerRef = ref(null);
const gridCols = ref(4);

/**
 * Chunks the flat file list into rows based on the current grid column count.
 * This is required for the VirtualScroller to render a grid layout.
 */
const chunkedFiles = computed(() => {
  const chunks = [];
  for (let i = 0; i < store.files.length; i += gridCols.value) {
    chunks.push(store.files.slice(i, i + gridCols.value));
  }
  return chunks;
});

const updateGridCols = () => {
  if (!galleryContainer.value) return;
  const el = galleryContainer.value.$el || galleryContainer.value;
  if (!el) return;

  requestAnimationFrame(() => {
    const containerWidth = el.clientWidth;
    const cardWidth = store.cardSize + 16;
    const cols = Math.floor(containerWidth / cardWidth) || 1;
    if (gridCols.value !== cols) {
      gridCols.value = cols;
    }
  });
};

watch(() => store.cardSize, () => {
  updateGridCols();
});

watch(() => store.selectedFile, async (newFile) => {
  if (!newFile || !scrollerRef.value) return;

  const rowIndex = chunkedFiles.value.findIndex(chunk => chunk.some(f => f.path === newFile));

  if (rowIndex !== -1) {
    await nextTick();

    const scrollerEl = scrollerRef.value.$el;
    if (scrollerEl) {
      const itemSize = store.cardSize + 16;
      const rowTop = rowIndex * itemSize;
      const rowBottom = rowTop + itemSize;

      const scrollTop = scrollerEl.scrollTop;
      const clientHeight = scrollerEl.clientHeight;

      if (rowTop < scrollTop) {
        scrollerEl.scrollTop = rowTop;
      } else if (rowBottom > scrollTop + clientHeight) {
        scrollerEl.scrollTop = rowBottom - clientHeight;
      }
    }
  }
});

let resizeObserver;

onMounted(() => {
  setTimeout(() => {
    if (galleryContainer.value) {
      const el = galleryContainer.value.$el || galleryContainer.value;
      resizeObserver = new ResizeObserver(() => updateGridCols());
      resizeObserver.observe(el);
      updateGridCols();
    }
  }, 100);
});

onUnmounted(() => {
  if (resizeObserver) resizeObserver.disconnect();
});

const onScrollIndexChange = (event) => {
  if (store.hasMore && !store.isFetchingMore) {
    const totalRows = chunkedFiles.value.length;
    if (event.last >= totalRows - 5) {
      store.loadMore();
    }
  }
};

const handleGalleryItemDoubleClick = (file) => {
  store.selectFile(file);
  store.setViewMode('browser');
  store.setSidebarOpen(true);
};

defineExpose({gridCols});
</script>

<template>
  <div class="h-full p-3 overflow-hidden" ref="galleryContainer">
    <VirtualScroller ref="scrollerRef" :items="chunkedFiles" :itemSize="store.cardSize + 16" class="h-full"
                     @scroll-index-change="onScrollIndexChange">
      <template v-slot:item="{ item, options }">
        <div class="flex gap-2 justify-content-center"
             :style="{ height: (store.cardSize + 8) + 'px', marginBottom: '8px' }">
          <div v-for="file in item" :key="file.path"
               :style="{ width: store.cardSize + 'px', height: store.cardSize + 'px' }"
               class="border-round transition-all transition-duration-100"
               :class="{ 'outline-active': store.selectedFile === file.path }"
               @click="store.selectFile(file)"
               @dblclick="handleGalleryItemDoubleClick(file)">
            <ImageCard :file="file"/>
          </div>
        </div>
      </template>
    </VirtualScroller>
    <div v-if="store.files.length === 0 && !store.isLoading" class="text-center p-5 text-gray-500">
      No images found.
    </div>
  </div>
</template>

<style scoped>
.outline-active {
  position: relative;
  z-index: 1;
  background: transparent;
  outline: none;
  box-shadow: none;
}

.outline-active::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: var(--app-grad-hover);
  border-radius: inherit;
  z-index: -2;
  filter: blur(2px);
}

.outline-active::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #000000;
  border-radius: inherit;
  z-index: -1;
}
</style>