import { api, API_BASE_URL, parseJsonWithLargeIntegerIds } from "@/services/api";
import type { ChatMessage, ChatResponse, ChatSession, ChatStreamEvent } from "@/types";
import { storage } from "@/utils/storage";

export async function createChatSession(userId: string, title = "新对话") {
  const session = await api.post<ChatSession, ChatSession>("/chat-sessions/create", {
    userId,
    title,
    status: "ACTIVE"
  });
  return normalizeSession(session);
}

export async function listChatSessions(userId: string) {
  const sessions = await api.get<ChatSession[], ChatSession[]>("/chat-sessions/list", { params: { userId } });
  return sessions.map(normalizeSession);
}

export async function getChatSession(id: string) {
  const session = await api.get<ChatSession, ChatSession>(`/chat-sessions/detail/${id}`);
  return normalizeSession(session);
}

export async function deleteChatSession(id: string) {
  return api.post<void, void>("/chat-sessions/delete", { id });
}

export async function listChatMessages(sessionId: string) {
  const messages = await api.get<ChatMessage[], ChatMessage[]>("/chat-messages/list", { params: { sessionId } });
  return messages.map(normalizeMessage);
}

export async function getChatMessage(id: string) {
  const message = await api.get<ChatMessage, ChatMessage>(`/chat-messages/detail/${id}`);
  return normalizeMessage(message);
}

export async function createChatMessage(payload: {
  sessionId: string;
  userId: string;
  role: string;
  content: string;
  contentType?: string;
  sequenceNo?: number;
  traceId?: string;
  modelProvider?: string;
  modelName?: string;
}) {
  const message = await api.post<ChatMessage, ChatMessage>("/chat-messages/create", payload);
  return normalizeMessage(message);
}

export async function sendChatMessage(payload: { sessionId: string; userId: string; message: string; traceId?: string }) {
  const response = await api.post<ChatResponse, ChatResponse>("/chat-messages/chat", payload);
  return normalizeChatResponse(response);
}

export async function streamChatMessage(
  payload: { sessionId: string; userId: string; message: string; traceId?: string },
  handlers: {
    onEvent: (event: ChatStreamEvent) => void;
    onError: (error: Error) => void;
    onDone: () => void;
  }
) {
  const token = storage.getToken();
  const response = await fetch(`${API_BASE_URL}/chat-messages/chatstream`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: token } : {})
    },
    body: JSON.stringify(payload)
  });
  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败：${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split("\n\n");
      buffer = events.pop() || "";
      for (const raw of events) {
        const dataLine = raw
          .split("\n")
          .find((line) => line.startsWith("data:"));
        if (!dataLine) continue;
        const data = dataLine.replace(/^data:\s*/, "");
        if (!data || data === "[DONE]") continue;
        handlers.onEvent(normalizeStreamEvent(parseJsonWithLargeIntegerIds<ChatStreamEvent>(data)));
      }
    }
    handlers.onDone();
  } catch (error) {
    handlers.onError(error as Error);
  }
}

function normalizeSession(session: ChatSession): ChatSession {
  return {
    ...session,
    id: String(session.id),
    userId: session.userId !== undefined ? String(session.userId) : session.userId
  };
}

function normalizeMessage(message: ChatMessage): ChatMessage {
  return {
    ...message,
    id: String(message.id),
    sessionId: message.sessionId !== undefined ? String(message.sessionId) : message.sessionId,
    userId: message.userId !== undefined ? String(message.userId) : message.userId,
    role: String(message.role).toLowerCase() === "assistant" ? "assistant" : "user"
  };
}

function normalizeChatResponse(response: ChatResponse): ChatResponse {
  return {
    ...response,
    sessionId: String(response.sessionId),
    userMessageId: String(response.userMessageId),
    assistantMessageId: String(response.assistantMessageId)
  };
}

function normalizeStreamEvent(event: ChatStreamEvent): ChatStreamEvent {
  return {
    ...event,
    sessionId: event.sessionId !== undefined ? String(event.sessionId) : event.sessionId,
    candidateId: event.candidateId !== undefined ? String(event.candidateId) : event.candidateId,
    assistantMessageId: event.assistantMessageId !== undefined ? String(event.assistantMessageId) : event.assistantMessageId
  };
}
