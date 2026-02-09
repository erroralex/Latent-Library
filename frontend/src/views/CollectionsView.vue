<script setup>
/**
 * CollectionsView.vue
 *
 * Manages user-defined image collections.
 * Allows users to view existing collections and create new ones via a dialog.
 */
import { ref, onMounted } from 'vue';
import axios from 'axios';
import Button from 'primevue/button';
import Dialog from 'primevue/dialog';
import InputText from 'primevue/inputtext';

const collections = ref([]);
const showCreateDialog = ref(false);
const newCollectionName = ref('');

const fetchCollections = async () => {
    try {
        const response = await axios.get('http://localhost:8080/api/collections');
        collections.value = response.data;
    } catch (error) {
        console.error('Failed to fetch collections:', error);
    }
};

const createCollection = async () => {
    if (!newCollectionName.value) return;
    try {
        await axios.post('http://localhost:8080/api/collections', null, {
            params: { name: newCollectionName.value }
        });
        newCollectionName.value = '';
        showCreateDialog.value = false;
        fetchCollections();
    } catch (error) {
        console.error('Failed to create collection:', error);
    }
};

onMounted(fetchCollections);
</script>

<template>
    <div class="collections-view p-5 h-full overflow-y-auto">
        <div class="flex justify-content-between align-items-center mb-5">
            <h2 class="m-0 text-3xl font-bold text-gradient">Collections</h2>
            <Button label="New Collection" icon="pi pi-plus" @click="showCreateDialog = true" class="p-button-outlined" />
        </div>

        <div class="grid">
            <div v-for="col in collections" :key="col" class="col-12 md:col-6 lg:col-4 xl:col-3">
                <div class="collection-card p-4 border-round cursor-pointer transition-all transition-duration-300 relative overflow-hidden">
                    <div class="text-xl font-bold mb-2 text-white">{{ col }}</div>
                    <div class="text-gray-400">0 items</div> <!-- Placeholder for count -->
                    <i class="pi pi-folder absolute bottom-0 right-0 text-8xl text-white-alpha-5" style="transform: translate(20%, 20%);"></i>
                </div>
            </div>
        </div>

        <Dialog v-model:visible="showCreateDialog" header="New Collection" :modal="true" class="glass-dialog">
            <div class="flex flex-column gap-3">
                <label for="name" class="text-gray-300">Name</label>
                <InputText id="name" v-model="newCollectionName" autofocus class="glass-input" />
            </div>
            <template #footer>
                <Button label="Cancel" text @click="showCreateDialog = false" class="text-gray-400 hover:text-white" />
                <Button label="Create" @click="createCollection" />
            </template>
        </Dialog>
    </div>
</template>

<style scoped>
.text-gradient {
    background: var(--app-grad-text, linear-gradient(90deg, #66fcf1, #d870ff));
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
}

/* Collection Card - Dark Glass */
.collection-card {
    background: rgba(20, 20, 20, 0.6);
    border: 1px solid rgba(255, 255, 255, 0.1);
    backdrop-filter: blur(10px);
    z-index: 1;
}

/* Hover Effect - Gradient Border & Glow */
.collection-card:hover {
    transform: translateY(-5px);
    border-color: transparent;
}

.collection-card::before {
    content: '';
    position: absolute;
    inset: -2px;
    background: var(--app-grad-hover);
    border-radius: inherit;
    z-index: -2;
    opacity: 0;
    filter: blur(8px);
    transition: opacity 0.3s ease;
}

.collection-card::after {
    content: '';
    position: absolute;
    inset: 0;
    background: #000000; /* Opaque black on hover to block fill */
    border-radius: inherit;
    z-index: -1;
    opacity: 0;
    transition: opacity 0.3s ease;
}

.collection-card:hover::before {
    opacity: 0.8;
}

.collection-card:hover::after {
    opacity: 1;
}

/* Dialog Styling (Duplicated from MetadataSidebar for consistency) */
:deep(.glass-dialog) {
    background: rgba(15, 15, 15, 0.95) !important;
    border: 1px solid rgba(255, 255, 255, 0.1) !important;
    box-shadow: 0 0 40px rgba(0,0,0,0.8) !important;
    backdrop-filter: blur(20px) !important;
    color: white !important;
}

:deep(.glass-dialog .p-dialog-header) {
    background: transparent !important;
    color: white !important;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
    padding: 1.5rem !important;
}

:deep(.glass-dialog .p-dialog-content) {
    background: transparent !important;
    color: white !important;
    padding: 1.5rem !important;
}

:deep(.glass-dialog .p-dialog-footer) {
    background: transparent !important;
    border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
    padding: 1.5rem !important;
}

/* Input Styling */
.glass-input {
    background: rgba(0,0,0,0.3) !important;
    border: 1px solid rgba(255,255,255,0.1) !important;
    color: white !important;
}
.glass-input:focus {
    box-shadow: none !important;
    border-image: var(--app-grad-hover) 1 !important;
    outline: none !important;
}
</style>