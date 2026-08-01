import axios, {
  AxiosError,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";

const API_BASE =
  process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

console.log("🔥 api.ts initialized");
console.log("API BASE =", API_BASE);

export const api = axios.create({
  baseURL: API_BASE,
  headers: {
    "Content-Type": "application/json",
  },
});

// ============================================================================
// Store request start times (Axios 1.x compatible)
// ============================================================================

const requestTimes = new WeakMap<InternalAxiosRequestConfig, number>();

// ============================================================================
// REQUEST LOGGER
// ============================================================================

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    requestTimes.set(config, Date.now());

    if (typeof window !== "undefined") {
      const token = localStorage.getItem("cp_token");

      if (token) {
        config.headers.set("Authorization", `Bearer ${token}`);
      }
    }

    const env =
      typeof window === "undefined"
        ? "🖥️ SERVER"
        : "🌐 CLIENT";

    console.groupCollapsed(
      `${env} 🚀 ${config.method?.toUpperCase()} ${config.baseURL}${config.url}`
    );

    console.log("Method :", config.method?.toUpperCase());
    console.log("URL    :", `${config.baseURL}${config.url}`);
    console.log("Params :", config.params);
    console.log("Body   :", config.data);
    console.log("Headers:", config.headers);

    console.groupEnd();

    return config;
  },
  (error) => {
    console.error("REQUEST ERROR", error);
    return Promise.reject(error);
  }
);

// ============================================================================
// RESPONSE LOGGER
// ============================================================================

api.interceptors.response.use(
  (response: AxiosResponse) => {
    const config = response.config as InternalAxiosRequestConfig;

    const start = requestTimes.get(config);

    const duration =
      start !== undefined ? Date.now() - start : 0;

    const env =
      typeof window === "undefined"
        ? "🖥️ SERVER"
        : "🌐 CLIENT";

    console.groupCollapsed(
      `${env} ✅ ${response.status} ${config.method?.toUpperCase()} ${config.baseURL}${config.url}`
    );

    console.log("Status   :", response.status);
    console.log("Duration :", `${duration} ms`);
    console.log("Response :", response.data);

    console.groupEnd();

    return response;
  },

  (error: AxiosError) => {
    const config = error.config as
      | InternalAxiosRequestConfig
      | undefined;

    const start =
      config !== undefined
        ? requestTimes.get(config)
        : undefined;

    const duration =
      start !== undefined ? Date.now() - start : 0;

    const env =
      typeof window === "undefined"
        ? "🖥️ SERVER"
        : "🌐 CLIENT";

    console.groupCollapsed(
      `${env} ❌ ${error.response?.status ?? "NETWORK"} ${config?.method?.toUpperCase()} ${config?.baseURL}${config?.url}`
    );

    console.log("Duration :", `${duration} ms`);
    console.log("Message  :", error.message);
    console.log("Response :", error.response?.data);

    console.groupEnd();

    if (
      error.response?.status === 401 &&
      typeof window !== "undefined"
    ) {
      localStorage.removeItem("cp_token");
      localStorage.removeItem("cp_user");
      window.location.href = "/auth/login";
    }

    return Promise.reject(error);
  }
);

// ============================================================================
// API
// ============================================================================

export const authApi = {
  register: (data: {
    username: string;
    email: string;
    password: string;
    fullName?: string;
  }) => api.post("/api/auth/register", data),

  login: (data: {
    email: string;
    password: string;
  }) => api.post("/api/auth/login", data),
};

export const userApi = {
  getMe: () => api.get("/api/users/me"),

  updateMe: (
    data: Partial<{
      fullName: string;
      avatarUrl: string;
      codeforcesHandle: string;
      leetcodeHandle: string;
      atcoderHandle: string;
      codechefHandle: string;
    }>
  ) => api.put("/api/users/me", data),
};

export const analyticsApi = {
  getMyAnalytics: () => api.get("/api/analytics/me"),

  syncData: () => api.post("/api/analytics/sync"),
};

export const recommendationsApi = {
  getRecommendations: () => api.get("/api/recommendations"),

  generate: () => api.post("/api/recommendations/generate"),

  markSolved: (id: number) =>
    api.patch(`/api/recommendations/${id}/solved`),

  dismiss: (id: number) =>
    api.patch(`/api/recommendations/${id}/dismiss`),
};

export const aiApi = {
  recommend: (prompt: string, count = 6) =>
    api.post("/api/ai/recommend", {
      prompt,
      count,
    }),

  getLatestSession: () =>
    api.get("/api/ai/sessions/latest"),

  markSolved: (id: number) =>
    api.patch(`/api/ai/items/${id}/solved`),

  dismiss: (id: number) =>
    api.patch(`/api/ai/items/${id}/dismiss`),
};

export const submissionsApi = {
  getRecent: (limit = 10) =>
    api.get(`/api/submissions/recent?limit=${limit}`),
};