import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ToastService from 'primevue/toastservice'; // Import ToastService

import 'primevue/resources/themes/aura-dark-green/theme.css'
import 'primeicons/primeicons.css'
import 'primeflex/primeflex.css'

import './assets/css/main.css' // Import our custom CSS

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(PrimeVue)
app.use(ToastService) // Use ToastService

app.mount('#app')
