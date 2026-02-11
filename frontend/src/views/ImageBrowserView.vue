<script setup>
/**
 * @file ImageBrowserView.vue
 * @description The primary application view for exploring and interacting with the image library.
 *
 * This view acts as the main orchestrator for the image browsing experience. It dynamically switches
 * between a high-level 'gallery' grid and a focused 'browser' (single image) view. It manages
 * global keyboard shortcuts for navigation and view control, ensuring a fluid user experience.
 *
 * Key functionalities:
 * - **View Orchestration:** Toggles between VirtualGallery and SingleImageViewer based on application state.
 * - **Keyboard Navigation:** Implements a comprehensive set of hotkeys (Arrows, WASD, Enter, Escape, G, B) for rapid browsing.
 * - **State Synchronization:** Integrates with the Pinia store to handle folder initialization, collection loading, and search.
 * - **Layout Management:** Controls the visibility of the MetadataSidebar and ensures the main viewer retains focus.
 * - **Deep Linking:** Supports direct navigation to specific collections via URL query parameters.
 */
import {onMounted, onUnmounted, ref, watch} from 'vue';
import {useBrowserStore} from '@/stores/browser';
import {useRoute, useRouter} from 'vue-router';
import axios from 'axios';
import BrowserToolbar from '@/components/BrowserToolbar.vue';
import MetadataSidebar from '@/components/MetadataSidebar.vue';
import VirtualGallery from '@/components/VirtualGallery.vue';
import SingleImageViewer from '@/components/SingleImageViewer.vue';
import CustomContextMenu from '@/components/CustomContextMenu.vue';
import {useToast} from 'primevue/usetoast';

const store = useBrowserStore();
const route = useRoute();
const router = useRouter();
const toast = useToast();
const containerRef = ref(null);
const virtualGalleryRef = ref(null);
const cm = ref();
const menuModel = ref([]);
const contextMenuSelection = ref(null);

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

  if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', ' '].includes(e.key)) {
    e.preventDefault();
  }

  const cols = (store.viewMode === 'gallery' && virtualGalleryRef.value) ? virtualGalleryRef.value.gridCols : 1;

  switch (e.key) {
    case 'ArrowLeft':
    case 'a':
    case 'A':
      store.navigate(-1);
      break;

    case 'ArrowRight':
    case 'd':
    case 'D':
      store.navigate(1);
      break;

    case 'ArrowUp':
    case 'w':
    case 'W':
      if (store.viewMode === 'gallery') store.navigate(-cols);
      break;

    case 'ArrowDown':
    case 's':
    case 'S':
      if (store.viewMode === 'gallery') store.navigate(cols);
      break;

    case 'g':
    case 'G':
      store.setViewMode('gallery');
      break;

    case 'b':
    case 'B':
      store.setViewMode('browser');
      break;

    case 'Enter':
      if (store.viewMode === 'gallery') {
        store.setViewMode('browser');
        store.setSidebarOpen(true);
      }
      break;
  }
};

const onContextMenu = async (payload) => {
  const { event, file } = payload;
  contextMenuSelection.value = file;

  // Fetch collections for the submenu
  let collections = [];
  try {
    const res = await axios.get('/api/collections');
    collections = res.data;
  } catch (e) {
    console.error("Failed to fetch collections for context menu", e);
  }

  const addToCollectionItems = collections.map(colName => ({
    label: colName,
    icon: 'pi pi-folder',
    command: () => addToCollection(colName, file.path)
  }));

  // Add "Create New Collection" option
  addToCollectionItems.unshift({
    label: 'Create New Collection...',
    icon: 'pi pi-plus',
    command: () => createNewCollection(file.path)
  });

  if (addToCollectionItems.length > 1) {
      addToCollectionItems.splice(1, 0, { separator: true });
  }

  const items = [];

  // "Add to Collection" is always available
  items.push({
    label: 'Add to Collection',
    icon: 'pi pi-plus',
    items: addToCollectionItems
  });

  // "Blacklist" only if inside a collection
  if (store.activeCollection) {
    items.push({
      label: 'Blacklist (Remove)',
      icon: 'pi pi-ban',
      command: () => blacklistImage(store.activeCollection, file.path)
    });
  }

  items.push({ separator: true });

  items.push({
    label: 'Open in Explorer',
    icon: 'pi pi-external-link',
    command: () => openInExplorer(file.path)
  });

  items.push({
    label: 'Delete (Trash)',
    icon: 'pi pi-trash',
    command: () => deleteImage(file.path)
  });

  menuModel.value = items;

  if (cm.value) {
    cm.value.show(event);
  }
};

