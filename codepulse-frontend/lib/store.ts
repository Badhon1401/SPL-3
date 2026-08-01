import { create } from "zustand";

export interface User {
  id: number; username: string; email: string;
  fullName?: string; avatarUrl?: string; role: string;
  codeforcesHandle?: string; leetcodeHandle?: string;
  atcoderHandle?: string; codechefHandle?: string;
  platformConnections?: Record<string, boolean>;
  lastSyncedAt?: string;
}

interface AuthState {
  user: User | null; token: string | null; isAuthenticated: boolean;
  setAuth: (user: User, token: string) => void;
  logout: () => void;
  updateUser: (user: Partial<User>) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: typeof window !== "undefined" ? JSON.parse(localStorage.getItem("cp_user") || "null") : null,
  token: typeof window !== "undefined" ? localStorage.getItem("cp_token") : null,
  isAuthenticated: typeof window !== "undefined" ? !!localStorage.getItem("cp_token") : false,

  setAuth: (user, token) => {
    localStorage.setItem("cp_token", token);
    localStorage.setItem("cp_user", JSON.stringify(user));
    set({ user, token, isAuthenticated: true });
  },
  logout: () => {
    localStorage.removeItem("cp_token"); localStorage.removeItem("cp_user");
    set({ user: null, token: null, isAuthenticated: false });
  },
  updateUser: (updated) => set((state) => {
    const user = state.user ? { ...state.user, ...updated } : null;
    if (user) localStorage.setItem("cp_user", JSON.stringify(user));
    return { user };
  }),
}));