import { describe, expect, it } from "vitest";

import { buildTraceOverview, buildTraceStageCards } from "@/pages/ragTraceDetailView";
import type { RagTraceNode } from "@/services/ragTraceService";

describe("ragTraceDetailView", () => {
  const nodes: RagTraceNode[] = [
    {
      traceId: "trace-1",
      nodeId: "STREAM_CANDIDATE",
      nodeType: "STREAM_CANDIDATE",
      nodeName: "STREAM_CANDIDATE",
      status: "FAILED",
      durationMs: 1200,
      details: {
        candidateId: "qwen-max",
        reason: "stream-error",
        deepThinking: true
      }
    },
    {
      traceId: "trace-1",
      nodeId: "STREAM_CANDIDATE",
      nodeType: "STREAM_CANDIDATE",
      nodeName: "STREAM_CANDIDATE",
      status: "SUCCESS",
      durationMs: 44020,
      details: {
        candidateId: "qwen-plus",
        attemptNo: 2,
        deepThinking: true,
        thinkingProvided: true,
        thinkingDurationMs: 49871
      }
    },
    {
      traceId: "trace-1",
      nodeId: "REWRITE",
      nodeType: "REWRITE",
      nodeName: "REWRITE",
      status: "SUCCESS",
      details: {
        originalQuestion: "开题报告怎么写",
        rewrittenQuestion: "开题报告怎么写 模板",
        historyCount: 2
      }
    },
    {
      traceId: "trace-1",
      nodeId: "INTENT",
      nodeType: "INTENT",
      nodeName: "INTENT",
      status: "SUCCESS",
      details: {
        resolvedIntentType: "KB_QA",
        fallbackModel: false,
        routeSource: "LEAF_SCORE",
        knowledgeBaseIds: [301, 302]
      }
    },
    {
      traceId: "trace-1",
      nodeId: "INTENT_SCORE",
      nodeType: "INTENT_SCORE",
      nodeName: "INTENT_SCORE",
      status: "SUCCESS",
      details: {
        leafCount: 3,
        matchedCount: 2,
        matchedLeaves: [
          { intentCode: "biz-kaiti", intentName: "开题报告", score: 0.93, kbId: 301 },
          { intentCode: "biz-lunwen", intentName: "论文写作", score: 0.76, kbId: 302 }
        ],
        routedKnowledgeBaseIds: [301, 302]
      }
    },
    {
      traceId: "trace-1",
      nodeId: "RETRIEVE",
      nodeType: "RETRIEVE",
      nodeName: "RETRIEVE",
      status: "SUCCESS",
      details: {
        scope: "KB_SCOPED",
        candidateCount: 2,
        knowledgeBaseIds: [301, 302],
        hits: [
          { documentTitle: "开题报告模板", chunkNo: 1, score: 0.92 },
          { documentTitle: "论文结构指南", chunkNo: 3, score: 0.75 }
        ]
      }
    },
    {
      traceId: "trace-1",
      nodeId: "RERANK",
      nodeType: "RERANK",
      nodeName: "RERANK",
      status: "SUCCESS",
      details: {
        rerankCount: 2,
        rerankedHits: [
          { documentTitle: "开题报告模板", chunkNo: 1, score: 0.97 },
          { documentTitle: "论文结构指南", chunkNo: 3, score: 0.78 }
        ]
      }
    },
    {
      traceId: "trace-1",
      nodeId: "GENERATE",
      nodeType: "GENERATE",
      nodeName: "GENERATE",
      status: "SUCCESS",
      details: {
        answerSource: "STREAM_MODEL",
        citationCount: 2,
        deepThinking: true,
        staticRefusal: false
      }
    }
  ];

  it("builds an overview from structured trace nodes", () => {
    expect(buildTraceOverview(nodes)).toEqual({
      originalQuestion: "开题报告怎么写",
      rewrittenQuestion: "开题报告怎么写 模板",
      intentType: "KB_QA",
      routeSource: "LEAF_SCORE",
      knowledgeBaseIds: ["301", "302"]
    });
  });

  it("builds detailed stage cards for display", () => {
    const cards = buildTraceStageCards(nodes);

    expect(cards).toHaveLength(7);
    expect(cards[0].fields).toContainEqual({ label: "叶子节点数", value: "3", span: "half" });
    expect(cards[1].fields).toContainEqual({ label: "决策来源", value: "LEAF_SCORE", span: "half" });
    expect(cards[2].fields).toContainEqual({ label: "原问题", value: "开题报告怎么写", span: "full" });
    expect(cards[0].entries).toContain("开题报告 · biz-kaiti · 0.93 · KB#301");
    expect(cards[3].entries).toContain("KB_SCOPED · 开题报告模板 · chunk#1 · 0.92");
    expect(cards[4].entries).toContain("开题报告模板 · chunk#1 · 0.97");
    expect(cards[5].fields).toContainEqual({ label: "最终候选模型", value: "qwen-plus", span: "full" });
    expect(cards[5].entries).toContain("#1 · qwen-max · 失败 · 1200ms · stream-error");
    expect(cards[6].fields).toContainEqual({ label: "答案来源", value: "STREAM_MODEL", span: "half" });
  });

  it("falls back to legacy payload keys for older trace nodes", () => {
    const legacyNodes: RagTraceNode[] = [
      {
        traceId: "trace-legacy",
        nodeId: "INTENT",
        nodeType: "INTENT",
        nodeName: "INTENT",
        status: "SUCCESS",
        payloadRef: "intent=KB_QA,fallback=true",
        details: {
          intent: "KB_QA",
          fallback: true,
          resolvedIntentType: "KB_QA",
          fallbackModel: true,
          routeSource: "MODEL_CLASSIFIER",
          rawPayload: "intent=KB_QA,fallback=true"
        }
      },
      {
        traceId: "trace-legacy",
        nodeId: "RETRIEVE",
        nodeType: "RETRIEVE",
        nodeName: "RETRIEVE",
        status: "SUCCESS",
        payloadRef: "candidateCount=2,kbIds=[301, 302]",
        details: {
          candidateCount: 2,
          kbIds: "[301, 302]",
          knowledgeBaseIds: ["301", "302"],
          scope: "KB_SCOPED",
          rawPayload: "candidateCount=2,kbIds=[301, 302]"
        }
      }
    ];

    const overview = buildTraceOverview(legacyNodes);
    const cards = buildTraceStageCards(legacyNodes);

    expect(overview.intentType).toBe("KB_QA");
    expect(cards[0].present).toBe(false);
    expect(cards[1].fields).toContainEqual({ label: "最终意图", value: "KB_QA", span: "half" });
    expect(cards[1].fields).toContainEqual({ label: "模型回退", value: "是", span: "half" });
    expect(cards[3].fields).toContainEqual({ label: "检索范围", value: "KB_SCOPED", span: "half" });
    expect(cards[3].fields).toContainEqual({ label: "召回数", value: "2", span: "half" });
    expect(cards[3].fields).toContainEqual({ label: "限定 KB", value: "301, 302", span: "full" });
  });

  it("fills missing steps with placeholder cards", () => {
    const cards = buildTraceStageCards([
      {
        traceId: "trace-2",
        nodeId: "REWRITE",
        nodeType: "REWRITE",
        nodeName: "REWRITE",
        status: "SUCCESS",
        details: {
          originalQuestion: "奖学金有哪些",
          rewrittenQuestion: "奖学金分类"
        }
      }
    ]);

    expect(cards.map((card) => card.stage)).toEqual([
      "INTENT_SCORE",
      "INTENT",
      "REWRITE",
      "RETRIEVE",
      "RERANK",
      "STREAM_CANDIDATE",
      "GENERATE"
    ]);
    expect(cards[0].present).toBe(false);
    expect(cards[2].present).toBe(true);
    expect(cards[6].fields).toContainEqual({
      label: "执行状态",
      value: "本次链路未经过该步骤",
      span: "full"
    });
  });
});
