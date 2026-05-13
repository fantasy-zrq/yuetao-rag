import type { User } from "@/types";
import { AUTH_TOKEN_STORAGE_KEY, AUTH_USER_STORAGE_KEY } from "@/constants/authStorage";

export const storage = {
  getToken() {
    // 当前会话登录优先读 sessionStorage，避免被旧的长期登录 token 覆盖。
    return sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY) ?? localStorage.getItem(AUTH_TOKEN_STORAGE_KEY);
  },
  setToken(token: string, rememberMe = true) {
    this.clearToken();
    const targetStorage = rememberMe ? localStorage : sessionStorage;
    targetStorage.setItem(AUTH_TOKEN_STORAGE_KEY, token);
  },
  getUser(): User | null {
    return parseUser(sessionStorage.getItem(AUTH_USER_STORAGE_KEY)) ?? parseUser(localStorage.getItem(AUTH_USER_STORAGE_KEY));
  },
  setUser(user: User, rememberMe = true) {
    this.clearUser();
    const targetStorage = rememberMe ? localStorage : sessionStorage;
    targetStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify(user));
  },
  isRememberMe() {
    return !sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY) && Boolean(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY));
  },
  clearAuth() {
    this.clearToken();
    this.clearUser();
  },
  clearToken() {
    localStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
    sessionStorage.removeItem(AUTH_TOKEN_STORAGE_KEY);
  },
  clearUser() {
    localStorage.removeItem(AUTH_USER_STORAGE_KEY);
    sessionStorage.removeItem(AUTH_USER_STORAGE_KEY);
  },
};

function parseUser(raw: string | null): User | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}
