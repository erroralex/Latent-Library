<script setup>
/**
 * BrowserToolbar.vue
 *
 * The top navigation bar for the Image Browser.
 * Contains controls for view switching (Gallery/Browser), search input,
 * filtering dropdowns (Model, Sampler, LoRA, Rating), and thumbnail size slider.
 */
import { useBrowserStore } from '@/stores/browser';
import Toolbar from 'primevue/toolbar';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Slider from 'primevue/slider';
import Menu from 'primevue/menu';
import Chip from 'primevue/chip';
import { ref, computed } from 'vue';

const store = useBrowserStore();

const modelMenu = ref();
const samplerMenu = ref();
const loraMenu = ref();
const ratingMenu = ref();

const onSearch = () => {
  store.search(store.searchQuery);
};

const toggleSidebar = () => {
  store.toggleSidebar();
};

const createMenuItems = (items, type) => {
  return items.map(item => ({
    label: item,
    command: () => store.setFilter(type, item)
  }));
};

const modelItems = computed(() => createMenuItems(store.availableModels, 'model'));
const samplerItems = computed(() => createMenuItems(store.availableSamplers, 'sampler'));
const loraItems = computed(() => createMenuItems(store.availableLoras, 'lora'));

const ratingItems = [
  { label: 'Any Star Count', command: () => store.setFilter('rating', 'Any Star Count') },
  { label: '1', value: '1', command: () => store.setFilter('rating', '1') },
  { label: '2', value: '2', command: () => store.setFilter('rating', '2') },
  { label: '3', value: '3', command: () => store.setFilter('rating', '3') },
  { label: '4', value: '4', command: () => store.setFilter('rating', '4') },
  { label: '5', value: '5', command: () => store.setFilter('rating', '5') }
];

const clearFilter = (type) => {
  store.setFilter(type, null);
};

const isFilterActive = (val) => val && val !== 'All' && val !== 'Any Star Count';

const refreshFilters = () => {
  store.loadFilters();
};

</script>

<template>
  <Toolbar class="browser-toolbar-glass border-none p-2">
    <template #start>
      <div class="flex gap-2 align-items-center flex-wrap">
        <div class="flex gap-1 mr-2">
          <Button icon="pi pi-th-large"
                  class="p-button-sm"
                  :class="{ 'p-button-outlined': store.viewMode !== 'gallery' }"
                  @click="store.setViewMode('gallery')"
                  tooltip="Gallery View" />
          <Button icon="pi pi-image"
                  class="p-button-sm"
                  :class="{ 'p-button-outlined': store.viewMode !== 'browser' }"
                  @click="store.setViewMode('browser')"
                  tooltip="Browser View" />
        </div>

        <span class="p-input-icon-left mr-2">
                    <i class="pi pi-search" />
                    <InputText v-model="store.searchQuery" placeholder="Search..." class="p-inputtext-sm w-15rem glass-input" @keyup.enter="onSearch" />
                </span>

        <div class="flex gap-2 align-items-center">

          <div class="flex align-items-center">
            <Button label="Model" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm text-white"
                    @click="(e) => { refreshFilters(); modelMenu.toggle(e); }" />
            <Menu ref="modelMenu" :model="modelItems" :popup="true" class="w-15rem" />

            <Chip v-if="isFilterActive(store.selectedModel)"
                  :label="store.selectedModel"
                  removable @remove="clearFilter('model')"
                  class="ml-1 text-xs" />
          </div>

          <div class="flex align-items-center">
            <Button label="Sampler" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm text-white"
                    @click="(e) => { refreshFilters(); samplerMenu.toggle(e); }" />
            <Menu ref="samplerMenu" :model="samplerItems" :popup="true" class="w-15rem" />

            <Chip v-if="isFilterActive(store.selectedSampler)"
                  :label="store.selectedSampler"
                  removable @remove="clearFilter('sampler')"
                  class="ml-1 text-xs" />
          </div>

          <div class="flex align-items-center">
            <Button label="LoRA" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm text-white"
                    @click="(e) => { refreshFilters(); loraMenu.toggle(e); }" />
            <Menu ref="loraMenu" :model="loraItems" :popup="true" class="w-15rem" />

            <Chip v-if="isFilterActive(store.selectedLora)"
                  :label="store.selectedLora"
                  removable @remove="clearFilter('lora')"
                  class="ml-1 text-xs" />
          </div>

          <div class="flex align-items-center">
            <Button label="Stars" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm text-white"
                    @click="(e) => ratingMenu.toggle(e)" />
            <Menu ref="ratingMenu" :model="ratingItems" :popup="true">
              <template #item="{ item, props }">
                <a v-if="item.label === 'Any Star Count'" v-bind="props.action" class="flex align-items-center">
                  <span class="ml-2">Any Star Count</span>
                </a>
                <a v-else v-bind="props.action" class="flex align-items-center">
                  <div class="flex ml-2">
                    <i v-for="i in 5" :key="i"
                       class="pi text-sm mr-1"
                       :class="i <= parseInt(item.value) ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
                  </div>
                </a>
              </template>
            </Menu>

            <Chip v-if="isFilterActive(store.selectedRating)"
                  removable @remove="clearFilter('rating')"
                  class="ml-1 text-xs px-2">
              <span v-if="store.selectedRating === 'Any Star Count'">Starred</span>
              <span v-else class="flex align-items-center gap-1">
                                {{ store.selectedRating }} <i class="pi pi-star-fill text-yellow-500 text-xs"></i>
                             </span>
            </Chip>
          </div>
        </div>
      </div>
    </template>

    <template #end>
      <div class="flex gap-3 align-items-center">
        <div v-if="store.viewMode === 'gallery'" class="flex gap-3 align-items-center">
          <i class="pi pi-images text-xl text-500"></i>
          <Slider v-model="store.cardSize" :min="100" :max="400" class="w-8rem" />
        </div>

        <Button icon="pi pi-info-circle"
                :class="[ store.isSidebarOpen ? 'text-primary' : 'text-white' ]"
                class="p-button-rounded p-button-text"
                @click="toggleSidebar"
                tooltip="Toggle Metadata"
                v-if="store.viewMode === 'browser'" />
      </div>
    </template>
  </Toolbar>
</template>

<style scoped>
/* Glass Toolbar */
.browser-toolbar-glass {
  background: var(--app-bg-header, rgba(10, 10, 10, 0.75));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 20px rgba(0,0,0,0.4);
  position: relative;
  z-index: 10;
}

/* Force Input to be glass */
.glass-input {
  background: rgba(0, 0, 0, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: white !important;
  backdrop-filter: blur(10px);
}

/* Override PrimeVue Toolbar internal background */
:deep(.p-toolbar) {
  background: transparent !important;
  border: none !important;
}
</style>