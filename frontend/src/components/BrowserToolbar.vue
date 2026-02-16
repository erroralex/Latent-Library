<script setup>
/**
 * @file BrowserToolbar.vue
 * @description The primary control interface for the Image Browser, providing tools for navigation, search, and advanced filtering.
 *
 * This component acts as the command center for the image exploration experience. It hosts
 * a variety of controls including view mode toggles, a global search bar with AI tag
 * integration, and dynamic metadata filters for Models, Samplers, LoRAs, and Ratings.
 *
 * Key functionalities:
 * - **View Orchestration:** Toggles between Gallery and Browser modes and provides a
 *   scaling slider for gallery thumbnails.
 * - **Advanced Search:** Implements a real-time search bar with an optional toggle
 *   to include AI-generated tags in the query results.
 * - **Dynamic Filtering:** Generates dropdown menus for technical metadata, allowing
 *   users to drill down into specific generation parameters.
 * - **Sidebar Controls:** Provides quick-access buttons to toggle the AI Auto-Tagger
 *   and Metadata sidebars.
 * - **State Integration:** Synchronizes all filter and search states with the global
 *   Pinia store to ensure consistent results across the application.
 */
import {useBrowserStore} from '@/stores/browser';
import Toolbar from 'primevue/toolbar';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Slider from 'primevue/slider';
import Menu from 'primevue/menu';
import Chip from 'primevue/chip';
import InputSwitch from 'primevue/inputswitch';
import {useConfirm} from 'primevue/useconfirm';
import {ref, computed} from 'vue';

const store = useBrowserStore();
const confirm = useConfirm();

const modelMenu = ref();
const samplerMenu = ref();
const loraMenu = ref();
const ratingMenu = ref();

const onSearch = (event) => {
  store.search(store.searchQuery, true);
  event.target.blur();
};

const toggleSidebar = () => {
  store.toggleSidebar();
};

const toggleTagger = () => {
    store.toggleTagger();
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
  {label: 'Any Star Count', command: () => store.setFilter('rating', 'Any Star Count')},
  {label: '1', value: '1', command: () => store.setFilter('rating', '1')},
  {label: '2', value: '2', command: () => store.setFilter('rating', '2')},
  {label: '3', value: '3', command: () => store.setFilter('rating', '3')},
  {label: '4', value: '4', command: () => store.setFilter('rating', '4')},
  {label: '5', value: '5', command: () => store.setFilter('rating', '5')}
];

const clearFilter = (type) => {
  store.setFilter(type, null);
};

const clearCollection = () => {
  store.clearCollection();
};

const isFilterActive = (val) => val && val !== 'All';

const refreshFilters = () => {
  store.loadFilters();
};

const toggleAiTags = () => {
    store.toggleIncludeAiTags();
};

const toggleRecursive = () => {
    if (!store.recursiveView) {
        confirm.require({
            message: 'Enabling recursive view will index and display all images in all subfolders. For large directories, this may take some time. Continue?',
            header: 'Include Subfolders',
            icon: 'pi pi-exclamation-triangle',
            acceptClass: 'p-button-warning',
            accept: () => {
                store.toggleRecursiveView();
            }
        });
    } else {
        store.toggleRecursiveView();
    }
};

</script>

