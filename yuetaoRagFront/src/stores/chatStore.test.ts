import { beforeEach, describe, expect, it, vi } from "vitest";

const createChatSessionMock = vi.fn();
const deleteChatSessionMock = vi.fn();
const listChatMessagesMock = vi.fn();
const listChatSessionsMock = vi.fn();
const sendChatMessageMock = vi.fn();
const streamChatMessageMock = vi.fn();
const stopChatStreamMock = vi.fn();
const currentUserIdMock = vi.fn();

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn()
  }
}));

vi.mock("@/stores/authStore", () => ({
  currentUserId: () => currentUserIdMock()
}));

vi.mock("@/services/chatService", () => ({
  createChatSession: (...args: unknown[]) => createChatSessionMock(...args),
  deleteChatSession: (...args: unknown[]) => deleteChatSessionMock(...args),
  listChatMessages: (...args: unknown[]) => listChatMessagesMock(...args),
  listChatSessions: (...args: unknown[]) => listChatSessionsMock(...args),
  sendChatMessage: (...args: unknown[]) => sendChatMessageMock(...args),
  streamChatMessage: (...args: unknown[]) => streamChatMessageMock(...args),
  stopChatStream: (...args: unknown[]) => stopChatStreamMock(...args)
}));

describe("chatStore", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    currentUserIdMock.mockReturnValue("1");
    createChatSessionMock.mockResolvedValue({
      id: "session-1",
      userId: "1",
      title: "你好",
      status: "ACTIVE"
    });
  });

  it("stops an active stream and aborts the in-flight request", async () => {
    let capturedSignal: AbortSignal | undefined;
    streamChatMessageMock.mockImplementation(
      (_payload: unknown, _handlers: unknown, options: { signal?: AbortSignal } | undefined) =>
        new Promise<void>((resolve, reject) => {
          capturedSignal = options?.signal;
          options?.signal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")));
        })
    );

    const { useChatStore } = await import("./chatStore");
    const sendPromise = useChatStore.getState().send("你好");
    await Promise.resolve();

    expect(useChatStore.getState().isStreaming).toBe(true);

    await useChatStore.getState().stopStreaming();
    await sendPromise.catch(() => undefined);

    expect(capturedSignal?.aborted).toBe(true);
    expect(stopChatStreamMock).toHaveBeenCalledWith({ sessionId: "session-1", traceId: expect.any(String) });
    expect(useChatStore.getState().isStreaming).toBe(false);
  });

  it("ignores a late onDone callback after the user has already stopped streaming", async () => {
    let capturedHandlers:
      | {
          onDone: () => void;
          onEvent: (event: Record<string, unknown>) => void;
        }
      | undefined;

    streamChatMessageMock.mockImplementation(
      (_payload: unknown, handlers: { onDone: () => void; onEvent: (event: Record<string, unknown>) => void }, options: { signal?: AbortSignal } | undefined) =>
        new Promise<void>((resolve, reject) => {
          capturedHandlers = handlers;
          options?.signal?.addEventListener("abort", () => reject(new DOMException("Aborted", "AbortError")));
        })
    );

    const { useChatStore } = await import("./chatStore");
    const sendPromise = useChatStore.getState().send("你好");
    await Promise.resolve();

    capturedHandlers?.onEvent({ event: "thinking_delta", content: "先分析一下" });
    await useChatStore.getState().stopStreaming();
    capturedHandlers?.onDone();
    await sendPromise.catch(() => undefined);

    const assistantMessage = useChatStore.getState().messages.find((message) => message.role === "assistant");
    expect(assistantMessage?.status).toBe("done");
    expect(assistantMessage?.thinkingDurationMs).toBeGreaterThan(0);
    expect(useChatStore.getState().currentStreamTraceId).toBeNull();
  });

  it("resets accumulated assistant output when the backend emits a reset event", async () => {
    streamChatMessageMock.mockImplementation(async (_payload: unknown, handlers: {
      onDone: () => void;
      onEvent: (event: Record<string, unknown>) => void;
    }) => {
      handlers.onEvent({ event: "delta", content: "旧模型输出" });
      handlers.onEvent({ event: "citation", citations: [{ index: 1, referenceLabel: "旧引用" }] });
      handlers.onEvent({ event: "reset", reason: "candidate-switch" });
      handlers.onEvent({ event: "delta", content: "新模型输出" });
      handlers.onEvent({ event: "message_end", sessionId: "session-1", assistantMessageId: "assistant-1" });
      handlers.onDone();
    });

    const { useChatStore } = await import("./chatStore");
    await useChatStore.getState().send("你好");

    const assistantMessage = useChatStore.getState().messages.find((message) => message.role === "assistant");
    expect(assistantMessage?.content).toBe("新模型输出");
    expect(assistantMessage?.citations ?? []).toHaveLength(0);
  });
});
