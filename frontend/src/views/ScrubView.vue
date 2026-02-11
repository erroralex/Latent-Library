<script setup>
/**
 * @file ScrubView.vue
 * @description A specialized utility view for stripping sensitive metadata from AI-generated images.
 *
 * This view provides a secure interface for users to upload images and generate "clean" versions.
 * It targets the removal of embedded generation parameters (prompts, seeds, workflow JSON) and
 * standard EXIF data to ensure privacy before sharing images publicly.
 *
 * Key functionalities:
 * - Secure Upload: Handles temporary image staging on the backend for processing.
 * - Visual Preview: Displays the uploaded image to confirm selection before scrubbing.
 * - Metadata Stripping: Triggers a backend process that re-encodes the image without metadata chunks.
 * - Automated Download: Facilitates the immediate download of the processed, clean image file.
 */
import {ref} from 'vue';
import axios from 'axios';
import FileUpload from 'primevue/fileupload';
import Button from 'primevue/button';
import Card from 'primevue/card';
import Toast from 'primevue/toast';
import {useToast} from 'primevue/usetoast';

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
    uploadedFile.value = response.data;
    previewUrl.value = `http://localhost:8080/api/scrub/preview/${uploadedFile.value}`;
    toast.add({severity: 'success', summary: 'Uploaded', detail: 'Image ready to scrub', life: 3000});
  } catch (error) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Upload failed', life: 3000});
  }
};

const scrubAndDownload = async () => {
  if (!uploadedFile.value) return;
  isProcessing.value = true;

  try {
    const response = await axios.post('/api/scrub/process', null, {
      params: {filename: uploadedFile.value},
      responseType: 'blob'
    });

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;

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

    toast.add({severity: 'success', summary: 'Success', detail: 'Metadata scrubbed & downloaded', life: 3000});
  } catch (error) {
    toast.add({severity: 'error', summary: 'Error', detail: 'Processing failed', life: 3000});
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
    <Toast/>

    <div class="text-center mb-5">
      <h1 class="text-4xl font-bold text-gradient mb-2">Metadata Scrubber</h1>
      <p class="text-gray-400">Remove hidden metadata (EXIF, Prompts, Workflow) for privacy.</p>
    </div>

    <Card class="w-full max-w-30rem glass-panel">
      <template #content>
        <div v-if="!previewUrl" class="flex flex-column align-items-center gap-4 py-5">
          <i class="pi pi-shield text-6xl text-gradient opacity-80"></i>
          <FileUpload mode="basic" name="file" :auto="true" customUpload @uploader="onUpload" accept="image/*"
                      chooseLabel="Select Image" class="p-button-outlined"/>
          <span class="text-sm text-gray-500">Supports PNG, JPG, WEBP</span>
        </div>

        <div v-else class="flex flex-column align-items-center gap-4">
          <div class="relative border-round overflow-hidden shadow-4" style="max-height: 300px;">
            <img :src="previewUrl" class="block max-w-full h-auto" style="max-height: 300px;"/>
          </div>

          <div class="flex gap-2 w-full">
            <Button label="Export Clean Copy" icon="pi pi-download"
                    @click="scrubAndDownload" :loading="isProcessing"
                    class="flex-grow-1"/>
            <Button icon="pi pi-times" @click="clear"
                    class="p-button-secondary p-button-outlined" tooltip="Clear"/>
          </div>
        </div>
      </template>
    </Card>
  </div>
</template>

<style scoped>
.text-gradient {
  background: var(--app-grad-text, linear-gradient(90deg, #66fcf1, #d870ff));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.glass-panel {
  background: rgba(20, 20, 20, 0.6) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  backdrop-filter: blur(10px) !important;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5) !important;
}

:deep(.p-card-body) {
  padding: 1.5rem;
}
</style>