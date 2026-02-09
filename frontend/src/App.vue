<script setup>
/**
 * App.vue
 * Root Navigation & Layout
 */
import { RouterView, useRouter } from 'vue-router'
import Menubar from 'primevue/menubar';
import { ref } from "vue";

const router = useRouter();

const items = ref([
  {
    label: 'Gallery',
    icon: 'pi pi-images',
    command: () => router.push('/')
  },
  {
    label: 'Collections',
    icon: 'pi pi-folder',
    command: () => router.push('/collections')
  },
  {
    label: 'Comparator',
    icon: 'pi pi-arrow-right-arrow-left',
    command: () => router.push('/comparator')
  },
  {
    label: 'Scrubber',
    icon: 'pi pi-shield',
    command: () => router.push('/scrub')
  },
  {
    label: 'Speed Sorter',
    icon: 'pi pi-bolt',
    command: () => router.push('/speedsorter')
  }
]);
</script>

<template>
  <div class="layout-wrapper h-screen flex flex-column overflow-hidden">
    <header>
      <Menubar :model="items" class="menubar-glass border-none border-noround">
        <template #start>
          <div class="flex align-items-center gap-2 mr-4">
            <img src="@/assets/icon.png" alt="Logo" style="height: 32px;" />
            <span class="text-xl font-bold text-gradient">AI Toolbox</span>
          </div>
        </template>
      </Menubar>
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

/* PrimeVue Menubar Override for Transparency */
:deep(.p-menubar) {
  background: transparent !important;
  border: none !important;
  padding: 0.5rem 1rem;
}

:deep(.p-menubar .p-menuitem-link .p-menuitem-text) {
  color: var(--text-secondary) !important;
  font-weight: 500;
}
:deep(.p-menubar .p-menuitem-link .p-menuitem-icon) {
  color: var(--app-cyan) !important;
}

/* Hover Effects */
:deep(.p-menubar .p-menuitem-link:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  border-radius: 6px;
}
:deep(.p-menubar .p-menuitem-link:hover .p-menuitem-text) {
  color: var(--text-primary) !important;
}

/* Active Focus */
:deep(.p-menubar .p-menuitem-link:focus) {
  box-shadow: inset 0 0 0 1px var(--app-cyan) !important;
}
</style>