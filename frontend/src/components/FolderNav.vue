<script setup>
/**
 * @file FolderNav.vue
 * @description A sophisticated navigation sidebar component that provides a unified interface for exploring the local file system and user-defined collections.
 *
 * This component implements a hierarchical tree structure using PrimeVue's Tree component. It serves as the primary entry point for users to locate and load images into the browser.
 *
 * Key architectural features:
 * - Multi-Root Navigation: Organizes navigation into logical sections: Collections, Pinned Folders, and Local Drives (This PC).
 * - Lazy Loading: Implements on-demand directory traversal. Child nodes for folders and drives are only fetched from the backend when a node is expanded, optimizing performance for deep file systems.
 * - Reactive State Integration: Synchronizes with the global Pinia store to trigger library scans and update the browser view when a folder or collection is selected.
 * - Contextual Actions: Features a custom context menu providing quick access to folder pinning, collection management, and OS-level integrations (Show in Explorer, Speed Sorter).
 * - Persistence Awareness: Automatically attempts to re-select and expand the last visited folder on initialization.
 */
import {ref, onMounted, watch} from 'vue';
import api from '@/services/api';
import {useBrowserStore} from '@/stores/browser';
import {useRouter} from 'vue-router';
import Tree from 'primevue/tree';
import CustomContextMenu from './CustomContextMenu.vue';
import {useToast} from 'primevue/usetoast';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import ConfirmDialog from 'primevue/confirmdialog';
import {useConfirm} from 'primevue/useconfirm';
import InputText from 'primevue/inputtext';

const store = useBrowserStore();
const router = useRouter();
const toast = useToast();
const confirm = useConfirm();

const nodes = ref([]);
const expandedKeys = ref({});
const selectedKey = ref({});
const contextMenuSelection = ref(null);

const cm = ref();
const menuModel = ref([]);

const showSettings = ref(false);
const excludedPaths = ref([]);
const newExcludedPath = ref('');

watch(() => store.navRefreshKey, () => {
  loadTree();
});

const loadTree = async () => {
  const rootNodes = [];

  try {
    const colRes = await api.get('/collections');
    const colChildren = colRes.data.map(c => ({
      key: `col-${c}`, label: c, data: c, icon: 'pi pi-folder', type: 'collection', leaf: true
    }));
    rootNodes.push({
      key: 'collections',
      label: 'Collections',
      icon: 'pi pi-list',
      children: colChildren,
      type: 'root',
      leaf: false
    });
  } catch (e) {
    console.error("Error loading collections", e);
  }

  rootNodes.push({key: 'sep-1', type: 'separator', selectable: false});

  try {
    const pinRes = await api.get('/folders/pinned');
    const pinChildren = pinRes.data.map(p => ({
      key: `pinned-${p.path}`, label: p.name || p.path, data: p, icon: 'pi pi-bookmark', type: 'pinned', leaf: false
    }));
    rootNodes.push({
      key: 'pinned',
      label: 'Pinned',
      icon: 'pi pi-bookmark',
      children: pinChildren,
      type: 'root',
      leaf: false
    });
  } catch (e) {
    console.error("Error loading pinned", e);
  }

  rootNodes.push({key: 'sep-2', type: 'separator', selectable: false});

  try {
    const driveRes = await api.get('/folders/roots');
    const driveChildren = driveRes.data.map(d => ({
      key: `drive-${d.path}`, label: d.name || d.path, data: d, icon: 'pi pi-server', type: 'folder', leaf: false
    }));
    rootNodes.push({
      key: 'drives',
      label: 'This PC',
      icon: 'pi pi-desktop',
      children: driveChildren,
      type: 'root',
      leaf: false
    });
  } catch (e) {
    console.error("Error loading drives", e);
  }

  nodes.value = rootNodes;
  expandedKeys.value = {'collections': true, 'pinned': true, 'drives': true};

  if (store.lastFolderPath) {
    setTimeout(() => {
      selectNodeByPath(store.lastFolderPath);
    }, 500);
  }
};