const addToCollection = async (collectionName, path) => {
  try {
    await axios.post(`/api/collections/${collectionName}/images`, null, { params: { path } });
    toast.add({ severity: 'success', summary: 'Added', detail: `Added to ${collectionName}`, life: 2000 });
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to add to collection', life: 3000 });
  }
};

const createNewCollection = (path) => {
    store.collectionToEdit = null; // Ensure we are creating new
    router.push('/collections');
    // Ideally we would pass the image path to pre-select it, but for now just navigating is fine.
    // Or we could use a query param to trigger the dialog immediately.
};

const blacklistImage = async (collectionName, path) => {
  try {
    await axios.post(`/api/collections/${collectionName}/blacklist`, null, { params: { path } });
    toast.add({ severity: 'success', summary: 'Removed', detail: `Removed from ${collectionName}`, life: 2000 });
    // Refresh the current view to hide the removed image
    store.loadCollection(collectionName);
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to blacklist image', life: 3000 });
  }
};

const openInExplorer = async (path) => {
  try {
    await axios.post('/api/system/show-in-explorer', null, { params: { path } });
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to open explorer', life: 3000 });
  }
};

const deleteImage = async (path) => {
  if (!confirm("Are you sure you want to move this file to the trash?")) return;

  try {
    await axios.post('/api/speedsorter/delete', null, { params: { path } });
    toast.add({ severity: 'success', summary: 'Deleted', detail: 'Moved to trash', life: 2000 });

    // Remove from local store to update UI immediately
    const index = store.files.findIndex(f => f.path === path);
    if (index !== -1) {
      store.files.splice(index, 1);
      // If the deleted file was selected, select the next one
      if (store.selectedFile === path) {
        if (store.files.length > 0) {
          const nextIndex = Math.min(index, store.files.length - 1);
          store.selectFile(store.files[nextIndex]);
        } else {
          store.selectedFile = null;
        }
      }
    }
  } catch (e) {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to delete file', life: 3000 });
  }
};

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown);

  if (route.query.collection) {
    if (store.availableModels.length === 0) {
      await store.loadFilters();
    }
    await store.loadCollection(route.query.collection);
  } else {
    await store.initialize();
    if (store.files.length === 0) {
      store.search('');
    }
  }
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
});

watch(() => route.query.collection, async (newCollection) => {
  if (newCollection) {
    await store.loadCollection(newCollection);
  }
});

watch(() => store.viewMode, (newMode) => {
  if (newMode === 'gallery') {
    store.setSidebarOpen(true);
  } else {
    store.setSidebarOpen(false);
  }
}, {immediate: true});

watch(() => store.imageFocusRequested, (requested) => {
  if (requested) {
    store.imageFocusRequested = false;
    store.setViewMode('browser');
    store.setSidebarOpen(true);
    if (containerRef.value) {
      containerRef.value.focus();
    }
  }
});

</script>

<template>
  <div class="flex flex-column h-full overflow-hidden">
    <BrowserToolbar class="flex-shrink-0"/>

    <div class="flex-grow-1 overflow-hidden relative flex">
      <div class="h-full transition-all duration-300"
           :class="[
             (store.viewMode === 'gallery' && store.isSidebarOpen) ? 'flex-grow-1 w-auto' : 'w-full'
           ]"
           ref="containerRef" tabindex="0" style="outline: none;">
        <VirtualGallery v-if="store.viewMode === 'gallery'" ref="virtualGalleryRef" @contextmenu="onContextMenu"/>
        <SingleImageViewer v-else @contextmenu="onContextMenu"/>
      </div>

      <Transition name="sidebar-slide">
        <div v-if="store.isSidebarOpen"
             class="h-full shadow-8 z-5"
             :class="[
               store.viewMode === 'gallery' ? 'relative flex-shrink-0' : 'absolute top-0 right-0'
             ]">
          <MetadataSidebar/>
        </div>
      </Transition>
    </div>

    <CustomContextMenu ref="cm" :model="menuModel"/>
  </div>
</template>

<style scoped>
.sidebar-slide-enter-active,
.sidebar-slide-leave-active {
  transition: transform 0.3s ease;
}

.sidebar-slide-enter-from,
.sidebar-slide-leave-to {
  transform: translateX(100%);
}
</style>
