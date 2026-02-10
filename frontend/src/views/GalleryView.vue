<script setup>
/**
 * @file GalleryView.vue
 * @description A simple gallery view that displays a grid of images based on a search query.
 * This component is a legacy or simplified browser, primarily used for demonstrating
 * basic search and context menu functionality.
 */
import { ref, onMounted } from 'vue';
import axios from 'axios';
import ImageCard from '../components/ImageCard.vue';
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';
import CustomContextMenu from '../components/CustomContextMenu.vue';
import { useToast } from 'primevue/usetoast';
import Toast from 'primevue/toast';

const images = ref([]);
const searchQuery = ref('');
const toast = useToast();
const cm = ref();
const menuModel = ref([]);

const search = async () => {
  try {
    const response = await axios.get('http://localhost:8080/api/images/search', { params: { query: searchQuery.value } });
    images.value = response.data;
  } catch (error) { toast.add({ severity: 'error', summary: 'Error', detail: 'Search failed', life: 3000 }); }
};

const showContextMenu = (payload) => {
  const { event, path } = payload;

  if (!event) {
    console.error("GalleryView: No event received");
    return;
  }

  menuModel.value = [
    {
      label: 'Open in Explorer',
      icon: 'pi pi-external-link',
      command: () => openInExplorer(path)
    },
    {
      label: 'Copy Path',
      icon: 'pi pi-copy',
      command: () => {
        navigator.clipboard.writeText(path);
        toast.add({ severity: 'info', summary: 'Copied', detail: 'Path copied to clipboard', life: 1000 });
      }
    }
  ];

  if (cm.value) {
    cm.value.show(event);
  } else {
    console.error("GalleryView: ContextMenu ref (cm) is null");
  }
};

const openInExplorer = async (path) => {
  try { await axios.post('/api/system/open-folder', null, { params: { path } }); }
  catch (e) { toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to open explorer', life: 2000 }); }
};

onMounted(() => { search(); });
</script>

<template>
  <div class="gallery-container p-3 h-full flex flex-column">
    <Toast />
    <div class="flex gap-2 mb-4 flex-shrink-0">
            <span class="p-input-icon-left flex-grow-1">
                <i class="pi pi-search" />
                <InputText v-model="searchQuery" placeholder="Search..." class="w-full" @keyup.enter="search" />
            </span>
      <Button label="Search" @click="search" />
    </div>

    <div class="overflow-y-auto flex-grow-1">
      <div class="grid grid-nogutter">
        <div v-for="(path, index) in images" :key="path + index" class="col-12 sm:col-6 md:col-4 lg:col-3 xl:col-2 p-2">
          <ImageCard :path="path" @contextmenu="showContextMenu" />
        </div>
      </div>
      <div v-if="images.length === 0" class="text-center p-5 text-gray-500">No images found.</div>
    </div>

    <CustomContextMenu ref="cm" :model="menuModel" />
  </div>
</template>