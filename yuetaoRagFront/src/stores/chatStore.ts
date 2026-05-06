import { create } from "zustand";
import { toast } from "sonner";

import {
  createChatSession,
  deleteChatSession,
  listChatMessages,
  listChatSessions,
  sendChatMessage,
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
  setUseStreaming: (value: boolean) => void;
  fetchSessions: () => Promise<void>;
  startNewSession: () => void;
  selectSession: (sessionId: string) => Promise<void>;
  deleteSession: (sessionId: string) => Promise<void>;
  send: (content: string) => Promise<void>;
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

export const useChatStore = create<ChatState>((set, get) => ({
  sessions: [],
  messages: [],
  currentSessionId: null,
  isLoading: false,
  isStreaming: false,
  useStreaming: true,
  setUseStreaming: (value) => set({ useStreaming: value }),
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
          status: "done"
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
  send: async (content) => {
    const trimmed = content.trim();
    const userId = currentUserId();
    if (!trimmed || !userId || get().isStreaming) return;

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
        citations: [],
        createTime: new Date().toISOString()
      };

      set((state) => ({
        messages: [...state.messages, userMessage, assistantMessage],
        sessions: touchSession(state.sessions, activeSessionId),
        isStreaming: true
      }));

      if (!get().useStreaming) {
        const response = await sendChatMessage({ sessionId: activeSessionId, userId, message: trimmed });
        set((state) => ({
          isStreaming: false,
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
        { sessionId: activeSessionId, userId, message: trimmed },
        {
          onEvent: (event) => {
            if (event.event === "delta" && event.content) {
              set((state) => ({
                messages: state.messages.map((message) =>
                  message.id === assistantId ? { ...message, content: message.content + event.content } : message
                )
              }));
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
            if (event.event === "message_end") {
              set((state) => ({
                isStreaming: false,
                sessions: touchSession(state.sessions, event.sessionId || activeSessionId),
                messages: state.messages.map((message) =>
                  message.id === assistantId
                    ? { ...message, id: event.assistantMessageId || message.id, status: "done" }
                    : message
                )
              }));
            }
            if (event.event === "error") {
              throw new Error(event.message || "生成失败");
            }
          },
          onError: (error) => {
            set((state) => ({
              isStreaming: false,
              messages: state.messages.map((message) =>
                message.id === assistantId ? { ...message, status: "error", content: message.content || error.message } : message
              )
            }));
            toast.error(error.message);
          },
          onDone: () => {
            set((state) => ({
              isStreaming: false,
              messages: state.messages.map((message) =>
                message.id === assistantId && message.status === "streaming" ? { ...message, status: "done" } : message
              )
            }));
          }
        }
      );
    } catch (error) {
      set({ isStreaming: false });
      toast.error((error as Error).message || "发送失败");
    }
  }
}));
