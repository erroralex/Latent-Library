<script setup>
/**
 * FilmstripView.vue
 *
 * A horizontal scrollable list of image thumbnails.
 * Used in the browser view to navigate between images in the current folder.
 * Automatically scrolls to keep the selected image in view.
 */
import { useBrowserStore } from '@/stores/browser';
import { ref, watch, nextTick, onMounted } from 'vue';

const store = useBrowserStore();
const container = ref(null);

const scrollToSelected = () => {
    if (!container.value || !store.selectedFile) return;

    const index = store.files.indexOf(store.selectedFile);
    if (index === -1) return;

    const element = container.value.children[index];
    if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
    }
};

watch(() => store.selectedFile, () => {
    nextTick(scrollToSelected);
});

onMounted(() => {
    scrollToSelected();
});
</script>

<template>
    <div class="filmstrip-view surface-card border-top-1 surface-border h-10rem flex flex-column">
        <div ref="container" class="flex-grow-1 overflow-x-auto overflow-y-hidden flex flex-nowrap gap-2 p-2 align-items-center">
            <div v-for="file in store.files" :key="file"
                 class="filmstrip-item flex-shrink-0 cursor-pointer border-round transition-all transition-duration-200 surface-ground"
                 :class="{ 'selected-item': store.selectedFile === file }"
                 @click="store.selectFile(file)">

                <div class="relative border-round overflow-hidden flex align-items-center justify-content-center" style="width: 120px; height: 120px;">
                    <img :src="`http://localhost:8080/api/images/content?path=${encodeURIComponent(file)}`"
                         loading="lazy"
                         class="w-full h-full"
                         style="object-fit: contain;" />
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.filmstrip-view {
    background-color: var(--surface-section);
}

.filmstrip-item {
    opacity: 0.7;
    border: 2px solid transparent;
}

.filmstrip-item:hover {
    opacity: 1;
}

.filmstrip-item.selected-item {
    opacity: 1;
    border-color: var(--primary-color);
    transform: scale(1.05);
    z-index: 1;
}
</style>
