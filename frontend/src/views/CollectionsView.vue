<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import axios from 'axios';
import { useBrowserStore } from '@/stores/browser';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';
import InputSwitch from 'primevue/inputswitch';
import Menu from 'primevue/menu';
import Chip from 'primevue/chip';
import Toast from 'primevue/toast';
import { useToast } from 'primevue/usetoast';
import { useRouter } from 'vue-router';
import CustomContextMenu from '@/components/CustomContextMenu.vue';

const store = useBrowserStore();
const router = useRouter();
const toast = useToast();

const collections = ref([]);
const displayCreateDialog = ref(false);
const isEditing = ref(false);

// Context Menu
const cm = ref();
const menuModel = ref([]);
const contextMenuSelection = ref(null);

// State for new collection form
const newCollectionName = ref('');
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
  const target = { model: selectedModels, lora: selectedLoras, sampler: selectedSamplers }[type];
  if (target && !target.value.includes(value)) {
    target.value.push(value);
  }
};

const removeFilter = (type, value) => {
  const target = { model: selectedModels, lora: selectedLoras, sampler: selectedSamplers }[type];
  if (target) {
    target.value = target.value.filter(item => item !== value);
  }
};

const modelItems = computed(() => store.availableModels.map(item => ({ label: item, command: () => addFilter('model', item) })));
const samplerItems = computed(() => store.availableSamplers.map(item => ({ label: item, command: () => addFilter('sampler', item) })));
const loraItems = computed(() => store.availableLoras.map(item => ({ label: item, command: () => addFilter('lora', item) })));
const ratingItems = [
  { label: 'Any', command: () => selectedRating.value = null },
  ...[1, 2, 3, 4, 5].map(i => ({ label: `${i} Star${i > 1 ? 's' : ''}`, command: () => selectedRating.value = i }))
];

const resetForm = () => {
    newCollectionName.value = '';
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
        const response = await axios.get(`/api/collections/${name}`);
        const data = response.data;

        newCollectionName.value = data.name;
        isSmartCollection.value = data.isSmart;

        if (data.filters) {
            selectedModels.value = data.filters.models || [];
            selectedLoras.value = data.filters.loras || [];
            selectedSamplers.value = data.filters.samplers || [];
            selectedRating.value = data.filters.rating ? parseInt(data.filters.rating) : null;
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
        toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load collection details', life: 3000 });
    }
};

const saveCollection = async () => {
  if (!newCollectionName.value.trim()) {
    toast.add({ severity: 'warn', summary: 'Validation Error', detail: 'Collection name cannot be empty.', life: 3000 });
    return;
  }

  const collectionData = {
    name: newCollectionName.value,
    isSmart: isSmartCollection.value,
    filters: isSmartCollection.value ? {
      models: selectedModels.value,
      loras: selectedLoras.value,
      samplers: selectedSamplers.value,
      rating: selectedRating.value ? String(selectedRating.value) : null,
      prompt: prompt.value.split(',').map(p => p.trim()).filter(Boolean),
    } : null,
  };

  try {
    if (isEditing.value) {
        await axios.put(`/api/collections/${newCollectionName.value}`, collectionData);
        toast.add({ severity: 'success', summary: 'Success', detail: 'Collection updated!', life: 3000 });
    } else {
        await axios.post('/api/collections', collectionData);
        toast.add({ severity: 'success', summary: 'Success', detail: 'Collection created!', life: 3000 });
    }

    await fetchCollections();
    store.refreshNav();
    displayCreateDialog.value = false;
  } catch (error) {
    console.error('Error saving collection:', error);
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to save collection. See console for details.', life: 3000 });
  }
};

const fetchCollections = async () => {
  try {
    const response = await axios.get('/api/collections');
    collections.value = response.data;
  } catch (error) {
    console.error('Error fetching collections:', error);
  }
};

const navigateToCollection = (collectionName) => {
    router.push({ path: '/browser', query: { collection: collectionName } });
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
        await axios.delete(`/api/collections/${name}`);
        toast.add({ severity: 'success', summary: 'Success', detail: 'Collection removed', life: 2000 });
        store.refreshNav(); // This will trigger fetchCollections via the watcher
    } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to remove collection', life: 2000 });
    }
};

onMounted(() => {
  fetchCollections();
  if (store.availableModels.length === 0) {
      store.loadFilters();
  }

  // Check if there's a pending edit request (e.g. from navigation)
  if (store.collectionToEdit) {
      editCollection(store.collectionToEdit);
      store.collectionToEdit = null;
  }
});
</script>

