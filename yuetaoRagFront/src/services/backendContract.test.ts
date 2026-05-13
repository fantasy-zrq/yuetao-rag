import { describe, expect, it } from "vitest";

import * as chatService from "@/services/chatService";
import * as knowledgeBaseService from "@/services/knowledgeBaseService";
import * as knowledgeDocumentService from "@/services/knowledgeDocumentService";
import * as ragTraceService from "@/services/ragTraceService";

describe("backend service coverage", () => {
  it("exposes every current knowledge base endpoint", () => {
    expect(knowledgeBaseService.listKnowledgeBases).toBeTypeOf("function");
    expect(knowledgeBaseService.getKnowledgeBase).toBeTypeOf("function");
    expect(knowledgeBaseService.createKnowledgeBase).toBeTypeOf("function");
    expect(knowledgeBaseService.updateKnowledgeBase).toBeTypeOf("function");
    expect(knowledgeBaseService.deleteKnowledgeBase).toBeTypeOf("function");
  });

  it("exposes every current knowledge document endpoint", () => {
    expect(knowledgeDocumentService.listDocuments).toBeTypeOf("function");
    expect(knowledgeDocumentService.getDocument).toBeTypeOf("function");
    expect(knowledgeDocumentService.uploadDocument).toBeTypeOf("function");
    expect(knowledgeDocumentService.updateDocument).toBeTypeOf("function");
    expect(knowledgeDocumentService.deleteDocument).toBeTypeOf("function");
    expect(knowledgeDocumentService.splitDocument).toBeTypeOf("function");
    expect(knowledgeDocumentService.toggleDocumentStatus).toBeTypeOf("function");
    expect(knowledgeDocumentService.listDocumentChunkLogs).toBeTypeOf("function");
  });

  it("exposes every current chat session/message endpoint", () => {
    expect(chatService.createChatSession).toBeTypeOf("function");
    expect(chatService.deleteChatSession).toBeTypeOf("function");
    expect(chatService.listChatSessions).toBeTypeOf("function");
    expect(chatService.getChatSession).toBeTypeOf("function");
    expect(chatService.createChatMessage).toBeTypeOf("function");
    expect(chatService.listChatMessages).toBeTypeOf("function");
    expect(chatService.getChatMessage).toBeTypeOf("function");
    expect(chatService.sendChatMessage).toBeTypeOf("function");
    expect(chatService.streamChatMessage).toBeTypeOf("function");
    expect(chatService.stopChatStream).toBeTypeOf("function");
  });

  it("exposes every current rag trace endpoint", () => {
    expect(ragTraceService.getRagTraceRuns).toBeTypeOf("function");
    expect(ragTraceService.getRagTraceDetail).toBeTypeOf("function");
  });
});
