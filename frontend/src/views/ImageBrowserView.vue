<script setup>
/**
 * @file ImageBrowserView.vue
 * @description The main user interface for browsing and viewing images. This component
 * orchestrates the entire browser experience, supporting two primary modes: 'gallery' for a
 * grid-based overview and 'browser' for a focused, single-image view with a filmstrip.
 * It handles keyboard navigation, folder selection, and integrates the metadata sidebar.
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

watch(() => store.viewMode, (newMode) => {
  if (newMode === 'gallery') {
    store.setSidebarOpen(true); // Keep sidebar open in gallery view
  } else {
    store.setSidebarOpen(false); // Or manage as before, e.g., close it
  }
}, { immediate: true }); // Run on component mount

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
