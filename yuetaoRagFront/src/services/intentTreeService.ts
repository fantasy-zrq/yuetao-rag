import { api } from "./api";

export interface IntentNodeTree {
  id: string;
  intentCode: string;
  name: string;
  level: number;
  parentCode?: string | null;
  description?: string | null;
  examples?: string | null;
  collectionName?: string | null;
  mcpToolId?: string | null;
  topK?: number | null;
  kind: number;
  sortOrder: number;
  enabled: number;
  promptSnippet?: string | null;
  promptTemplate?: string | null;
  paramPromptTemplate?: string | null;
  children?: IntentNodeTree[];
}

export interface IntentNodeCreatePayload {
  intentCode: string;
  name: string;
  level: number;
  parentCode?: string;
  kind: number;
  description?: string;
  examples?: string[];
  collectionName?: string;
  mcpToolId?: string;
  topK?: number;
  sortOrder?: number;
  enabled?: number;
  promptSnippet?: string;
  promptTemplate?: string;
  paramPromptTemplate?: string;
}

export interface IntentNodeUpdatePayload {
  name?: string;
  description?: string;
  examples?: string[];
  collectionName?: string;
  mcpToolId?: string;
  topK?: number;
  kind?: number;
  sortOrder?: number;
  enabled?: number;
  promptSnippet?: string;
  promptTemplate?: string;
  paramPromptTemplate?: string;
  parentCode?: string;
}

export async function getIntentTree(): Promise<IntentNodeTree[]> {
  return api.get("/intent-tree/trees");
}

export async function createIntentNode(payload: IntentNodeCreatePayload): Promise<string> {
  return api.post("/intent-tree", payload);
}

export async function updateIntentNode(id: string, payload: IntentNodeUpdatePayload): Promise<void> {
  return api.put(`/intent-tree/${id}`, payload);
}

export async function deleteIntentNode(id: string): Promise<void> {
  return api.delete(`/intent-tree/${id}`);
}

export async function batchEnableIntentNodes(ids: string[]): Promise<void> {
  return api.post("/intent-tree/batch/enable", { ids });
}

export async function batchDisableIntentNodes(ids: string[]): Promise<void> {
  return api.post("/intent-tree/batch/disable", { ids });
}

export async function batchDeleteIntentNodes(ids: string[]): Promise<void> {
  return api.post("/intent-tree/batch/delete", { ids });
}
