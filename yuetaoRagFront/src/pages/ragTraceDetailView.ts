import type { RagTraceNode } from "@/services/ragTraceService";

export interface TraceOverview {
  originalQuestion?: string;
  rewrittenQuestion?: string;
  intentType?: string;
  routeSource?: string;
  knowledgeBaseIds: string[];
}

export interface TraceField {
  label: string;
  value: string;
  span?: "half" | "full";
}

export interface TraceStageCard {
  key: string;
  title: string;
  stage: string;
  status: string;
  present: boolean;
  durationMs?: number | null;
  startTime?: string | null;
  endTime?: string | null;
  fields: TraceField[];
  entries: string[];
  entryTitle?: string;
  rawPayload?: string | null;
}

const TRACE_STAGE_ORDER = ["INTENT_SCORE", "INTENT", "REWRITE", "RETRIEVE", "RERANK", "STREAM_CANDIDATE", "GENERATE"] as const;

const STAGE_TITLE: Record<string, string> = {
  INTENT_SCORE: "意图打分",
  INTENT: "意图识别",
  REWRITE: "问题改写",
  RETRIEVE: "向量检索",
  RERANK: "重排序",
  STREAM_CANDIDATE: "流式候选模型",
  GENERATE: "答案生成"
};

const STAGE_ENTRY_TITLE: Record<string, string> = {
  INTENT_SCORE: "命中详情",
  RETRIEVE: "召回结果",
  RERANK: "重排结果",
  STREAM_CANDIDATE: "候选尝试"
};

export function buildTraceOverview(nodes: RagTraceNode[]): TraceOverview {
  const grouped = groupNodesByStage(nodes);
  const rewrite = firstDetails(grouped.get("REWRITE"));
  const intent = firstDetails(grouped.get("INTENT"));
  const score = firstDetails(grouped.get("INTENT_SCORE"));
  return {
    originalQuestion: asText(rewrite?.originalQuestion) || asText(intent?.originalQuestion) || asText(score?.question),
    rewrittenQuestion: asText(rewrite?.rewrittenQuestion) || asText(rewrite?.rewrittenQuery),
    intentType: asText(intent?.resolvedIntentType) || asText(intent?.intent),
    routeSource: asText(intent?.routeSource),
    knowledgeBaseIds:
      toStringList(intent?.knowledgeBaseIds).length > 0
        ? toStringList(intent?.knowledgeBaseIds)
        : toStringList(score?.routedKnowledgeBaseIds).length > 0
          ? toStringList(score?.routedKnowledgeBaseIds)
          : toStringList(score?.kbIds)
  };
}

export function buildTraceStageCards(nodes: RagTraceNode[]): TraceStageCard[] {
  const grouped = groupNodesByStage(nodes);
  return TRACE_STAGE_ORDER.map((stage) => buildStageCard(stage, grouped.get(stage) || []));
}

function buildStageCard(stage: (typeof TRACE_STAGE_ORDER)[number], nodes: RagTraceNode[]): TraceStageCard {
  if (!nodes.length) {
    return {
      key: stage,
      title: STAGE_TITLE[stage],
      stage,
      status: "未执行",
      present: false,
      durationMs: null,
      startTime: null,
      endTime: null,
      fields: [
        {
          label: "执行状态",
          value: "本次链路未经过该步骤",
          span: "full"
        }
      ],
      entries: []
    };
  }

  const durationMs = nodes.reduce((sum, node) => sum + toNumber(node.durationMs), 0);
  const lastNode = nodes[nodes.length - 1];
  const card: TraceStageCard = {
    key: stage,
    title: STAGE_TITLE[stage],
    stage,
    status: resolveStageStatus(nodes),
    present: true,
    durationMs: durationMs || lastNode.durationMs,
    startTime: nodes[0].startTime ?? null,
    endTime: lastNode.endTime ?? null,
    fields: [],
    entries: [],
    entryTitle: STAGE_ENTRY_TITLE[stage],
    rawPayload: nodes.map((node) => node.payloadRef).filter(Boolean).join("\n") || null
  };

  if (stage === "INTENT_SCORE") {
    buildIntentScoreCard(card, nodes);
    return finalizeCard(card, nodes);
  }
  if (stage === "INTENT") {
    buildIntentCard(card, nodes);
    return finalizeCard(card, nodes);
  }
  if (stage === "REWRITE") {
    buildRewriteCard(card, nodes);
    return finalizeCard(card, nodes);
  }
  if (stage === "RETRIEVE") {
    buildRetrieveCard(card, nodes);
    return finalizeCard(card, nodes);
  }
  if (stage === "RERANK") {
    buildRerankCard(card, nodes);
    return finalizeCard(card, nodes);
  }
  if (stage === "STREAM_CANDIDATE") {
    buildStreamCandidateCard(card, nodes);
    return finalizeCard(card, nodes);
  }
  buildGenerateCard(card, nodes);
  return finalizeCard(card, nodes);
}

function buildIntentScoreCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const details = lastDetails(nodes);
  pushField(card.fields, "叶子节点数", details?.leafCount);
  pushField(card.fields, "命中数", details?.matchedCount ?? details?.matchCount);
  const kbIds = toStringList(details?.routedKnowledgeBaseIds).length > 0
    ? toStringList(details?.routedKnowledgeBaseIds)
    : toStringList(details?.kbIds);
  if (kbIds.length) {
    card.fields.push({ label: "候选 KB", value: kbIds.join(", "), span: "full" });
  }
  card.entries = collectMatchedLeafEntries(nodes);
}

function buildIntentCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const details = lastDetails(nodes);
  pushField(card.fields, "最终意图", details?.resolvedIntentType ?? details?.intent);
  pushField(card.fields, "决策来源", details?.routeSource);
  pushField(card.fields, "模型回退", asBooleanText(details?.fallbackModel ?? details?.fallback));
  pushField(card.fields, "命中意图", details?.matchedIntentCode);
  const kbIds = toStringList(details?.knowledgeBaseIds);
  if (kbIds.length) {
    card.fields.push({ label: "路由 KB", value: kbIds.join(", "), span: "full" });
  }
}

function buildRewriteCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const details = lastDetails(nodes);
  pushField(card.fields, "原问题", details?.originalQuestion, "full");
  pushField(card.fields, "改写后", details?.rewrittenQuestion ?? details?.rewrittenQuery, "full");
  pushField(card.fields, "历史消息数", details?.historyCount);
}

function buildRetrieveCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const latest = lastDetails(nodes);
  pushField(card.fields, "检索范围", latest?.scope);
  pushField(card.fields, "召回数", latest?.candidateCount);
  if (nodes.length > 1) {
    pushField(card.fields, "检索次数", nodes.length);
  }
  const kbIds = toStringList(latest?.knowledgeBaseIds);
  if (kbIds.length) {
    card.fields.push({ label: "限定 KB", value: kbIds.join(", "), span: "full" });
  }
  card.entries = collectChunkEntries(nodes, "hits");
}

function buildRerankCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const latest = lastDetails(nodes);
  pushField(card.fields, "重排数", latest?.rerankCount);
  if (nodes.length > 1) {
    pushField(card.fields, "重排次数", nodes.length);
  }
  card.entries = collectChunkEntries(nodes, "rerankedHits");
}

function buildStreamCandidateCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const latest = lastDetails(nodes);
  pushField(card.fields, "最终候选模型", latest?.candidateId, "full");
  pushField(card.fields, "尝试次数", nodes.length);
  pushField(card.fields, "深度思考", asBooleanText(latest?.deepThinking));
  pushField(card.fields, "思考耗时", formatOptionalDuration(latest?.thinkingDurationMs));
  card.entries = nodes.map((node, index) => {
    const details = asRecord(node.details);
    const candidateId = asText(details?.candidateId) || "未知模型";
    const status = statusLabel(node.status);
    const reason = asText(details?.reason);
    const duration = formatOptionalDuration(node.durationMs);
    return [`#${index + 1}`, candidateId, status, duration, reason].filter(Boolean).join(" · ");
  });
}

function buildGenerateCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  const details = lastDetails(nodes);
  pushField(card.fields, "答案来源", details?.answerSource);
  pushField(card.fields, "引用数", details?.citationCount);
  pushField(card.fields, "深度思考", asBooleanText(details?.deepThinking));
  pushField(card.fields, "静态拒答", asBooleanText(details?.staticRefusal));
  pushField(card.fields, "终止原因", details?.termination, "full");
  pushField(card.fields, "失败原因", details?.failure, "full");
}

function finalizeCard(card: TraceStageCard, nodes: RagTraceNode[]) {
  if (!card.fields.length && !card.entries.length) {
    const details = lastDetails(nodes);
    if (details) {
      Object.entries(details)
        .filter(([key]) => !["rawPayload", "hits", "rerankedHits", "matchedLeaves"].includes(key))
        .forEach(([label, value]) => pushField(card.fields, formatDetailLabel(label), value));
    }
  }
  if (!card.fields.length && !card.entries.length) {
    card.fields.push({
      label: "执行状态",
      value: "该步骤已执行，但没有可展示的结构化详情",
      span: "full"
    });
  }
  return card;
}

