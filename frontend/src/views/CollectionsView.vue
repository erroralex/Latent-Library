<script setup>
/**
 * @file CollectionsView.vue
 * @description A comprehensive management interface for image collections, supporting both static and dynamic (smart) groupings.
 *
 * This view allows users to browse their existing collections, create new ones, and configure
 * "Smart Collections" using a rich set of metadata filters (Model, Sampler, LoRA, Rating, and Prompt).
 * It features a responsive grid layout with stacked image previews for each collection and
 * provides a context menu for quick editing and removal.
 */
import {ref, onMounted, computed, watch} from 'vue';
import api, {authenticatedUrl} from '@/services/api';
import {useBrowserStore} from '@/stores/browser';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import InputSwitch from 'primevue/inputswitch';
import Dropdown from 'primevue/dropdown';
import MultiSelect from 'primevue/multiselect';
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

const ratingOptions = [
  {label: 'None (Unrated)', value: 0},
  {label: 'Any Star (> 0)', value: 'Any Star Count'},
  {label: '1 Star', value: 1},
  {label: '2 Stars', value: 2},
  {label: '3 Stars', value: 3},
  {label: '4 Stars', value: 4},
  {label: '5 Stars', value: 5}
];

watch(() => store.navRefreshKey, () => {
  fetchCollections();
});

watch(() => store.collectionToEdit, (newName) => {
  if (newName) {
    editCollection(newName);
    store.collectionToEdit = null;
  }
});

const refreshFilters = async () => {
  await store.loadFilters();
};

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
  }
};

