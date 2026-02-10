<script setup>
import { computed } from 'vue';
import { useBrowserStore } from '@/stores/browser';
import FilmstripView from '@/components/FilmstripView.vue';

const store = useBrowserStore();

const mainImageUrl = computed(() => {
  if (!store.selectedFile) return null;
  return `http://localhost:8080/api/images/content?path=${encodeURIComponent(store.selectedFile)}`;
});

const handleImageClick = () => {
  store.toggleSidebar();
};
</script>

<template>
    <div class="relative h-full">
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
</template>

<style scoped>
.image-viewer-glass {
  background: rgba(0, 0, 0, 0.2);
}
</style>