function groupNodesByStage(nodes: RagTraceNode[]) {
  const grouped = new Map<string, RagTraceNode[]>();
  nodes.forEach((node) => {
    const stage = resolveStage(node);
    const stageNodes = grouped.get(stage) || [];
    stageNodes.push(node);
    grouped.set(stage, stageNodes);
  });
  return grouped;
}

function firstDetails(nodes?: RagTraceNode[]) {
  if (!nodes?.length) return null;
  return asRecord(nodes[0].details);
}

function lastDetails(nodes: RagTraceNode[]) {
  if (!nodes.length) return null;
  return asRecord(nodes[nodes.length - 1].details);
}

function collectMatchedLeafEntries(nodes: RagTraceNode[]) {
  const entries = nodes.flatMap((node) => {
    const details = asRecord(node.details);
    return toObjectList(details?.matchedLeaves).map((item) => {
      const label = asText(item.intentName) || asText(item.intentCode) || "-";
      const intentCode = asText(item.intentCode);
      const score = formatScore(item.score);
      const kbId = asText(item.kbId);
      return [label, intentCode, score, kbId ? `KB#${kbId}` : ""].filter(Boolean).join(" · ");
    });
  });
  return dedupe(entries);
}

function collectChunkEntries(nodes: RagTraceNode[], key: "hits" | "rerankedHits") {
  const entries = nodes.flatMap((node) => {
    const details = asRecord(node.details);
    const scope = asText(details?.scope);
    return toObjectList(details?.[key]).map((item) => {
      const content = formatChunkEntry(item);
      return scope ? `${scope} · ${content}` : content;
    });
  });
  return dedupe(entries);
}

function dedupe(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)));
}

function resolveStage(node: RagTraceNode) {
  return String(node.nodeType || node.nodeName || node.nodeId || "").toUpperCase();
}

function resolveStageStatus(nodes: RagTraceNode[]) {
  const lastStatus = String(nodes[nodes.length - 1]?.status || "").toUpperCase();
  if (lastStatus) {
    return lastStatus;
  }
  if (nodes.some((node) => String(node.status || "").toUpperCase() === "FAILED")) {
    return "FAILED";
  }
  if (nodes.some((node) => String(node.status || "").toUpperCase() === "RUNNING")) {
    return "RUNNING";
  }
  return "SUCCESS";
}

function statusLabel(value?: string | null) {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "SUCCESS") return "成功";
  if (normalized === "FAILED") return "失败";
  if (normalized === "RUNNING") return "执行中";
  if (normalized === "SKIPPED") return "跳过";
  if (normalized === "CANCELLED") return "已取消";
  return normalized || "-";
}

function asRecord(value: unknown) {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

function toObjectList(value: unknown) {
  return Array.isArray(value) ? (value.map(asRecord).filter(Boolean) as Array<Record<string, unknown>>) : [];
}

function toStringList(value: unknown) {
  return Array.isArray(value) ? value.map((item) => String(item)) : [];
}

function toNumber(value?: string | number | null) {
  if (value === null || value === undefined || value === "") return 0;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

function asText(value: unknown) {
  if (value === null || value === undefined || value === "") return "";
  return String(value);
}

function asBooleanText(value: unknown) {
  if (value === null || value === undefined || value === "") return "";
  if (typeof value === "boolean") return value ? "是" : "否";
  if (typeof value === "string") {
    if (value.toLowerCase() === "true") return "是";
    if (value.toLowerCase() === "false") return "否";
  }
  return Boolean(value) ? "是" : "否";
}

function formatScore(value: unknown) {
  if (value === null || value === undefined || value === "") return "";
  const score = typeof value === "number" ? value : Number(value);
  return Number.isFinite(score) ? score.toFixed(2) : String(value);
}

function formatChunkEntry(item: Record<string, unknown>) {
  const title = asText(item.documentTitle) || "未命名文档";
  const chunkNo = asText(item.chunkNo);
  const score = formatScore(item.score);
  return [title, chunkNo ? `chunk#${chunkNo}` : "", score].filter(Boolean).join(" · ");
}

function formatOptionalDuration(value: unknown) {
  if (value === null || value === undefined || value === "") return "";
  const duration = typeof value === "number" ? value : Number(value);
  return Number.isFinite(duration) ? `${Math.round(duration)}ms` : String(value);
}

function formatDetailLabel(label: string) {
  return label.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/_/g, " ").trim();
}

function pushField(fields: TraceField[], label: string, value: unknown, span: "half" | "full" = "half") {
  const text = asText(value);
  if (!text) return;
  fields.push({ label, value: text, span });
}
