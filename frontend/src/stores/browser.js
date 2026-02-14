import {defineStore} from 'pinia';
import axios from 'axios';
import api from '../services/api';

/**
 * @file browser.js
 * @description The central state management hub for the Image Browser application using Pinia.
 *
 * This store manages the global state for image browsing, including file lists, selection state,
 * search criteria, and UI configuration. It acts as the primary interface between the frontend
 * views and the backend API, handling complex data orchestration such as paginated searching,
 * metadata retrieval, and theme persistence.
 *
 * Key Responsibilities:
 * - **State Management:** Maintains the current list of images, multi-selection sets,
 *   and active filters (Model, Sampler, LoRA, Rating).
 * - **Data Orchestration:** Handles asynchronous operations for scanning folders,
 *   fetching paginated search results, and loading image-specific metadata.
 * - **UI Configuration:** Manages global UI state such as view modes (Gallery vs. Browser),
 *   sidebar visibility, and application-wide themes.
 * - **Backend Synchronization:** Implements a robust initialization sequence that polls
 *   the backend for readiness and restores the user's last visited folder and theme.
 * - **Error Handling:** Tracks critical backend connection failures to trigger global error views.
 */

const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

export const useBrowserStore = defineStore('browser', {
    state: () => ({
        files: [],
        selectedFile: null,
        selectedFiles: new Set(),
        viewMode: 'browser',
        searchQuery: '',
        isLoading: false,
        backendError: false,
        currentMetadata: {},
        currentRating: 0,
        currentTags: [],
        cardSize: 160,
        isSidebarOpen: false,
        isTaggerOpen: false,
        currentTheme: 'neon',
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
        includeAiTags: true,
        page: 0,
        pageSize: 50,
        hasMore: true,
        isFetchingMore: false
    }),

    actions: {
        async waitForBackend(retries = 20) {
            for (let i = 0; i < retries; i++) {
                try {
                    await axios.get('/api/images/filters', {timeout: 2000});
                    return true;
                } catch (e) {
                    console.debug(`Backend not ready, retrying (${i + 1}/${retries})...`);
                    await delay(1000);
                }
            }
            throw new Error('Backend connection timeout: Server failed to start.');
        },

        async initialize() {
            this.isLoading = true;
            this.backendError = false;
            try {
                await this.waitForBackend();
                await this.loadFilters();
                await this.loadTheme();

                try {
                    const res = await api.get('/system/last-folder');
                    if (res.data && res.data.path) {
                        this.lastFolderPath = res.data.path;
                        await this.loadFolder(this.lastFolderPath);
                    }
                } catch (e) {
                    console.warn("Failed to load last folder setting", e);
                }

            } catch (error) {
                console.error("Initialization failed:", error);
                this.backendError = true;
            } finally {
                this.isLoading = false;
            }
        },

        async loadTheme() {
            try {
                const res = await api.get('/system/theme');
                if (res.data && res.data.theme) {
                    this.setTheme(res.data.theme, false);
                }
            } catch (e) {
                console.warn("Failed to load theme setting", e);
            }
        },

        async setTheme(themeName, save = true) {
            this.currentTheme = themeName;
            document.body.className = '';
            document.body.classList.add(`theme-${themeName}`);

            if (save) {
                try {
                    await api.post('/system/theme', null, {params: {theme: themeName}});
                } catch (e) {
                    console.error("Failed to save theme setting", e);
                }
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
                    params: {path}
                });
                this.files = response.data;
                this.searchQuery = '';
                this.lastFolderPath = path;

                if (this.files.length > 0) {
                    this.selectFile(this.files[0]);
                } else {
                    this.selectedFile = null;
                    this.selectedFiles.clear();
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
                    this.selectedFiles.clear();
                    this.currentMetadata = {};
                }
            } catch (error) {
                console.error('Failed to load collection:', error);
                this.files = [];
                this.selectedFile = null;
                this.selectedFiles.clear();
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
                    this.selectedFiles.clear();
                    this.currentMetadata = {};
                }
            } catch (error) {
                console.error('Search failed:', error);
            } finally {
                this.isLoading = false;
            }
        },

        toggleIncludeAiTags() {
            this.search(this.searchQuery);
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
                    includeAiTags: this.includeAiTags,
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

        async selectFile(file, multiSelect = false, rangeSelect = false) {
            const path = file.path || file;

            if (multiSelect) {
                if (this.selectedFiles.has(path)) {
                    this.selectedFiles.delete(path);
                    if (this.selectedFile === path) {
                        const iterator = this.selectedFiles.values();
                        const next = iterator.next();
                        this.selectedFile = next.done ? null : next.value;
                    }
                } else {
                    this.selectedFiles.add(path);
                    this.selectedFile = path;
                }
            } else if (rangeSelect && this.selectedFile) {
                const startIdx = this.files.findIndex(f => f.path === this.selectedFile);
                const endIdx = this.files.findIndex(f => f.path === path);

                if (startIdx !== -1 && endIdx !== -1) {
                    const min = Math.min(startIdx, endIdx);
                    const max = Math.max(startIdx, endIdx);
                    for (let i = min; i <= max; i++) {
                        this.selectedFiles.add(this.files[i].path);
                    }
                    this.selectedFile = path;
                }
            } else {
                this.selectedFiles.clear();
                this.selectedFiles.add(path);
                this.selectedFile = path;
            }

            if (this.selectedFile) {
                this.fetchMetadata(this.selectedFile);
                this.preloadAdjacentImages(this.selectedFile);
            } else {
                this.currentMetadata = {};
                this.currentRating = 0;
            }
        },

        preloadAdjacentImages(currentPath) {
            if (!this.files.length) return;
            const currentIndex = this.files.findIndex(f => f.path === currentPath);
            if (currentIndex === -1) return;

            const nextIndex = (currentIndex + 1) % this.files.length;
            const prevIndex = (currentIndex - 1 + this.files.length) % this.files.length;

            const preload = (path) => {
                const img = new Image();
                img.src = `/api/images/content?path=${encodeURIComponent(path)}`;
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
                    params: {path}
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
                    params: {path: this.selectedFile, rating}
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

        toggleTagger() {
            this.isTaggerOpen = !this.isTaggerOpen;
        },

        setTaggerOpen(isOpen) {
            this.isTaggerOpen = isOpen;
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
