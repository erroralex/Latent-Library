<script setup>
/**
 * FolderNav.vue
 * Sidebar navigation for file system and collections.
 */
import { ref, onMounted } from 'vue';
import axios from 'axios';
import { useBrowserStore } from '@/stores/browser';
import { useRouter } from 'vue-router';
import Tree from 'primevue/tree';
import ContextMenu from 'primevue/contextmenu';
import Toast from 'primevue/toast';
import { useToast } from 'primevue/usetoast';

const store = useBrowserStore();
const router = useRouter();
const toast = useToast();

const nodes = ref([]);
const expandedKeys = ref({});
const selectedKey = ref({});
const contextMenuSelection = ref({});
const cm = ref();
const menuModel = ref([]);

// --- Data Loading ---

const loadTree = async () => {
  const rootNodes = [];

  // 1. Collections
  try {
    const colRes = await axios.get('/api/collections');
    const colChildren = colRes.data.map(c => ({
      key: `col-${c}`,
      label: c,
      data: c,
      icon: 'pi pi-folder',
      type: 'collection',
      leaf: true
    }));

    rootNodes.push({
      key: 'collections',
      label: 'Collections',
      icon: 'pi pi-list',
      children: colChildren,
      selectable: false,
      type: 'root',
      leaf: false
    });
  } catch (e) {
    console.error("Error loading collections", e);
  }

  // 2. Pinned
  try {
    const pinRes = await axios.get('/api/folders/pinned');
    const pinChildren = pinRes.data.map(p => ({
      key: `pinned-${p.path}`, // Unique key prefix
      label: p.name || p.path,
      data: p,
      icon: 'pi pi-bookmark',
      type: 'pinned',
      leaf: false,
      children: []
    }));

    rootNodes.push({
      key: 'pinned',
      label: 'Pinned',
      icon: 'pi pi-bookmark',
      children: pinChildren,
      selectable: false,
      type: 'root',
      leaf: false
    });
  } catch (e) {
    console.error("Error loading pinned", e);
  }

  // 3. Drives (This PC)
  try {
    const driveRes = await axios.get('/api/folders/roots');
    const driveChildren = driveRes.data.map(d => ({
      key: `drive-${d.path}`, // Unique key prefix
      label: d.name || d.path,
      data: d,
      icon: 'pi pi-server',
      type: 'folder',
      leaf: false,
      children: []
    }));

    rootNodes.push({
      key: 'drives',
      label: 'This PC',
      icon: 'pi pi-desktop',
      children: driveChildren,
      selectable: false,
      type: 'root',
      leaf: false
    });
  } catch (e) {
    console.error("Error loading drives", e);
  }

  nodes.value = rootNodes;

  // Auto-expand the main groups
  expandedKeys.value = {
    'collections': true,
    'pinned': true,
    'drives': true
  };
};

const onNodeExpand = async (node) => {
  // PrimeVue Tree passes the node object directly in the event for lazy loading
  // BUT the event structure depends on the version.
  // In newer PrimeVue versions, the event is the node itself or {node: ...}
  // Let's handle both cases safely.
  const actualNode = node.node || node;

  if (!actualNode.data?.path) return;
  if (actualNode._loaded) return;

  actualNode.loading = true;

  try {
    const res = await axios.get('/api/folders/children', {
      params: { path: actualNode.data.path }
    });

    actualNode.children = res.data.map(f => ({
      key: `${actualNode.key}-${f.name}`, // Generate unique key based on parent key
      label: f.name || f.path,
      data: f,
      icon: f.isDirectory ? 'pi pi-folder' : 'pi pi-file',
      type: 'folder',
      leaf: !f.isDirectory,
      children: []
    }));

    actualNode._loaded = true;

  } catch (e) {
    console.error('Failed to load children for path:', actualNode.data.path, e);
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Could not access folder.',
      life: 3000
    });
    // Do NOT mark as leaf, allow retry
    actualNode._loaded = false;
  } finally {
    actualNode.loading = false;
  }
};

