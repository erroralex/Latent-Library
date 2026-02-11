/**
 * @file main.js
 * @description The entry point for the AI Toolbox frontend application.
 * 
 * This script initializes the Vue 3 application instance, configures global plugins,
 * and mounts the root component to the DOM. It orchestrates the integration of
 * state management (Pinia), routing (Vue Router), and the UI component library (PrimeVue).
 * 
 * Key initializations:
 * - Pinia: Global state management.
 * - Vue Router: Client-side navigation.
 * - PrimeVue: UI components and theme configuration (Aura Dark Green).
 * - ToastService: Global notification system.
 * - ConfirmationService: Global confirmation dialog system.
 * - Tooltip: Custom directive for UI hints.
 * - Global CSS: Imports base styles, icons, and utility classes (PrimeFlex).
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip';

import 'primevue/resources/themes/aura-dark-green/theme.css'
import 'primeicons/primeicons.css'
import 'primeflex/primeflex.css'

import './assets/css/main.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(PrimeVue)
app.use(ToastService)
app.use(ConfirmationService)

app.directive('tooltip', Tooltip);

app.mount('#app')
