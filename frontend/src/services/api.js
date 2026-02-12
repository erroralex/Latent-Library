import axios from 'axios';
import { toast } from 'vue3-toastify';

// Create instance
const api = axios.create({
    baseURL: 'http://localhost:8080/api', // Or dynamic port if you implemented that
    timeout: 10000,
});

// Response Interceptor
api.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        // Handle Error Response
        if (error.response) {
            const status = error.response.status;
            const data = error.response.data;

            // Expected Error from Backend (ErrorResponse)
            if (data && data.message) {
                if (status === 404) {
                    toast.warn(data.message);
                } else if (status >= 500) {
                    toast.error(`System Error: ${data.message}`);
                } else {
                    toast.error(data.message);
                }
            } else {
                // Fallback for weird errors
                toast.error(`Error ${status}: An unexpected error occurred.`);
            }
        } else if (error.request) {
            // No response received (Network Error / Server Down)
            toast.error("Network Error: Backend is unreachable.");
        } else {
            toast.error("Request Error: " + error.message);
        }

        return Promise.reject(error);
    }
);

export default api;