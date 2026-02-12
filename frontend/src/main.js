import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip';

// Import Vue3Toastify for global error notifications
import Vue3Toastify, { toast } from 'vue3-toastify';
import 'vue3-toastify/dist/index.css';

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

// Configure generic Toast notifications (for errors/system messages)
app.use(Vue3Toastify, {
    autoClose: 4000,
    position: 'bottom-right',
    theme: 'dark',
    clearOnUrlChange: false,
    transition: 'slide',
});

app.directive('tooltip', Tooltip);

app.mount('#app')