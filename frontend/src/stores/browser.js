import { defineStore } from 'pinia';
import axios from 'axios';

/**
 * Pinia store for managing the state of the Image Browser.
 * Handles file lists, selection, metadata fetching, filtering, and view modes.
 * Acts as the central data source for the browser UI components.
 */
export const useBrowserStore = defineStore('browser', {
    state: () => ({
        files: [],
        selectedFile: null,
        viewMode: 'browser', // Default to browser view
        searchQuery: '',
        isLoading: false,
        currentMetadata: {},
        currentRating: 0,
        currentTags: [],
        cardSize: 160,
        isSidebarOpen: false, // Control sidebar visibility
        
        // Filters
        availableModels: [],
        availableSamplers: [],
        availableLoras: [],
        selectedModel: null,
        selectedSampler: null,
        selectedLora: null,
        selectedRating: null
    }),

    actions: {
        async initialize() {
            await this.loadFilters();
            const lastFolder = localStorage.getItem('lastFolder');
            if (lastFolder) {
                await this.loadFolder(lastFolder);
            }
        },
        
        async loadFilters() {
            try {
                const res = await axios.get('/api/images/filters');
                this.availableModels = ['All', ...res.data.models];
                this.availableSamplers = ['All', ...res.data.samplers];
                this.availableLoras = ['All', ...res.data.loras];
            } catch (e) {
                console.error("Failed to load filters", e);
            }
        },

        async loadFolder(path) {
            this.isLoading = true;
            try {
                const response = await axios.post('/api/library/scan', null, {
                    params: { path }
                });
                this.files = response.data;
                this.searchQuery = ''; // Clear search when loading new folder
                
                // Persist last folder
                localStorage.setItem('lastFolder', path);

                if (this.files.length > 0) {
                    this.selectFile(this.files[0]);
                } else {
                    this.selectedFile = null;
                    this.currentMetadata = {};
                }
            } catch (error) {
                console.error('Failed to load folder:', error);
            } finally {
                this.isLoading = false;
            }
        },

        async search(query) {
            this.isLoading = true;
            this.searchQuery = query;
            try {
                const response = await axios.get('/api/images/search', {
                    params: { 
                        query: query,
                        model: this.selectedModel,
                        sampler: this.selectedSampler,
                        lora: this.selectedLora,
                        rating: this.selectedRating
                    }
                });
                this.files = response.data;
                // If we have files and none selected, select the first one
                if (this.files.length > 0 && !this.selectedFile) {
                    this.selectFile(this.files[0]);
                }
            } catch (error) {
                console.error('Search failed:', error);
            } finally {
                this.isLoading = false;
            }
        },
        
        setFilter(type, value) {
            if (type === 'model') this.selectedModel = value;
            if (type === 'sampler') this.selectedSampler = value;
            if (type === 'lora') this.selectedLora = value;
            if (type === 'rating') this.selectedRating = value;
            
            this.search(this.searchQuery);
        },

        async selectFile(path) {
            if (this.selectedFile === path) return;
            this.selectedFile = path;
            this.fetchMetadata(path);
        },

        async fetchMetadata(path) {
            if (!path) {
                this.currentMetadata = {};
                this.currentRating = 0;
                return;
            }
            try {
                const response = await axios.get('/api/images/metadata', {
                    params: { path }
                });
                this.currentMetadata = response.data;
                // Extract rating from response if present, otherwise default to 0
                this.currentRating = response.data.rating || 0;
            } catch (error) {
                console.error('Metadata fetch failed:', error);
                this.currentMetadata = {};
                this.currentRating = 0;
            }
        },

        async setRating(rating) {
            if (!this.selectedFile) return;
            try {
                await axios.post('/api/images/rating', null, {
                    params: { path: this.selectedFile, rating }
                });
                this.currentRating = rating;
            } catch (error) {
                console.error('Failed to set rating:', error);
            }
        },

        setViewMode(mode) {
            this.viewMode = mode;
        },

        toggleSidebar() {
            this.isSidebarOpen = !this.isSidebarOpen;
        },

        setSidebarOpen(isOpen) {
            this.isSidebarOpen = isOpen;
        },

        navigate(direction) {
            if (this.files.length === 0) return;
            const currentIndex = this.files.indexOf(this.selectedFile);
            if (currentIndex === -1) {
                this.selectFile(this.files[0]);
                return;
            }
            
            let newIndex = currentIndex + direction;
            
            // Handle wrapping and clamping
            if (Math.abs(direction) === 1) {
                // Simple wrap for single step
                if (newIndex < 0) newIndex = this.files.length - 1;
                if (newIndex >= this.files.length) newIndex = 0;
            } else {
                // Clamp for jumps (like grid navigation)
                if (newIndex < 0) newIndex = 0;
                if (newIndex >= this.files.length) newIndex = this.files.length - 1;
            }
            
            this.selectFile(this.files[newIndex]);
        }
    }
});
