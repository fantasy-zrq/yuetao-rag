import { describe, expect, it } from "vitest";

import {
  buildCreateIntentNodePayload,
  buildUpdateIntentNodePayload,
  type IntentNodeDraft
} from "@/pages/intentTreePayload";
import type { KnowledgeBase } from "@/types";

const baseDraft: IntentNodeDraft = {
  intentCode: "biz-kaiti",
  name: "开题报告",
  level: 1,
  kind: 0,
  parentCodeValue: "biz",
  description: "节点描述",
  examplesText: "问题1\n问题2",
  collectionName: "legacy_collection",
  kbId: "kb-1",
  mcpToolId: "sales_query",
  topK: "5",
  sortOrder: "2",
  enabled: true,
  promptSnippet: "规则片段",
  promptTemplate: "模板",
  paramPromptTemplate: "参数模板"
};

const knowledgeBases: KnowledgeBase[] = [{
  id: "kb-1",
  name: "开题报告知识库",
  collectionName: "kb_kaiti",
  status: "READY"
}];

describe("intentTreePayload", () => {
  it("buildUpdateIntentNodePayload should preserve explicit clear semantics for edit mode", () => {
    const payload = buildUpdateIntentNodePayload({
      ...baseDraft,
      kind: 1,
      parentCodeValue: "",
      description: "   ",
      examplesText: "",
      kbId: "",
      mcpToolId: "",
      topK: "",
      promptSnippet: " ",
      promptTemplate: "",
      paramPromptTemplate: ""
    }, knowledgeBases);

    expect(payload).toMatchObject({
      name: "开题报告",
      kind: 1,
      parentCode: "",
      description: null,
      examples: [],
      collectionName: null,
      kbId: null,
      mcpToolId: null,
      topK: null,
      promptSnippet: null,
      promptTemplate: null,
      paramPromptTemplate: null,
      sortOrder: 2,
      enabled: 1
    });
  });

  it("buildCreateIntentNodePayload should derive collectionName from selected knowledge base", () => {
    const payload = buildCreateIntentNodePayload(baseDraft, knowledgeBases);

    expect(payload).toMatchObject({
      intentCode: "biz-kaiti",
      name: "开题报告",
      parentCode: "biz",
      collectionName: "kb_kaiti",
      kbId: "kb-1",
      topK: 5,
      promptSnippet: "规则片段",
      promptTemplate: "模板",
      paramPromptTemplate: "参数模板"
    });
    expect(payload.examples).toEqual(["问题1", "问题2"]);
  });
});
