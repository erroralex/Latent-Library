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
 * - **Secure Upload:** Handles temporary image staging on the backend for processing.
 * - **Visual Preview:** Displays the uploaded image using an authenticated URL to confirm
 *   selection before scrubbing.
 * - **Metadata Stripping:** Triggers a backend process that re-encodes the image without
 *   metadata chunks.
 * - **Automated Download:** Facilitates the immediate download of the processed, clean
 *   image file with its original filename preserved.
 * - **Drag-and-Drop:** Supports dragging files directly onto the upload area.
 */
import {ref} from 'vue';
import api, {authenticatedUrl} from '@/services/api';
import Button from 'primevue/button';
import Card from 'primevue/card';
import {useToast} from 'primevue/usetoast';

const toast = useToast();
const uploadedFile = ref(null);
const previewUrl = ref(null);
const isProcessing = ref(false);
const isDragging = ref(false);
const fileInput = ref(null);

const handleFileSelect = (event) => {
  const file = event.target.files[0];
  if (file) processUpload(file);
};

const handleDrop = (event) => {
  isDragging.value = false;
  const file = event.dataTransfer.files[0];
  if (file) processUpload(file);
};

const processUpload = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await api.post('/scrub/upload', formData);
    uploadedFile.value = response.data;
    previewUrl.value = authenticatedUrl(`/api/scrub/preview/${uploadedFile.value}`);
    toast.add({severity: 'success', summary: 'Uploaded', detail: 'Image ready to scrub', life: 3000});
  } catch (error) {
    console.error("Upload failed", error);
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to upload image', life: 3000});
  }
};

const scrubAndDownload = async () => {
  if (!uploadedFile.value) return;
  isProcessing.value = true;

  try {
    const response = await api.post('/scrub/process', null, {
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
      if (fileNameMatch && fileNameMatch.length === 2)
        fileName = fileNameMatch[1];
    }

    link.setAttribute('download', fileName);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    toast.add({severity: 'success', summary: 'Success', detail: 'Metadata scrubbed & downloaded', life: 3000});
  } catch (error) {
    console.error("Scrubbing failed", error);
    toast.add({severity: 'error', summary: 'Error', detail: 'Failed to process image', life: 3000});
  } finally {
    isProcessing.value = false;
  }
};

const clear = () => {
  uploadedFile.value = null;
  previewUrl.value = null;
  if (fileInput.value) fileInput.value.value = '';
};
</script>

<template>
  <div class="flex flex-column align-items-center justify-content-center h-full p-4 scrub-view-bg">
    <div class="text-center mb-5">
      <h1 class="text-4xl font-bold text-gradient mb-2">Metadata Scrubber</h1>
      <p class="text-gray-400">Remove hidden metadata (EXIF, Prompts, Workflow) for privacy.</p>
    </div>

    <Card class="w-full max-w-30rem glass-panel">
      <template #content>
        <div v-if="!previewUrl"
             class="drop-zone flex flex-column align-items-center gap-4 py-5 cursor-pointer transition-all transition-duration-300 relative border-round"
             :class="{ 'drop-zone-active': isDragging }"
             @click="fileInput.click()"
             @dragover.prevent
             @dragenter="isDragging = true"
             @dragleave="isDragging = false"
             @drop.prevent="handleDrop">

          <input type="file" ref="fileInput" class="hidden" accept="image/*" @change="handleFileSelect"/>

          <i class="pi pi-shield text-6xl text-gradient opacity-80 pointer-events-none"></i>
          <div class="text-center pointer-events-none">
            <div class="font-bold text-xl mb-1 text-white">Drop Image Here</div>
            <div class="text-gray-400 text-sm">or click to browse</div>
          </div>
          <span class="text-xs text-gray-500 mt-2 pointer-events-none">Supports PNG, JPG, WEBP</span>
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
.scrub-view-bg {
  background: transparent;
}

.text-gradient {
  background: var(--grad-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.glass-panel {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-light) !important;
  backdrop-filter: var(--glass-blur) !important;
  box-shadow: var(--shadow-panel) !important;
}

:deep(.p-card-body) {
  padding: 1.5rem;
}

/* Drop Zone Styling matching ComparatorView */
.drop-zone {
  background: var(--bg-input);
  border: 2px dashed var(--border-input);
  position: relative;
  z-index: 1;
}

.drop-zone:hover, .drop-zone-active {
  background: var(--bg-card);
  border-color: var(--accent-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}

.drop-zone-active {
  box-shadow: 0 0 30px var(--accent-primary);
}
</style>
