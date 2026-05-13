import { beforeEach, describe, expect, it, vi } from "vitest";
import { AUTH_TOKEN_STORAGE_KEY, AUTH_USER_STORAGE_KEY } from "@/constants/authStorage";

const loginMock = vi.fn();
const logoutMock = vi.fn();
const getCurrentUserMock = vi.fn();
const setAuthTokenMock = vi.fn();

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn()
  }
}));

vi.mock("@/services/api", () => ({
  setAuthToken: (...args: unknown[]) => setAuthTokenMock(...args)
}));

vi.mock("@/services/authService", () => ({
  login: (...args: unknown[]) => loginMock(...args),
  logout: (...args: unknown[]) => logoutMock(...args),
  getCurrentUser: (...args: unknown[]) => getCurrentUserMock(...args)
}));

describe("authStore", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    installMemoryStorage("localStorage");
    installMemoryStorage("sessionStorage");
  });

  it("stores session-only logins in sessionStorage", async () => {
    loginMock.mockResolvedValue({
      userId: "1",
      username: "tester",
      displayName: "Tester",
      role: "ADMIN",
      token: "session-token"
    });

    const { useAuthStore } = await import("./authStore");
    const login = useAuthStore.getState().login as unknown as (
      username: string,
      password: string,
      rememberMe: boolean
    ) => Promise<void>;

    await login("tester", "secret", false);

    expect(sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBe("session-token");
    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(AUTH_USER_STORAGE_KEY)).toContain("\"username\":\"tester\"");
  });

  it("clears stale auth state when current user refresh fails", async () => {
    sessionStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "expired-token");
    sessionStorage.setItem(
      AUTH_USER_STORAGE_KEY,
      JSON.stringify({
        userId: "1",
        username: "tester",
        role: "ADMIN",
        token: "expired-token"
      })
    );
    getCurrentUserMock.mockRejectedValue(new Error("未登录或登录已过期"));

    const { useAuthStore } = await import("./authStore");

    await useAuthStore.getState().checkAuth();

    expect(useAuthStore.getState().isAuthenticated).toBe(false);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(AUTH_USER_STORAGE_KEY)).toBeNull();
    expect(setAuthTokenMock).toHaveBeenLastCalledWith(null);
  });
});

function installMemoryStorage(key: "localStorage" | "sessionStorage") {
  const values = new Map<string, string>();
  const storageLike: Storage = {
    get length() {
      return values.size;
    },
    clear() {
      values.clear();
    },
    getItem(itemKey) {
      return values.has(itemKey) ? values.get(itemKey)! : null;
    },
    key(index) {
      return Array.from(values.keys())[index] ?? null;
    },
    removeItem(itemKey) {
      values.delete(itemKey);
    },
    setItem(itemKey, value) {
      values.set(itemKey, value);
    }
  };
  Object.defineProperty(globalThis, key, {
    configurable: true,
    value: storageLike
  });
}
