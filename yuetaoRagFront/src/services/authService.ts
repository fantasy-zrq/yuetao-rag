import { api } from "@/services/api";
import type { User } from "@/types";

export interface LoginPayload {
  username: string;
  password: string;
  rememberMe?: boolean;
}

export async function login(payload: LoginPayload) {
  const user = await api.post<User, User>("/auth/login", payload);
  return normalizeUser(user);
}

export async function logout() {
  return api.post<void, void>("/auth/logout");
}

export async function getCurrentUser() {
  const user = await api.get<Omit<User, "token">, Omit<User, "token">>("/user/me");
  return normalizeUser(user);
}

function normalizeUser<T extends Partial<User>>(user: T): T {
  return {
    ...user,
    userId: user.userId !== undefined ? String(user.userId) : user.userId
  };
}
