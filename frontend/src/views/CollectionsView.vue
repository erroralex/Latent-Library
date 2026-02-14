<script setup>
/**
 * @file CollectionsView.vue
 * @description A comprehensive management interface for image collections, supporting both static and dynamic (smart) groupings.
 *
 * This view allows users to organize their image library into logical sets. It features a robust "Smart Collection"
 * builder that uses metadata filters to create live-updating groups of images.
 *
 * Key functionalities:
 * - Collection CRUD: Full support for creating, reading, updating, and deleting collections.
 * - Smart Filter Builder: An advanced UI for defining dynamic criteria (Model, Sampler, LoRA, Rating, Prompt keywords).
 * - Real-time Preview: Integrates with the browser store to fetch and display available metadata values for filters.
 * - Contextual Management: Provides a right-click menu for quick editing or removal of collections.
 * - Navigation Integration: Seamlessly transitions to the Image Browser with pre-applied collection filters.
 */
import {ref, onMounted, computed, watch} from 'vue';
import api from '@/services/api';
import {useBrowserStore} from '@/stores/browser';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import InputSwitch from 'primevue/inputswitch';
import Menu from 'primevue/menu';
import Chip from 'primevue/chip';
import {useToast} from 'primevue/usetoast';
import {useRouter} from 'vue-router';
import CustomContextMenu from '@/components/CustomContextMenu.vue';

const store = useBrowserStore();
const router = useRouter();
const toast = useToast();

const collections = ref([]);
const displayCreateDialog = ref(false);
const isEditing = ref(false);

const cm = ref();
const menuModel = ref([]);
const contextMenuSelection = ref(null);

const newCollectionName = ref('');
const originalCollectionName = ref('');
const isSmartCollection = ref(false);
const prompt = ref('');
const selectedModels = ref([]);
const selectedLoras = ref([]);
const selectedSamplers = ref([]);
const selectedRating = ref(null);

const modelMenu = ref();
const samplerMenu = ref();
const loraMenu = ref();
const ratingMenu = ref();

watch(() => store.navRefreshKey, () => {
  fetchCollections();
});

watch(() => store.collectionToEdit, (newName) => {
  if (newName) {
    editCollection(newName);
    store.collectionToEdit = null;
  }
});

const addFilter = (type, value) => {
  if (value === 'All') return;
  const target = {model: selectedModels, lora: selectedLoras, sampler: selectedSamplers}[type];
  if (target && !target.value.includes(value)) {
    target.value.push(value);
  }
};

const removeFilter = (type, value) => {
  const target = {model: selectedModels, lora: selectedLoras, sampler: selectedSamplers}[type];
  if (target) {
    target.value = target.value.filter(item => item !== value);
  }
};

const modelItems = computed(() => store.availableModels.map(item => ({
  label: item,
  command: () => addFilter('model', item)
})));
const samplerItems = computed(() => store.availableSamplers.map(item => ({
  label: item,
  command: () => addFilter('sampler', item)
})));
const loraItems = computed(() => store.availableLoras.map(item => ({
  label: item,
  command: () => addFilter('lora', item)
})));
const ratingItems = [
  {label: 'None (Unrated)', command: () => selectedRating.value = 0},
  {label: 'Any Star (> 0)', command: () => selectedRating.value = 'Any Star Count'},
  ...[1, 2, 3, 4, 5].map(i => ({label: `${i} Star${i > 1 ? 's' : ''}`, command: () => selectedRating.value = i}))
];

const resetForm = () => {
  newCollectionName.value = '';
  originalCollectionName.value = '';
  isSmartCollection.value = false;
  prompt.value = '';
  selectedModels.value = [];
  selectedLoras.value = [];
  selectedSamplers.value = [];
  selectedRating.value = null;
  isEditing.value = false;
};

const openCreateDialog = () => {
  resetForm();
  displayCreateDialog.value = true;
};

const editCollection = async (name) => {
  try {
    const response = await api.get(`/collections/${name}`);
    const data = response.data;

    newCollectionName.value = data.name;
    originalCollectionName.value = data.name;
    isSmartCollection.value = data.isSmart;

    if (data.filters) {
      selectedModels.value = data.filters.models || [];
      selectedLoras.value = data.filters.loras || [];
      selectedSamplers.value = data.filters.samplers || [];

      if (data.filters.rating === '0') selectedRating.value = 0;
      else if (data.filters.rating === 'Any Star Count') selectedRating.value = 'Any Star Count';
      else selectedRating.value = data.filters.rating ? parseInt(data.filters.rating) : null;

      prompt.value = data.filters.prompt ? data.filters.prompt.join(', ') : '';
    } else {
      selectedModels.value = [];
      selectedLoras.value = [];
      selectedSamplers.value = [];
      selectedRating.value = null;
      prompt.value = '';
    }

    isEditing.value = true;
    displayCreateDialog.value = true;
  } catch (error) {
    console.error('Error fetching collection details:', error);
  }
};

