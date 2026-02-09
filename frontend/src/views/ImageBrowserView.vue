<script setup>
/**
 * ImageBrowserView.vue
 *
 * The main interface for browsing and viewing images.
 * Supports two modes: 'gallery' (grid view) and 'browser' (single image view).
 * Handles keyboard navigation, folder selection, and integrates with the metadata sidebar.
 */
import { onMounted, onUnmounted, ref, computed, watch } from 'vue';
import { useBrowserStore } from '@/stores/browser';
import BrowserToolbar from '@/components/BrowserToolbar.vue';
import MetadataSidebar from '@/components/MetadataSidebar.vue';
import ImageCard from '@/components/ImageCard.vue';
import FolderNav from '@/components/FolderNav.vue';
import FilmstripView from '@/components/FilmstripView.vue';

const store = useBrowserStore();
const containerRef = ref(null);
const galleryContainer = ref(null);

// Helper to calculate grid columns for Up/Down navigation
const getGridColumns = () => {
  if (!galleryContainer.value) return 1;
  const containerWidth = galleryContainer.value.clientWidth;
  const cardWidth = store.cardSize + 16;
  return Math.floor(containerWidth / cardWidth) || 1;
};

// Keyboard navigation
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
  await store.initialize();
  if (store.files.length === 0) {
    store.search('');
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

// Watch for focus requests from the store (e.g., after search)
watch(() => store.imageFocusRequested, (requested) => {
  if (requested) {
    // Reset the flag
    store.imageFocusRequested = false;

    // Switch to browser view to "focus" the image
    store.setViewMode('browser');
    store.setSidebarOpen(true);

    // Ensure the container is focused so keyboard nav works immediately
    if (containerRef.value) {
      containerRef.value.focus();
    }
  }
});

</script>

<template>
  <div class="flex flex-column h-full overflow-hidden">
    <BrowserToolbar class="flex-shrink-0" />

    <div class="flex flex-grow-1 overflow-hidden relative">

      <FolderNav />

      <div class="flex-grow-1 flex flex-column overflow-hidden relative" ref="containerRef" tabindex="0" style="outline: none;">

        <div v-if="store.viewMode === 'gallery'" class="h-full overflow-y-auto p-3" ref="galleryContainer">
          <div class="flex flex-wrap gap-2 justify-content-center">
            <div v-for="file in store.files" :key="file"
                 :style="{ width: store.cardSize + 'px' }"
                 class="border-round transition-all transition-duration-100"
                 :class="{ 'outline-active': store.selectedFile === file }"
                 @click="store.selectFile(file)"
                 @dblclick="handleGalleryItemDoubleClick(file)">
              <ImageCard :path="file" />
            </div>
          </div>
        </div>

        <div v-else class="flex flex-column h-full">
          <div class="flex-grow-1 flex align-items-center justify-content-center image-viewer-glass relative overflow-hidden">
            <img v-if="mainImageUrl" :src="mainImageUrl"
                 class="max-w-full max-h-full object-contain shadow-8 cursor-pointer"
                 style="transition: all 0.2s ease;"
                 @click="handleImageClick" />

            <div v-else class="text-white text-xl">No image selected</div>

            <div class="absolute left-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200"
                 @click="store.navigate(-1)">
              <i class="pi pi-chevron-left text-4xl text-white-alpha-50"></i>
            </div>
            <div class="absolute right-0 top-0 bottom-0 w-4rem flex align-items-center justify-content-center hover:surface-white-alpha-10 cursor-pointer transition-colors transition-duration-200"
                 @click="store.navigate(1)">
              <i class="pi pi-chevron-right text-4xl text-white-alpha-50"></i>
            </div>
          </div>

          <FilmstripView />
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
/* Selected Item - Gradient Border Effect */
.outline-active {
  position: relative;
  z-index: 1;
  background: transparent;
  /* Remove old outline styles */
  outline: none;
  box-shadow: none;
}

/* 1. Gradient Border/Glow (Deepest) */
.outline-active::before {
  content: '';
  position: absolute;
  inset: -2px; /* Creates the border width */
  background: var(--app-grad-hover);
  border-radius: inherit; /* Match parent radius */
  z-index: -2; /* Behind everything */
  filter: blur(2px); /* Slight glow */
}

/* 2. Black Background (Middle) */
.outline-active::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #000000; /* Opaque black to block center */
  border-radius: inherit;
  z-index: -1; /* Behind content, in front of glow */
}

/* New Glass Class for the Single Image Viewer */
.image-viewer-glass {
  /* Reduced opacity to let the background shine through more */
  background: rgba(0, 0, 0, 0.2);
}

/* Sidebar Slide Transition */
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