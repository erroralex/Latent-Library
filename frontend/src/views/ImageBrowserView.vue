<script setup>
/**
 * @file ImageBrowserView.vue
 * @description The main user interface for browsing and viewing images. This component
 * orchestrates the entire browser experience, supporting two primary modes: 'gallery' for a
 * grid-based overview and 'browser' for a focused, single-image view with a filmstrip.
 * It handles keyboard navigation, folder selection, and integrates the metadata sidebar.
 */
import { onMounted, onUnmounted, ref, computed, watch } from 'vue';
import { useBrowserStore } from '@/stores/browser';
import { useRoute } from 'vue-router';
import BrowserToolbar from '@/components/BrowserToolbar.vue';
import MetadataSidebar from '@/components/MetadataSidebar.vue';
import ImageCard from '@/components/ImageCard.vue';
import FilmstripView from '@/components/FilmstripView.vue';
import VirtualScroller from 'primevue/virtualscroller';

const store = useBrowserStore();
const route = useRoute();
const containerRef = ref(null);
const galleryContainer = ref(null);

const getGridColumns = () => {
  if (!galleryContainer.value) return 1;
  // VirtualScroller might wrap the container, so we need to be careful about width
  const containerWidth = galleryContainer.value.$el ? galleryContainer.value.$el.clientWidth : galleryContainer.value.clientWidth;
  const cardWidth = store.cardSize + 16;
  return Math.floor(containerWidth / cardWidth) || 1;
};

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) {
    e.preventDefault();
  }

  const cols = store.viewMode === 'gallery' ? getGridColumns() : 1;

  switch(e.key) {
    case 'ArrowLeft':
    case 'a':
    case 'A':
      store.navigate(-1);
      break;

    case 'ArrowRight':
    case 'd':
    case 'D':
      store.navigate(1);
      break;

    case 'ArrowUp':
    case 'w':
    case 'W':
      if (store.viewMode === 'gallery') store.navigate(-cols);
      break;

    case 'ArrowDown':
    case 's':
    case 'S':
      if (store.viewMode === 'gallery') store.navigate(cols);
      break;

    case 'g':
    case 'G':
      store.setViewMode('gallery');
      break;

    case 'b':
    case 'B':
      store.setViewMode('browser');
      break;

    case 'Enter':
      if (store.viewMode === 'gallery') {
        store.setViewMode('browser');
        store.setSidebarOpen(true);
      }
      break;

    case 'Escape':
      if (store.viewMode === 'browser') store.setViewMode('gallery');
      break;
  }
};

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown);

  // Check if we are navigating to a specific collection
  if (route.query.collection) {
      if (store.availableModels.length === 0) {
          await store.loadFilters();
      }
      await store.loadCollection(route.query.collection);
  } else {
      // Default behavior: load last folder or initialize
      await store.initialize();
      if (store.files.length === 0) {
        store.search('');
      }
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});

const mainImageUrl = computed(() => {
  if (!store.selectedFile) return null;
  return `http://localhost:8080/api/images/content?path=${encodeURIComponent(store.selectedFile)}`;
});

const handleImageClick = () => {
  store.toggleSidebar();
};

const handleGalleryItemDoubleClick = (file) => {
  store.selectFile(file);
  store.setViewMode('browser');
  store.setSidebarOpen(true);
};

watch(() => store.viewMode, (newMode) => {
  if (newMode === 'gallery') {
    store.setSidebarOpen(false);
  }
});

watch(() => store.imageFocusRequested, (requested) => {
  if (requested) {
    store.imageFocusRequested = false;
    store.setViewMode('browser');
    store.setSidebarOpen(true);
    if (containerRef.value) {
      containerRef.value.focus();
    }
  }
});

// Virtual Scroller Logic
const virtualItems = computed(() => {
    // We need to chunk the files array into rows for the virtual scroller
    // But VirtualScroller handles lists. For a grid, we can use the 'grid' display of VirtualScroller
    // However, PrimeVue's VirtualScroller is list-based. To make a grid, we need to chunk the data ourselves
    // or use CSS grid within the item template if the scroller supports it.
    // A simpler approach for a responsive grid with VirtualScroller is to pre-calculate rows.
    // But that breaks responsiveness on resize.
    //
    // Better approach: Use VirtualScroller with a fixed item size and flex wrap? No.
    //
    // Let's use the standard VirtualScroller and chunk the data into arrays of arrays (rows).
    // We need to re-calculate chunks when window resizes.
    return store.files;
});

// For simplicity and robustness with PrimeVue VirtualScroller, we will use it in list mode
// but each "item" will be a row of images.
const gridCols = ref(4); // Default
const chunkedFiles = computed(() => {
    const chunks = [];
    for (let i = 0; i < store.files.length; i += gridCols.value) {
        chunks.push(store.files.slice(i, i + gridCols.value));
    }
    return chunks;
});