const getThumbnailUrl = (path) => {
  return authenticatedUrl(`/api/images/thumbnail?path=${encodeURIComponent(path)}`);
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
      <div class="flex flex-column align-items-center mb-5">
        <h1 class="text-4xl font-bold text-gradient mb-2">Collections</h1>
        <Button label="Create New Collection" icon="pi pi-plus" @click="openCreateDialog"
                class="p-button-raised p-button-rounded mt-2"/>
      </div>

      <div class="grid px-4 justify-content-center">
        <div v-for="col in collections" :key="col.name" class="col-12 sm:col-6 md:col-4 lg:col-3 xl:col-2 p-3">
          <div class="collection-card-container cursor-pointer"
               @click="navigateToCollection(col.name)"
               @contextmenu.prevent.stop="onCardContextMenu($event, col.name)">

            <div class="stack-container mb-3">
              <div v-if="col.previewPaths && col.previewPaths.length > 0" class="stack">
                <div v-for="(path, index) in col.previewPaths.slice().reverse()" :key="path"
                     class="stack-item"
                     :style="{
                       transform: `translate(${index * 8}px, ${index * -8}px) scale(${1 - (index * 0.04)})`,
                       zIndex: 10 - index,
                       opacity: 1 - (index * 0.15)
                     }">
                  <img :src="getThumbnailUrl(path)" alt="Preview" class="stack-img"/>
                </div>
              </div>
              <div v-else class="stack-empty flex align-items-center justify-content-center">
                <i class="pi pi-folder-open text-4xl text-white-alpha-20"></i>
              </div>
            </div>

            <div class="collection-info text-center">
              <div
                  class="text-base font-bold text-white mb-1 text-overflow-ellipsis overflow-hidden white-space-nowrap px-2"
                  :title="col.name">
                {{ col.name }}
              </div>
              <div class="flex align-items-center justify-content-center gap-2">
                <i :class="col.isSmart ? 'pi pi-bolt text-primary' : 'pi pi-folder text-gray-500'"
                   style="font-size: 0.7rem"></i>
                <span class="text-xs uppercase tracking-wider font-semibold"
                      :class="col.isSmart ? 'text-primary' : 'text-gray-500'">
                  {{ col.isSmart ? 'Smart' : 'Static' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <Dialog :header="isEditing ? 'Edit Collection' : 'Create New Collection'" v-model:visible="displayCreateDialog"
              :modal="true" :style="{ width: '60vw' }"
              class="glass-dialog">
        <div class="p-fluid">
          <div class="p-field mb-4">
            <label for="collectionName" class="text-white font-semibold block mb-2">Collection Name</label>
            <InputText id="collectionName" v-model="newCollectionName" class="glass-input"
                       placeholder="e.g. My Favorites"/>
          </div>

          <div class="p-field-checkbox mb-2 flex align-items-center">
            <InputSwitch id="isSmartCollection" v-model="isSmartCollection" class="dynamic-toggle"/>
            <label for="isSmartCollection" class="ml-2 font-semibold transition-colors transition-duration-200"
                   :class="isSmartCollection ? 'text-primary' : 'text-white'">
              Dynamic Auto-Population
            </label>
          </div>
          <p class="text-xs text-gray-400 mb-4 ml-7">
            When enabled, this collection will automatically include images matching the filters below.
          </p>

          <div v-if="isSmartCollection" class="mt-4 grid animation-fade-in">
            <div class="col-12">
              <h3 class="text-lg font-semibold mb-3 text-white">Smart Filters</h3>
              <div class="grid formgrid">
                <div class="field col-12 md:col-6">
                  <label class="font-semibold text-white">Models</label>
                  <MultiSelect v-model="selectedModels" :options="store.availableModels"
                               placeholder="Select Models" class="w-full mt-2"
                               :scrollHeight="'40vh'" @before-show="refreshFilters"/>
                </div>
                <div class="field col-12 md:col-6">
                  <label class="font-semibold text-white">Samplers</label>
                  <MultiSelect v-model="selectedSamplers" :options="store.availableSamplers"
                               placeholder="Select Samplers" class="w-full mt-2"
                               :scrollHeight="'40vh'" @before-show="refreshFilters"/>
                </div>
                <div class="field col-12 md:col-6">
                  <label class="font-semibold text-white">LoRAs</label>
                  <MultiSelect v-model="selectedLoras" :options="store.availableLoras"
                               placeholder="Select LoRAs" class="w-full mt-2"
                               :scrollHeight="'40vh'" @before-show="refreshFilters"/>
                </div>
                <div class="field col-12 md:col-6">
                  <label class="font-semibold text-white">Rating</label>
                  <Dropdown v-model="selectedRating" :options="ratingOptions" optionLabel="label" optionValue="value"
                            placeholder="Select Rating" class="w-full mt-2"
                            :scrollHeight="'40vh'"/>
                </div>
                <div class="field col-12">
                  <label for="prompt" class="text-white font-semibold">Prompt contains</label>
                  <InputText id="prompt" v-model="prompt" class="glass-input mt-2"
                             placeholder="e.g. portrait, 8k, masterpiece"/>
                </div>
              </div>
            </div>
          </div>
        </div>
        <template #footer>
          <Button label="Cancel" icon="pi pi-times" @click="displayCreateDialog = false" class="p-button-text"/>
          <Button :label="isEditing ? 'Save Changes' : 'Create Collection'" icon="pi pi-check" @click="saveCollection"/>
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

.collection-card-container {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  max-width: 220px;
  margin: 0 auto;
}

.collection-card-container:hover {
  transform: translateY(-8px);
}

.stack-container {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stack {
  position: relative;
  width: 160px;
  height: 160px;
}

.stack-item {
  position: absolute;
  inset: 0;
  border-radius: 8px;
  overflow: hidden;
  border: 2px solid var(--border-light);
  background: var(--bg-card);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.4);
  transition: all 0.4s ease;
}

.collection-card-container:hover .stack-item {
  border-color: var(--accent-primary);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.6);
}

.stack-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stack-empty {
  width: 160px;
  height: 160px;
  background: var(--bg-card);
  border: 2px dashed var(--border-light);
  border-radius: 8px;
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

.text-white {
  color: var(--text-primary) !important;
}

.text-primary {
  color: var(--accent-primary) !important;
}

.animation-fade-in {
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

:deep(.p-multiselect), :deep(.p-dropdown) {
    background: var(--bg-input) !important;
    border: 1px solid var(--border-input) !important;
}

:deep(.dynamic-toggle.p-inputswitch) {
  width: 2.4rem !important;
  height: 1.2rem !important;
}

:deep(.dynamic-toggle.p-inputswitch .p-inputswitch-slider) {
  background-color: var(--bg-input) !important;
  border: 1px solid var(--border-input) !important;
}

:deep(.dynamic-toggle.p-inputswitch.p-inputswitch-checked .p-inputswitch-slider) {
  background-color: var(--accent-primary) !important;
  border-color: var(--accent-primary) !important;
}

:deep(.dynamic-toggle.p-inputswitch .p-inputswitch-slider:before) {
  background-color: var(--text-secondary) !important;
  width: 0.8rem !important;
  height: 0.8rem !important;
  left: 0.2rem !important;
  margin-top: -0.4rem !important;
}

:deep(.dynamic-toggle.p-inputswitch.p-inputswitch-checked .p-inputswitch-slider:before) {
  background-color: var(--bg-app) !important;
  transform: translateX(1.2rem) !important;
}
</style>