import { beforeEach, describe, expect, it } from "vitest";

import { AUTH_TOKEN_STORAGE_KEY, AUTH_USER_STORAGE_KEY } from "@/constants/authStorage";
import { storage } from "./storage";

describe("storage", () => {
  beforeEach(() => {
    installMemoryStorage("localStorage");
    installMemoryStorage("sessionStorage");
  });

  it("prefers the session token over the persistent token", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "local-token");
    sessionStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "session-token");

    expect(storage.getToken()).toBe("session-token");
  });

  it("clears auth data from both storage scopes", () => {
    localStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "local-token");
    localStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify({ userId: "1" }));
    sessionStorage.setItem(AUTH_TOKEN_STORAGE_KEY, "session-token");
    sessionStorage.setItem(AUTH_USER_STORAGE_KEY, JSON.stringify({ userId: "1" }));

    storage.clearAuth();

    expect(localStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(localStorage.getItem(AUTH_USER_STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(AUTH_TOKEN_STORAGE_KEY)).toBeNull();
    expect(sessionStorage.getItem(AUTH_USER_STORAGE_KEY)).toBeNull();
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
