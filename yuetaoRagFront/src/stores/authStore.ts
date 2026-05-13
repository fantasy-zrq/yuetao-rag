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
  login: (username: string, password: string, rememberMe: boolean) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: storage.getUser(),
  token: storage.getToken(),
  isAuthenticated: Boolean(storage.getToken()),
  isLoading: false,
  login: async (username, password, rememberMe) => {
    set({ isLoading: true });
    try {
      const user = await loginRequest({ username, password, rememberMe });
      // 浏览器端仅负责决定 token 落到 session 还是 local，不参与服务端续期策略。
      storage.setToken(user.token, rememberMe);
      storage.setUser(user, rememberMe);
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
    if (!token || !user) {
      // 本地缺 token 或用户快照时直接视为未登录，避免前端保留半残状态。
      storage.clearAuth();
      setAuthToken(null);
      set({ user: null, token: null, isAuthenticated: false });
      return;
    }
    try {
      const currentUser = await getCurrentUser();
      const nextUser = { ...currentUser, token } as User;
      storage.setUser(nextUser, storage.isRememberMe());
      set({ user: nextUser, token, isAuthenticated: true });
    } catch {
      // 后端已判定 token 失效时，必须同步清掉本地状态，避免错误持续报给前端。
      storage.clearAuth();
      setAuthToken(null);
      set({ user: null, token: null, isAuthenticated: false });
    }
  }
}));

export function currentUserId() {
  return useAuthStore.getState().user?.userId;
}