<template>
  <div class="flex h-full overflow-hidden">
    <div class="flex-grow-1 flex flex-column overflow-y-auto collections-view p-4">
        <Toast/>
        <div class="flex flex-column align-items-center mb-4">
          <h1 class="text-4xl font-bold text-gradient">Collections</h1>
          <Button label="Create New Collection" icon="pi pi-plus" @click="openCreateDialog" class="p-button-raised p-button-rounded mt-2"/>
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

        <Dialog :header="isEditing ? 'Edit Collection' : 'Create New Collection'" v-model:visible="displayCreateDialog" :modal="true" :style="{ width: '60vw' }"
                class="glass-dialog">
          <div class="p-fluid">
            <div class="p-field">
              <label for="collectionName" class="text-white font-semibold">Name</label>
              <InputText id="collectionName" v-model="newCollectionName" class="glass-input mt-2" :disabled="isEditing"/>
            </div>

            <div class="p-field-checkbox mt-4 flex align-items-center">
                <InputSwitch id="isSmartCollection" v-model="isSmartCollection" />
                <label for="isSmartCollection" class="ml-2 text-white font-semibold">Smart Collection</label>
            </div>

            <div v-if="isSmartCollection" class="mt-4 grid">
                <div class="col-7">
                    <h3 class="text-lg font-semibold mb-3 text-white">Smart Filters</h3>
                    <div class="flex flex-column gap-4">
                        <div class="flex align-items-center gap-3">
                            <label class="font-semibold w-6rem">Models</label>
                            <Button icon="pi pi-plus" @click="(e) => modelMenu.toggle(e)" class="p-button-secondary p-button-rounded p-button-sm"/>
                            <Menu ref="modelMenu" :model="modelItems" :popup="true" />
                        </div>
                        <div class="flex align-items-center gap-3">
                            <label class="font-semibold w-6rem">Samplers</label>
                            <Button icon="pi pi-plus" @click="(e) => samplerMenu.toggle(e)" class="p-button-secondary p-button-rounded p-button-sm"/>
                            <Menu ref="samplerMenu" :model="samplerItems" :popup="true" />
                        </div>
                        <div class="flex align-items-center gap-3">
                            <label class="font-semibold w-6rem">LoRAs</label>
                            <Button icon="pi pi-plus" @click="(e) => loraMenu.toggle(e)" class="p-button-secondary p-button-rounded p-button-sm"/>
                            <Menu ref="loraMenu" :model="loraItems" :popup="true" />
                        </div>
                        <div class="flex align-items-center gap-3">
                            <label class="font-semibold w-6rem">Rating</label>
                            <Button icon="pi pi-plus" @click="(e) => ratingMenu.toggle(e)" class="p-button-secondary p-button-rounded p-button-sm"/>
                            <Menu ref="ratingMenu" :model="ratingItems" :popup="true" />
                        </div>
                        <div class="p-field mt-2">
                            <label for="prompt" class="text-white font-semibold">Prompt contains</label>
                            <InputText id="prompt" v-model="prompt" class="glass-input mt-2" placeholder="e.g. portrait, 8k, masterpiece"/>
                        </div>
                    </div>
                </div>
                <div class="col-5">
                    <div class="selected-chips-container p-3 border-round h-full">
                        <h4 class="mt-0 mb-3 text-white font-semibold">Selected Filters</h4>
                        <div class="flex flex-wrap gap-2">
                            <Chip v-for="model in selectedModels" :key="model" :label="model" removable @remove="removeFilter('model', model)" />
                            <Chip v-for="lora in selectedLoras" :key="lora" :label="lora" removable @remove="removeFilter('lora', lora)" />
                            <Chip v-for="sampler in selectedSamplers" :key="sampler" :label="sampler" removable @remove="removeFilter('sampler', sampler)" />
                            <Chip v-if="selectedRating" :label="`${selectedRating} Stars`" removable @remove="selectedRating = null" />
                        </div>
                    </div>
                </div>
            </div>
          </div>
          <template #footer>
            <Button label="Cancel" icon="pi pi-times" @click="displayCreateDialog = false" class="p-button-text" />
            <Button :label="isEditing ? 'Save Changes' : 'Create'" icon="pi pi-check" @click="saveCollection" />
          </template>
        </Dialog>

        <CustomContextMenu ref="cm" :model="menuModel" />
    </div>
  </div>
</template>

<style scoped>
.collections-view {
  min-height: 100vh;
}

.text-gradient {
  background: var(--app-grad-text, linear-gradient(90deg, #66fcf1, #d870ff));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.collection-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(0,0,0,0.2);
}

.collection-card:hover {
  transform: translateY(-5px);
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

.glass-dialog .p-dialog-header,
.glass-dialog .p-dialog-content,
.glass-dialog .p-dialog-footer {
  background: rgba(10, 10, 10, 0.75) !important;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  color: white;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

.glass-dialog .p-dialog-header {
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.glass-input {
  background: rgba(0, 0, 0, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: white !important;
}

.selected-chips-container {
    background: rgba(0,0,0,0.3);
    border: 1px solid rgba(255, 255, 255, 0.1);
}
</style>