const selectNodeByPath = (path) => {
  const pinnedNode = nodes.value.find(n => n.key === 'pinned')?.children?.find(c => c.data.path === path);
  if (pinnedNode) {
    selectedKey.value = {[pinnedNode.key]: true};
    return;
  }

  const driveNode = nodes.value.find(n => n.key === 'drives')?.children?.find(c => c.data.path === path);
  if (driveNode) {
    selectedKey.value = {[driveNode.key]: true};
  }
};

const onNodeExpand = async (node) => {
  const actualNode = node.node || node;
  if (!actualNode.data?.path || actualNode._loaded) return;
  actualNode.loading = true;
  try {
    const res = await api.get('/folders/children', {params: {path: actualNode.data.path}});
    actualNode.children = res.data.map(f => ({
      key: `${actualNode.key}-${f.name}`,
      label: f.name || f.path,
      data: f,
      icon: f.isDirectory ? 'pi pi-folder' : 'pi pi-file',
      type: 'folder',
      leaf: !f.isDirectory,
      children: []
    }));
    actualNode._loaded = true;
  } catch (e) {
    // Error handled by api interceptor
  } finally {
    actualNode.loading = false;
  }
};

const onNodeSelect = async (node) => {
  const actualNode = node.node || node;
  if (actualNode.type === 'collection') {
    router.push({path: '/', query: {collection: actualNode.data}});
  } else if (actualNode.data?.path) {
    try {
      await store.loadFolder(actualNode.data.path);
      if (router.currentRoute.value.path !== '/') {
        router.push('/');
      }
    } catch (e) {
      // Error handled by api interceptor
    }
  }
};

const onCustomContextMenu = (event, node) => {
  if (!node || node.type === 'separator' || node.type === 'root') return;

  contextMenuSelection.value = node;

  menuModel.value = [
    {
      label: 'Pin Folder',
      icon: 'pi pi-bookmark',
      command: () => pinFolder(node.data.path),
      visible: node.type !== 'pinned' && node.type !== 'collection' && node.data?.path
    },
    {
      label: 'Unpin Folder',
      icon: 'pi pi-bookmark-fill',
      command: () => unpinFolder(node.data.path),
      visible: node.type === 'pinned'
    },
    {
      label: 'Edit Collection',
      icon: 'pi pi-pencil',
      command: () => editCollection(node.data),
      visible: node.type === 'collection'
    },
    {
      label: 'Remove Collection',
      icon: 'pi pi-trash',
      command: () => removeCollection(node.data),
      visible: node.type === 'collection'
    },
    {separator: true},
    {
      label: 'Show in Explorer',
      icon: 'pi pi-external-link',
      command: () => openInExplorer(node.data.path),
      visible: !!node.data?.path
    },
    {
      label: 'Open in Speed Sorter',
      icon: 'pi pi-bolt',
      command: () => openInSpeedSorter(node.data.path),
      visible: !!node.data?.path
    }
  ];

  if (cm.value) {
    cm.value.show(event);
  }
};

const pinFolder = async (path) => {
  await api.post('/folders/pin', null, {params: {path}});
  loadTree();
};
const unpinFolder = async (path) => {
  await api.post('/folders/unpin', null, {params: {path}});
  loadTree();
};
const removeCollection = async (name) => {
  try {
    await api.delete(`/collections/${name}`);
    toast.add({severity: 'success', summary: 'Success', detail: 'Collection removed', life: 2000});
    store.refreshNav();
  } catch (e) {
    // Error handled by api interceptor
  }
};
const editCollection = (name) => {
  store.collectionToEdit = name;
  router.push('/collections');
};
const openInSpeedSorter = async (path) => {
  await api.post('/speedsorter/config/input', null, {params: {path}});
  router.push('/speedsorter');
};
const openInExplorer = async (path) => {
  await api.post('/system/open-folder', null, {params: {path}});
};

const openSettings = async () => {
  showSettings.value = true;
  try {
    const res = await api.get('/system/excluded-paths');
    excludedPaths.value = res.data;
  } catch (e) {
    console.error("Failed to load excluded paths", e);
  }
};

const openDataFolder = async () => {
  try {
    await api.post('/system/open-data-folder');
  } catch (e) {
    // Error handled by api interceptor
  }
};

