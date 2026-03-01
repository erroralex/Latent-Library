import {defineStore} from 'pinia';
import api, {authenticatedUrl, patchImageMetadata} from '../services/api';

/**
 * @file browser.js
 * @description The central state management hub for the Latent Library application, powered by Pinia.
 *
 * This store orchestrates the global application state, providing a reactive and unified interface
 * for all major functional areas:
 * 
 * - **File & Collection Management:** Tracks the active set of images, handles selection states
 *   (including complex range and multi-select), and manages pagination for large datasets.
 * - **Search & Discovery:** Orchestrates multi-criteria filtering across technical metadata
 *   (models, samplers, ratings) and executes Full-Text Search (FTS) queries against the backend.
 * - **Metadata & User Overrides:** Manages the retrieval of technical metadata and facilitates
 *   the persistence of user-defined notes and prompt/model overrides.
 * - **System & UI State:** Monitors backend connectivity, manages theme preferences, and
 *   controls the visibility of sidebars and utility panels.
 * - **Navigation Logic:** Implements sequential browsing, folder traversal, and automatic
 *   refresh polling to keep the UI in sync with the physical file system.
 * - **Indexing Status:** Polls the backend for real-time progress updates during massive folder scans.
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
        recursiveView: false,
        autoShowLatest: false,
        page: 0,
        pageSize: 100,
        totalPages: 0,
        hasMore: true,
        isFetchingMore: false,
        refreshInterval: null,
        isIndexing: false,
        totalFilesToScan: 0,
        filesProcessed: 0,
        indexingPollInterval: null
    }),

    getters: {
        formattedBreadcrumb: (state) => {
            let targetPath = state.lastFolderPath;
            
            if (state.selectedFile) {
                const separator = state.selectedFile.includes('/') ? '/' : '\\';
                const lastSlash = state.selectedFile.lastIndexOf(separator);
                if (lastSlash !== -1) {
                    targetPath = state.selectedFile.substring(0, lastSlash);
                }
            }

            if (!targetPath) return '';
            
            const parts = targetPath.split(/[/\\]/);
            if (parts.length <= 3) return targetPath;
            return '... / ' + parts.slice(-3).join(' / ');
        }
    },

    actions: {
        async waitForBackend(retries = 20) {
            for (let i = 0; i < retries; i++) {
                try {
                    await api.get('/images/filters', {timeout: 2000});
                    return true;
                } catch (e) {
                    if (e.response && e.response.status === 401) {
                        throw new Error("Security Handshake Failed: Unauthorized.");
                    }
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
                        await this.loadInitialFolder(this.lastFolderPath);
                    }
                } catch (e) {
                    console.warn("Failed to load last folder setting", e);
                }

                this.startAutoRefresh();
                this.startIndexingPoll();

            } catch (error) {
                console.error("Initialization failed:", error);
                this.backendError = true;
            } finally {
                this.isLoading = false;
            }
        },

        startAutoRefresh() {
            if (this.refreshInterval) clearInterval(this.refreshInterval);
            this.refreshInterval = setInterval(() => {
                if (!this.isLoading && !this.isFetchingMore && this.lastFolderPath && !this.searchQuery && !this.activeCollection) {
                    this.refreshCurrentFolder();
                }
            }, 3000);
        },

        startIndexingPoll() {
            if (this.indexingPollInterval) clearInterval(this.indexingPollInterval);
            
            this.indexingPollInterval = setInterval(async () => {
                try {
                    const res = await api.get('/library/indexing-status');
                    const status = res.data;
                    
                    this.isIndexing = status.isIndexing;
                    if (status.isIndexing || status.totalFiles > 0) {
                        this.totalFilesToScan = status.totalFiles;
                        this.filesProcessed = status.processedFiles;
                    }
                } catch (e) {
                    console.debug("Indexing poll failed (ignoring):", e.message);
                }
            }, 500);
        },

        stopIndexingPoll() {
            if (this.indexingPollInterval) {
                clearInterval(this.indexingPollInterval);
                this.indexingPollInterval = null;
            }
        },

        async refreshCurrentFolder() {
            if (!this.lastFolderPath) return;
            try {
                const response = await api.post('/library/scan', null, {
                    params: {
                        path: this.lastFolderPath,
                        recursive: this.recursiveView,
                        skipIndex: true,
                        page: 0,
                        size: this.pageSize
                    }
                });
                
                const newFiles = response.data.content;
                
                if (newFiles.length > 0 && this.files.length > 0 && newFiles[0].path !== this.files[0].path) {
                    if (this.autoShowLatest) {
                        this.files = newFiles;
                        this.page = 0;
                        this.totalPages = response.data.totalPages;
                        this.hasMore = !response.data.last;
                        this.selectFile(newFiles[0]);
                        this.setViewMode('browser');
                    }
                }
            } catch (error) {
                console.debug('Auto-refresh poll skipped:', error.message);
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

        async loadInitialFolder(path) {
            this.isLoading = true;
            this.files = [];
            this.page = 0;
            this.hasMore = true;
            this.activeCollection = null;
            this.searchQuery = '';
            this.lastFolderPath = path;

            try {
                const response = await api.post('/library/scan', null, {
                    params: {
                        path,
                        recursive: this.recursiveView,
                        page: 0,
                        size: this.pageSize
                    }
                });
                
                const pageData = response.data;
                this.files = pageData.content;
                this.totalPages = pageData.totalPages;
                this.hasMore = !pageData.last;

                if (this.files.length > 0) {
                    this.selectFile(this.files[0]);
                } else {
                    this.selectedFile = null;
                    this.selectedFiles.clear();
                    this.currentMetadata = {};
                }
            } catch (error) {
                console.error('Failed to load folder:', error);
                this.files = [];
            } finally {
                this.isLoading = false;
            }
        },

        async loadMoreImages() {
            if (this.isFetchingMore || !this.hasMore || this.isLoading) return;
            
            this.isFetchingMore = true;
            const nextPage = this.page + 1;

            try {
                let response;
                
                if (this.activeCollection) {
                    response = await api.get('/images/search', {
                        params: {
                            collection: this.activeCollection,
                            page: nextPage,
                            size: this.pageSize
                        }
                    });
                } else if (this.searchQuery || this.selectedModel || this.selectedRating) {
                    response = await api.get('/images/search', {
                        params: {
                            query: this.searchQuery,
                            model: this.selectedModel,
                            sampler: this.selectedSampler,
                            lora: this.selectedLora,
                            rating: this.selectedRating,
                            includeAiTags: this.includeAiTags,
                            page: nextPage,
                            size: this.pageSize
                        }
                    });
                } else {
                    response = await api.post('/library/scan', null, {
                        params: {
                            path: this.lastFolderPath,
                            recursive: this.recursiveView,
                            skipIndex: true,
                            page: nextPage,
                            size: this.pageSize
                        }
                    });
                }

                const newFiles = this.activeCollection || this.searchQuery ? response.data : response.data.content;
                const isLast = this.activeCollection || this.searchQuery ? (newFiles.length < this.pageSize) : response.data.last;

                if (newFiles.length > 0) {
                    this.files.push(...newFiles);
                    this.page = nextPage;
                }
                
                this.hasMore = !isLast;

            } catch (error) {
                console.error('Load more failed:', error);
            } finally {
                this.isFetchingMore = false;
            }
        },

        async loadFolder(path) {
            return this.loadInitialFolder(path);
        },

        async toggleRecursiveView() {
            this.recursiveView = !this.recursiveView;
            if (this.lastFolderPath) {
                await this.loadInitialFolder(this.lastFolderPath);
            }
        },

        toggleAutoShowLatest() {
            this.autoShowLatest = !this.autoShowLatest;
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
            await this.loadMoreImages();
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
                this.loadInitialFolder(this.lastFolderPath);
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
                    this.loadInitialFolder(this.lastFolderPath);
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
                img.src = authenticatedUrl(`/api/images/content?path=${encodeURIComponent(path)}`);
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

        async updateMetadata(payload) {
            if (!this.currentMetadata || !this.currentMetadata.id) {
                console.error("Cannot update metadata: No image ID available.");
                return;
            }
            
            try {
                await patchImageMetadata(this.currentMetadata.id, payload);
                
                if (payload.userNotes !== undefined) this.currentMetadata.user_notes = payload.userNotes;
                if (payload.customPrompt !== undefined) this.currentMetadata.custom_prompt = payload.customPrompt;
                if (payload.customNegativePrompt !== undefined) this.currentMetadata.custom_negative_prompt = payload.customNegativePrompt;
                if (payload.customModel !== undefined) this.currentMetadata.custom_model = payload.customModel;
                
            } catch (error) {
                console.error("Failed to update metadata:", error);
                throw error;
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

            if (direction === 1 && newIndex >= this.files.length - 5 && this.hasMore && !this.isFetchingMore) {
                this.loadMoreImages();
            }

            this.selectFile(this.files[newIndex]);
        }
    }
});