const saveCollection = async () => {
  if (!newCollectionName.value.trim()) {
    toast.add({severity: 'warn', summary: 'Validation Error', detail: 'Collection name cannot be empty.', life: 3000});
    return;
  }

  const collectionData = {
    name: newCollectionName.value,
    isSmart: isSmartCollection.value,
    filters: isSmartCollection.value ? {
      models: selectedModels.value,
      loras: selectedLoras.value,
      samplers: selectedSamplers.value,
      rating: selectedRating.value !== null ? String(selectedRating.value) : null,
      prompt: prompt.value.split(',').map(p => p.trim()).filter(Boolean),
    } : null,
  };

  try {
    if (isEditing.value) {
      await api.put(`/collections/${originalCollectionName.value}`, collectionData);
      toast.add({severity: 'success', summary: 'Success', detail: 'Collection updated!', life: 3000});
    } else {
      await api.post('/collections', collectionData);
      toast.add({severity: 'success', summary: 'Success', detail: 'Collection created!', life: 3000});
    }

    await fetchCollections();
    store.refreshNav();
    displayCreateDialog.value = false;
  } catch (error) {
    console.error('Error saving collection:', error);
  }
};

const fetchCollections = async () => {
  try {
    const response = await api.get('/collections');
    collections.value = response.data;
  } catch (error) {
    console.error('Error fetching collections:', error);
  }
};

const navigateToCollection = (collectionName) => {
  router.push({path: '/', query: {collection: collectionName}});
};

