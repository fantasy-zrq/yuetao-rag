import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { GitBranch, Pencil, RefreshCw, Search, Trash2, X } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/Button";
import { Modal } from "@/components/Modal";
import {
  batchDeleteIntentNodes,
  batchDisableIntentNodes,
  batchEnableIntentNodes,
  deleteIntentNode,
  getIntentTree,
  type IntentNodeTree
} from "@/services/intentTreeService";

const ALL_VALUE = "__ALL__";
const ROOT_VALUE = "__ROOT__";
const PAGE_SIZE_OPTIONS = [10, 20, 50];

const LEVEL_OPTIONS = [
  { value: 0, label: "DOMAIN" },
  { value: 1, label: "CATEGORY" },
  { value: 2, label: "TOPIC" }
];

const KIND_OPTIONS = [
  { value: 0, label: "KB" },
  { value: 1, label: "SYSTEM" },
  { value: 2, label: "MCP" }
];

interface FlatNode {
  id: string;
  intentCode: string;
  name: string;
  level: number;
  kind: number;
  enabled: number;
  parentCode?: string | null;
  collectionName?: string | null;
  mcpToolId?: string | null;
  topK?: number | null;
  examples?: string | null;
  sortOrder: number;
  depth: number;
  pathNames: string[];
  pathCodes: string[];
  exampleCount: number;
}

function flattenTree(nodes: IntentNodeTree[], parentNames: string[] = [], parentCodes: string[] = []): FlatNode[] {
  const result: FlatNode[] = [];
  for (const node of nodes) {
    const curNames = [...parentNames, node.name];
    const curCodes = [...parentCodes, node.intentCode];
    result.push({
      id: node.id,
      intentCode: node.intentCode,
      name: node.name,
      level: node.level,
      kind: node.kind,
      enabled: node.enabled,
      parentCode: node.parentCode,
      collectionName: node.collectionName,
      mcpToolId: node.mcpToolId,
      topK: node.topK,
      examples: node.examples,
      sortOrder: node.sortOrder,
      depth: Math.max(curNames.length - 1, 0),
      pathNames: curNames,
      pathCodes: curCodes,
      exampleCount: parseExamples(node.examples).length
    });
    if (node.children) {
      result.push(...flattenTree(node.children, curNames, curCodes));
    }
  }
  return result;
}

function parseExamples(value?: string | null): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.map(String).filter(Boolean);
  } catch { /* ignore */ }
  return value.split("\n").map((s) => s.trim()).filter(Boolean);
}

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

