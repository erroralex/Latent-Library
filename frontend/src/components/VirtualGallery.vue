<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useBrowserStore } from '@/stores/browser';
import ImageCard from '@/components/ImageCard.vue';
import VirtualScroller from 'primevue/virtualscroller';

const store = useBrowserStore();
const galleryContainer = ref(null);
const scrollerRef = ref(null);
const gridCols = ref(4);

const chunkedFiles = computed(() => {
    const chunks = [];
    for (let i = 0; i < store.files.length; i += gridCols.value) {
        chunks.push(store.files.slice(i, i + gridCols.value));
    }
    return chunks;
});

const updateGridCols = () => {
    if (!galleryContainer.value) return;
    const el = galleryContainer.value.$el || galleryContainer.value;
    if (!el) return;

    const containerWidth = el.clientWidth;
    const cardWidth = store.cardSize + 16; // card size + gap
    const cols = Math.floor(containerWidth / cardWidth) || 1;
    gridCols.value = cols;
};

// Watch for cardSize changes to recalculate grid columns immediately
watch(() => store.cardSize, () => {
    updateGridCols();
});

// Watch for selected file changes to scroll to it
watch(() => store.selectedFile, async (newFile) => {
    if (!newFile || !scrollerRef.value) return;

    // Find which chunk (row) contains the selected file
    const rowIndex = chunkedFiles.value.findIndex(chunk => chunk.includes(newFile));

    if (rowIndex !== -1) {
        await nextTick();

        // Manual scroll logic to ensure the item is in view (smart scrolling)
        // We prefer this over scrollToIndex which always snaps to top
        const scrollerEl = scrollerRef.value.$el;
        if (scrollerEl) {
            const itemSize = store.cardSize + 16;
            const rowTop = rowIndex * itemSize;
            const rowBottom = rowTop + itemSize;

            const scrollTop = scrollerEl.scrollTop;
            const clientHeight = scrollerEl.clientHeight;

            if (rowTop < scrollTop) {
                // Item is above visible area -> Scroll up to show it at top
                scrollerEl.scrollTop = rowTop;
            } else if (rowBottom > scrollTop + clientHeight) {
                // Item is below visible area -> Scroll down to show it at bottom
                scrollerEl.scrollTop = rowBottom - clientHeight;
            }
        }
    }
});

let resizeObserver;

onMounted(() => {
    // We need to wait for the element to be available
    setTimeout(() => {
        if (galleryContainer.value) {
            const el = galleryContainer.value.$el || galleryContainer.value;
            resizeObserver = new ResizeObserver(() => updateGridCols());
            resizeObserver.observe(el);
            updateGridCols();
        }
    }, 100);
});

onUnmounted(() => {
    if (resizeObserver) resizeObserver.disconnect();
});

const onScrollIndexChange = (event) => {
    if (store.hasMore && !store.isFetchingMore) {
        const totalRows = chunkedFiles.value.length;
        if (event.last >= totalRows - 2) {
            store.loadMore();
        }
    }
};

const handleGalleryItemDoubleClick = (file) => {
  store.selectFile(file);
  store.setViewMode('browser');
  store.setSidebarOpen(true);
};

defineExpose({ gridCols });
</script>

<template>
    <div class="h-full p-3 overflow-hidden" ref="galleryContainer">
         <VirtualScroller ref="scrollerRef" :items="chunkedFiles" :itemSize="store.cardSize + 16" class="h-full"
                          @scroll-index-change="onScrollIndexChange">
            <template v-slot:item="{ item, options }">
                <div class="flex gap-2 justify-content-center" :style="{ height: (store.cardSize + 8) + 'px', marginBottom: '8px' }">
                    <div v-for="file in item" :key="file"
                         :style="{ width: store.cardSize + 'px', height: store.cardSize + 'px' }"
                         class="border-round transition-all transition-duration-100"
                         :class="{ 'outline-active': store.selectedFile === file }"
                         @click="store.selectFile(file)"
                         @dblclick="handleGalleryItemDoubleClick(file)">
                        <ImageCard :path="file" />
                    </div>
                    <!-- Fill empty space in the last row to align left if needed, or center as per flex -->
                </div>
            </template>
         </VirtualScroller>
         <div v-if="store.files.length === 0 && !store.isLoading" class="text-center p-5 text-gray-500">
             No images found.
         </div>
    </div>
</template>

<style scoped>
.outline-active {
  position: relative;
  z-index: 1;
  background: transparent;
  outline: none;
  box-shadow: none;
}

.outline-active::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: var(--app-grad-hover);
  border-radius: inherit;
  z-index: -2;
  filter: blur(2px);
}

.outline-active::after {
  content: '';
  position: absolute;
  inset: 0;
  background: #000000;
  border-radius: inherit;
  z-index: -1;
}
</style>