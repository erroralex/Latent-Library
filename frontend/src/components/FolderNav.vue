<script setup>
import {ref, onMounted} from 'vue';
import axios from 'axios';
import {useBrowserStore} from '@/stores/browser';
import {useRouter} from 'vue-router';
import Tree from 'primevue/tree';
import ContextMenu from 'primevue/contextmenu';
import Toast from 'primevue/toast';
import {useToast} from 'primevue/usetoast';

const store = useBrowserStore();
const router = useRouter();
const toast = useToast();

const nodes = ref([]);
const expandedKeys = ref({});
const selectedKey = ref(null);
const contextMenuSelection = ref(null); // Added for context menu support
const cm = ref();
const menuModel = ref([]);

// Load initial structure
const loadTree = async () => {
  const rootNodes = [];

  // Collections Section
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
      type: 'root' // Added type
    });
  } catch (e) {
    console.error(e);
  }

  // Pinned Section
  try {
    const pinRes = await axios.get('/api/folders/pinned');
    const pinChildren = pinRes.data.map(p => ({
      key: p.path,
      label: p.name,
      data: p,
      icon: 'pi pi-thumbtack',
      type: 'pinned',
      leaf: false // Can be expanded to show subfolders
    }));
    rootNodes.push({
      key: 'pinned',
      label: 'Pinned',
      icon: 'pi pi-bookmark',
      children: pinChildren,
      selectable: false,
      type: 'root' // Added type
    });
  } catch (e) {
    console.error(e);
  }

  // Drives Section
  try {
    const driveRes = await axios.get('/api/folders/roots');
    const driveChildren = driveRes.data.map(d => ({
      key: d.path,
      label: d.path,
      data: d,
      icon: 'pi pi-desktop',
      type: 'folder',
      leaf: false
    }));
    rootNodes.push({
      key: 'drives',
      label: 'This PC',
      icon: 'pi pi-server',
      children: driveChildren,
      selectable: false,
      type: 'root' // Added type
    });
  } catch (e) {
    console.error(e);
  }

  nodes.value = rootNodes;
  // Auto-expand top-level sections
  expandedKeys.value = {'collections': true, 'pinned': true, 'drives': true};
};

const onNodeExpand = async (node) => {
  if (!node.children && !node.leaf) {
    try {
      const res = await axios.get('/api/folders/children', {
        params: {path: node.data.path}
      });
      node.children = res.data.map(f => ({
        key: f.path,
        label: f.name,
        data: f,
        icon: 'pi pi-folder',
        type: 'folder',
        leaf: false // Assume folders have children for now
      }));
    } catch (e) {
      console.error('Failed to load children', e);
      node.leaf = true; // Mark as leaf if error or empty
    }
  }
};

const onNodeSelect = (node) => {
  if (node.type === 'collection') {
    // TODO: Implement collection loading in store
    console.log('Selected collection:', node.data);
  } else if (node.type === 'folder' || node.type === 'pinned') {
    store.loadFolder(node.data.path);
  }
};

const onNodeContextMenu = (event) => {
  const node = event.node;

  if (node && (node.type === 'folder' || node.type === 'pinned')) {
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
      {separator: true},
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
    if (cm.value) {
      cm.value.show(event.originalEvent);
    }
  }
};

const pinFolder = async (path) => {
  try {
    await axios.post('/api/folders/pin', null, {params: {path}});
    toast.add({severity: 'success', summary: 'Pinned', detail: 'Folder added to pinned list', life: 2000});
    loadTree();
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to pin folder', life: 2000});
  }
};

const unpinFolder = async (path) => {
  try {
    await axios.post('/api/folders/unpin', null, {params: {path}});
    toast.add({severity: 'info', summary: 'Unpinned', detail: 'Folder removed from pinned list', life: 2000});
    loadTree();
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to unpin folder', life: 2000});
  }
};

const openInSpeedSorter = async (path) => {
  try {
    await axios.post('/api/speedsorter/config/input', null, {params: {path}});
    router.push('/speedsorter');
    toast.add({severity: 'success', summary: 'Speed Sorter', detail: 'Folder loaded in Speed Sorter', life: 2000});
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to load in Speed Sorter', life: 2000});
  }
};

const openInExplorer = async (path) => {
  // This requires backend support to open system explorer
  try {
    await axios.post('/api/system/open-folder', null, {params: {path}});
  } catch (e) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to open explorer', life: 2000});
  }
};

onMounted(loadTree);
</script>

<template>
  <div class="folder-nav h-full flex flex-column surface-card border-right-1 surface-border"
       style="width: 260px; min-width: 260px;">
    <Toast/>
    <div class="p-3 font-bold text-lg border-bottom-1 surface-border">Library</div>
    <div class="flex-grow-1 overflow-y-auto">
      <Tree :value="nodes" selectionMode="single"
            v-model:selectionKeys="selectedKey"
            v-model:contextMenuSelection="contextMenuSelection"
            v-model:expandedKeys="expandedKeys"
            :contextMenu="cm"
            @nodeExpand="onNodeExpand"
            @nodeSelect="onNodeSelect"
            @nodeContextMenu="onNodeContextMenu"
            class="w-full border-none p-0"/>
    </div>
    <ContextMenu ref="cm" :model="menuModel" appendTo="body"/>
  </div>
</template>

<style>
.p-tree {
  background: transparent !important;
}

.p-tree .p-tree-container .p-treenode .p-treenode-content {
  padding: 0.25rem 0.5rem;
  border-radius: 0;
}

.p-tree .p-tree-container .p-treenode .p-treenode-content:focus {
  box-shadow: none;
}
</style>
