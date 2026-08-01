import axios from "axios";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const api = axios.create({ baseURL: API_BASE, headers: { "Content-Type": "application/json" } });

api.interceptors.request.use((config) => {
  if (typeof window !== "undefined") {
    const token = localStorage.getItem("cp_token");
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use((res) => res, (err) => {
  if (err.response?.status === 401 && typeof window !== "undefined") {
    localStorage.removeItem("cp_token");
    localStorage.removeItem("cp_user");
    window.location.href = "/auth/login";
  }
  return Promise.reject(err);
});

export const authApi = {
  register: (data: { username: string; email: string; password: string; fullName?: string }) =>
    api.post("/api/auth/register", data),
  login: (data: { email: string; password: string }) =>
    api.post("/api/auth/login", data),
};

export const userApi = {
  getMe: () => api.get("/api/users/me"),
  updateMe: (data: Partial<{
    fullName: string; avatarUrl: string;
    codeforcesHandle: string; leetcodeHandle: string;
    atcoderHandle: string; codechefHandle: string;
  }>) => api.put("/api/users/me", data),
};

export const analyticsApi = {
  getMyAnalytics: () => api.get("/api/analytics/me"),
  syncData: () => api.post("/api/analytics/sync"),
};

export const recommendationsApi = {
  getRecommendations: () => api.get("/api/recommendations"),
  generate: () => api.post("/api/recommendations/generate"),
  markSolved: (id: number) => api.patch(`/api/recommendations/${id}/solved`),
  dismiss: (id: number) => api.patch(`/api/recommendations/${id}/dismiss`),
};

export const aiApi = {
  recommend: (prompt: string, count = 6) => api.post("/api/ai/recommend", { prompt, count }),
  getLatestSession: () => api.get("/api/ai/sessions/latest"),
  markSolved: (itemId: number) => api.patch(`/api/ai/items/${itemId}/solved`),
  dismiss: (itemId: number) => api.patch(`/api/ai/items/${itemId}/dismiss`),
};

export const submissionsApi = {
  getRecent: (limit = 10) => api.get(`/api/submissions/recent?limit=${limit}`),
};