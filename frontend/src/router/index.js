/**
 * @file index.js (Router)
 * @description The primary routing configuration for the AI Toolbox frontend.
 * 
 * This module defines the application's URL structure and maps paths to their respective view components.
 * It utilizes Vue Router's web history mode and implements lazy-loading for secondary views to optimize
 * initial bundle size and application startup time.
 * 
 * Routes:
 * - / (browser): The main image gallery and browser interface.
 * - /collections: Management view for static and smart image groupings.
 * - /comparator: Side-by-side image comparison utility.
 * - /scrub: Metadata removal and privacy tool.
 * - /speedsorter: High-speed image triage and organization interface.
 */
import { createRouter, createWebHistory } from 'vue-router'
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
    }
  ]
})

export default router
