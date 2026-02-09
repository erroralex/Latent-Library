<script setup>
import { ref, onMounted } from 'vue';
import axios from 'axios';
import ImageCard from '../components/ImageCard.vue';
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';

const images = ref([]);
const searchQuery = ref('');

const search = async () => {
    try {
        const response = await axios.get('http://localhost:8080/api/images/search', {
            params: { query: searchQuery.value }
        });
        images.value = response.data;
    } catch (error) {
        console.error('Search failed:', error);
    }
};

onMounted(() => {
    search();
});
</script>

<template>
    <div class="gallery-container">
        <div class="flex gap-2 mb-4">
            <span class="p-input-icon-left flex-grow-1">
                <i class="pi pi-search" />
                <InputText v-model="searchQuery" placeholder="Search prompts, models, tags..." class="w-full" @keyup.enter="search" />
            </span>
            <Button label="Search" @click="search" />
        </div>

        <div class="grid grid-nogutter">
            <div v-for="path in images" :key="path" class="col-12 sm:col-6 md:col-4 lg:col-3 xl:col-2 p-2">
                <ImageCard :path="path" />
            </div>
        </div>
    </div>
</template>
