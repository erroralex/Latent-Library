import { createRouter, createWebHistory } from 'vue-router'
import ImageBrowserView from '../views/ImageBrowserView.vue'

/**
 * Vue Router configuration.
 * Defines the application's navigation structure and lazy-loads views for performance.
 */
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