const updateGridCols = () => {
    if (!galleryContainer.value) return;
    // Access the DOM element correctly whether it's a component ref or element ref
    const el = galleryContainer.value.$el || galleryContainer.value;
    if (!el) return;

    const containerWidth = el.clientWidth;
    const cardWidth = store.cardSize + 16; // card size + gap
    const cols = Math.floor(containerWidth / cardWidth) || 1;
    gridCols.value = cols;
};

// Resize observer for the gallery container
let resizeObserver;
watch(() => store.viewMode, (newMode) => {
    if (newMode === 'gallery') {
        // Wait for DOM update
        setTimeout(() => {
            if (galleryContainer.value) {
                const el = galleryContainer.value.$el || galleryContainer.value;
                if (!resizeObserver) {
                    resizeObserver = new ResizeObserver(() => updateGridCols());
                }
                resizeObserver.observe(el);
                updateGridCols();
            }
        }, 100);
    } else {
        if (resizeObserver) resizeObserver.disconnect();
    }
});

const onScrollIndexChange = (event) => {
    // Check if we are near the end to load more
    // event.last is the index of the last visible item
    if (store.hasMore && !store.isFetchingMore) {
        const totalRows = chunkedFiles.value.length;
        if (event.last >= totalRows - 2) { // Load more when 2 rows from bottom
            store.loadMore();
        }
    }
};

</script>

<template>
  <div class="flex flex-column h-full overflow-hidden">
    <BrowserToolbar class="flex-shrink-0" />

    <div class="flex-grow-1 overflow-hidden relative">
      <div class="h-full" ref="containerRef" tabindex="0" style="outline: none;">

        <div v-if="store.viewMode === 'gallery'" class="h-full p-3" ref="galleryContainer">
             <VirtualScroller :items="chunkedFiles" :itemSize="store.cardSize + 16" class="h-full"
                              @scroll-index-change="onScrollIndexChange">
                <template v-slot:item="{ item, options }">
                    <div class="flex gap-2 justify-content-center" :style="{ height: (store.cardSize + 8) + 'px', marginBottom: '8px' }">
                        <div v-for="file in item" :key="file"
                             :style="{ width: store.cardSize + 'px', height: store.cardSize + 'px' }"
                             class="border-round transition-all transition-duration-100"
                             :class="{ 'outline-active': store.selectedFile === file }"
                             @click="store.selectFile(file)"
                             @dblclick="handleGalleryItemDoubleClick(file)">
                            <ImageCard :path="file" />
                        </div>
                        <!-- Fill empty space in the last row to align left if needed, or center as per flex -->
                    </div>
                </template>
             </VirtualScroller>
             <div v-if="store.files.length === 0 && !store.isLoading" class="text-center p-5 text-gray-500">
                 No images found.
             </div>
        </div>

        <!--
          DEFINITIVE LAYOUT FIX:
          This layout uses absolute positioning to create two independent, non-interacting regions.
          This prevents the image's aspect ratio from influencing the filmstrip's position.
        -->
        <div v-else class="relative h-full">
          <!-- The image viewer is locked to the top and fills all space DOWN TO the filmstrip's height. -->
          <div class="absolute top-0 left-0 right-0 flex align-items-center justify-content-center image-viewer-glass" style="bottom: 10rem;">
            <img v-if="mainImageUrl" :src="mainImageUrl"
                 class="max-w-full max-h-full object-contain shadow-8 cursor-pointer"
                 style="transition: all 0.2s ease;"
                 @click="handleImageClick" />

            <div v-else class="text-white text-xl">No image selected</div>

            <!-- Navigation Arrows -->
            <div class="absolute left-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200"
                 @click="store.navigate(-1)">
              <i class="pi pi-chevron-left text-4xl text-white-alpha-50"></i>
            </div>
            <div class="absolute right-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200"
                 @click="store.navigate(1)">
              <i class="pi pi-chevron-right text-4xl text-white-alpha-50"></i>
            </div>
          </div>

          <!-- The filmstrip is locked to the bottom with a fixed height matching its internal class `h-10rem`. -->
          <FilmstripView class="absolute bottom-0 left-0 right-0 w-full" style="height: 10rem;" />
        </div>
      </div>

      <Transition name="slide-sidebar">
        <div v-if="store.isSidebarOpen" class="absolute top-0 right-0 bottom-0 z-5 shadow-8">
          <MetadataSidebar />
        </div>
      </Transition>
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

.image-viewer-glass {
  background: rgba(0, 0, 0, 0.2);
}

.slide-sidebar-enter-active,
.slide-sidebar-leave-active {
  transition: transform 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.slide-sidebar-enter-from,
.slide-sidebar-leave-to {
  transform: translateX(100%);
}

.slide-sidebar-enter-to,
.slide-sidebar-leave-from {
  transform: translateX(0);
}
</style>