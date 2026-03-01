/**
 * @file api.js
 * @description Centralized Axios service for backend communication with integrated security and error handling.
 *
 * Key Responsibilities:
 * - **Security Integration:** Synchronously retrieves the handshake token from the Electron
 *   bridge and attaches it to the {@code Authorization} header of every outgoing request.
 * - **Media Authentication:** Provides a helper utility to append the security token to
 *   URLs used in {@code <img>} tags, ensuring media resources are protected.
 * - **Global Error Handling:** Implements a response interceptor that categorizes and
 *   displays errors (401, 404, 500+) using standardized toast notifications.
 * - **Network Resilience:** Detects and reports connection failures when the backend
 *   service is unreachable.
 */
import axios from 'axios';
import {toast} from 'vue3-toastify';

export const handshakeToken = window.electronAPI ? window.electronAPI.getHandshakeToken() : null;

export const authenticatedUrl = (url) => {
    if (!handshakeToken || !url) return url;
    const separator = url.includes('?') ? '&' : '?';
    return `${url}${separator}token=${handshakeToken}`;
};

const api = axios.create({
    baseURL: '/api',
    timeout: 60000,
});

api.interceptors.request.use((config) => {
    if (handshakeToken) {
        config.headers['Authorization'] = `Bearer ${handshakeToken}`;
    }
    if (config.params) {
        if (config.params.page !== undefined) config.params.page = parseInt(config.params.page);
        if (config.params.size !== undefined) config.params.size = parseInt(config.params.size);
    }
    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response) {
            const status = error.response.status;
            const data = error.response.data;

            if (status === 401) {
                toast.error("Security Error: Unauthorized access blocked.");
            } else if (data && data.message) {
                if (status === 404) toast.warn(data.message);
                else if (status >= 500) toast.error(`System Error: ${data.message}`);
                else toast.error(data.message);
            } else {
                toast.error(`Error ${status}: An unexpected error occurred.`);
            }
        } else if (error.request) {
            toast.error("Network Error: Backend is unreachable.");
        } else {
            toast.error("Request Error: " + error.message);
        }
        return Promise.reject(error);
    }
);

export const patchImageMetadata = (id, payload) => {
    return api.patch(`/images/${id}/metadata`, payload);
};

export default api;
