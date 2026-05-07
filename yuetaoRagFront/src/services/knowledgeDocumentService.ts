import { api } from "@/services/api";
import type { KnowledgeDocument } from "@/types";

export interface UploadDocumentPayload {
  file: File;
  knowledgeBaseId: string;
  chunkMode: string;
  chunkConfig?: string;
  visibilityScope: string;
  minRankLevel?: number;
  authorizedDepartmentIds?: number[];
}

export interface UpdateDocumentPayload {
  id: string;
  title: string;
  chunkMode?: string;
  chunkConfig?: string;
  visibilityScope?: string;
  minRankLevel?: number;
  authorizedDepartmentIds?: number[];
}

export interface KnowledgeDocumentChunkLog {
  id: string;
  documentId: string;
  knowledgeBaseId?: string | null;
  operationType?: string | null;
  status?: string | null;
  chunkMode?: string | null;
  chunkCount?: number | null;
  splitCostMillis?: number | null;
  vectorCostMillis?: number | null;
  totalCostMillis?: number | null;
  errorMessage?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}

export async function listDocuments(knowledgeBaseId: string) {
  return api.get<KnowledgeDocument[], KnowledgeDocument[]>("/knowledge-documents/list", {
    params: { knowledgeBaseId }
  });
}

export async function getDocument(id: string) {
  return api.get<KnowledgeDocument, KnowledgeDocument>(`/knowledge-documents/detail/${id}`);
}

export async function uploadDocument(payload: UploadDocumentPayload) {
  const formData = new FormData();
  formData.append("file", payload.file);
  formData.append("knowledgeBaseId", payload.knowledgeBaseId);
  formData.append("chunkMode", payload.chunkMode);
  formData.append("visibilityScope", payload.visibilityScope);
  if (payload.chunkConfig) formData.append("chunkConfig", payload.chunkConfig);
  if (payload.minRankLevel !== undefined) formData.append("minRankLevel", String(payload.minRankLevel));
  payload.authorizedDepartmentIds?.forEach((id) => formData.append("authorizedDepartmentIds", String(id)));
  return api.post<KnowledgeDocument, KnowledgeDocument>("/knowledge-documents/create", formData);
}

export async function updateDocument(payload: UpdateDocumentPayload) {
  return api.post<KnowledgeDocument, KnowledgeDocument>("/knowledge-documents/update", payload);
}

export async function deleteDocument(id: string) {
  return api.post<void, void>("/knowledge-documents/delete", { id });
}

export async function splitDocument(documentId: string) {
  return api.post<void, void>("/knowledge-documents/split", { documentId });
}

export async function toggleDocumentStatus(id: string, status: string) {
  return api.post("/knowledge-documents/status", { id, status });
}

export async function listDocumentChunkLogs(documentId: string) {
  return api.get<KnowledgeDocumentChunkLog[], KnowledgeDocumentChunkLog[]>(`/knowledge-documents/${documentId}/chunk-logs`);
}
