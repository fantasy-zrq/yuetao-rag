export interface ApiResult<T> {
  code: string;
  message?: string;
  data: T;
  requestId?: string;
}

export interface User {
  userId: string;
  username: string;
  displayName?: string;
  role: "admin" | "user" | string;
  token: string;
}

export interface KnowledgeBase {
  id: string;
  name: string;
  description?: string | null;
  status: string;
  embeddingModel?: string | null;
  collectionName?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface KnowledgeDocument {
  id: string;
  knowledgeBaseId: string;
  title: string;
  sourceType?: string | null;
  mimeType?: string | null;
  storageBucket?: string | null;
  storageKey?: string | null;
  storageEtag?: string | null;
  storageUrl?: string | null;
  fileSize?: number | null;
  parseStatus?: string | null;
  failReason?: string | null;
  chunkMode?: string | null;
  chunkConfig?: string | null;
  visibilityScope?: string | null;
  minRankLevel?: number | null;
  status?: string | null;
  chunkCount?: number | null;
  createTime?: string | null;
  updateTime?: string | null;
  authorizedDepartmentIds?: string[];
}

export interface ChatSession {
  id: string;
  userId?: string;
  title: string;
  status: string;
  lastActiveAt?: string | null;
  createTime?: string | null;
  updateTime?: string | null;
}

export type MessageRole = "user" | "assistant";

export interface ChatCitation {
  index: number;
  documentId?: string;
  documentTitle?: string;
  chunkId?: string;
  chunkNo?: number;
  referenceLabel?: string;
  snippet?: string;
}

export interface ChatMessage {
  id: string;
  sessionId?: string;
  userId?: string;
  role: MessageRole;
  content: string;
  contentType?: string | null;
  sequenceNo?: number | null;
  traceId?: string | null;
  modelProvider?: string | null;
  modelName?: string | null;
  createTime?: string | null;
  status?: "streaming" | "done" | "error";
  citations?: ChatCitation[];
  thinkingContent?: string | null;
  thinkingDurationMs?: number | null;
  isThinking?: boolean;
  thinking?: string;
}

export interface ChatResponse {
  sessionId: string;
  userMessageId: string;
  assistantMessageId: string;
  traceId?: string | null;
  intentType?: string | null;
  knowledgeHit?: boolean | null;
  rewrittenQuery?: string | null;
  answer: string;
  citations?: ChatCitation[];
}

export interface ChatStreamEvent {
  event: "message_start" | "delta" | "thinking_delta" | "citation" | "reset" | "message_end" | "error" | string;
  traceId?: string;
  sessionId?: string;
  candidateId?: string;
  attemptNo?: number;
  content?: string;
  type?: string;
  reason?: string;
  code?: string;
  message?: string;
  assistantMessageId?: string;
  finished?: boolean;
  citations?: ChatCitation[];
}
