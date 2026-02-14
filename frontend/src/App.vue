<script setup>
/**
 * @file App.vue
 * @description The root component of the AI Toolbox application, defining the global layout and navigation structure.
 *
 * This component serves as the primary layout shell for the entire application. It manages the
 * global navigation bar, window controls for the Electron environment, and the integration
 * of the folder navigation sidebar. It utilizes a centralized Pinia store to coordinate
 * state across different views and provides a consistent user experience through a
 * glassmorphism-inspired design.
 *
 * Key Responsibilities:
 * - **Global Navigation:** Defines and renders the primary navigation links (Gallery, Collections, etc.).
 * - **Window Management:** Implements IPC-based controls for minimizing, maximizing, and closing
 *   the application window when running in Electron.
 * - **Layout Orchestration:** Coordinates the positioning of the header, sidebar, and main content area.
 * - **Visual Feedback:** Hosts the global PrimeVue Toast component for application-wide notifications.
 * - **Error Handling:** Integrates with the SystemError component to provide a global error boundary.
 */
import {RouterView, useRouter, useRoute} from 'vue-router'
import Button from 'primevue/button';
import Toast from 'primevue/toast';
import {ref, onMounted} from "vue";
import FolderNav from '@/components/FolderNav.vue';
import SystemError from '@/components/SystemError.vue';
import {useBrowserStore} from '@/stores/browser';

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
  },
  {
    label: 'Duplicates',
    icon: 'pi pi-clone',
    path: '/duplicates'
  }
]);

onMounted(() => {
  store.initialize();
});

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
  <SystemError v-if="store.backendError"/>

  <div v-else class="layout-wrapper h-screen flex flex-column overflow-hidden">
    <Toast position="bottom-right"/>

    <div v-if="store.isLoading && !store.files.length"
         class="loading-overlay flex align-items-center justify-content-center">
      <div class="flex flex-column align-items-center gap-3">
        <i class="pi pi-spin pi-spinner text-4xl text-primary"></i>
        <span class="text-xl font-bold text-gray-400">Initializing System...</span>
      </div>
    </div>

    <header class="menubar-glass flex align-items-center pl-5 pr-1 py-2 gap-5 draggable-header">
      <div class="flex align-items-center gap-3 mr-5 no-drag">
        <img src="@/assets/icon.png" alt="Logo" style="height: 42px;"/>
        <span class="text-2xl font-bold text-gradient text-primary">AI Toolbox</span>
      </div>

      <div class="flex gap-2 flex-grow-1 no-drag overflow-x-auto custom-scrollbar pb-1">
        <Button v-for="item in items" :key="item.path"
                :label="item.label"
                :icon="item.icon"
                class="nav-btn p-button-text font-semibold text-lg px-4 py-2 transition-all transition-duration-200 white-space-nowrap"
                :class="{ 'active-nav-btn': isActive(item.path) }"
                @click="navigate(item.path)"/>
      </div>

      <div class="flex gap-1 no-drag ml-2">
        <Button icon="pi pi-minus"
                class="p-button-text text-gray-400 hover:text-white hover:bg-white-alpha-10 w-3rem h-3rem"
                @click="minimizeWindow"/>
        <Button icon="pi pi-window-maximize"
                class="p-button-text text-gray-400 hover:text-white hover:bg-white-alpha-10 w-3rem h-3rem"
                @click="maximizeWindow"/>
        <Button icon="pi pi-times" class="window-close-btn p-button-text text-gray-400 w-3rem h-3rem"
                @click="closeWindow"/>
      </div>
    </header>

    <main class="flex-grow-1 overflow-hidden flex">
      <FolderNav/>
      <div class="flex-grow-1 overflow-hidden relative">
        <RouterView/>
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

.loading-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-app);
  z-index: 9999;
}

.text-primary {
  color: var(--accent-primary) !important;
}
</style>
