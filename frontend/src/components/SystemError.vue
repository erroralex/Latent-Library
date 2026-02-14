<script setup>
/**
 * @file SystemError.vue
 * @description A fallback screen shown when the backend is unreachable or fails to initialize.
 *
 * This component acts as a global error boundary for the application. It is triggered when
 * the frontend cannot establish a connection with the Spring Boot backend service during
 * initialization. It provides the user with diagnostic information and a mechanism to
 * retry the connection.
 *
 * Key Responsibilities:
 * - **Error Visualization:** Displays a clear, user-friendly error message when the backend is down.
 * - **Diagnostic Guidance:** Lists common reasons for connection failure (Java version, firewall, etc.).
 * - **Recovery Action:** Provides a "Retry Connection" button that reloads the application.
 */
import Button from 'primevue/button';

const reload = () => {
  window.location.reload();
};
</script>

<template>
  <div
      class="system-error-container flex flex-column align-items-center justify-content-center h-screen p-5 text-center">
    <div class="error-card p-6 border-round-xl shadow-8 max-w-30rem">
      <i class="pi pi-exclamation-triangle text-6xl text-red-500 mb-4"></i>
      <h1 class="text-3xl font-bold mb-3 text-white">Backend Unreachable</h1>
      <p class="text-gray-400 mb-5 line-height-3">
        The application could not connect to the background service. This usually happens if:
      </p>
      <ul class="text-left text-gray-300 mb-5 px-4">
        <li class="mb-2">Java 21 or higher is not installed.</li>
        <li class="mb-2">A firewall is blocking local port communication.</li>
        <li class="mb-2">The backend process failed to start.</li>
      </ul>
      <div class="flex gap-3 justify-content-center">
        <Button label="Retry Connection" icon="pi pi-refresh" class="p-button-primary" @click="reload"/>
      </div>
    </div>
  </div>
</template>

<style scoped>
.system-error-container {
  background: var(--bg-app);
  background-image: var(--bg-app-image);
}

.error-card {
  background: var(--bg-panel-opaque);
  border: 1px solid var(--border-light);
  backdrop-filter: var(--glass-blur);
}

.text-red-500 {
  color: #ff4d4d !important;
}
</style>