const clearDatabase = () => {
  confirm.require({
    message: 'Are you sure you want to clear the database? All metadata and collections will be lost.',
    header: 'Clear Database',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await api.post('/system/clear-database');
        toast.add({severity: 'success', summary: 'Success', detail: 'Database cleared', life: 3000});
        store.initialize(); // Reload
      } catch (e) {
        // Error handled by api interceptor
      }
    }
  });
};

const clearThumbnails = () => {
  confirm.require({
    message: 'Are you sure you want to delete all thumbnails? They will be regenerated on demand.',
    header: 'Clear Thumbnails',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await api.post('/system/clear-thumbnails');
        toast.add({severity: 'success', summary: 'Success', detail: 'Thumbnails cleared', life: 3000});
      } catch (e) {
        // Error handled by api interceptor
      }
    }
  });
};

const addExcludedPath = async () => {
  if (!newExcludedPath.value) return;
  try {
    await api.post('/system/excluded-paths', null, {params: {path: newExcludedPath.value}});
    excludedPaths.value.push(newExcludedPath.value);
    newExcludedPath.value = '';
  } catch (e) {
    // Error handled by api interceptor
  }
};

const selectExcludedFolder = async () => {
  if (window.electronAPI) {
    const path = await window.electronAPI.selectFolder();
    if (path) {
      newExcludedPath.value = path;
    }
  } else {
    // Fallback for browser dev mode
    const path = prompt("Enter absolute path to exclude:");
    if (path) {
      newExcludedPath.value = path;
    }
  }
};

const removeExcludedPath = async (path) => {
  try {
    await api.delete('/system/excluded-paths', {params: {path}});
    excludedPaths.value = excludedPaths.value.filter(p => p !== path);
  } catch (e) {
    // Error handled by api interceptor
  }
};

onMounted(loadTree);
</script>

<template>
  <div class="folder-nav-glass h-full flex flex-column"
       style="width: 290px; min-width: 300px;">
    <ConfirmDialog></ConfirmDialog>

    <div class="p-3 font-bold text-lg border-bottom-1 border-white-alpha-10 flex align-items-center justify-content-between"
         style="background: rgba(255,255,255,0.02)">
      <div class="flex align-items-center gap-2">
        <span class="text-gradient">Library</span>
      </div>
      <Button icon="pi pi-cog" class="p-button-text p-button-rounded p-button-sm text-white" @click="openSettings" />
    </div>

    <div class="flex-grow-1 overflow-y-auto custom-scrollbar">
      <Tree
          :value="nodes"
          selectionMode="single"
          v-model:selectionKeys="selectedKey"
          v-model:expandedKeys="expandedKeys"
          :lazy="true"
          @node-expand="onNodeExpand"
          @node-select="onNodeSelect"
      >
        <template #default="slotProps">
          <div v-if="slotProps.node.type === 'separator'" class="separator-line"></div>
          <div v-else class="w-full h-full flex align-items-center"
               @contextmenu.prevent.stop="onCustomContextMenu($event, slotProps.node)">
            <span class="p-treenode-label">{{ slotProps.node.label }}</span>
          </div>
        </template>
      </Tree>
    </div>

    <div class="p-2 mt-auto border-top-1 border-white-alpha-10 flex justify-content-center">
      <img src="@/assets/alx_logo.png" alt="ALX Logo" style="height: 60px; opacity: 0.9;">
    </div>

    <CustomContextMenu ref="cm" :model="menuModel"/>

    <Dialog v-model:visible="showSettings" modal header="Settings" :style="{ width: '50vw' }" class="glass-dialog">
      <div class="flex flex-column gap-4">
        <div>
          <h3 class="text-lg font-semibold mb-2 text-white">Data Management</h3>
          <div class="flex gap-2">
            <Button label="Open Data Folder" icon="pi pi-folder-open" class="p-button-outlined" @click="openDataFolder" />
            <Button label="Clear Database" icon="pi pi-database" class="p-button-danger p-button-outlined" @click="clearDatabase" />
            <Button label="Clear Thumbnails" icon="pi pi-images" class="p-button-warning p-button-outlined" @click="clearThumbnails" />
          </div>
        </div>

        <div>
          <h3 class="text-lg font-semibold mb-2 text-white">Excluded Paths</h3>
          <p class="text-sm text-gray-400 mb-2">Folders starting with these paths will be ignored by the indexer.</p>

          <div class="flex gap-2 mb-3">
            <div class="p-inputgroup flex-grow-1">
                <InputText v-model="newExcludedPath" placeholder="Enter path to exclude..." class="glass-input" />
                <Button icon="pi pi-folder-open" @click="selectExcludedFolder" />
            </div>
            <Button icon="pi pi-plus" @click="addExcludedPath" />
          </div>

          <div class="glass-box p-2 border-round" style="max-height: 200px; overflow-y: auto;">
            <div v-for="path in excludedPaths" :key="path" class="flex justify-content-between align-items-center p-2 hover:surface-white-alpha-10 border-round">
              <span class="text-sm text-white">{{ path }}</span>
              <Button icon="pi pi-trash" class="p-button-text p-button-danger p-button-sm" @click="removeExcludedPath(path)" />
            </div>
            <div v-if="excludedPaths.length === 0" class="text-center text-gray-500 text-sm p-2">No excluded paths</div>
          </div>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<style scoped>