<template>
  <Toolbar class="browser-toolbar-glass border-none p-2">
    <template #start>
        <div class="flex gap-2 align-items-center ml-2">
            <Button icon="pi pi-tags"
                    :class="[ store.isTaggerOpen ? 'text-primary' : 'text-secondary' ]"
                    class="p-button-rounded p-button-text"
                    @click="toggleTagger"
                    v-tooltip.bottom="'Toggle Auto-Tagger'"
                    v-if="store.viewMode === 'browser'"/>
        </div>
    </template>

    <template #center>
      <div class="flex gap-2 align-items-center flex-wrap justify-content-center">
        <div v-if="store.viewMode === 'gallery'" class="flex gap-3 align-items-center mr-3">
          <i class="pi pi-search-plus text-xl text-500"></i>
          <Slider v-model="store.cardSize" :min="100" :max="400" class="w-8rem"/>
        </div>

        <div class="flex gap-1 mr-2 align-items-center">
          <div class="flex align-items-center gap-1 mr-2 border-right-1 border-white-alpha-10 pr-2" v-if="store.lastFolderPath">
              <Button icon="pi pi-sitemap"
                      class="p-button-sm nav-btn"
                      :class="{ 'active-nav-btn': store.recursiveView }"
                      v-tooltip.bottom="'Include Subfolders'"
                      @click="toggleRecursive"/>
          </div>

          <Button icon="pi pi-th-large"
                  class="p-button-sm nav-btn"
                  :class="{ 'active-nav-btn': store.viewMode === 'gallery' }"
                  @click="store.setViewMode('gallery')"
                  v-tooltip.bottom="'Gallery View'"/>
          <Button icon="pi pi-image"
                  class="p-button-sm nav-btn"
                  :class="{ 'active-nav-btn': store.viewMode === 'browser' }"
                  @click="store.setViewMode('browser')"
                  v-tooltip.bottom="'Browser View'"/>
        </div>

        <div class="flex align-items-center gap-2 mr-2">
          <Chip v-if="store.activeCollection"
                :label="store.activeCollection"
                icon="pi pi-folder"
                removable @remove="clearCollection"
                class="collection-chip text-xs"/>

          <span class="p-input-icon-left p-input-icon-right">
              <i class="pi pi-search"/>
              <InputText v-model="store.searchQuery" placeholder="Search..." class="p-inputtext-sm w-15rem glass-input"
                         @keyup.enter="onSearch"/>
              <i v-if="store.searchQuery" class="pi pi-times cursor-pointer" @click="store.clearSearch()"/>
          </span>

          <div class="flex align-items-center gap-2 ml-2" v-tooltip.bottom="'Include AI Tags in Search'">
            <InputSwitch v-model="store.includeAiTags" @change="toggleAiTags" class="ai-tags-toggle" />
            <label class="text-xs font-bold uppercase tracking-wider select-none"
                   :class="store.includeAiTags ? 'text-primary' : 'text-gray-500'">AI Tags</label>
          </div>
        </div>

        <div class="flex gap-2 align-items-center">

          <div class="flex align-items-center">
            <Button label="Model" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm"
                    v-tooltip.bottom="'Filter by Model'"
                    @click="(e) => { refreshFilters(); modelMenu.toggle(e); }"/>
            <Menu ref="modelMenu" :model="modelItems" :popup="true" class="w-15rem"/>

            <Chip v-if="isFilterActive(store.selectedModel)"
                  :label="store.selectedModel"
                  removable @remove="clearFilter('model')"
                  class="ml-1 text-xs"/>
          </div>

          <div class="flex align-items-center">
            <Button label="Sampler" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm"
                    v-tooltip.bottom="'Filter by Sampler'"
                    @click="(e) => { refreshFilters(); samplerMenu.toggle(e); }"/>
            <Menu ref="samplerMenu" :model="samplerItems" :popup="true" class="w-15rem"/>

            <Chip v-if="isFilterActive(store.selectedSampler)"
                  :label="store.selectedSampler"
                  removable @remove="clearFilter('sampler')"
                  class="ml-1 text-xs"/>
          </div>

          <div class="flex align-items-center">
            <Button label="LoRA" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm"
                    v-tooltip.bottom="'Filter by LoRA'"
                    @click="(e) => { refreshFilters(); loraMenu.toggle(e); }"/>
            <Menu ref="loraMenu" :model="loraItems" :popup="true" class="w-15rem"/>

            <Chip v-if="isFilterActive(store.selectedLora)"
                  :label="store.selectedLora"
                  removable @remove="clearFilter('lora')"
                  class="ml-1 text-xs"/>
          </div>

          <div class="flex align-items-center">
            <Button label="Stars" icon="pi pi-chevron-down" iconPos="right"
                    class="p-button-text p-button-secondary p-button-sm"
                    v-tooltip.bottom="'Filter by Rating'"
                    @click="(e) => ratingMenu.toggle(e)"/>
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
        <Button icon="pi pi-info-circle"
                :class="[ store.isSidebarOpen ? 'text-primary' : 'text-secondary' ]"
                class="p-button-rounded p-button-text"
                @click="toggleSidebar"
                v-tooltip.bottom="'Toggle Metadata'"
                v-if="store.viewMode === 'browser'"/>
      </div>
    </template>
  </Toolbar>
</template>

<style scoped>
.browser-toolbar-glass {
  background: var(--bg-toolbar);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-radius: 0;
  border-bottom: 1px solid var(--border-light);
  box-shadow: var(--shadow-panel);
  position: relative;
  z-index: 10;
}

.glass-input {
  background: var(--bg-input) !important;
  border: 1px solid var(--border-input) !important;
  color: var(--text-primary) !important;
  backdrop-filter: blur(10px);
}

.glass-input:enabled:focus {
  box-shadow: none !important;
  outline: none !important;
  border-color: transparent !important;
  border-image: var(--grad-hover) 1 !important;
}

.collection-chip {
  background: rgba(102, 252, 241, 0.15) !important;
  color: var(--accent-primary) !important;
  border: 1px solid rgba(102, 252, 241, 0.3) !important;
}

:deep(.p-toolbar) {
  background: transparent !important;
  border: none !important;
}

/* AI Tags Toggle Specific Overrides */
:deep(.ai-tags-toggle.p-inputswitch) {
    width: 2.4rem !important;
    height: 1.2rem !important;
}

:deep(.ai-tags-toggle.p-inputswitch .p-inputswitch-slider) {
    background-color: var(--bg-input) !important;
    border: 1px solid var(--border-input) !important;
}

:deep(.ai-tags-toggle.p-inputswitch.p-inputswitch-checked .p-inputswitch-slider) {
    background-color: var(--accent-primary) !important;
    border-color: var(--accent-primary) !important;
}

:deep(.ai-tags-toggle.p-inputswitch .p-inputswitch-slider:before) {
    background-color: var(--text-secondary) !important;
    width: 0.8rem !important;
    height: 0.8rem !important;
    left: 0.2rem !important;
    margin-top: -0.4rem !important;
}

:deep(.ai-tags-toggle.p-inputswitch.p-inputswitch-checked .p-inputswitch-slider:before) {
    background-color: var(--bg-app) !important;
    transform: translateX(1.2rem) !important;
}

.text-primary {
    color: var(--accent-primary) !important;
}
</style>
