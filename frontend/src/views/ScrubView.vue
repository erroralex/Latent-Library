<script setup>
import { ref } from 'vue';
import axios from 'axios';
import FileUpload from 'primevue/fileupload';
import Button from 'primevue/button';
import Card from 'primevue/card';
import Toast from 'primevue/toast';
import { useToast } from 'primevue/usetoast';

const toast = useToast();
const uploadedFile = ref(null);
const previewUrl = ref(null);
const isProcessing = ref(false);

const onUpload = async (event) => {
    const file = event.files[0];
    const formData = new FormData();
    formData.append('file', file);

    try {
        const response = await axios.post('/api/scrub/upload', formData);
        uploadedFile.value = response.data; // Filename
        previewUrl.value = `http://localhost:8080/api/scrub/preview/${uploadedFile.value}`;
        toast.add({ severity: 'success', summary: 'Uploaded', detail: 'Image ready to scrub', life: 3000 });
    } catch (error) {
        toast.add({ severity: 'error', summary: 'Error', detail: 'Upload failed', life: 3000 });
    }
};

const scrubAndDownload = async () => {
    if (!uploadedFile.value) return;
    isProcessing.value = true;

    try {
        const response = await axios.post('/api/scrub/process', null, {
            params: { filename: uploadedFile.value },
            responseType: 'blob'
        });

        // Create download link
        const url = window.URL.createObjectURL(new Blob([response.data]));
        const link = document.createElement('a');
        link.href = url;

        // Extract filename from header or default
        const contentDisposition = response.headers['content-disposition'];
        let fileName = 'clean_image.png';
        if (contentDisposition) {
            const fileNameMatch = contentDisposition.match(/filename="?([^"]+)"?/);
            if (fileNameMatch.length === 2)
                fileName = fileNameMatch[1];
        }

        link.setAttribute('download', fileName);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        toast.add({ severity: 'success', summary: 'Success', detail: 'Metadata scrubbed & downloaded', life: 3000 });
    } catch (error) {
        toast.add({ severity: 'error', summary: 'Error', detail: 'Processing failed', life: 3000 });
    } finally {
        isProcessing.value = false;
    }
};

const clear = () => {
    uploadedFile.value = null;
    previewUrl.value = null;
};
</script>

<template>
    <div class="flex flex-column align-items-center justify-content-center h-full p-4">
        <Toast />

        <div class="text-center mb-5">
            <h1 class="text-4xl font-bold text-gradient mb-2">Metadata Scrubber</h1>
            <p class="text-secondary">Remove hidden metadata (EXIF, Prompts, Workflow) for privacy.</p>
        </div>

        <Card class="w-full max-w-30rem glass-panel">
            <template #content>
                <div v-if="!previewUrl" class="flex flex-column align-items-center gap-4 py-5">
                    <i class="pi pi-shield text-6xl text-primary opacity-50"></i>
                    <FileUpload mode="basic" name="file" :auto="true" customUpload @uploader="onUpload" accept="image/*"
                                chooseLabel="Select Image" class="p-button-outlined" />
                    <span class="text-sm text-muted">Supports PNG, JPG, WEBP</span>
                </div>

                <div v-else class="flex flex-column align-items-center gap-4">
                    <div class="relative border-round overflow-hidden shadow-4" style="max-height: 300px;">
                        <img :src="previewUrl" class="block max-w-full h-auto" style="max-height: 300px;" />
                    </div>

                    <div class="flex gap-2 w-full">
                        <Button label="Export Clean Copy" icon="pi pi-download"
                                @click="scrubAndDownload" :loading="isProcessing"
                                class="flex-grow-1 p-button-success" />
                        <Button icon="pi pi-times" @click="clear"
                                class="p-button-secondary p-button-outlined" tooltip="Clear" />
                    </div>
                </div>
            </template>
        </Card>
    </div>
</template>

<style scoped>
/* Scoped styles if needed, mostly using utility classes */
</style>
