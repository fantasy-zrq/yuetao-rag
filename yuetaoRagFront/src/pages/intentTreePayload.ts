import type {
  IntentNodeCreatePayload,
  IntentNodeUpdatePayload
} from "@/services/intentTreeService";
import type { KnowledgeBase } from "@/types";

export interface IntentNodeDraft {
  intentCode: string;
  name: string;
  level: number;
  kind: number;
  parentCodeValue: string;
  description: string;
  examplesText: string;
  collectionName: string;
  kbId: string;
  mcpToolId: string;
  topK: string;
  sortOrder: string;
  enabled: boolean;
  promptSnippet: string;
  promptTemplate: string;
  paramPromptTemplate: string;
}

interface NormalizedIntentNodeDraft {
  description: string | null;
  examples: string[];
  collectionName: string | null;
  kbId: string | null;
  mcpToolId: string | null;
  topK: number | null;
  promptSnippet: string | null;
  promptTemplate: string | null;
  paramPromptTemplate: string | null;
}

export function buildCreateIntentNodePayload(
  draft: IntentNodeDraft,
  knowledgeBases: KnowledgeBase[]
): IntentNodeCreatePayload {
  const normalized = normalizeDraft(draft, knowledgeBases);
  return {
    intentCode: draft.intentCode.trim(),
    name: draft.name.trim(),
    level: draft.level,
    kind: draft.kind,
    parentCode: draft.parentCodeValue || undefined,
    description: normalized.description || undefined,
    examples: normalized.examples.length > 0 ? normalized.examples : undefined,
    collectionName: normalized.collectionName || undefined,
    kbId: normalized.kbId || undefined,
    mcpToolId: normalized.mcpToolId || undefined,
    topK: normalized.topK ?? undefined,
    sortOrder: Number(draft.sortOrder),
    enabled: draft.enabled ? 1 : 0,
    promptSnippet: normalized.promptSnippet || undefined,
    promptTemplate: normalized.promptTemplate || undefined,
    paramPromptTemplate: normalized.paramPromptTemplate || undefined
  };
}

export function buildUpdateIntentNodePayload(
  draft: IntentNodeDraft,
  knowledgeBases: KnowledgeBase[]
): IntentNodeUpdatePayload {
  const normalized = normalizeDraft(draft, knowledgeBases);
  return {
    name: draft.name.trim(),
    kind: draft.kind,
    parentCode: draft.parentCodeValue,
    description: normalized.description,
    examples: normalized.examples.length > 0 ? normalized.examples : [],
    collectionName: normalized.collectionName,
    kbId: normalized.kbId,
    mcpToolId: normalized.mcpToolId,
    topK: normalized.topK,
    sortOrder: Number(draft.sortOrder),
    enabled: draft.enabled ? 1 : 0,
    promptSnippet: normalized.promptSnippet,
    promptTemplate: normalized.promptTemplate,
    paramPromptTemplate: normalized.paramPromptTemplate
  };
}

function normalizeDraft(
  draft: IntentNodeDraft,
  knowledgeBases: KnowledgeBase[]
): NormalizedIntentNodeDraft {
  const examples = draft.examplesText
    .split("\n")
    .map((value) => value.trim())
    .filter(Boolean);
  const kbId = draft.kind === 0 ? normalizeNullableText(draft.kbId) : null;
  const collectionName = kbId
    ? knowledgeBases.find((item) => item.id === kbId)?.collectionName
      ?? normalizeNullableText(draft.collectionName)
    : null;
  return {
    description: normalizeNullableText(draft.description),
    examples,
    collectionName,
    kbId,
    mcpToolId: draft.kind === 2 ? normalizeNullableText(draft.mcpToolId) : null,
    topK: draft.topK ? Number(draft.topK) : null,
    promptSnippet: normalizeNullableText(draft.promptSnippet),
    promptTemplate: normalizeNullableText(draft.promptTemplate),
    paramPromptTemplate: normalizeNullableText(draft.paramPromptTemplate)
  };
}

function normalizeNullableText(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}
