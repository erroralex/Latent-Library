import { defineStore } from 'pinia';
import axios from 'axios';

/**
 * @file browser.js
 * @description Pinia store for managing the state of the Image Browser. This store handles
 * file lists, selection, metadata fetching, filtering, and view modes. It acts as the
 * central data hub for all browser-related UI components.
 */
export const useBrowserStore = defineStore('browser', {
    state: () => ({
        files: [],
        selectedFile: null,
        viewMode: 'browser',
        searchQuery: '',
        isLoading: false,
        currentMetadata: {},
        currentRating: 0,
        currentTags: [],
        cardSize: 160,
        isSidebarOpen: false,
        
        availableModels: [],
        availableSamplers: [],
        availableLoras: [],
        selectedModel: null,
        selectedSampler: null,
        selectedLora: null,
        selectedRating: null,

        lastFolderPath: null,
    }),

    actions: {
        async initialize() {
            await this.loadFilters();
            this.lastFolderPath = localStorage.getItem('lastFolder');
            if (this.lastFolderPath) {
                await this.loadFolder(this.lastFolderPath);
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
                this.searchQuery = '';
                
                this.lastFolderPath = path;
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

        async search(query, focusImage = false) {
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
                
                if (this.files.length > 0) {
                    this.selectFile(this.files[0]);
                } else {
                    this.selectedFile = null;
                    this.currentMetadata = {};
                }

                if (focusImage) {
                    this.imageFocusRequested = true;
                }

            } catch (error) {
                console.error('Search failed:', error);
            } finally {
                this.isLoading = false;
            }
        },

        clearSearch() {
            this.searchQuery = '';
            const isAnyFilterActive = this.selectedModel || this.selectedSampler || this.selectedLora || this.selectedRating;
            if (!isAnyFilterActive && this.lastFolderPath) {
                this.loadFolder(this.lastFolderPath);
            }
        },
        
        setFilter(type, value) {
            if (value === null) {
                if (type === 'model') this.selectedModel = null;
                if (type === 'sampler') this.selectedSampler = null;
                if (type === 'lora') this.selectedLora = null;
                if (type === 'rating') this.selectedRating = null;

                const isAnyFilterActive = this.selectedModel || this.selectedSampler || this.selectedLora || this.selectedRating || this.searchQuery;

                if (!isAnyFilterActive && this.lastFolderPath) {
                    this.loadFolder(this.lastFolderPath);
                } else {
                    this.search(this.searchQuery);
                }
            } else {
                if (type === 'model') this.selectedModel = value;
                if (type === 'sampler') this.selectedSampler = value;
                if (type === 'lora') this.selectedLora = value;
                if (type === 'rating') this.selectedRating = value;
                
                this.search(this.searchQuery);
            }
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
            
            if (Math.abs(direction) === 1) {
                if (newIndex < 0) newIndex = this.files.length - 1;
                if (newIndex >= this.files.length) newIndex = 0;
            } else {
                if (newIndex < 0) newIndex = 0;
                if (newIndex >= this.files.length) newIndex = this.files.length - 1;
            }
            
            this.selectFile(this.files[newIndex]);
        }
    }
});
