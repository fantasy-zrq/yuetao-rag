import { create } from "zustand";
import { toast } from "sonner";

import { CHAT_STREAM_ABORT_ERROR_NAME, CHAT_STREAM_TRACE_PREFIX } from "@/constants/chatStream";
import {
  createChatSession,
  deleteChatSession,
  listChatMessages,
  listChatSessions,
  sendChatMessage,
  stopChatStream as stopChatStreamRequest,
  streamChatMessage
} from "@/services/chatService";
import { currentUserId } from "@/stores/authStore";
import type { ChatCitation, ChatMessage, ChatSession } from "@/types";

interface ChatState {
  sessions: ChatSession[];
  messages: ChatMessage[];
  currentSessionId: string | null;
  isLoading: boolean;
  isStreaming: boolean;
  useStreaming: boolean;
  deepThinkingEnabled: boolean;
  thinkingStartAt: number | null;
  currentStreamTraceId: string | null;
  currentStreamSessionId: string | null;
  streamAbortController: AbortController | null;
  setUseStreaming: (value: boolean) => void;
  setDeepThinkingEnabled: (value: boolean) => void;
  fetchSessions: () => Promise<void>;
  startNewSession: () => void;
  selectSession: (sessionId: string) => Promise<void>;
  deleteSession: (sessionId: string) => Promise<void>;
  send: (content: string) => Promise<void>;
  stopStreaming: () => Promise<void>;
}

function sortSessions(sessions: ChatSession[]) {
  return [...sessions].sort((a, b) => {
    const left = a.lastActiveAt ? new Date(a.lastActiveAt).getTime() : 0;
    const right = b.lastActiveAt ? new Date(b.lastActiveAt).getTime() : 0;
    return right - left;
  });
}

function upsertSession(sessions: ChatSession[], next: ChatSession) {
  const exists = sessions.some((session) => session.id === next.id);
  return sortSessions(exists ? sessions.map((session) => (session.id === next.id ? next : session)) : [next, ...sessions]);
}

function touchSession(sessions: ChatSession[], sessionId: string, lastActiveAt = new Date().toISOString()) {
  return sortSessions(
    sessions.map((session) => (session.id === sessionId ? { ...session, lastActiveAt } : session))
  );
}

function createStreamTraceId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${CHAT_STREAM_TRACE_PREFIX}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function resolveThinkingDurationMs(thinkingStartAt: number | null, existingDurationMs?: number | null) {
  if (existingDurationMs != null) {
    return existingDurationMs;
  }
  if (thinkingStartAt == null) {
    return undefined;
  }
  return Math.max(1, Math.round((Date.now() - thinkingStartAt) / 1000)) * 1000;
}

