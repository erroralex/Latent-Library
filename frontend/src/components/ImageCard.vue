<script setup>
/**
 * ImageCard.vue
 *
 * A reusable component for displaying a single image thumbnail.
 * Includes a context menu for file operations (Open in Explorer, Copy Path).
 * Uses a blurred background effect for aspect ratio filling.
 */
import { computed, ref } from 'vue';
import Card from 'primevue/card';
import ContextMenu from 'primevue/contextmenu';
import Toast from 'primevue/toast';
import { useToast } from 'primevue/usetoast';
import axios from 'axios';

const props = defineProps({
    path: String
});

const toast = useToast();
const cm = ref();
const menuModel = ref([
    {
        label: 'Open in Explorer',
        icon: 'pi pi-external-link',
        command: () => openInExplorer()
    },
    {
        label: 'Copy Path',
        icon: 'pi pi-copy',
        command: () => copyPath()
    }
]);

const imageUrl = computed(() => `http://localhost:8080/api/images/content?path=${encodeURIComponent(props.path)}`);

const onRightClick = (event) => {
    cm.value.show(event);
};

const openInExplorer = async () => {
    try {
        await axios.post('/api/system/open-folder', null, { params: { path: props.path } });
    } catch (e) {
        toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to open explorer', life: 2000 });
    }
};

const copyPath = () => {
    navigator.clipboard.writeText(props.path);
    toast.add({ severity: 'info', summary: 'Copied', detail: 'Path copied to clipboard', life: 1000 });
};
</script>

<template>
    <div class="h-full" @contextmenu.prevent="onRightClick">
        <Card class="h-full cursor-pointer hover:surface-hover transition-colors transition-duration-150 overflow-hidden">
            <template #header>
                <div class="aspect-ratio-container surface-card flex align-items-center justify-content-center relative">
                    <div class="absolute top-0 left-0 w-full h-full overflow-hidden opacity-50"
                         :style="{ backgroundImage: `url('${imageUrl}')`, backgroundSize: 'cover', filter: 'blur(10px)' }">
                    </div>

                    <img :src="imageUrl" alt="AI Image" loading="lazy"
                         class="relative w-full h-full"
                         style="object-fit: contain;" />
                </div>
            </template>
            <template #content>
                <div class="text-xs text-overflow-ellipsis white-space-nowrap overflow-hidden px-2 pb-2" :title="path">
                    {{ path.split(/[\\/]/).pop() }}
                </div>
            </template>
        </Card>
        <ContextMenu ref="cm" :model="menuModel" />
        <Toast />
    </div>
</template>

<style scoped>
.aspect-ratio-container {
    aspect-ratio: 1 / 1;
    overflow: hidden;
    background-color: var(--surface-ground);
}

:deep(.p-card-body) {
    padding: 0;
}
:deep(.p-card-content) {
    padding: 0;
}
</style>
