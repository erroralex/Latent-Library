<script setup>
/**
 * @file FolderNav.vue
 * @description A sidebar component for navigating the file system and user collections.
 * It provides a hierarchical tree view of drives, pinned folders, and collections,
 * with lazy-loading for directory contents and context menu actions.
 */
import { ref, onMounted, watch } from 'vue';
import axios from 'axios';
import { useBrowserStore } from '@/stores/browser';
import { useRouter } from 'vue-router';
import Tree from 'primevue/tree';
import CustomContextMenu from './CustomContextMenu.vue';
import Toast from 'primevue/toast';
import { useToast } from 'primevue/usetoast';

const store = useBrowserStore();
const router = useRouter();
const toast = useToast();

const nodes = ref([]);
const expandedKeys = ref({});
const selectedKey = ref({});
const contextMenuSelection = ref(null);

const cm = ref();
const menuModel = ref([]);

watch(() => store.navRefreshKey, () => {
    loadTree();
});

const loadTree = async () => {
  const rootNodes = [];

  try {
    const colRes = await axios.get('/api/collections');
    const colChildren = colRes.data.map(c => ({
      key: `col-${c}`, label: c, data: c, icon: 'pi pi-folder', type: 'collection', leaf: true
    }));
    rootNodes.push({ key: 'collections', label: 'Collections', icon: 'pi pi-list', children: colChildren, type: 'root', leaf: false });
  } catch (e) { console.error("Error loading collections", e); }

  rootNodes.push({ key: 'sep-1', type: 'separator', selectable: false });

  try {
    const pinRes = await axios.get('/api/folders/pinned');
    const pinChildren = pinRes.data.map(p => ({
      key: `pinned-${p.path}`, label: p.name || p.path, data: p, icon: 'pi pi-bookmark', type: 'pinned', leaf: false
    }));
    rootNodes.push({ key: 'pinned', label: 'Pinned', icon: 'pi pi-bookmark', children: pinChildren, type: 'root', leaf: false });
  } catch (e) { console.error("Error loading pinned", e); }

  rootNodes.push({ key: 'sep-2', type: 'separator', selectable: false });

  try {
    const driveRes = await axios.get('/api/folders/roots');
    const driveChildren = driveRes.data.map(d => ({
      key: `drive-${d.path}`, label: d.name || d.path, data: d, icon: 'pi pi-server', type: 'folder', leaf: false
    }));
    rootNodes.push({ key: 'drives', label: 'This PC', icon: 'pi pi-desktop', children: driveChildren, type: 'root', leaf: false });
  } catch (e) { console.error("Error loading drives", e); }

  nodes.value = rootNodes;
  expandedKeys.value = { 'collections': true, 'pinned': true, 'drives': true };
};

const onNodeExpand = async (node) => {
  const actualNode = node.node || node;
  if (!actualNode.data?.path || actualNode._loaded) return;
  actualNode.loading = true;
  try {
    const res = await axios.get('/api/folders/children', { params: { path: actualNode.data.path } });
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
    toast.add({ severity: 'error', summary: 'Error', detail: 'Could not access folder.', life: 3000 });
  } finally { actualNode.loading = false; }
};

const onNodeSelect = async (node) => {
  const actualNode = node.node || node;
  if (actualNode.type === 'collection') {
      router.push({ path: '/browser', query: { collection: actualNode.data } });
  }
  else if (actualNode.data?.path) {
    try { await store.loadFolder(actualNode.data.path); }
    catch (e) { toast.add({ severity: 'error', summary: 'Error', detail: 'Could not load folder contents', life: 2000 }); }
  }
};

const onCustomContextMenu = (event, node) => {
  if (!node || node.type === 'separator') return;

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
    { separator: true },
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

const pinFolder = async (path) => { await axios.post('/api/folders/pin', null, {params: {path}}); loadTree(); };
const unpinFolder = async (path) => { await axios.post('/api/folders/unpin', null, {params: {path}}); loadTree(); };
const removeCollection = async (name) => {
    try {
        await axios.delete(`/api/collections/${name}`);
        toast.add({ severity: 'success', summary: 'Success', detail: 'Collection removed', life: 2000 });
        store.refreshNav();
    } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to remove collection', life: 2000 });
    }
};
const editCollection = (name) => {
    store.collectionToEdit = name;
    router.push('/collections');
};
const openInSpeedSorter = async (path) => { await axios.post('/api/speedsorter/config/input', null, {params: {path}}); router.push('/speedsorter'); };
const openInExplorer = async (path) => { await axios.post('/api/system/open-folder', null, {params: {path}}); };

onMounted(loadTree);
</script>

<template>
  <div class="folder-nav-glass h-full flex flex-column"
       style="width: 290px; min-width: 300px;">
    <Toast/>
    <div class="p-3 font-bold text-lg border-bottom-1 border-white-alpha-10 flex align-items-center gap-2" style="background: rgba(255,255,255,0.02)">
      <img src="@/assets/icon.png" alt="Logo" style="width: 24px; height: 24px;">
      <span class="text-gradient">Library</span>
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
            <div v-else class="w-full h-full flex align-items-center" @contextmenu.prevent.stop="onCustomContextMenu($event, slotProps.node)">
                <span class="p-treenode-label">{{ slotProps.node.label }}</span>
            </div>
        </template>
      </Tree>
    </div>

    <div class="p-2 mt-auto border-top-1 border-white-alpha-10 flex justify-content-center">
        <img src="@/assets/alx_logo.png" alt="ALX Logo" style="height: 60px; opacity: 0.9;">
    </div>

    <CustomContextMenu ref="cm" :model="menuModel" />
  </div>
</template>

<style scoped>
.folder-nav-glass {
  background: var(--app-bg-panel, rgba(0, 0, 0, 0.7));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 5px 0 30px rgba(0,0,0,0.3);
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