import { defineStore } from 'pinia';
import axios from 'axios'; // Kept ONLY for silent startup polling
import api from '../services/api'; // Use centralized API service for everything else

/**
 * @file browser.js
 * @description The central state management hub for the Image Browser application.
 */

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

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
        activeCollection: null,
        lastFolderPath: null,
        navRefreshKey: 0,
        collectionToEdit: null,

        page: 0,
        pageSize: 50,
        hasMore: true,
        isFetchingMore: false
    }),

    actions: {
        /**
         * Polls the backend to ensure it is ready before attempting to load data.
         * Uses raw axios to avoid triggering global error toasts during polling.
         */
        async waitForBackend(retries = 20) {
            for (let i = 0; i < retries; i++) {
                try {
                    // Raw axios call to avoid global interceptor toasts
                    await axios.get('http://localhost:8080/api/images/filters');
                    return true;
                } catch (e) {
                    console.debug(`Backend not ready, retrying (${i + 1}/${retries})...`);
                    await delay(500);
                }
            }
            throw new Error('Backend connection timeout: Server failed to start.');
        },

        async initialize() {
            this.isLoading = true;
            try {
                await this.waitForBackend();

                await this.loadFilters();
                this.lastFolderPath = localStorage.getItem('lastFolder');
                if (this.lastFolderPath) {
                    await this.loadFolder(this.lastFolderPath);
                }
            } catch (error) {
                console.error("Initialization failed:", error);
                // Note: The global interceptor in api.js will handle the visual notification
            } finally {
                this.isLoading = false;
            }
        },

        refreshNav() {
            this.navRefreshKey++;
        },

        async loadFilters() {
            try {
                const res = await api.get('/images/filters');
                this.availableModels = ['All', ...res.data.models];
                this.availableSamplers = ['All', ...res.data.samplers];
                this.availableLoras = ['All', ...res.data.loras];
            } catch (e) {
                console.error("Failed to load filters", e);
            }
        },

        async loadFolder(path) {
            this.isLoading = true;
            this.files = [];
            this.page = 0;
            this.hasMore = false;
            this.activeCollection = null;

            try {
                const response = await api.post('/library/scan', null, {
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

        async loadCollection(collectionName) {
            this.isLoading = true;
            this.activeCollection = collectionName;
            this.searchQuery = '';
            this.files = [];
            this.page = 0;
            this.hasMore = true;

            try {
                await this.fetchPage();

                if (this.files.length > 0) {
                    this.selectFile(this.files[0]);
                    this.setViewMode('gallery');
                } else {
                    this.selectedFile = null;
                    this.currentMetadata = {};
                }
            } catch (error) {
                console.error('Failed to load collection:', error);
                this.files = [];
                this.selectedFile = null;
            } finally {
                this.isLoading = false;
            }
        },

        async search(query, focusImage = false) {
            this.isLoading = true;
            this.searchQuery = query;
            this.page = 0;
            this.files = [];
            this.hasMore = true;

            try {
                await this.fetchPage();

                if (this.files.length > 0) {
                    this.selectFile(this.files[0]);
                } else {
                    this.selectedFile = null;
                    this.currentMetadata = {};
                }

                if (focusImage) {
                    // Logic for focusing image if implemented in view
                }

            } catch (error) {
                console.error('Search failed:', error);
            } finally {
                this.isLoading = false;
            }
        },

        async loadMore() {
            if (this.isFetchingMore || !this.hasMore) return;
            this.isFetchingMore = true;
            this.page++;
            try {
                await this.fetchPage();
            } catch (error) {
                console.error('Load more failed:', error);
                this.page--;
            } finally {
                this.isFetchingMore = false;
            }
        },

        async fetchPage() {
            const response = await api.get('/images/search', {
                params: {
                    query: this.searchQuery,
                    model: this.selectedModel,
                    sampler: this.selectedSampler,
                    lora: this.selectedLora,
                    rating: this.selectedRating,
                    collection: this.activeCollection,
                    page: this.page,
                    size: this.pageSize
                }
            });

            const newFiles = response.data;
            if (newFiles.length < this.pageSize) {
                this.hasMore = false;
            }

            if (this.page === 0) {
                this.files = newFiles;
            } else {
                this.files = [...this.files, ...newFiles];
            }
        },

        clearSearch() {
            this.searchQuery = '';
            const isAnyFilterActive = this.selectedModel || this.selectedSampler || this.selectedLora || this.selectedRating || this.activeCollection;

            if (!isAnyFilterActive && this.lastFolderPath) {
                this.loadFolder(this.lastFolderPath);
            } else {
                this.search('');
            }
        },

        clearCollection() {
            this.activeCollection = null;
            this.clearSearch();
        },

        setFilter(type, value) {
            if (value === null) {
                if (type === 'model') this.selectedModel = null;
                if (type === 'sampler') this.selectedSampler = null;
                if (type === 'lora') this.selectedLora = null;
                if (type === 'rating') this.selectedRating = null;

                const isAnyFilterActive = this.selectedModel || this.selectedSampler || this.selectedLora || this.selectedRating || this.searchQuery || this.activeCollection;

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

        async selectFile(file) {
            const path = file.path || file;
            if (this.selectedFile === path) return;
            this.selectedFile = path;
            this.fetchMetadata(path);
            this.preloadAdjacentImages(path);
        },

        preloadAdjacentImages(currentPath) {
            if (!this.files.length) return;
            const currentIndex = this.files.findIndex(f => f.path === currentPath);
            if (currentIndex === -1) return;

            const nextIndex = (currentIndex + 1) % this.files.length;
            const prevIndex = (currentIndex - 1 + this.files.length) % this.files.length;

            // Note: Preloading uses raw Image object, does not use Axios/API service
            const preload = (path) => {
                const img = new Image();
                // Ensure this URL matches your backend serving strategy
                img.src = `http://localhost:8080/api/images/content?path=${encodeURIComponent(path)}`;
            };

            preload(this.files[nextIndex].path);
            preload(this.files[prevIndex].path);
        },

        async fetchMetadata(path) {
            if (!path) {
                this.currentMetadata = {};
                this.currentRating = 0;
                return;
            }
            try {
                const response = await api.get('/images/metadata', {
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
                await api.post('/images/rating', null, {
                    params: { path: this.selectedFile, rating }
                });
                this.currentRating = rating;

                const fileIndex = this.files.findIndex(f => f.path === this.selectedFile);
                if (fileIndex !== -1) {
                    this.files[fileIndex].rating = rating;
                }
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
            const currentIndex = this.files.findIndex(f => f.path === this.selectedFile);
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