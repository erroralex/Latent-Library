<script setup>
import { RouterView } from 'vue-router'
import Menubar from 'primevue/menubar';
import { ref } from "vue";

const items = ref([
    {
        label: 'Gallery',
        icon: 'pi pi-images',
        to: '/'
    },
    {
        label: 'Collections',
        icon: 'pi pi-folder',
        to: '/collections'
    },
    {
        label: 'Comparator',
        icon: 'pi pi-arrow-right-arrow-left',
        to: '/comparator'
    },
    {
        label: 'Scrubber',
        icon: 'pi pi-shield',
        to: '/scrub'
    },
    {
        label: 'Speed Sorter',
        icon: 'pi pi-bolt',
        to: '/speedsorter'
    }
]);
</script>

<template>
  <div class="layout-wrapper h-screen flex flex-column overflow-hidden">
    <header>
      <Menubar :model="items" class="border-none border-bottom-1 surface-border border-noround">
        <template #start>
            <span class="text-xl font-bold mr-4 text-gradient">AI Toolbox</span>
        </template>
        <template #item="{ item, props }">
            <router-link v-if="item.to" v-slot="{ href, navigate }" :to="item.to" custom>
                <a :href="href" v-bind="props.action" @click="navigate">
                    <span :class="item.icon" />
                    <span class="ml-2">{{ item.label }}</span>
                </a>
            </router-link>
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
