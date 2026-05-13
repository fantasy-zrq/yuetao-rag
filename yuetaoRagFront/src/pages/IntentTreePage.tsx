import { FormEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ChevronDown,
  ChevronRight,
  Layers,
  Pencil,
  Plus,
  RefreshCw,
  Trash2
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { Modal } from "@/components/Modal";
import {
  createIntentNode,
  deleteIntentNode,
  getIntentTree,
  updateIntentNode,
  type IntentNodeCreatePayload,
  type IntentNodeTree,
  type IntentNodeUpdatePayload
} from "@/services/intentTreeService";
import { listKnowledgeBases } from "@/services/knowledgeBaseService";
import type { KnowledgeBase } from "@/types";
import {
  buildCreateIntentNodePayload,
  buildUpdateIntentNodePayload
} from "@/pages/intentTreePayload";

const LEVEL_LABELS: Record<number, string> = { 0: "DOMAIN", 1: "CATEGORY", 2: "TOPIC" };
const KIND_LABELS: Record<number, string> = { 0: "KB", 1: "SYSTEM", 2: "MCP" };
const LEVEL_DESC: Record<number, string> = { 0: "顶层领域", 1: "业务分类", 2: "具体主题" };
const KIND_DESC: Record<number, string> = { 0: "知识库检索", 1: "系统交互", 2: "工具调用" };

const LEVEL_BADGE_CLASS: Record<number, string> = {
  0: "intent-badge-domain",
  1: "intent-badge-category",
  2: "intent-badge-topic"
};

const KIND_BADGE_CLASS: Record<number, string> = {
  0: "intent-badge-kb",
  1: "intent-badge-system",
  2: "intent-badge-mcp"
};

export function IntentTreePage() {
  const [searchParams] = useSearchParams();
  const [tree, setTree] = useState<IntentNodeTree[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);
  const [expandedMap, setExpandedMap] = useState<Record<string, boolean>>({});
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogMode, setDialogMode] = useState<"create" | "edit">("create");
  const [dialogParent, setDialogParent] = useState<IntentNodeTree | null>(null);
  const [editingNode, setEditingNode] = useState<IntentNodeTree | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<IntentNodeTree | null>(null);
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([]);

  const selectedNode = useMemo(() => findNodeByCode(tree, selectedCode), [tree, selectedCode]);
  const flatNodes = useMemo(() => flattenTree(tree), [tree]);

  async function loadTree() {
    setLoading(true);
    try {
      const data = await getIntentTree();
      setTree(data || []);
      const focusCode = searchParams.get("intentCode")?.trim();
      if (focusCode && findNodeByCode(data || [], focusCode)) {
        setSelectedCode(focusCode);
        expandPathToCode(data || [], focusCode);
      } else if (!selectedCode && data?.length) {
        setSelectedCode(data[0].intentCode);
      }
    } catch (error) {
      toast.error((error as Error).message || "加载意图树失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadTree();
  }, []);

  useEffect(() => {
    listKnowledgeBases()
      .then((data) => setKnowledgeBases(data || []))
      .catch(() => setKnowledgeBases([]));
  }, []);

  function expandPathToCode(nodes: IntentNodeTree[], targetCode: string) {
    const path = findPath(nodes, targetCode);
    if (path) {
      setExpandedMap((prev) => {
        const next = { ...prev };
        path.forEach((n) => (next[n.intentCode] = true));
        return next;
      });
    }
  }

  function openCreateDialog(parent: IntentNodeTree | null) {
    setDialogMode("create");
    setDialogParent(parent);
    setEditingNode(null);
    setDialogOpen(true);
  }

  function openEditDialog(node: IntentNodeTree) {
    setDialogMode("edit");
    setEditingNode(node);
    setDialogParent(null);
    setDialogOpen(true);
  }

  async function handleDelete() {
    if (!deleteTarget) return;
    try {
      await deleteIntentNode(deleteTarget.id);
      toast.success("已删除");
      if (selectedCode === deleteTarget.intentCode) setSelectedCode(null);
      setDeleteTarget(null);
      loadTree();
    } catch (error) {
      toast.error((error as Error).message || "删除失败");
    }
  }

  function renderNode(node: IntentNodeTree, depth = 0) {
    const hasChildren = Boolean(node.children?.length);
    const isExpanded = expandedMap[node.intentCode] ?? true;
    const isSelected = selectedCode === node.intentCode;

    return (
      <div key={node.intentCode}>
        <div
          className={`intent-tree-row ${isSelected ? "intent-tree-row--selected" : ""} ${node.enabled === 0 ? "intent-tree-row--disabled" : ""}`}
          style={{ paddingLeft: `${depth * 20 + 12}px` }}
          onClick={() => setSelectedCode(node.intentCode)}
        >
          {hasChildren ? (
            <button
              className="intent-tree-toggle"
              onClick={(e) => {
                e.stopPropagation();
                setExpandedMap((prev) => ({ ...prev, [node.intentCode]: !isExpanded }));
              }}
            >
              {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            </button>
          ) : (
            <span className="intent-tree-toggle-spacer" />
          )}
          <span className="intent-tree-name">{node.name}</span>
          <span className={`intent-level-badge ${LEVEL_BADGE_CLASS[node.level] || ""}`}>
            {LEVEL_LABELS[node.level] || `L${node.level}`}
          </span>
          <span className={`intent-kind-badge ${KIND_BADGE_CLASS[node.kind] || ""}`}>
            {KIND_LABELS[node.kind] || `K${node.kind}`}
          </span>
        </div>
        {hasChildren && isExpanded && node.children!.map((child) => renderNode(child, depth + 1))}
      </div>
    );
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1 className="admin-page-title">意图树配置</h1>
          <p className="admin-page-subtitle">配置意图层级、类型和节点关系</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="secondary" onClick={loadTree} disabled={loading}>
            <RefreshCw size={16} className={loading ? "animate-spin" : ""} />
            刷新
          </Button>
          <Button onClick={() => openCreateDialog(null)}>
            <Plus size={16} />
            新建根节点
          </Button>
        </div>
      </header>

      <div className="intent-layout">
        {/* Left: Tree Panel */}
        <div className="intent-card">
          <div className="intent-card-header">
            <h2>意图树结构</h2>
            <p>点击节点查看详情或进行编辑</p>
          </div>
          <div className="intent-card-body">
            {loading ? (
              <div className="intent-empty">加载中...</div>
            ) : tree.length === 0 ? (
              <div className="intent-empty">暂无节点，请先创建</div>
            ) : (
              <div className="intent-tree-list">
                {tree.map((node) => renderNode(node))}
              </div>
            )}
          </div>
        </div>

        {/* Right: Detail Panel */}
        <div className="intent-card">
          <div className="intent-card-header">
            <h2>节点详情</h2>
            <p>查看并管理当前选择的节点</p>
          </div>
          <div className="intent-card-body">
            {!selectedNode ? (
              <div className="intent-empty">请选择左侧节点</div>
            ) : (
              <div className="intent-detail-content">
                {/* Name + Badges + Actions */}
                <div className="intent-detail-top">
                  <div>
                    <div className="intent-detail-title-row">
                      <h3>{selectedNode.name}</h3>
                      <span className={`intent-level-badge ${LEVEL_BADGE_CLASS[selectedNode.level] || ""}`}>
                        {LEVEL_LABELS[selectedNode.level]}
                      </span>
                      <span className={`intent-kind-badge ${KIND_BADGE_CLASS[selectedNode.kind] || ""}`}>
                        {KIND_LABELS[selectedNode.kind]}
                      </span>
                      <span className={`intent-status-badge ${selectedNode.enabled === 1 ? "intent-status-enabled" : "intent-status-disabled"}`}>
                        {selectedNode.enabled === 1 ? "启用" : "停用"}
                      </span>
                    </div>
                    <p className="intent-detail-code">{selectedNode.intentCode}</p>
                  </div>
                  <div className="intent-detail-btns">
                    <Button onClick={() => openCreateDialog(selectedNode)}>
                      <Plus size={14} />
                      新建子节点
                    </Button>
                    <Button variant="secondary" onClick={() => openEditDialog(selectedNode)}>
                      <Pencil size={14} />
                      编辑节点
                    </Button>
                    <Button variant="ghost" className="danger" onClick={() => setDeleteTarget(selectedNode)}>
                      <Trash2 size={14} />
                      删除节点
                    </Button>
                  </div>
                </div>

                {/* Metadata rows */}
                <div className="intent-meta-list">
                  <div className="intent-meta-row">
                    <span>父节点</span>
                    <span>{selectedNode.parentCode || "ROOT"}</span>
                  </div>
                  <div className="intent-meta-row">
                    <span>排序</span>
                    <span>{selectedNode.sortOrder ?? 0}</span>
                  </div>
                  <div className="intent-meta-row">
                    <span>知识库 ID</span>
                    <span>{selectedNode.kbId || "-"}</span>
                  </div>
                  <div className="intent-meta-row">
                    <span>Collection</span>
                    <span>{selectedNode.collectionName || "-"}</span>
                  </div>
                  <div className="intent-meta-row">
                    <span>节点 TopK</span>
                    <span>{selectedNode.topK ?? "默认（全局）"}</span>
                  </div>
                  {selectedNode.mcpToolId && (
                    <div className="intent-meta-row">
                      <span>MCP 工具</span>
                      <span>{selectedNode.mcpToolId}</span>
                    </div>
                  )}
                </div>

                {/* Description */}
                <div className="intent-section">
                  <h4>描述</h4>
                  <p>{selectedNode.description || "暂无描述"}</p>
                </div>

                {/* Examples */}
                <div className="intent-section">
                  <h4>示例问题</h4>
                  <div className="intent-example-list">
                    {parseExamples(selectedNode.examples).length === 0 ? (
                      <span className="intent-empty-text">暂无示例</span>
                    ) : (
                      parseExamples(selectedNode.examples).map((ex, i) => (
                        <span key={i} className="intent-example-tag">{ex}</span>
                      ))
                    )}
                  </div>
                </div>

                {/* Prompt Snippet */}
                {selectedNode.promptSnippet && (
                  <div className="intent-section">
                    <h4>Prompt 规则片段</h4>
                    <pre className="intent-pre">{selectedNode.promptSnippet}</pre>
                  </div>
                )}

                {/* Prompt Template */}
                {selectedNode.promptTemplate && (
                  <div className="intent-section">
                    <h4>Prompt 模板</h4>
                    <pre className="intent-pre">{selectedNode.promptTemplate}</pre>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Create/Edit Dialog */}
      <IntentNodeForm
        open={dialogOpen}
        mode={dialogMode}
        node={editingNode}
        parentNode={dialogParent}
        flatNodes={flatNodes}
        knowledgeBases={knowledgeBases}
        onClose={() => setDialogOpen(false)}
        onDone={() => {
          setDialogOpen(false);
          loadTree();
        }}
      />

      {/* Delete Confirmation */}
      <Modal title="确认删除节点？" open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)}>
        <p className="intent-delete-msg">
          节点「{deleteTarget?.name}」将被永久删除，无法恢复。
        </p>
        <div className="intent-delete-actions">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>取消</Button>
          <Button variant="danger" onClick={handleDelete}>删除</Button>
        </div>
      </Modal>
    </section>
  );
}

function IntentNodeForm({
  open,
  mode,
  node,
  parentNode,
  flatNodes,
  knowledgeBases,
  onClose,
  onDone
}: {
  open: boolean;
  mode: "create" | "edit";
  node: IntentNodeTree | null;
  parentNode: IntentNodeTree | null;
  flatNodes: IntentNodeTree[];
  knowledgeBases: KnowledgeBase[];
  onClose: () => void;
  onDone: () => void;
}) {
  const [intentCode, setIntentCode] = useState("");
  const [name, setName] = useState("");
  const [level, setLevel] = useState(0);
  const [kind, setKind] = useState(0);
  const [parentCodeValue, setParentCodeValue] = useState("");
  const [description, setDescription] = useState("");
  const [examplesText, setExamplesText] = useState("");
  const [collectionName, setCollectionName] = useState("");
  const [kbId, setKbId] = useState("");
  const [mcpToolId, setMcpToolId] = useState("");
  const [topK, setTopK] = useState("");
  const [sortOrder, setSortOrder] = useState("0");
  const [enabled, setEnabled] = useState(true);
  const [promptSnippet, setPromptSnippet] = useState("");
  const [promptTemplate, setPromptTemplate] = useState("");
  const [paramPromptTemplate, setParamPromptTemplate] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (mode === "edit" && node) {
      setIntentCode(node.intentCode);
      setName(node.name);
      setLevel(node.level);
      setKind(node.kind);
      setParentCodeValue(node.parentCode || "");
      setDescription(node.description || "");
      setExamplesText(parseExamples(node.examples).join("\n"));
      setCollectionName(node.collectionName || "");
      setKbId(node.kbId || "");
      setMcpToolId(node.mcpToolId || "");
      setTopK(node.topK != null ? String(node.topK) : "");
      setSortOrder(String(node.sortOrder));
      setEnabled(node.enabled === 1);
      setPromptSnippet(node.promptSnippet || "");
      setPromptTemplate(node.promptTemplate || "");
      setParamPromptTemplate(node.paramPromptTemplate || "");
    } else {
      const nextLevel = parentNode ? Math.min((parentNode.level ?? 0) + 1, 2) : 0;
      setIntentCode("");
      setName("");
      setLevel(nextLevel);
      setKind(parentNode?.kind ?? 0);
      setParentCodeValue(parentNode?.intentCode || "");
      setDescription("");
      setExamplesText("");
      setCollectionName("");
      setKbId("");
      setMcpToolId("");
      setTopK("");
      setSortOrder("0");
      setEnabled(true);
      setPromptSnippet("");
      setPromptTemplate("");
      setParamPromptTemplate("");
    }
  }, [open, mode, node, parentNode]);

  const parentOptions = useMemo(() => {
    const opts = flatNodes.filter((n) => mode !== "edit" || n.id !== node?.id);
    return opts;
  }, [flatNodes, mode, node]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) { toast.error("请输入节点名称"); return; }
    if (mode === "create" && !intentCode.trim()) { toast.error("请输入意图标识"); return; }
    if (mode === "create" && !/^[a-zA-Z0-9_-]+$/.test(intentCode.trim())) {
      toast.error("意图标识仅支持字母、数字、-和_");
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "create") {
        const payload: IntentNodeCreatePayload = buildCreateIntentNodePayload({
          intentCode,
          name,
          level,
          kind,
          parentCodeValue,
          description,
          examplesText,
          collectionName,
          kbId,
          mcpToolId,
          topK,
          sortOrder,
          enabled,
          promptSnippet,
          promptTemplate,
          paramPromptTemplate
        }, knowledgeBases);
        await createIntentNode(payload);
        toast.success("创建成功");
      } else if (node) {
        const payload: IntentNodeUpdatePayload = buildUpdateIntentNodePayload({
          intentCode,
          name,
          level,
          kind,
          parentCodeValue,
          description,
          examplesText,
          collectionName,
          kbId,
          mcpToolId,
          topK,
          sortOrder,
          enabled,
          promptSnippet,
          promptTemplate,
          paramPromptTemplate
        }, knowledgeBases);
        await updateIntentNode(node.id, payload);
        toast.success("更新成功");
      }
      onDone();
    } catch (error) {
      toast.error((error as Error).message || "操作失败");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal title={mode === "create" ? "新建意图节点" : "编辑意图节点"} open={open} onClose={onClose}>
      <p className="modal-subtitle">
        {mode === "create" ? "配置意图节点的层级、类型与描述信息" : "更新节点基础信息"}
      </p>
      <form className="form-stack intent-form" onSubmit={handleSubmit}>
        {/* Name + IntentCode */}
        <div className="form-row">
          <label>
            节点名称
            <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="例如：OA系统" />
          </label>
          <label>
            意图标识
            <input
              value={intentCode}
              onChange={(e) => setIntentCode(e.target.value)}
              required
              disabled={mode === "edit"}
              placeholder="例如：biz-oa"
            />
          </label>
        </div>

        {/* Level + Kind */}
        <div className="form-row">
          <label>
            层级
            <select value={level} onChange={(e) => setLevel(Number(e.target.value))} disabled={mode === "edit"}>
              {Object.entries(LEVEL_LABELS).map(([val, label]) => (
                <option key={val} value={val}>{label} - {LEVEL_DESC[Number(val)]}</option>
              ))}
            </select>
          </label>
          <label>
            类型
            <select value={kind} onChange={(e) => setKind(Number(e.target.value))}>
              {Object.entries(KIND_LABELS).map(([val, label]) => (
                <option key={val} value={val}>{label} - {KIND_DESC[Number(val)]}</option>
              ))}
            </select>
          </label>
        </div>

        {/* Parent + SortOrder */}
        <div className="form-row">
          <label>
            父节点
            <select value={parentCodeValue} onChange={(e) => setParentCodeValue(e.target.value)}>
              <option value="">ROOT（根节点）</option>
              {parentOptions.map((n) => (
                <option key={n.id} value={n.intentCode}>
                  {"  ".repeat(n.level)}{n.name} ({n.intentCode})
                </option>
              ))}
            </select>
          </label>
          <label>
            排序
            <input type="number" value={sortOrder} onChange={(e) => setSortOrder(e.target.value)} />
          </label>
        </div>

        {/* Collection / MCP Tool */}
        {kind === 0 && (
          <>
            <label>
              绑定知识库
              <select value={kbId} onChange={(e) => setKbId(e.target.value)}>
                <option value="">请选择知识库</option>
                {knowledgeBases.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Collection 名称
              <input
                value={knowledgeBases.find((item) => item.id === kbId)?.collectionName || collectionName}
                readOnly
                placeholder="选择知识库后自动带出"
              />
            </label>
          </>
        )}
        {kind === 2 && (
          <label>
            MCP 工具 ID
            <input value={mcpToolId} onChange={(e) => setMcpToolId(e.target.value)} placeholder="例如：sales_query" />
          </label>
        )}

        {/* Collapsible: Description & Examples */}
        <details className="intent-form-section" open>
          <summary>描述与示例</summary>
          <div className="intent-form-section-body">
            <label>
              描述
              <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} placeholder="节点的语义说明与场景" />
            </label>
            <label>
              示例问题（每行一个）
              <textarea
                value={examplesText}
                onChange={(e) => setExamplesText(e.target.value)}
                rows={3}
                placeholder={"如何退货\n退货政策是什么\n几天内可以退"}
              />
            </label>
          </div>
        </details>

        {/* Collapsible: Prompt Config */}
        <details className="intent-form-section">
          <summary>Prompt 配置</summary>
          <div className="intent-form-section-body">
            <label>
              短规则片段（可选）
              <textarea
                value={promptSnippet}
                onChange={(e) => setPromptSnippet(e.target.value)}
                rows={3}
                placeholder="多意图场景下的特定规则，会添加到整体提示词中"
              />
            </label>
            <label>
              Prompt 模板（可选）
              <textarea
                value={promptTemplate}
                onChange={(e) => setPromptTemplate(e.target.value)}
                rows={4}
                placeholder="场景用的完整 Prompt 模板"
              />
            </label>
            {kind === 2 && (
              <label>
                参数提取提示词模板（MCP 专属）
                <textarea
                  value={paramPromptTemplate}
                  onChange={(e) => setParamPromptTemplate(e.target.value)}
                  rows={4}
                  placeholder="用于从用户输入中提取 MCP 工具参数的提示词模板"
                />
              </label>
            )}
          </div>
        </details>

        {/* Collapsible: Advanced */}
        <details className="intent-form-section">
          <summary>高级设置</summary>
          <div className="intent-form-section-body">
            <div className="form-row">
              <label>
                节点 TopK（可选）
                <input
                  type="number"
                  min={1}
                  value={topK}
                  onChange={(e) => setTopK(e.target.value)}
                  placeholder="留空则使用全局 TopK"
                />
              </label>
              <label className="form-checkbox-label">
                <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
                启用节点
              </label>
            </div>
          </div>
        </details>

        <Button type="submit" disabled={submitting}>
          {submitting ? (mode === "create" ? "创建中..." : "保存中...") : mode === "create" ? "创建" : "保存"}
        </Button>
      </form>
    </Modal>
  );
}

function parseExamples(examples: string | null | undefined): string[] {
  if (!examples) return [];
  try {
    const parsed = JSON.parse(examples);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function flattenTree(nodes: IntentNodeTree[]): IntentNodeTree[] {
  const result: IntentNodeTree[] = [];
  function walk(list: IntentNodeTree[]) {
    for (const node of list) {
      result.push(node);
      if (node.children) walk(node.children);
    }
  }
  walk(nodes);
  return result;
}

function findNodeByCode(nodes: IntentNodeTree[], code: string | null): IntentNodeTree | null {
  if (!code) return null;
  for (const node of nodes) {
    if (node.intentCode === code) return node;
    if (node.children) {
      const found = findNodeByCode(node.children, code);
      if (found) return found;
    }
  }
  return null;
}

function findPath(nodes: IntentNodeTree[], targetCode: string): IntentNodeTree[] | null {
  for (const node of nodes) {
    if (node.intentCode === targetCode) return [node];
    if (node.children) {
      const childPath = findPath(node.children, targetCode);
      if (childPath) return [node, ...childPath];
    }
  }
  return null;
}
