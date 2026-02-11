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
import { ref } from "vue";
import FolderNav from '@/components/FolderNav.vue';
import { useBrowserStore } from '@/stores/browser';
import { onMounted } from 'vue';

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
    <header class="menubar-glass flex align-items-center px-5 py-3 gap-5 draggable-header">
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

      <div class="flex gap-2 no-drag">
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

.nav-btn {
  color: #9ca3af !important;
  border-radius: 8px;
  position: relative !important;
  z-index: 1 !important;
  background: transparent !important;
  overflow: visible !important;
}

.nav-btn::before {
  content: '';
  position: absolute;
  inset: -1px;
  background: var(--app-grad-hover);
  border-radius: 9px;
  z-index: -2;
  opacity: 0;
  filter: blur(4px);
  transition: opacity 0.3s ease;
}

.nav-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: 8px;
  z-index: -1;
  transition: background 0.3s ease;
}

.nav-btn:hover {
  color: white !important;
  transform: translateY(-1px);
}

.nav-btn:hover::before {
  opacity: 0.8;
}

.nav-btn:hover::after {
  background: #000000;
}

:deep(.nav-btn:hover .p-button-label),
:deep(.nav-btn:hover .p-button-icon) {
  background: none !important;
  -webkit-text-fill-color: white !important;
  color: white !important;
}

.active-nav-btn::before {
  opacity: 0.8;
}

.active-nav-btn::after {
  background: #000000;
}

:deep(.active-nav-btn .p-button-label),
:deep(.active-nav-btn .p-button-icon) {
  background: var(--app-grad-hover);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent !important;
}

.window-close-btn {
  position: relative !important;
  z-index: 1 !important;
  background: transparent !important;
  overflow: visible !important;
}

.window-close-btn::before {
  content: '';
  position: absolute;
  inset: -1px;
  background: var(--app-red-warning);
  border-radius: 9px;
  z-index: -2;
  opacity: 0;
  filter: blur(4px);
  transition: opacity 0.3s ease;
}

.window-close-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: 8px;
  z-index: -1;
  transition: background 0.3s ease;
}

.window-close-btn:hover {
  color: white !important;
  transform: translateY(-1px);
  box-shadow: none !important;
  background: transparent !important;
}

.window-close-btn:hover::before {
  opacity: 0.8;
}

.window-close-btn:hover::after {
  background: #000000;
}

:deep(.window-close-btn:hover .p-button-icon) {
  background: none !important;
  -webkit-text-fill-color: white !important;
  color: white !important;
}
</style>