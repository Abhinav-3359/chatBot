import axios from "axios";

// Set VITE_API_BASE_URL in deployment (e.g. https://api.yourdomain.com);
// falls back to localhost for local dev with zero setup.
const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  headers: {
    "Content-Type": "application/json",
  },
});

// "Remember me" (AuthContext) decides whether the token lives in
// localStorage or sessionStorage - check both, same as AuthContext does
// when it rehydrates on load.
function getStoredToken() {
  return localStorage.getItem("token") || sessionStorage.getItem("token");
}

// Request Interceptor
axiosInstance.interceptors.request.use(
  (config) => {
    const token = getStoredToken();

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    // 401 = not authenticated at all (missing/expired/invalid token) -
    // the session is gone, so clear it and send them to log in again.
    //
    // 403 = authenticated, but not allowed to do this specific thing
    // (e.g. a chatbot you don't own) - that is NOT a reason to log the
    // user out, so it's left for the calling code to show as a normal
    // error instead.
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      sessionStorage.removeItem("token");
      window.location.href = "/";
    }

    return Promise.reject(error);
  }
);

export default axiosInstance;
