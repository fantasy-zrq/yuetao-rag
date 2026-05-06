import { create } from "zustand";
import { toast } from "sonner";

import { getCurrentUser, login as loginRequest, logout as logoutRequest } from "@/services/authService";
import { setAuthToken } from "@/services/api";
import type { User } from "@/types";
import { storage } from "@/utils/storage";

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: storage.getUser(),
  token: storage.getToken(),
  isAuthenticated: Boolean(storage.getToken()),
  isLoading: false,
  login: async (username, password) => {
    set({ isLoading: true });
    try {
      const user = await loginRequest({ username, password });
      storage.setToken(user.token);
      storage.setUser(user);
      setAuthToken(user.token);
      set({ user, token: user.token, isAuthenticated: true });
      toast.success("登录成功");
    } catch (error) {
      toast.error((error as Error).message || "登录接口未就绪");
      throw error;
    } finally {
      set({ isLoading: false });
    }
  },
  logout: async () => {
    try {
      await logoutRequest();
    } catch {
      // 登录接口补齐前允许本地退出。
    }
    storage.clearAuth();
    setAuthToken(null);
    set({ user: null, token: null, isAuthenticated: false });
  },
  checkAuth: async () => {
    const token = storage.getToken();
    const user = storage.getUser();
    setAuthToken(token);
    set({ token, user, isAuthenticated: Boolean(token) });
    if (!token || !user) return;
    try {
      const currentUser = await getCurrentUser();
      const nextUser = { ...currentUser, token } as User;
      storage.setUser(nextUser);
      set({ user: nextUser, token, isAuthenticated: true });
    } catch {
      set({ user, token, isAuthenticated: true });
    }
  }
}));

export function currentUserId() {
  return useAuthStore.getState().user?.userId;
}
