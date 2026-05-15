import { afterEach, describe, expect, it, vi } from "vitest";

import { streamChatMessage } from "./chatService";

vi.mock("@/utils/storage", () => ({
  storage: {
    getToken: vi.fn(() => null)
  }
}));

function createSseResponse(chunks: string[]) {
  const encoder = new TextEncoder();
  return new Response(
    new ReadableStream({
      start(controller) {
        chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
        controller.close();
      }
    }),
    {
      status: 200,
      headers: { "Content-Type": "text/event-stream" }
    }
  );
}

describe("chatService.streamChatMessage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("parses multi-line SSE data blocks as a single event payload", async () => {
    const onEvent = vi.fn();
    const onError = vi.fn();
    const onDone = vi.fn();

    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(createSseResponse([
      "event: message\n"
        + "data: {\"event\":\"delta\",\n"
        + "data: \"content\":\"第一行\\n第二行\"}\n\n"
    ])));

    await streamChatMessage(
      { sessionId: "1", userId: "1", message: "你好" },
      { onEvent, onError, onDone }
    );

    expect(onError).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalledOnce();
    expect(onEvent).toHaveBeenCalledWith(
      expect.objectContaining({
        event: "delta",
        content: "第一行\n第二行"
      })
    );
  });
});
