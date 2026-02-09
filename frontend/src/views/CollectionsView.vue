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
    <div class="collections-view">
        <div class="flex justify-content-between align-items-center mb-4">
            <h2 class="m-0">Collections</h2>
            <Button label="New Collection" icon="pi pi-plus" @click="showCreateDialog = true" />
        </div>

        <div class="grid">
            <div v-for="col in collections" :key="col" class="col-12 md:col-6 lg:col-4">
                <div class="surface-card p-4 shadow-2 border-round cursor-pointer hover:surface-hover">
                    <div class="text-xl font-bold mb-2">{{ col }}</div>
                    <div class="text-500">0 items</div> <!-- Placeholder for count -->
                </div>
            </div>
        </div>

        <Dialog v-model:visible="showCreateDialog" header="New Collection" :modal="true">
            <div class="flex flex-column gap-2">
                <label for="name">Name</label>
                <InputText id="name" v-model="newCollectionName" autofocus />
            </div>
            <template #footer>
                <Button label="Cancel" text @click="showCreateDialog = false" />
                <Button label="Create" @click="createCollection" />
            </template>
        </Dialog>
    </div>
</template>
