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

// Ref for the custom menu
const cm = ref();
const menuModel = ref([]);

// --- Data Loading ---
const loadTree = async () => {
  const rootNodes = [];

  // 1. Collections
  try {
    const colRes = await axios.get('/api/collections');
    const colChildren = colRes.data.map(c => ({
      key: `col-${c}`, label: c, data: c, icon: 'pi pi-folder', type: 'collection', leaf: true
    }));
    rootNodes.push({ key: 'collections', label: 'Collections', icon: 'pi pi-list', children: colChildren, type: 'root', leaf: false });
  } catch (e) { console.error("Error loading collections", e); }

  // 2. Pinned
  try {
    const pinRes = await axios.get('/api/folders/pinned');
    const pinChildren = pinRes.data.map(p => ({
      key: `pinned-${p.path}`, label: p.name || p.path, data: p, icon: 'pi pi-bookmark', type: 'pinned', leaf: false
    }));
    rootNodes.push({ key: 'pinned', label: 'Pinned', icon: 'pi pi-bookmark', children: pinChildren, type: 'root', leaf: false });
  } catch (e) { console.error("Error loading pinned", e); }

  // 3. Drives
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
  if (actualNode.data?.path) {
    try { await store.loadFolder(actualNode.data.path); }
    catch (e) { toast.add({ severity: 'error', summary: 'Error', detail: 'Could not load folder contents', life: 2000 }); }
  }
};

// --- Context Menu Handlers ---
const onNodeContextMenu = (event) => {
  // PrimeVue Tree returns { originalEvent, node }
  const node = event.node;
  console.log("FolderNav: Right Click detected on", node.label);

  if (!node) return;

  contextMenuSelection.value = node;

  // Build model
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
      label: 'Show in Explorer',
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
    // Pass the native DOM event
    cm.value.show(event.originalEvent);
  } else {
    console.error("FolderNav: ContextMenu ref (cm) is null");
  }
};

// Actions
const pinFolder = async (path) => { await axios.post('/api/folders/pin', null, {params: {path}}); loadTree(); };
const unpinFolder = async (path) => { await axios.post('/api/folders/unpin', null, {params: {path}}); loadTree(); };
const openInSpeedSorter = async (path) => { await axios.post('/api/speedsorter/config/input', null, {params: {path}}); router.push('/speedsorter'); };
const openInExplorer = async (path) => { await axios.post('/api/system/open-folder', null, {params: {path}}); };

onMounted(loadTree);
</script>

<template>
  <div class="folder-nav-glass h-full flex flex-column"
       style="width: 260px; min-width: 260px;">
    <Toast/>
    <div class="p-3 font-bold text-lg border-bottom-1 border-white-alpha-10 flex align-items-center gap-2" style="background: rgba(255,255,255,0.02)">
      <img src="@/assets/icon.png" alt="Logo" style="width: 24px; height: 24px;">
      <span class="text-gradient">Library</span>
    </div>

    <div class="flex-grow-1 overflow-y-auto">
      <Tree
          :value="nodes"
          selectionMode="single"
          v-model:selectionKeys="selectedKey"
          v-model:contextMenuSelection="contextMenuSelection"
          v-model:expandedKeys="expandedKeys"
          :lazy="true"
          @node-expand="onNodeExpand"
          @node-select="onNodeSelect"
          @node-contextmenu="onNodeContextMenu"
      />
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

/* Force Tree Transparency */
:deep(.p-tree) { background: transparent !important; border: none !important; padding: 0.5rem; }
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content) { padding: 0.2rem; border-radius: 4px; color: var(--text-secondary); }
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:hover) { background: rgba(255, 255, 255, 0.05); color: white; }
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content:focus) { box-shadow: inset 0 0 0 1px var(--primary-color); }
:deep(.p-tree .p-tree-container .p-treenode .p-treenode-content.p-highlight) {
  background: rgba(102, 252, 241, 0.1) !important;
  color: var(--app-cyan-bright);
  border-left: 2px solid var(--app-cyan-bright);
}
</style>