export function IntentListPage() {
  const navigate = useNavigate();
  const [tree, setTree] = useState<IntentNodeTree[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [levelFilter, setLevelFilter] = useState(ALL_VALUE);
  const [kindFilter, setKindFilter] = useState(ALL_VALUE);
  const [statusFilter, setStatusFilter] = useState(ALL_VALUE);
  const [parentFilter, setParentFilter] = useState(ALL_VALUE);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE_OPTIONS[1]);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [deleteTarget, setDeleteTarget] = useState<FlatNode | null>(null);

  async function load() {
    setLoading(true);
    try {
      setTree(await getIntentTree());
    } catch (error) {
      toast.error((error as Error).message || "加载意图列表失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  const rows = useMemo(() => flattenTree(tree), [tree]);

  const parentOptions = useMemo(() => {
    return [
      { value: ALL_VALUE, label: "全部父节点" },
      { value: ROOT_VALUE, label: "ROOT（根节点）" },
      ...rows.map((r) => ({ value: r.intentCode, label: r.pathNames.join(" > ") }))
    ];
  }, [rows]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return rows.filter((r) => {
      if (kw && ![r.name, r.intentCode, r.pathNames.join(" ")].join(" ").toLowerCase().includes(kw)) return false;
      if (levelFilter !== ALL_VALUE && r.level !== Number(levelFilter)) return false;
      if (kindFilter !== ALL_VALUE && r.kind !== Number(kindFilter)) return false;
      if (statusFilter === "enabled" && r.enabled === 0) return false;
      if (statusFilter === "disabled" && r.enabled !== 0) return false;
      if (parentFilter !== ALL_VALUE) {
        if (parentFilter === ROOT_VALUE) { if (r.parentCode) return false; }
        else if (r.parentCode !== parentFilter) return false;
      }
      return true;
    });
  }, [rows, keyword, levelFilter, kindFilter, statusFilter, parentFilter]);

  const total = filtered.length;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const currentPage = Math.min(pageNo, totalPages);
  const startIdx = (currentPage - 1) * pageSize;
  const pageRows = filtered.slice(startIdx, startIdx + pageSize);
  const rangeStart = total === 0 ? 0 : startIdx + 1;
  const rangeEnd = total === 0 ? 0 : Math.min(startIdx + pageRows.length, total);

  useEffect(() => { if (pageNo !== currentPage) setPageNo(currentPage); }, [currentPage, pageNo]);
  useEffect(() => {
    setSelectedIds((prev) => {
      const rowIds = new Set(rows.map((r) => r.id));
      const next = new Set<string>();
      prev.forEach((id) => { if (rowIds.has(id)) next.add(id); });
      return next;
    });
  }, [rows]);

  const allPageSelected = pageRows.length > 0 && pageRows.every((r) => selectedIds.has(r.id));
  const someSelected = selectedIds.size > 0;

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    if (allPageSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        pageRows.forEach((r) => next.add(r.id));
        return next;
      });
    }
  }

  async function handleBatchEnable() {
    if (selectedIds.size === 0) return;
    try {
      await batchEnableIntentNodes(Array.from(selectedIds));
      toast.success(`已启用 ${selectedIds.size} 项`);
      setSelectedIds(new Set());
      load();
    } catch (error) {
      toast.error((error as Error).message || "批量启用失败");
    }
  }

  async function handleBatchDisable() {
    if (selectedIds.size === 0) return;
    try {
      await batchDisableIntentNodes(Array.from(selectedIds));
      toast.success(`已禁用 ${selectedIds.size} 项`);
      setSelectedIds(new Set());
      load();
    } catch (error) {
      toast.error((error as Error).message || "批量禁用失败");
    }
  }

  async function handleBatchDelete() {
    if (selectedIds.size === 0) return;
    try {
      await batchDeleteIntentNodes(Array.from(selectedIds));
      toast.success(`已删除 ${selectedIds.size} 项`);
      setSelectedIds(new Set());
      load();
    } catch (error) {
      toast.error((error as Error).message || "批量删除失败");
    }
  }

  async function handleDeleteOne() {
    if (!deleteTarget) return;
    try {
      await deleteIntentNode(deleteTarget.id);
      toast.success("已删除");
      setDeleteTarget(null);
      load();
    } catch (error) {
      toast.error((error as Error).message || "删除失败");
    }
  }

  function clearFilters() {
    setKeyword("");
    setLevelFilter(ALL_VALUE);
    setKindFilter(ALL_VALUE);
    setStatusFilter(ALL_VALUE);
    setParentFilter(ALL_VALUE);
    setPageNo(1);
  }

  function resolveResource(row: FlatNode) {
    if (row.kind === 0) return row.collectionName || "-";
    if (row.kind === 2) return row.mcpToolId || "-";
    return "系统策略";
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1 className="admin-page-title">意图列表</h1>
          <p className="admin-page-subtitle">支持多维筛选、分页查看和快速定位到意图树节点</p>
        </div>
      </header>

      {/* Filter Bar */}
      <div className="intent-filter-card">
        <div className="intent-filter-row">
          <div className="search-box intent-search">
            <Search size={16} />
            <input
              value={keyword}
              onChange={(e) => { setKeyword(e.target.value); setPageNo(1); }}
              placeholder="搜索意图名称/标识..."
            />
          </div>
          <div className="intent-filter-selects">
            <select value={levelFilter} onChange={(e) => { setLevelFilter(e.target.value); setPageNo(1); }}>
              <option value={ALL_VALUE}>全部层级</option>
              {LEVEL_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
            <select value={kindFilter} onChange={(e) => { setKindFilter(e.target.value); setPageNo(1); }}>
              <option value={ALL_VALUE}>全部类型</option>
              {KIND_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
            <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPageNo(1); }}>
              <option value={ALL_VALUE}>全部状态</option>
              <option value="enabled">仅启用</option>
              <option value="disabled">仅禁用</option>
            </select>
            <select value={parentFilter} onChange={(e) => { setParentFilter(e.target.value); setPageNo(1); }}>
              <option value={ALL_VALUE}>全部父节点</option>
              <option value={ROOT_VALUE}>ROOT（根节点）</option>
              {rows.map((r) => <option key={r.id} value={r.intentCode}>{r.pathNames.join(" > ")}</option>)}
            </select>
            <Button variant="secondary" onClick={load} disabled={loading}>
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} />
              刷新
            </Button>
            <Button variant="ghost" className="intent-clear-btn" onClick={clearFilters}>
              <X size={14} />
              清空筛选
            </Button>
          </div>
        </div>
      </div>

      {/* Batch Bar */}
      {someSelected && (
        <div className="intent-batch-bar">
          <span>已选 {selectedIds.size} 项</span>
          <div className="intent-batch-actions">
            <Button variant="secondary" onClick={handleBatchEnable}>批量启用</Button>
            <Button variant="secondary" onClick={handleBatchDisable}>批量禁用</Button>
            <Button variant="danger" onClick={handleBatchDelete}>批量删除</Button>
          </div>
        </div>
      )}

      {/* Table */}
      <div className="intent-table-card">
        {loading ? (
          <div className="intent-empty">加载中...</div>
        ) : pageRows.length === 0 ? (
          <div className="intent-empty">
            {rows.length === 0 ? "暂无意图节点，请先在意图树配置中创建" : "没有匹配结果，请调整筛选条件"}
          </div>
        ) : (
          <div className="intent-table-wrap">
            <table className="intent-table">
              <thead>
                <tr>
                  <th style={{ width: 44 }}>
                    <input type="checkbox" checked={allPageSelected} onChange={toggleSelectAll} />
                  </th>
                  <th style={{ width: 280 }}>意图节点</th>
                  <th style={{ width: 110 }}>层级</th>
                  <th style={{ width: 110 }}>类型</th>
                  <th style={{ width: 300 }}>路径</th>
                  <th style={{ width: 200 }}>关联资源</th>
                  <th style={{ width: 80 }}>示例数</th>
                  <th style={{ width: 80 }}>状态</th>
                  <th style={{ width: 160 }} className="intent-th-sticky">操作</th>
                </tr>
              </thead>
              <tbody>
                {pageRows.map((row) => (
                  <tr key={row.id} className={row.enabled === 0 ? "intent-row-disabled" : ""}>
                    <td>
                      <input
                        type="checkbox"
                        checked={selectedIds.has(row.id)}
                        onChange={() => toggleSelect(row.id)}
                      />
                    </td>
                    <td>
                      <div className="intent-node-cell">
                        <span className="intent-node-name">{row.name}</span>
                        <span className="intent-node-code">{row.intentCode}</span>
                      </div>
                    </td>
                    <td>
                      <span className={`intent-level-badge ${LEVEL_BADGE_CLASS[row.level] || ""}`}>
                        {LEVEL_OPTIONS.find((o) => o.value === row.level)?.label || `L${row.level}`}
                      </span>
                    </td>
                    <td>
                      <span className={`intent-kind-badge ${KIND_BADGE_CLASS[row.kind] || ""}`}>
                        {KIND_OPTIONS.find((o) => o.value === row.kind)?.label || `K${row.kind}`}
                      </span>
                    </td>
                    <td>
                      <div className="intent-path-cell">
                        {row.pathNames.map((seg, i) => (
                          <span key={i} className="intent-path-seg">
                            {i > 0 && <span className="intent-path-sep">/</span>}
                            <button
                              className={`intent-path-btn ${i === row.pathNames.length - 1 ? "intent-path-btn--current" : ""}`}
                              onClick={() => navigate(`/admin/intent-tree?intentCode=${encodeURIComponent(row.pathCodes[i])}`)}
                            >
                              {seg}
                            </button>
                          </span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <div className="intent-resource-cell">
                        <span>{resolveResource(row)}</span>
                        <small>TopK: {row.topK ?? "全局默认"}</small>
                      </div>
                    </td>
                    <td><span className="intent-example-count">{row.exampleCount}</span></td>
                    <td>
                      <span className={`intent-status-badge ${row.enabled === 1 ? "intent-status-enabled" : "intent-status-disabled"}`}>
                        {row.enabled === 1 ? "启用" : "禁用"}
                      </span>
                    </td>
                    <td className="intent-td-sticky">
                      <div className="intent-row-actions">
                        <button
                          className="icon-btn"
                          title="编辑"
                          onClick={() => navigate(`/admin/intent-tree?intentCode=${row.intentCode}`)}
                        >
                          <Pencil size={14} />
                        </button>
                        <button
                          className="icon-btn"
                          title="定位树"
                          onClick={() => navigate(`/admin/intent-tree?intentCode=${row.intentCode}`)}
                        >
                          <GitBranch size={14} />
                        </button>
                        <button className="icon-btn danger" title="删除" onClick={() => setDeleteTarget(row)}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {total > 0 && (
          <div className="intent-pagination">
            <span>共 {total} 条，显示 {rangeStart}-{rangeEnd}</span>
            <div className="intent-pagination-controls">
              <span>每页</span>
              <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPageNo(1); }}>
                {PAGE_SIZE_OPTIONS.map((s) => <option key={s} value={s}>{s} 条</option>)}
              </select>
              <Button variant="ghost" disabled={currentPage <= 1} onClick={() => setPageNo(1)}>首页</Button>
              <Button variant="ghost" disabled={currentPage <= 1} onClick={() => setPageNo((p) => Math.max(1, p - 1))}>上一页</Button>
              <span>{currentPage} / {totalPages}</span>
              <Button variant="ghost" disabled={currentPage >= totalPages} onClick={() => setPageNo((p) => Math.min(totalPages, p + 1))}>下一页</Button>
              <Button variant="ghost" disabled={currentPage >= totalPages} onClick={() => setPageNo(totalPages)}>末页</Button>
            </div>
          </div>
        )}
      </div>

      {/* Delete Confirmation */}
      <Modal title="确认删除节点？" open={Boolean(deleteTarget)} onClose={() => setDeleteTarget(null)}>
        <p className="intent-delete-msg">
          节点「{deleteTarget?.name}」将被永久删除，无法恢复。
        </p>
        <div className="intent-delete-actions">
          <Button variant="secondary" onClick={() => setDeleteTarget(null)}>取消</Button>
          <Button variant="danger" onClick={handleDeleteOne}>删除</Button>
        </div>
      </Modal>
    </section>
  );
}
