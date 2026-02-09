<script setup>
/**
 * App.vue
 * Root Navigation & Layout
 */
import { RouterView, useRouter, useRoute } from 'vue-router'
import Button from 'primevue/button';
import { ref, computed } from "vue";

const router = useRouter();
const route = useRoute();

const items = ref([
  {
    label: 'Gallery',
    icon: 'pi pi-images',
    path: '/'
  },
  {
    label: 'Collections',
    icon: 'pi pi-folder',
    path: '/collections'
  },
  {
    label: 'Comparator',
    icon: 'pi pi-arrow-right-arrow-left',
    path: '/comparator'
  },
  {
    label: 'Scrubber',
    icon: 'pi pi-shield',
    path: '/scrub'
  },
  {
    label: 'Speed Sorter',
    icon: 'pi pi-bolt',
    path: '/speedsorter'
  }
]);

const navigate = (path) => {
  router.push(path);
};

const isActive = (path) => {
  if (path === '/' && route.path === '/') return true;
  if (path !== '/' && route.path.startsWith(path)) return true;
  return false;
};

const minimizeWindow = () => {
  if (window.windowAPI) window.windowAPI.minimize();
};

const maximizeWindow = () => {
  if (window.windowAPI) window.windowAPI.maximize();
};

const closeWindow = () => {
  if (window.windowAPI) window.windowAPI.close();
};
</script>

<template>
  <div class="layout-wrapper h-screen flex flex-column overflow-hidden">
    <header class="menubar-glass flex align-items-center px-5 py-3 gap-5 draggable-header">
      <div class="flex align-items-center gap-3 mr-5 no-drag">
        <img src="@/assets/icon.png" alt="Logo" style="height: 42px;" />
        <span class="text-2xl font-bold text-gradient">AI Toolbox</span>
      </div>

      <div class="flex gap-2 flex-grow-1 no-drag">
        <Button v-for="item in items" :key="item.path"
                :label="item.label"
                :icon="item.icon"
                class="p-button-text font-semibold text-lg px-4 py-2 transition-colors transition-duration-200"
                :class="[ isActive(item.path) ? 'text-cyan-400 bg-white-alpha-10' : 'text-gray-400 hover:text-white hover:bg-white-alpha-10' ]"
                @click="navigate(item.path)" />
      </div>

      <div class="flex gap-2 no-drag">
        <Button icon="pi pi-minus" class="p-button-text text-gray-400 hover:text-white hover:bg-white-alpha-10 w-3rem h-3rem" @click="minimizeWindow" />
        <Button icon="pi pi-window-maximize" class="p-button-text text-gray-400 hover:text-white hover:bg-white-alpha-10 w-3rem h-3rem" @click="maximizeWindow" />
        <Button icon="pi pi-times" class="window-close-btn p-button-text text-gray-400 w-3rem h-3rem" @click="closeWindow" />
      </div>
    </header>

    <main class="flex-grow-1 overflow-hidden relative">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
/* Glass Header Style */
.menubar-glass {
  background: var(--app-bg-header, rgba(15, 18, 25, 0.75));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 30px rgba(0,0,0,0.5);
  z-index: 1000;
}

.text-gradient {
  background: var(--app-grad-text, linear-gradient(90deg, #66fcf1, #d870ff));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.draggable-header {
  -webkit-app-region: drag;
}

.no-drag {
  -webkit-app-region: no-drag;
}

/* Specific override for the close button to ensure red hover */
.window-close-btn:hover {
  background-color: var(--app-red-warning) !important;
  color: white !important;
  box-shadow: 0 0 15px rgba(255, 77, 77, 0.6) !important;
  background-image: none !important; /* Remove the global gradient */
}
</style>