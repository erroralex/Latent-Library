<script setup>
/**
 * App.vue
 *
 * Root component of the application.
 * Uses programmatic navigation (router.push) in the Menubar model
 * to ensure reliable clicking and routing.
 */
import { RouterView, useRouter } from 'vue-router'
import Menubar from 'primevue/menubar';
import { ref } from "vue";

const router = useRouter();

// Define menu items with explicit command handlers for navigation
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
      <Menubar :model="items" class="border-none border-bottom-1 surface-border border-noround">
        <template #start>
            <div class="flex align-items-center gap-2 mr-4">
                <img src="@/assets/icon.png" alt="AI Toolbox Logo" style="height: 32px;" />
                <span class="text-xl font-bold text-gradient">AI Toolbox</span>
            </div>
        </template>
      </Menubar>
    </header>

    <main class="flex-grow-1 flex flex-column overflow-hidden relative">
      <RouterView />
    </main>
  </div>
</template>

<style>
/* Global styles are now handled by assets/css/main.css */
</style>