.folder-nav-glass {
  background: var(--app-bg-panel, rgba(0, 0, 0, 0.7));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 5px 0 30px rgba(0, 0, 0, 0.3);
}

.text-gradient {
  background: var(--app-grad-text, linear-gradient(90deg, #66fcf1, #d870ff));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.separator-line {
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
  margin: 0.5rem 0;
  width: 100%;
}

.glass-dialog .p-dialog-header,
.glass-dialog .p-dialog-content,
.glass-dialog .p-dialog-footer {
  background: rgba(10, 10, 10, 0.95) !important;
  color: white;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

.glass-input {
  background: rgba(0, 0, 0, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: white !important;
}

.glass-box {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.p-tree) {
  background: transparent !important;
  border: none !important;
  padding: 0.5rem;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content) {
  padding: 0.5rem 0.75rem;
  margin: 4px 0;
  border-radius: 6px;
  color: #9ca3af;
  transition: all 0.2s ease;
  border: none;
  font-weight: 600;
  position: relative;
  z-index: 1;
  background: transparent !important;
  overflow: visible !important;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:focus),
:deep(.p-tree .p-tree-container .p-treenode:focus),
:deep(.p-tree:focus),
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:focus-visible),
:deep(.p-tree .p-tree-container .p-treenode:focus-visible),
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight:focus),
:deep(.p-tree *) {
  box-shadow: none !important;
  outline: none !important;
  border: none !important;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content::before) {
  content: '';
  position: absolute;
  inset: -1px;
  background: var(--app-grad-hover);
  border-radius: 6px;
  z-index: -2;
  opacity: 0;
  filter: blur(4px);
  transition: opacity 0.3s ease;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content::after) {
  content: '';
  position: absolute;
  inset: 0;
  background: transparent;
  border-radius: 6px;
  z-index: -1;
  transition: background 0.3s ease;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:hover) {
  color: white !important;
  transform: translateY(-1px);
}

:deep(.p-treenode-content:hover::before) {
  opacity: 0.8;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:hover::after) {
  background: #000000;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:hover .p-treenode-label),
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:hover .p-treenode-icon) {
  background: none !important;
  -webkit-text-fill-color: white !important;
  color: white !important;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight) {
  color: transparent !important;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight .p-treenode-label),
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight .p-treenode-icon) {
  background-image: var(--app-grad-hover);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent !important;
  display: inline-block;
  position: relative;
  z-index: 2;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight::before) {
  opacity: 0.8;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight::after) {
  background: #000000;
}

:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight:hover) {
  background: transparent !important;
}

:deep(.p-tree .p-treenode-icon) {
  color: inherit !important;
  transition: color 0.2s;
}

:deep(.p-tree .p-tree-toggler) {
  color: #6b7280;
  margin-right: 0.25rem;
}

:deep(.p-tree .p-tree-toggler:hover) {
  color: white;
  background: transparent;
}
</style>