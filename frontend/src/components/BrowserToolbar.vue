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
 * - **Hot Folder Controls:** Provides toggles for recursive subfolder scanning and
 *   the "Auto-Show Latest" mode for real-time generation monitoring.
 * - **Live Indexing Status:** Displays real-time progress of background indexing operations.
 */
import {useBrowserStore} from '@/stores/browser';
import Toolbar from 'primevue/toolbar';
import Button from 'primevue/button';
import InputText from 'primevue/inputtext';
import Slider from 'primevue/slider';
import Chip from 'primevue/chip';
import InputSwitch from 'primevue/inputswitch';
import Dropdown from 'primevue/dropdown';
import {useConfirm} from 'primevue/useconfirm';
import {onUnmounted} from 'vue';

const store = useBrowserStore();
const confirm = useConfirm();

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

onUnmounted(() => {
    store.stopIndexingPoll();
});

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
      <div class="flex flex-column align-items-center w-full">
          <!-- Breadcrumb Display -->
          <div v-if="store.formattedBreadcrumb" class="text-xs text-gray-400 mb-1 font-mono select-none">
              {{ store.formattedBreadcrumb }}
          </div>

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
                  <Button icon="pi pi-bolt"
                          class="p-button-sm nav-btn"
                          :class="{ 'active-nav-btn': store.autoShowLatest }"
                          v-tooltip.bottom="'Auto-Show Latest Image'"
                          @click="store.toggleAutoShowLatest()"/>
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
                <Dropdown :options="store.availableModels" v-model="store.selectedModel"
                          placeholder="Model" class="p-button-sm"
                          :showClear="isFilterActive(store.selectedModel)"
                          @change="store.setFilter('model', $event.value)"
                          :scrollHeight="'40vh'"
                          @before-show="refreshFilters"
                          v-tooltip.bottom="'Filter by Model'"/>
              </div>

              <div class="flex align-items-center">
                <Dropdown :options="store.availableSamplers" v-model="store.selectedSampler"
                          placeholder="Sampler" class="p-button-sm"
                          :showClear="isFilterActive(store.selectedSampler)"
                          @change="store.setFilter('sampler', $event.value)"
                          :scrollHeight="'40vh'"
                          @before-show="refreshFilters"
                          v-tooltip.bottom="'Filter by Sampler'"/>
              </div>

              <div class="flex align-items-center">
                <Dropdown :options="store.availableLoras" v-model="store.selectedLora"
                          placeholder="LoRA" class="p-button-sm"
                          :showClear="isFilterActive(store.selectedLora)"
                          @change="store.setFilter('lora', $event.value)"
                          :scrollHeight="'40vh'"
                          @before-show="refreshFilters"
                          v-tooltip.bottom="'Filter by LoRA'"/>
              </div>

              <div class="flex align-items-center">
                <Dropdown v-model="store.selectedRating"
                          :options="['Any Star Count', '1', '2', '3', '4', '5']"
                          placeholder="Stars" class="p-button-sm"
                          :showClear="isFilterActive(store.selectedRating)"
                          @change="store.setFilter('rating', $event.value)"
                          :scrollHeight="'40vh'"
                          v-tooltip.bottom="'Filter by Rating'">
                  <template #option="slotProps">
                    <div v-if="slotProps.option === 'Any Star Count'" class="flex align-items-center">
                      <span>Any Star Count</span>
                    </div>
                    <div v-else class="flex">
                      <i v-for="i in 5" :key="i"
                         class="pi text-sm mr-1"
                         :class="i <= parseInt(slotProps.option) ? 'pi-star-fill text-yellow-500' : 'pi-star text-500'"></i>
                    </div>
                  </template>
                  <template #value="slotProps">
                    <div v-if="slotProps.value === 'Any Star Count'" class="flex align-items-center">
                      <span>Starred</span>
                    </div>
                    <div v-else-if="slotProps.value" class="flex align-items-center gap-1">
                      <span>{{ slotProps.value }}</span>
                      <i class="pi pi-star-fill text-yellow-500 text-xs"></i>
                    </div>
                    <span v-else>
                      {{ slotProps.placeholder }}
                    </span>
                  </template>
                </Dropdown>
              </div>
            </div>
          </div>
      </div>
    </template>

    <template #end>
      <div class="flex gap-3 align-items-center">
        <!-- Indexing Status Indicator -->
        <div v-if="store.isIndexing" class="flex align-items-center gap-2 text-xs text-primary animate-pulse">
            <i class="pi pi-spin pi-spinner"></i>
            <span>Indexing: {{ store.filesProcessed }} / {{ store.totalFilesToScan }}</span>
        </div>
        <div v-else-if="store.totalFilesToScan > 0" class="flex align-items-center gap-2 text-xs text-green-400">
            <i class="pi pi-check-circle"></i>
            <span>Indexed: {{ store.totalFilesToScan }} files</span>
        </div>

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

:deep(.p-dropdown) {
    background: var(--bg-input) !important;
    border: 1px solid var(--border-input) !important;
}

@keyframes pulse {
    0% { opacity: 1; }
    50% { opacity: 0.7; }
    100% { opacity: 1; }
}

.animate-pulse {
    animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
</style>