const onNodeSelect = async (node) => {
  const actualNode = node.node || node;
  if (!actualNode.data?.path) return;

  try {
    await store.loadFolder(actualNode.data.path);
  } catch (e) {
    toast.add({
      severity: 'error',
      summary: 'Error',
      detail: 'Could not load folder contents',
      life: 2000
    });
  }
};

// --- Context Menu Handlers ---

const onNodeContextMenu = (event) => {
  // PrimeVue Tree context menu event structure: { originalEvent: Event, node: TreeNode }
  const node = event.node;

  if (!node) return;

  contextMenuSelection.value = {
    [node.key]: true
  };

  menuModel.value = [
    {
      label: 'Pin Folder',
      icon: 'pi pi-bookmark',
      command: () => pinFolder(node.data.path),
      visible: node.type !== 'pinned'
    },
    {
      label: 'Unpin Folder',
      icon: 'pi pi-bookmark-fill',
      command: () => unpinFolder(node.data.path),
      visible: node.type === 'pinned'
    },
    { separator: true },
    {
      label: 'Show Folder (Explorer)',
      icon: 'pi pi-external-link',
      command: () => openInExplorer(node.data.path)
    },
    {
      label: 'Open in Speed Sorter',
      icon: 'pi pi-bolt',
      command: () => openInSpeedSorter(node.data.path)
    }
  ];

  // The context menu component needs to be shown manually with the original event
  if (cm.value) {
      cm.value.show(event.originalEvent);
  }
};

const pinFolder = async (path) => {
  try {
    await axios.post('/api/folders/pin', null, {params: {path}});
    toast.add({severity: 'success', summary: 'Pinned', detail: 'Folder added to pinned list', life: 2000});
    await loadTree();
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to pin folder', life: 2000});
  }
};

const unpinFolder = async (path) => {
  try {
    await axios.post('/api/folders/unpin', null, {params: {path}});
    toast.add({severity: 'info', summary: 'Unpinned', detail: 'Folder removed from pinned list', life: 2000});
    await loadTree();
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to unpin folder', life: 2000});
  }
};

const openInSpeedSorter = async (path) => {
  try {
    await axios.post('/api/speedsorter/config/input', null, {params: {path}});
    await router.push('/speedsorter');
    toast.add({severity: 'success', summary: 'Speed Sorter', detail: 'Folder loaded', life: 2000});
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to load in Speed Sorter', life: 2000});
  }
};

const openInExplorer = async (path) => {
  try {
    await axios.post('/api/system/open-folder', null, {params: {path}});
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to open OS explorer', life: 2000});
  }
};

onMounted(loadTree);
</script>

<template>
  <div class="folder-nav h-full flex flex-column surface-card border-right-1 surface-border"
       style="width: 260px; min-width: 260px;">
    <Toast/>

    <div class="p-3 font-bold text-lg border-bottom-1 surface-border flex align-items-center gap-2">
      <img src="@/assets/icon.png" alt="Logo" style="width: 24px; height: 24px;">
      Library
    </div>

    <div class="flex-grow-1 overflow-y-auto">
      <Tree
          :value="nodes"
          selectionMode="single"
          v-model:selectionKeys="selectedKey"
          v-model:contextMenuSelection="contextMenuSelection"
          v-model:expandedKeys="expandedKeys"
          contextMenu
          :lazy="true"
          @node-expand="onNodeExpand"
          @node-select="onNodeSelect"
          @node-contextmenu="onNodeContextMenu"
      />
    </div>

    <ContextMenu ref="cm" :model="menuModel" appendTo="body"/>
  </div>
</template>

<style>
/* Remove default PrimeVue Tree background to blend with sidebar */
.p-tree {
  background: transparent !important;
  border: none !important;
  padding: 0.5rem;
}

/* Compact tree nodes */
.p-tree .p-tree-container .p-treenode .p-treenode-content {
  padding: 0.2rem 0.2rem;
  border-radius: 4px;
  transition: background-color 0.2s;
}

/* Better focus/hover states */
.p-tree .p-tree-container .p-treenode .p-treenode-content:focus {
  box-shadow: inset 0 0 0 1px var(--primary-color);
}

.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight {
  background: var(--primary-100);
  color: var(--primary-900);
}
</style>