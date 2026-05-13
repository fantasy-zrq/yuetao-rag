import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const navigateMock = vi.fn();
const useAuthStoreMock = vi.fn();
const useChatStoreMock = vi.fn();

vi.mock("react-router-dom", () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ sessionId: "session-1" })
}));

vi.mock("@/stores/authStore", () => ({
  useAuthStore: () => useAuthStoreMock()
}));

vi.mock("@/stores/chatStore", () => ({
  useChatStore: () => useChatStoreMock()
}));

describe("ChatPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStoreMock.mockReturnValue({
      user: { username: "tester", displayName: "Tester" },
      logout: vi.fn()
    });
    useChatStoreMock.mockReturnValue({
      sessions: [],
      messages: [],
      currentSessionId: "session-1",
      isLoading: false,
      isStreaming: true,
      useStreaming: true,
      deepThinkingEnabled: false,
      setUseStreaming: vi.fn(),
      setDeepThinkingEnabled: vi.fn(),
      fetchSessions: vi.fn(),
      selectSession: vi.fn(),
      startNewSession: vi.fn(),
      deleteSession: vi.fn(),
      send: vi.fn(),
      stopStreaming: vi.fn()
    });
  });

  it("keeps the textarea editable while a response is streaming", async () => {
    const { ChatPage } = await import("./ChatPage");

    const html = renderToStaticMarkup(<ChatPage />);
    const textareaTag = html.match(/<textarea[^>]*>/)?.[0] ?? "";

    expect(textareaTag).not.toContain("disabled");
  }, 20000);
});
