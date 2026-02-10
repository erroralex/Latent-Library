<script setup>
/**
 * @file ImageBrowserView.vue
 * @description The primary application view for exploring and interacting with the image library.
 *
 * This view acts as the main orchestrator for the image browsing experience. It dynamically switches
 * between a high-level 'gallery' grid and a focused 'browser' (single image) view. It manages
 * global keyboard shortcuts for navigation and view control, ensuring a fluid user experience.
 *
 * Key functionalities:
 * - View Orchestration: Toggles between VirtualGallery and SingleImageViewer based on application state.
 * - Keyboard Navigation: Implements a comprehensive set of hotkeys (Arrows, WASD, Enter, Escape, G, B) for rapid browsing.
 * - State Synchronization: Integrates with the Pinia store to handle folder initialization, collection loading, and search.
 * - Layout Management: Controls the visibility of the MetadataSidebar and ensures the main viewer retains focus.
 * - Deep Linking: Supports direct navigation to specific collections via URL query parameters.
 */
import { onMounted, onUnmounted, ref, watch } from 'vue';
import { useBrowserStore } from '@/stores/browser';
import { useRoute } from 'vue-router';
import BrowserToolbar from '@/components/BrowserToolbar.vue';
import MetadataSidebar from '@/components/MetadataSidebar.vue';
import VirtualGallery from '@/components/VirtualGallery.vue';
import SingleImageViewer from '@/components/SingleImageViewer.vue';

const store = useBrowserStore();
const route = useRoute();
const containerRef = ref(null);
const virtualGalleryRef = ref(null);

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) {
    e.preventDefault();
  }

  const cols = (store.viewMode === 'gallery' && virtualGalleryRef.value) ? virtualGalleryRef.value.gridCols : 1;

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

  if (route.query.collection) {
      if (store.availableModels.length === 0) {
          await store.loadFilters();
      }
      await store.loadCollection(route.query.collection);
  } else {
      await store.initialize();
      if (store.files.length === 0) {
        store.search('');
      }
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});

watch(() => store.viewMode, (newMode) => {
  if (newMode === 'gallery') {
    store.setSidebarOpen(true);
  } else {
    store.setSidebarOpen(false);
  }
}, { immediate: true });

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

</script>

<template>
  <div class="flex flex-column h-full overflow-hidden">
    <BrowserToolbar class="flex-shrink-0" />

    <div class="flex-grow-1 overflow-hidden flex">
      <div class="flex-grow-1 h-full" ref="containerRef" tabindex="0" style="outline: none;">
        <VirtualGallery v-if="store.viewMode === 'gallery'" ref="virtualGalleryRef" />
        <SingleImageViewer v-else />
      </div>

      <div v-if="store.isSidebarOpen" class="flex-shrink-0 shadow-8 z-5">
        <MetadataSidebar />
      </div>
    </div>
  </div>
</template>
