<script setup>
/**
 * @file App.vue
 * @description The root component of the AI Toolbox application, defining the global layout and navigation structure.
 *
 * This component establishes the primary UI shell, including the top navigation bar (header) and the main
 * content area. It manages the application's top-level routing and provides a consistent frame for all
 * functional views.
 *
 * Key functionalities:
 * - Global Navigation: Implements the primary menu for switching between Gallery, Collections, Comparator, Scrubber, and Speed Sorter.
 * - Window Management: Provides custom controls for minimizing, maximizing, and closing the application window, integrated with Electron APIs.
 * - Layout Orchestration: Combines the FolderNav sidebar with the dynamic RouterView for content rendering.
 * - Visual Identity: Defines the application's signature "glassmorphism" aesthetic and gradient-based branding.
 * - Draggable Header: Implements Electron-specific draggable regions for the custom title bar.
 */
import { RouterView, useRouter, useRoute } from 'vue-router'
import Button from 'primevue/button';
import Toast from 'primevue/toast';
import { ref } from "vue";
import FolderNav from '@/components/FolderNav.vue';
import { useBrowserStore } from '@/stores/browser';

const router = useRouter();
const route = useRoute();
const store = useBrowserStore();
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
  if (path === '/') {
    return route.path === '/' || route.path.startsWith('/browser');
  }
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
    <Toast position="bottom-right" />
    <header class="menubar-glass flex align-items-center pl-5 pr-1 py-2 gap-5 draggable-header">
      <div class="flex align-items-center gap-3 mr-5 no-drag">
        <img src="@/assets/icon.png" alt="Logo" style="height: 42px;" />
        <span class="text-2xl font-bold text-gradient">AI Toolbox</span>
      </div>

      <div class="flex gap-2 flex-grow-1 no-drag">
        <Button v-for="item in items" :key="item.path"
                :label="item.label"
                :icon="item.icon"
                class="nav-btn p-button-text font-semibold text-lg px-4 py-2 transition-all transition-duration-200"
                :class="{ 'active-nav-btn': isActive(item.path) }"
                @click="navigate(item.path)" />
      </div>

      <div class="flex gap-1 no-drag">
        <Button icon="pi pi-minus" class="p-button-text text-gray-400 hover:text-white hover:bg-white-alpha-10 w-3rem h-3rem" @click="minimizeWindow" />
        <Button icon="pi pi-window-maximize" class="p-button-text text-gray-400 hover:text-white hover:bg-white-alpha-10 w-3rem h-3rem" @click="maximizeWindow" />
        <Button icon="pi pi-times" class="window-close-btn p-button-text text-gray-400 w-3rem h-3rem" @click="closeWindow" />
      </div>
    </header>

    <main class="flex-grow-1 overflow-hidden flex">
      <FolderNav />
      <div class="flex-grow-1 overflow-hidden relative">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.menubar-glass {
  background: var(--bg-header);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-bottom: 1px solid var(--border-light);
  box-shadow: var(--shadow-panel);
  z-index: 1000;
}

.draggable-header {
  -webkit-app-region: drag;
}

.no-drag {
  -webkit-app-region: no-drag;
}

/* Window close button styles moved to buttons.css, but keeping specific overrides here if needed */
/* Actually, they are fully defined in buttons.css now, so we can remove them from here to avoid conflicts */
</style>
