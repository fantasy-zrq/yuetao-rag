import { api } from "@/services/api";
import type { KnowledgeBase } from "@/types";

export interface CreateKnowledgeBasePayload {
  name: string;
  description?: string;
  status: string;
  embeddingModel?: string;
  collectionName?: string;
}

export interface UpdateKnowledgeBasePayload {
  id: string;
  name: string;
}

export async function listKnowledgeBases() {
  return api.get<KnowledgeBase[], KnowledgeBase[]>("/knowledge-bases/list");
}

export async function getKnowledgeBase(id: string) {
  return api.get<KnowledgeBase, KnowledgeBase>(`/knowledge-bases/detail/${id}`);
}

export async function createKnowledgeBase(payload: CreateKnowledgeBasePayload) {
  return api.post<KnowledgeBase, KnowledgeBase>("/knowledge-bases/create", payload);
}

export async function updateKnowledgeBase(payload: UpdateKnowledgeBasePayload) {
  return api.post<KnowledgeBase, KnowledgeBase>("/knowledge-bases/update", payload);
}

export async function deleteKnowledgeBase(id: string) {
  return api.post<void, void>("/knowledge-bases/delete", { id });
}
