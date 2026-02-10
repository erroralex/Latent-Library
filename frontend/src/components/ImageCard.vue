<script setup>
/**
 * @file ImageCard.vue
 * @description A compact, high-performance UI component for displaying image thumbnails within a grid or list.
 *
 * This component is optimized for use within virtualized containers. It renders an image with a blurred
 * background effect to maintain a consistent aspect ratio regardless of the source image's dimensions.
 * It also features an information overlay that displays key metadata (rating, model name, filename)
 * without requiring additional network requests, as it consumes data passed via the `file` prop.
 *
 * Key functionalities:
 * - Visual Consistency: Implements a "glass" overlay and blurred background for a premium aesthetic.
 * - Contextual Interaction: Emits a custom `contextmenu` event to allow parent components to handle right-click actions.
 * - Lazy Loading: Utilizes native browser lazy loading for images to optimize initial page load and scroll performance.
 * - Responsive Metadata: Conditionally renders star ratings and model information based on availability.
 */
import { computed } from 'vue';
import Card from 'primevue/card';

const props = defineProps({
    file: {
        type: Object,
        required: true
    }
});

const imageUrl = computed(() => `http://localhost:8080/api/images/content?path=${encodeURIComponent(props.file.path)}`);

const emit = defineEmits(['contextmenu']);

const onRightClick = (event) => {
  emit('contextmenu', { event, path: props.file.path });
};

</script>

<template>
  <div @contextmenu.prevent="onRightClick" class="h-full">
        <Card class="h-full cursor-pointer hover:surface-hover transition-colors transition-duration-150 overflow-hidden relative border-none">
            <template #header>
                <div class="aspect-ratio-container surface-card flex align-items-center justify-content-center relative h-full">
                    <div class="absolute top-0 left-0 w-full h-full overflow-hidden opacity-50"
                         :style="{ backgroundImage: `url('${imageUrl}')`, backgroundSize: 'cover', filter: 'blur(10px)' }">
                    </div>

                    <img :src="imageUrl" alt="AI Image" loading="lazy"
                         class="relative w-full h-full"
                         style="object-fit: contain;" />

                    <div class="absolute bottom-0 left-0 w-full p-2 glass-overlay flex flex-column gap-1">
                        <div class="flex gap-1" v-if="file.rating > 0">
                             <i v-for="i in 5" :key="i"
                                class="pi text-xs"
                                :class="i <= file.rating ? 'pi-star-fill text-yellow-500' : 'pi-star text-white-alpha-30'"
                                style="font-size: 0.7rem"></i>
                        </div>

                        <div v-if="file.model" class="text-xs text-white-alpha-80 text-overflow-ellipsis white-space-nowrap overflow-hidden font-semibold">
                            {{ file.model }}
                        </div>

                        <div class="text-xs text-white text-overflow-ellipsis white-space-nowrap overflow-hidden" :title="file.path">
                            {{ file.path.split(/[\\/]/).pop() }}
                        </div>
                    </div>
                </div>
            </template>
            <template #content>
            </template>
        </Card>
    </div>
</template>

<style scoped>
.aspect-ratio-container {
    aspect-ratio: 1 / 1;
    overflow: hidden;
    background-color: var(--surface-ground);
}

.glass-overlay {
    background: rgba(0, 0, 0, 0.6);
    backdrop-filter: blur(4px);
    -webkit-backdrop-filter: blur(4px);
    border-top: 1px solid rgba(255, 255, 255, 0.1);
}

:deep(.p-card-body) {
    padding: 0;
    height: 100%;
}
:deep(.p-card-content) {
    padding: 0;
}
:deep(.p-card) {
    background: transparent;
    box-shadow: none;
}
</style>