import { api } from "@/services/api";

export interface RagTraceRun {
  traceId: string;
  traceName?: string | null;
  conversationId?: string | null;
  taskId?: string | null;
  userId?: string | null;
  username?: string | null;
  status?: string | null;
  durationMs?: number | null;
  startTime?: string | null;
  endTime?: string | null;
}

export interface RagTraceNode {
  traceId: string;
  nodeId: string;
  parentNodeId?: string | null;
  depth?: number | null;
  nodeType?: string | null;
  nodeName?: string | null;
  status?: string | null;
  errorMessage?: string | null;
  payloadRef?: string | null;
  details?: Record<string, unknown> | null;
  durationMs?: number | null;
  startTime?: string | null;
  endTime?: string | null;
}

export interface RagTraceDetail {
  run: RagTraceRun;
  nodes: RagTraceNode[];
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export async function getRagTraceRuns(query: { current?: number; size?: number; traceId?: string } = {}) {
  return api.get<PageResult<RagTraceRun>, PageResult<RagTraceRun>>("/rag/traces/runs", {
    params: {
      current: query.current ?? 1,
      size: query.size ?? 10,
      traceId: query.traceId || undefined
    }
  });
}

export async function getRagTraceDetail(traceId: string) {
  return api.get<RagTraceDetail, RagTraceDetail>(`/rag/traces/runs/${traceId}`);
}
