import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const authAPI = {
  sendOtp: (phoneNumber) => api.post('/api/auth/send-otp', { phoneNumber }),
  verifyOtp: (phoneNumber, otpCode) => api.post('/api/auth/verify-otp', { phoneNumber, otpCode }),
};

export const vendorAPI = {
  register: (data) => api.post('/api/vendor/register', data),
  getProfile: () => api.get('/api/vendor/profile'),
  updateProfile: (data) => api.put('/api/vendor/profile', data),
  getBySlug: (slug) => api.get(`/api/vendor/public/vendor/${slug}`),
};

export const orderAPI = {
  create: (data) => api.post('/api/orders/create', data),
  getByCode: (orderCode) => api.get(`/api/orders/${orderCode}`),
  getStatus: (orderCode) => api.get(`/api/orders/${orderCode}/status`),
  getVendorOrders: (vendorId) => api.get(`/api/orders/vendor?vendorId=${vendorId}`),
  quickCheck: (orderCode) => api.get(`/api/orders/quick-check/${orderCode}`),
};

export default api;
