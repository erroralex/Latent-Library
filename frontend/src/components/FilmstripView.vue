<script setup>
/**
 * FilmstripView.vue
 *
 * A horizontal scrollable list of image thumbnails.
 * Used in the browser view to navigate between images in the current folder.
 * Automatically scrolls to keep the selected image in view.
 */
import { useBrowserStore } from '@/stores/browser';
import { ref, watch, nextTick, onMounted } from 'vue';

const store = useBrowserStore();
const container = ref(null);

const scrollToSelected = () => {
  if (!container.value || !store.selectedFile) return;

  const index = store.files.indexOf(store.selectedFile);
  if (index === -1) return;

  const element = container.value.children[index];
  if (element) {
    element.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
  }
};

watch(() => store.selectedFile, () => {
  nextTick(scrollToSelected);
});

onMounted(() => {
  scrollToSelected();
});
</script>

<template>
  <div class="filmstrip-view filmstrip-glass h-10rem flex flex-column">
    <div ref="container" class="flex-grow-1 overflow-x-auto overflow-y-hidden flex flex-nowrap gap-2 p-2 align-items-center">
      <div v-for="file in store.files" :key="file"
           class="filmstrip-item flex-shrink-0 cursor-pointer border-round transition-all transition-duration-200"
           :class="{ 'selected-item': store.selectedFile === file }"
           @click="store.selectFile(file)">

        <div class="relative border-round overflow-hidden flex align-items-center justify-content-center" style="width: 120px; height: 120px;">
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
  /* Dark Glass Theme */
  background: var(--app-bg-panel, rgba(20, 25, 35, 0.6));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 -5px 30px rgba(0,0,0,0.3);
}

.filmstrip-item {
  opacity: 0.7;
  border: 2px solid transparent; /* Placeholder for spacing */
  background: rgba(255, 255, 255, 0.05); /* Slight box background */
  position: relative;
  z-index: 0;
}

.filmstrip-item:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.1);
  transform: scale(0.95);
}

/* Selected Item - Gradient Border Effect */
.filmstrip-item.selected-item {
  opacity: 1;
  background: transparent; /* Let pseudo-elements handle background */
  transform: scale(1.05);
  z-index: 1;
  border-color: transparent; /* Remove default border */
}

/* 1. Gradient Border/Glow (Deepest) */
.filmstrip-item.selected-item::before {
  content: '';
  position: absolute;
  inset: -2px; /* Creates the border width */
  background: var(--app-grad-hover);
  border-radius: inherit; /* Match parent radius */
  z-index: -2; /* Behind everything */
  filter: blur(2px); /* Slight glow */
}

/* 2. Black Background (Middle) */
.filmstrip-item.selected-item::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #000000; /* Opaque black to block center */
  border-radius: inherit;
  z-index: -1; /* Behind content, in front of glow */
}
</style>