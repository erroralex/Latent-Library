<script setup>
/**
 * @file ImageCard.vue
 * @description A reusable component for displaying a single image thumbnail in a card format.
 * It features a blurred background effect to fill the aspect ratio and emits a contextmenu
 * event for parent components to handle right-click actions.
 */
import { computed, ref, onMounted } from 'vue';
import Card from 'primevue/card';
import axios from 'axios';

const props = defineProps({
    path: String
});

const imageUrl = computed(() => `http://localhost:8080/api/images/content?path=${encodeURIComponent(props.path)}`);

const emit = defineEmits(['contextmenu']);

const onRightClick = (event) => {
  emit('contextmenu', { event, path: props.path });
};

const rating = ref(0);
const model = ref('');

const fetchMetadata = async () => {
    try {
        const response = await axios.get('/api/images/metadata', {
            params: { path: props.path }
        });
        rating.value = response.data.rating || 0;
        model.value = response.data.Model || '';
    } catch (error) {
        // Silent fail for card metadata
    }
};

onMounted(() => {
    // We fetch metadata for the overlay.
    // Optimization: In a real virtual scroller, we might want to pass this data in
    // rather than fetching it per card, but for now this meets the requirement.
    fetchMetadata();
});

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

                    <!-- Overlay -->
                    <div class="absolute bottom-0 left-0 w-full p-2 glass-overlay flex flex-column gap-1">
                        <!-- Top: Stars -->
                        <div class="flex gap-1" v-if="rating > 0">
                             <i v-for="i in 5" :key="i"
                                class="pi text-xs"
                                :class="i <= rating ? 'pi-star-fill text-yellow-500' : 'pi-star text-white-alpha-30'"
                                style="font-size: 0.7rem"></i>
                        </div>

                        <!-- Middle: Model -->
                        <div v-if="model" class="text-xs text-white-alpha-80 text-overflow-ellipsis white-space-nowrap overflow-hidden font-semibold">
                            {{ model }}
                        </div>

                        <!-- Bottom: Filename -->
                        <div class="text-xs text-white text-overflow-ellipsis white-space-nowrap overflow-hidden" :title="path">
                            {{ path.split(/[\\/]/).pop() }}
                        </div>
                    </div>
                </div>
            </template>
            <template #content>
                <!-- Content is now empty as info is in overlay -->
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