/**
 * @file main.js
 * @description The entry point for the Vue.js frontend application.
 *
 * This script initializes the Vue application instance and configures the global plugin
 * ecosystem. It orchestrates the integration of state management (Pinia), routing,
 * and the UI component library (PrimeVue). It also establishes the global CSS foundation,
 * including the application's multi-theme system and custom component overrides.
 *
 * Key Responsibilities:
 * - **App Initialization:** Bootstraps the root `App.vue` component.
 * - **Plugin Registration:** Configures Pinia, Vue Router, and PrimeVue services (Toast, Confirm).
 * - **Global Directives:** Registers custom directives like Tooltip for application-wide use.
 * - **Style Orchestration:** Imports core CSS frameworks (PrimeFlex, PrimeIcons) and
 *   the application's custom theme and layout systems.
 * - **Notification System:** Initializes `vue3-toastify` for standardized global error handling.
 */
import {createApp} from 'vue'
import {createPinia} from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip';

import Vue3Toastify, {toast} from 'vue3-toastify';
import 'vue3-toastify/dist/index.css';

import 'primevue/resources/themes/aura-dark-green/theme.css'
import 'primeicons/primeicons.css'
import 'primeflex/primeflex.css'

import './assets/css/themes/neon.css';
import './assets/css/themes/light.css';
import './assets/css/themes/gold.css';
import './assets/css/components/base.css';
import './assets/css/components/primevue-overrides.css';
import './assets/css/components/layout.css';
import './assets/css/components/buttons.css';

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(PrimeVue)
app.use(ToastService)
app.use(ConfirmationService)

app.use(Vue3Toastify, {
    autoClose: 4000,
    position: 'bottom-right',
    theme: 'dark',
    clearOnUrlChange: false,
    transition: 'slide',
});

app.directive('tooltip', Tooltip);

app.mount('#app')
