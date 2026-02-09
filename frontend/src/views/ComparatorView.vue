<script setup>
import { ref, onUnmounted } from 'vue';
import Button from 'primevue/button';

const imageA = ref(null);
const imageB = ref(null);
const sliderPosition = ref(50); // Percentage
const containerRef = ref(null);

// Cleanup ObjectURLs to avoid memory leaks
onUnmounted(() => {
    if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
    if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
});

const handleFileSelect = (event, target) => {
    const file = event.target.files[0];
    if (!file) return;

    const url = URL.createObjectURL(file);
    if (target === 'A') {
        if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
        imageA.value = url;
    } else {
        if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
        imageB.value = url;
    }
};

const handleDrop = (event, target) => {
    const file = event.dataTransfer.files[0];
    if (!file) return;

    const url = URL.createObjectURL(file);
    if (target === 'A') {
        if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
        imageA.value = url;
    } else {
        if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
        imageB.value = url;
    }
};

const updateSlider = (event) => {
    if (!containerRef.value) return;
    const rect = containerRef.value.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const percent = (x / rect.width) * 100;
    sliderPosition.value = Math.min(100, Math.max(0, percent));
};

const reset = () => {
    if (imageA.value?.startsWith('blob:')) URL.revokeObjectURL(imageA.value);
    if (imageB.value?.startsWith('blob:')) URL.revokeObjectURL(imageB.value);
    imageA.value = null;
    imageB.value = null;
    sliderPosition.value = 50;
};
</script>

<template>
    <div class="comparator-view h-full flex flex-column p-4 overflow-hidden">
        <div class="text-center mb-4 flex-shrink-0">
            <h1 class="text-2xl font-bold m-0 mb-2">Comparator</h1>
            <p class="text-500 m-0">Compare images by dropping them into the slots below.</p>
        </div>

        <!-- Comparison Area -->
        <div v-if="imageA && imageB" class="flex-grow-1 flex flex-column overflow-hidden relative">
            <div class="relative flex-grow-1 bg-black-alpha-90 border-round overflow-hidden select-none cursor-crosshair"
                 ref="containerRef"
                 @mousemove="updateSlider"
                 @touchmove="updateSlider">

                <!-- Image B (Background/Right) -->
                <img :src="imageB"
                     class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
                     style="object-fit: contain;" />

                <!-- Image A (Foreground/Left) - Clipped via CSS clip-path -->
                <img :src="imageA"
                     class="absolute top-0 left-0 w-full h-full select-none pointer-events-none"
                     style="object-fit: contain;"
                     :style="{ clipPath: `inset(0 ${100 - sliderPosition}% 0 0)` }" />

                <!-- Slider Handle -->
                <div class="absolute top-0 bottom-0 w-2px bg-white shadow-4 pointer-events-none"
                     :style="{ left: sliderPosition + '%' }">
                     <div class="absolute top-50 left-50 -ml-2 -mt-2 w-2rem h-2rem border-circle bg-white flex align-items-center justify-content-center shadow-2">
                        <i class="pi pi-arrows-h text-900"></i>
                     </div>
                </div>
            </div>

            <div class="flex justify-content-center mt-3 flex-shrink-0">
                <Button label="Reset" severity="secondary" @click="reset" />
            </div>
        </div>

        <!-- Drop Zones -->
        <div v-else class="flex-grow-1 flex align-items-center justify-content-center gap-4">
            <!-- Slot A -->
            <div class="drop-zone surface-card border-2 border-dashed surface-border border-round p-4 flex flex-column align-items-center justify-content-center cursor-pointer hover:surface-hover transition-colors transition-duration-200 relative"
                 @click="$refs.fileInputA.click()"
                 @dragover.prevent
                 @drop.prevent="handleDrop($event, 'A')">

                <input type="file" ref="fileInputA" class="hidden" accept="image/*" @change="handleFileSelect($event, 'A')" />

                <div v-if="imageA" class="w-full h-full absolute top-0 left-0 p-2">
                    <img :src="imageA" class="w-full h-full object-contain border-round" />
                </div>
                <div v-else class="text-center">
                    <i class="pi pi-image text-5xl text-primary mb-3"></i>
                    <div class="font-bold text-xl mb-1">Image A (Left)</div>
                    <div class="text-500">Drop or Click</div>
                </div>
            </div>

            <!-- Slot B -->
            <div class="drop-zone surface-card border-2 border-dashed surface-border border-round p-4 flex flex-column align-items-center justify-content-center cursor-pointer hover:surface-hover transition-colors transition-duration-200 relative"
                 @click="$refs.fileInputB.click()"
                 @dragover.prevent
                 @drop.prevent="handleDrop($event, 'B')">

                <input type="file" ref="fileInputB" class="hidden" accept="image/*" @change="handleFileSelect($event, 'B')" />

                <div v-if="imageB" class="w-full h-full absolute top-0 left-0 p-2">
                    <img :src="imageB" class="w-full h-full object-contain border-round" />
                </div>
                <div v-else class="text-center">
                    <i class="pi pi-image text-5xl text-primary mb-3"></i>
                    <div class="font-bold text-xl mb-1">Image B (Right)</div>
                    <div class="text-500">Drop or Click</div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.drop-zone {
    width: 300px;
    height: 300px;
}
</style>
