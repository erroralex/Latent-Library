<script setup>
/**
 * App.vue
 * Root Navigation & Layout
 */
import { RouterView, useRouter, useRoute } from 'vue-router'
import Button from 'primevue/button';
import { ref } from "vue";
import FolderNav from '@/components/FolderNav.vue';

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
  // Make Gallery active for both '/' and '/browser'
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

/* Nav Button Styling */
.nav-btn {
  color: #9ca3af !important; /* text-gray-400 */
  border-radius: 8px;
  position: relative !important;
  z-index: 1 !important;
  background: transparent !important;
  overflow: visible !important;
}

/* 1. Gradient Glow Layer (Deepest) */
.nav-btn::before {
  content: '';
  position: absolute;
  inset: -1px; /* Tighter inset */
  background: var(--app-grad-hover);
  border-radius: 9px;
  z-index: -2;
  opacity: 0;
  filter: blur(4px); /* Tighter glow */
  transition: opacity 0.3s ease;
}

/* 2. Background Layer (Middle) */
.nav-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: 8px;
  z-index: -1;
  transition: background 0.3s ease;
}

/* Hover: Black Background + White Text + Glow */
.nav-btn:hover {
  color: white !important;
  transform: translateY(-1px);
}

.nav-btn:hover::before {
  opacity: 0.8;
}

.nav-btn:hover::after {
  background: #000000; /* Opaque black */
}

/* Reset gradient text on hover to ensure it's white */
:deep(.nav-btn:hover .p-button-label),
:deep(.nav-btn:hover .p-button-icon) {
  background: none !important;
  -webkit-text-fill-color: white !important;
  color: white !important;
}

/* Active: Black Background + Gradient Text + Glow (Static) */
.active-nav-btn::before {
  opacity: 0.8;
}

.active-nav-btn::after {
  background: #000000; /* Opaque black */
}

/* Apply gradient to text and icon ONLY when active */
:deep(.active-nav-btn .p-button-label),
:deep(.active-nav-btn .p-button-icon) {
  background: var(--app-grad-hover);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  color: transparent !important;
}

/* --- Close Button Override --- */
.window-close-btn {
  position: relative !important;
  z-index: 1 !important;
  background: transparent !important;
  overflow: visible !important;
}

/* 1. Red Glow Layer */
.window-close-btn::before {
  content: '';
  position: absolute;
  inset: -1px;
  background: var(--app-red-warning); /* Red Glow */
  border-radius: 9px;
  z-index: -2;
  opacity: 0;
  filter: blur(4px);
  transition: opacity 0.3s ease;
}

/* 2. Background Layer */
.window-close-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: 8px;
  z-index: -1;
  transition: background 0.3s ease;
}

/* Hover State */
.window-close-btn:hover {
  color: white !important;
  transform: translateY(-1px);
  box-shadow: none !important; /* Remove any direct shadow */
  background: transparent !important;
}

.window-close-btn:hover::before {
  opacity: 0.8; /* Show Red Glow */
}

.window-close-btn:hover::after {
  background: #000000; /* Opaque Black Background */
}

/* Ensure icon stays white */
:deep(.window-close-btn:hover .p-button-icon) {
  background: none !important;
  -webkit-text-fill-color: white !important;
  color: white !important;
}
</style>