const onCardContextMenu = (event, collectionName) => {
  contextMenuSelection.value = collectionName;
  menuModel.value = [
    {
      label: 'Edit Collection',
      icon: 'pi pi-pencil',
      command: () => editCollection(collectionName)
    },
    {
      label: 'Remove Collection',
      icon: 'pi pi-trash',
      command: () => removeCollection(collectionName)
    }
  ];
  if (cm.value) {
    cm.value.show(event);
  }
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

onMounted(() => {
  fetchCollections();
  if (store.availableModels.length === 0) {
    store.loadFilters();
  }

  if (store.collectionToEdit) {
    editCollection(store.collectionToEdit);
    store.collectionToEdit = null;
  }
});
</script>

<template>
  <div class="flex h-full overflow-hidden collections-view-bg">
    <div class="flex-grow-1 flex flex-column overflow-y-auto collections-view p-4">
      <div class="flex flex-column align-items-center mb-4">
        <h1 class="text-4xl font-bold text-gradient">Collections</h1>
        <Button label="Create New Collection" icon="pi pi-plus" @click="openCreateDialog"
                class="p-button-raised p-button-rounded mt-2"/>
      </div>

      <div class="grid">
        <div v-for="collectionName in collections" :key="collectionName" class="col-12 md:col-6 lg:col-4 xl:col-3">
          <div class="collection-card p-4 border-round cursor-pointer"
               @click="navigateToCollection(collectionName)"
               @contextmenu.prevent.stop="onCardContextMenu($event, collectionName)">
            <div class="text-xl font-semibold text-white">{{ collectionName }}</div>
          </div>
        </div>
      </div>

      <Dialog :header="isEditing ? 'Edit Collection' : 'Create New Collection'" v-model:visible="displayCreateDialog"
              :modal="true" :style="{ width: '60vw' }"
              class="glass-dialog">
        <div class="p-fluid">
          <div class="p-field">
            <label for="collectionName" class="text-white font-semibold">Name</label>
            <InputText id="collectionName" v-model="newCollectionName" class="glass-input mt-2"/>
          </div>

          <div class="p-field-checkbox mt-4 flex align-items-center">
            <InputSwitch id="isSmartCollection" v-model="isSmartCollection"/>
            <label for="isSmartCollection" class="ml-2 text-white font-semibold">Smart Collection</label>
          </div>

          <div v-if="isSmartCollection" class="mt-4 grid">
            <div class="col-7">
              <h3 class="text-lg font-semibold mb-3 text-white">Smart Filters</h3>
              <div class="flex flex-column gap-4">
                <div class="flex align-items-center gap-3">
                  <label class="font-semibold w-6rem text-white">Models</label>
                  <Button icon="pi pi-plus" @click="(e) => modelMenu.toggle(e)"
                          class="p-button-secondary p-button-rounded p-button-sm"/>
                  <Menu ref="modelMenu" :model="modelItems" :popup="true" class="custom-menu"/>
                </div>
                <div class="flex align-items-center gap-3">
                  <label class="font-semibold w-6rem text-white">Samplers</label>
                  <Button icon="pi pi-plus" @click="(e) => samplerMenu.toggle(e)"
                          class="p-button-secondary p-button-rounded p-button-sm"/>
                  <Menu ref="samplerMenu" :model="samplerItems" :popup="true" class="custom-menu"/>
                </div>
                <div class="flex align-items-center gap-3">
                  <label class="font-semibold w-6rem text-white">LoRAs</label>
                  <Button icon="pi pi-plus" @click="(e) => loraMenu.toggle(e)"
                          class="p-button-secondary p-button-rounded p-button-sm"/>
                  <Menu ref="loraMenu" :model="loraItems" :popup="true" class="custom-menu"/>
                </div>
                <div class="flex align-items-center gap-3">
                  <label class="font-semibold w-6rem text-white">Rating</label>
                  <Button icon="pi pi-plus" @click="(e) => ratingMenu.toggle(e)"
                          class="p-button-secondary p-button-rounded p-button-sm"/>
                  <Menu ref="ratingMenu" :model="ratingItems" :popup="true" class="custom-menu"/>
                </div>
                <div class="p-field mt-2">
                  <label for="prompt" class="text-white font-semibold">Prompt contains</label>
                  <InputText id="prompt" v-model="prompt" class="glass-input mt-2"
                             placeholder="e.g. portrait, 8k, masterpiece"/>
                </div>
              </div>
            </div>
            <div class="col-5">
              <div class="selected-chips-container p-3 border-round h-full">
                <h4 class="mt-0 mb-3 text-white font-semibold">Selected Filters</h4>
                <div class="flex flex-wrap gap-2">
                  <Chip v-for="model in selectedModels" :key="model" :label="model" removable
                        @remove="removeFilter('model', model)"/>
                  <Chip v-for="lora in selectedLoras" :key="lora" :label="lora" removable
                        @remove="removeFilter('lora', lora)"/>
                  <Chip v-for="sampler in selectedSamplers" :key="sampler" :label="sampler" removable
                        @remove="removeFilter('sampler', sampler)"/>
                  <Chip v-if="selectedRating !== null"
                        :label="selectedRating === 0 ? 'Unrated' : (selectedRating === 'Any Star Count' ? 'Any Star' : `${selectedRating} Stars`)"
                        removable
                        @remove="selectedRating = null"/>
                </div>
              </div>
            </div>
          </div>
        </div>
        <template #footer>
          <Button label="Cancel" icon="pi pi-times" @click="displayCreateDialog = false" class="p-button-text"/>
          <Button :label="isEditing ? 'Save Changes' : 'Create'" icon="pi pi-check" @click="saveCollection"/>
        </template>
      </Dialog>

      <CustomContextMenu ref="cm" :model="menuModel"/>
    </div>
  </div>
</template>

<style scoped>
.collections-view-bg {
  background: transparent;
}

.collections-view {
  min-height: 100vh;
}

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.collection-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}

.collection-card:hover {
  transform: translateY(-5px);
  background: var(--bg-input);
  border-color: var(--border-input);
}

.glass-dialog .p-dialog-header,
.glass-dialog .p-dialog-content,
.glass-dialog .p-dialog-footer {
  background: var(--bg-panel-opaque) !important;
  color: var(--text-primary) !important;
  border-color: var(--border-input) !important;
}

.glass-dialog .p-dialog-header {
  border-bottom: 1px solid var(--border-input);
}

.glass-input {
  background: var(--bg-input) !important;
  border: 1px solid var(--border-input) !important;
  color: var(--text-primary) !important;
}

.selected-chips-container {
  background: var(--bg-input);
  border: 1px solid var(--border-input);
}

.text-white {
  color: var(--text-primary) !important;
}

:deep(.custom-menu .p-menuitem-link .p-menuitem-text) {
  color: var(--text-primary) !important;
}

:deep(.custom-menu) {
  background: var(--bg-menu) !important;
  border: 1px solid var(--border-light) !important;
}
</style>