function isAbortError(error: unknown) {
  return error instanceof Error && error.name === CHAT_STREAM_ABORT_ERROR_NAME;
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessions: [],
  messages: [],
  currentSessionId: null,
  isLoading: false,
  isStreaming: false,
  useStreaming: true,
  deepThinkingEnabled: false,
  thinkingStartAt: null,
  currentStreamTraceId: null,
  currentStreamSessionId: null,
  streamAbortController: null,
  setUseStreaming: (value) => set({ useStreaming: value }),
  setDeepThinkingEnabled: (value) => set({ deepThinkingEnabled: value }),
  fetchSessions: async () => {
    const userId = currentUserId();
    if (!userId) return;
    set({ isLoading: true });
    try {
      const sessions = await listChatSessions(userId);
      set({ sessions: sortSessions(sessions) });
    } catch (error) {
      toast.error((error as Error).message || "加载会话失败");
    } finally {
      set({ isLoading: false });
    }
  },
  startNewSession: () => {
    set({ currentSessionId: null, messages: [] });
  },
  selectSession: async (sessionId) => {
    set({ currentSessionId: sessionId, isLoading: true });
    try {
      const messages = await listChatMessages(sessionId);
      set({
        messages: messages.map((message) => ({
          ...message,
          role: String(message.role).toLowerCase() === "assistant" ? "assistant" : "user",
          status: "done",
          thinking: message.thinkingContent || undefined,
          isThinking: false
        }))
      });
    } catch (error) {
      toast.error((error as Error).message || "加载消息失败");
    } finally {
      set({ isLoading: false });
    }
  },
  deleteSession: async (sessionId) => {
    try {
      await deleteChatSession(sessionId);
      set((state) => ({
        sessions: state.sessions.filter((session) => session.id !== sessionId),
        messages: state.currentSessionId === sessionId ? [] : state.messages,
        currentSessionId: state.currentSessionId === sessionId ? null : state.currentSessionId
      }));
      toast.success("会话已删除");
    } catch (error) {
      toast.error((error as Error).message || "删除会话失败");
      throw error;
    }
  },
  stopStreaming: async () => {
    const { currentStreamSessionId, currentStreamTraceId, isStreaming, streamAbortController } = get();
    if (!isStreaming) {
      return;
    }

    // 先在本地终止读取并收敛 UI，再尝试通知后端释放模型流，避免中止动作继续冒泡成前端错误。
    set((state) => ({
      isStreaming: false,
      thinkingStartAt: null,
      currentStreamTraceId: null,
      currentStreamSessionId: null,
      streamAbortController: null,
      messages: state.messages.map((message) =>
        message.role === "assistant" && message.status === "streaming" && message.traceId === currentStreamTraceId
          ? {
              ...message,
              status: "done",
              isThinking: false,
              thinkingDurationMs: resolveThinkingDurationMs(state.thinkingStartAt, message.thinkingDurationMs)
            }
          : message
      )
    }));

    streamAbortController?.abort();

    if (!currentStreamSessionId || !currentStreamTraceId) {
      return;
    }
    try {
      await stopChatStreamRequest({ sessionId: currentStreamSessionId, traceId: currentStreamTraceId });
    } catch {
      // stop 是 best-effort，前端已本地中止，不再向用户重复提示后台停止失败。
    }
  },
  send: async (content) => {
    const trimmed = content.trim();
    const userId = currentUserId();
    if (!trimmed || !userId || get().isStreaming) return;

    const deepThinking = get().deepThinkingEnabled;
    const useStreaming = get().useStreaming;
    const traceId = createStreamTraceId();
    let sessionId = get().currentSessionId;
    try {
      if (!sessionId) {
        const session = await createChatSession(userId, trimmed.slice(0, 24) || "新对话");
        sessionId = session.id;
        set((state) => ({
          currentSessionId: session.id,
          sessions: upsertSession(state.sessions, session)
        }));
      }
      const activeSessionId = sessionId;

      const userMessage: ChatMessage = {
        id: `user-${Date.now()}`,
        sessionId: activeSessionId,
        userId,
        role: "user",
        content: trimmed,
        status: "done",
        createTime: new Date().toISOString()
      };
      const assistantId = `assistant-${Date.now()}`;
      const assistantMessage: ChatMessage = {
        id: assistantId,
        sessionId: activeSessionId,
        userId,
        role: "assistant",
        content: "",
        status: "streaming",
        traceId,
        citations: [],
        thinking: deepThinking ? "" : undefined,
        isThinking: deepThinking,
        createTime: new Date().toISOString()
      };
      const streamAbortController = useStreaming ? new AbortController() : null;

      set((state) => ({
        messages: [...state.messages, userMessage, assistantMessage],
        sessions: touchSession(state.sessions, activeSessionId),
        isStreaming: true,
        thinkingStartAt: null,
        currentStreamTraceId: useStreaming ? traceId : null,
        currentStreamSessionId: useStreaming ? activeSessionId : null,
        streamAbortController
      }));

      if (!useStreaming) {
        const response = await sendChatMessage({ sessionId: activeSessionId, userId, message: trimmed, traceId });
        set((state) => ({
          isStreaming: false,
          currentStreamTraceId: null,
          currentStreamSessionId: null,
          streamAbortController: null,
          sessions: touchSession(state.sessions, response.sessionId),
          messages: state.messages.map((message) =>
            message.id === assistantId
              ? {
                  ...message,
                  id: response.assistantMessageId,
                  content: response.answer,
                  traceId: response.traceId,
                  citations: response.citations,
                  status: "done"
                }
              : message
          )
        }));
        return;
      }

      await streamChatMessage(
        {
          sessionId: activeSessionId,
          userId,
          message: trimmed,
          traceId,
          deepThinking: deepThinking || undefined
        },
        {
          onEvent: (event) => {
            if (get().currentStreamTraceId !== traceId) {
              return;
            }
            if (event.event === "thinking_delta" && event.content) {
              set((state) => ({
                thinkingStartAt: state.thinkingStartAt ?? Date.now(),
                messages: state.messages.map((message) =>
                  message.id === assistantId
                    ? { ...message, thinking: (message.thinking ?? "") + event.content, isThinking: true }
                    : message
                )
              }));
            }
            if (event.event === "delta" && event.content) {
              set((state) => {
                const shouldFinalize = state.thinkingStartAt != null;
                const duration = shouldFinalize
                  ? Math.max(1, Math.round((Date.now() - (state.thinkingStartAt as number)) / 1000))
                  : undefined;
                return {
                  thinkingStartAt: shouldFinalize ? null : state.thinkingStartAt,
                  messages: state.messages.map((message) =>
                    message.id === assistantId
                      ? {
                          ...message,
                          content: message.content + event.content,
                          isThinking: shouldFinalize ? false : message.isThinking,
                          thinkingDurationMs: shouldFinalize && !message.thinkingDurationMs
                            ? (duration ?? 0) * 1000
                            : message.thinkingDurationMs
                        }
                      : message
                  )
                };
              });
            }
            if (event.event === "citation" && event.citations) {
              set((state) => ({
                messages: state.messages.map((message) =>
                  message.id === assistantId
                    ? { ...message, citations: event.citations as ChatCitation[] }
                    : message
                )
              }));
            }
            if (event.event === "reset") {
              set((state) => ({
                thinkingStartAt: null,
                messages: state.messages.map((message) =>
                  message.id === assistantId
                    ? {
                        ...message,
                        content: "",
                        thinking: deepThinking ? "" : undefined,
                        citations: [],
                        isThinking: false,
                        thinkingDurationMs: undefined
                      }
                    : message
                )
              }));
            }
            if (event.event === "message_end") {
              set((state) => ({
                isStreaming: false,
                thinkingStartAt: null,
                currentStreamTraceId: null,
                currentStreamSessionId: null,
                streamAbortController: null,
                sessions: touchSession(state.sessions, event.sessionId || activeSessionId),
                messages: state.messages.map((message) =>
                  message.id === assistantId
                    ? {
                        ...message,
                        id: event.assistantMessageId || message.id,
                        status: "done",
                        isThinking: false,
                        thinkingDurationMs: message.thinkingDurationMs ?? (state.thinkingStartAt
                          ? Math.max(1, Math.round((Date.now() - state.thinkingStartAt) / 1000)) * 1000
                          : undefined)
                      }
                    : message
                )
              }));
            }
            if (event.event === "error") {
              throw new Error(event.message || "生成失败");
            }
          },
          onError: (error) => {
            if (isAbortError(error)) {
              return;
            }
            set((state) => ({
              isStreaming: false,
              thinkingStartAt: null,
              currentStreamTraceId: null,
              currentStreamSessionId: null,
              streamAbortController: null,
              messages: state.messages.map((message) =>
                message.id === assistantId
                  ? {
                      ...message,
                      status: "error",
                      content: message.content || error.message,
                      isThinking: false,
                      thinkingDurationMs: message.thinkingDurationMs ?? (state.thinkingStartAt
                        ? Math.max(1, Math.round((Date.now() - state.thinkingStartAt) / 1000)) * 1000
                        : undefined)
                    }
                  : message
              )
            }));
            toast.error(error.message);
          },
          onDone: () => {
            set((state) => ({
              isStreaming: false,
              currentStreamTraceId: null,
              currentStreamSessionId: null,
              streamAbortController: null,
              messages: state.messages.map((message) =>
                message.id === assistantId && message.status === "streaming" ? { ...message, status: "done" } : message
              )
            }));
          }
        },
        { signal: streamAbortController?.signal }
      );
    } catch (error) {
      set({
        isStreaming: false,
        currentStreamTraceId: null,
        currentStreamSessionId: null,
        streamAbortController: null
      });
      toast.error((error as Error).message || "发送失败");
    }
  }
}));
