/**
 * @file index.js
 * @description The primary routing configuration for the AI Toolbox frontend application.
 *
 * This module defines the navigation structure of the application using Vue Router. It maps
 * URL paths to specific view components, enabling a single-page application (SPA) experience.
 * The router is configured with HTML5 history mode and supports lazy-loading for secondary
 * views to optimize initial bundle size.
 *
 * Key Routes:
 * - **Browser (`/`):** The default landing page, hosting the main image exploration interface.
 * - **Collections (`/collections`):** Management interface for static and smart image groupings.
 * - **Comparator (`/comparator`):** Side-by-side visual comparison tool for images.
 * - **Scrubber (`/scrub`):** Utility for removing sensitive metadata from AI-generated images.
 * - **Speed Sorter (`/speedsorter`):** High-efficiency, keyboard-driven image triage tool.
 * - **Duplicates (`/duplicates`):** Interface for identifying and resolving visual duplicates.
 */
import {createRouter, createWebHistory} from 'vue-router'
import ImageBrowserView from '../views/ImageBrowserView.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'browser',
            component: ImageBrowserView
        },
        {
            path: '/collections',
            name: 'collections',
            component: () => import('../views/CollectionsView.vue')
        },
        {
            path: '/comparator',
            name: 'comparator',
            component: () => import('../views/ComparatorView.vue')
        },
        {
            path: '/scrub',
            name: 'scrub',
            component: () => import('../views/ScrubView.vue')
        },
        {
            path: '/speedsorter',
            name: 'speedsorter',
            component: () => import('../views/SpeedSorterView.vue')
        },
        {
            path: '/duplicates',
            name: 'duplicates',
            component: () => import('../views/DuplicateDetectiveView.vue')
        }
    ]
})

export default router
