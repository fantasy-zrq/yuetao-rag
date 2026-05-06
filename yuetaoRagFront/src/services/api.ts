import axios from "axios";
import { toast } from "sonner";

import { storage } from "@/utils/storage";

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/yuetaoRag").replace(/\/$/, "");

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  transformResponse: [
    (data) => {
      if (typeof data !== "string" || !data) return data;
      return parseJsonWithLargeIntegerIds(data);
    }
  ]
});

export function parseJsonWithLargeIntegerIds<T = unknown>(data: string): T {
  return JSON.parse(quoteLargeIntegerIds(data)) as T;
}

function quoteLargeIntegerIds(data: string) {
  return data.replace(
    /"((?:id|userId|sessionId|knowledgeBaseId|documentId|chunkId|userMessageId|assistantMessageId))"\s*:\s*(-?\d+)/g,
    '"$1":"$2"'
  );
}

export function setAuthToken(token: string | null) {
  if (token) {
    api.defaults.headers.common.Authorization = token;
  } else {
    delete api.defaults.headers.common.Authorization;
  }
}

api.interceptors.request.use((config) => {
  const token = storage.getToken();
  if (token) {
    config.headers.Authorization = token;
  }
  return config;
});

api.interceptors.response.use(
  (response) => {
    const payload = response.data;
    if (payload && typeof payload === "object" && "code" in payload) {
      if (payload.code !== "0") {
        return Promise.reject(new Error(payload.message || "请求失败"));
      }
      return payload.data;
    }
    return payload;
  },
  (error) => {
    const message =
      error?.response?.data?.message ||
      (error?.code === "ERR_NETWORK" ? "网络错误，请检查后端服务" : error?.message || "请求失败");
    toast.error(message);
    return Promise.reject(new Error(message));
  }
);
