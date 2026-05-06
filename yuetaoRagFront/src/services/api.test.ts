import { describe, expect, it } from "vitest";

import { API_BASE_URL, parseJsonWithLargeIntegerIds } from "./api";

describe("api config", () => {
  it("defaults to the backend context path", () => {
    expect(API_BASE_URL).toBe("/yuetaoRag");
  });

  it("keeps snowflake ids as strings before JSON parsing loses precision", () => {
    const payload = parseJsonWithLargeIntegerIds<{
      data: { id: string; sessionId: string; userId: string; assistantMessageId: string };
    }>(
      '{"data":{"id":2051951710308442114,"sessionId":2051951710308442114,"userId":202605031002,"assistantMessageId":2051951725961584642}}'
    );

    expect(payload.data.id).toBe("2051951710308442114");
    expect(payload.data.sessionId).toBe("2051951710308442114");
    expect(payload.data.userId).toBe("202605031002");
    expect(payload.data.assistantMessageId).toBe("2051951725961584642");
  });
});
