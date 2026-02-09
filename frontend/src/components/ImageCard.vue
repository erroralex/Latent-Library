<script setup>
/**
 * ImageCard.vue
 *
 * A reusable component for displaying a single image thumbnail.
 * Emits a contextmenu event for the parent to handle.
 * Uses a blurred background effect for aspect ratio filling.
 */
import { computed } from 'vue';
import Card from 'primevue/card';

const props = defineProps({
    path: String
});

const imageUrl = computed(() => `http://localhost:8080/api/images/content?path=${encodeURIComponent(props.path)}`);

const emit = defineEmits(['contextmenu']);

const onRightClick = (event) => {
  // Emit the native event and the path
  emit('contextmenu', { event, path: props.path });
};
</script>

<template>
  <div @contextmenu.prevent="onRightClick">
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