import { FormEvent, useEffect, useMemo, useState, type ReactNode } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Clock3, FilePlus2, FileText, Power, RefreshCw, Scissors, Search, ShieldCheck, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { getKnowledgeBase } from "@/services/knowledgeBaseService";
import {
  deleteDocument,
  getDocument,
  listDocumentChunkLogs,
  listDocuments,
  splitDocument,
  toggleDocumentStatus,
  uploadDocument,
  type KnowledgeDocumentChunkLog
} from "@/services/knowledgeDocumentService";
import type { KnowledgeBase, KnowledgeDocument } from "@/types";
import { formatDate, formatFileSize } from "@/utils/format";

export function KnowledgeDocumentsPage() {
  const { kbId = "" } = useParams();
  const [kb, setKb] = useState<KnowledgeBase | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [chunkLogTarget, setChunkLogTarget] = useState<KnowledgeDocument | null>(null);
  const [chunkLogs, setChunkLogs] = useState<KnowledgeDocumentChunkLog[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);

  async function load() {
    if (!kbId) return;
    setLoading(true);
    try {
      const [base, docs] = await Promise.all([getKnowledgeBase(kbId), listDocuments(kbId)]);
      setKb(base);
      const details = await Promise.all(
        docs.map(async (doc) => {
          try {
            return { ...doc, ...(await getDocument(doc.id)) };
          } catch {
            return doc;
          }
        })
      );
      setDocuments(details);
    } catch (error) {
      toast.error((error as Error).message || "加载文档失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [kbId]);

  const filteredDocuments = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    const sorted = [...documents].sort((left, right) => {
      const leftTime = new Date(left.updateTime || left.createTime || 0).getTime();
      const rightTime = new Date(right.updateTime || right.createTime || 0).getTime();
      return rightTime - leftTime;
    });
    if (!value) return sorted;
    return sorted.filter((doc) =>
      [doc.title, doc.parseStatus, doc.status, doc.chunkMode, doc.failReason]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(value))
    );
  }, [documents, keyword]);

  const stats = useMemo(() => {
    const total = documents.length;
    const success = documents.filter((doc) => String(doc.parseStatus).toUpperCase() === "SUCCESS").length;
    const failed = documents.filter((doc) => String(doc.parseStatus).toUpperCase() === "FAILED").length;
    const pending = Math.max(0, total - success - failed);
    return { total, success, failed, pending };
  }, [documents]);

  async function handleDelete(id: string) {
    if (!window.confirm("确认删除该文档？")) return;
    await deleteDocument(id);
    toast.success("已删除");
    load();
  }

  async function handleSplit(id: string) {
    await splitDocument(id);
    toast.success("已提交切分任务");
    load();
  }

  async function handleToggleStatus(doc: KnowledgeDocument) {
    const nextStatus = doc.status === "ENABLED" ? "DISABLED" : "ENABLED";
    await toggleDocumentStatus(doc.id, nextStatus);
    toast.success(nextStatus === "ENABLED" ? "文档已启用" : "文档已停用");
    load();
  }

  async function openChunkLogs(doc: KnowledgeDocument) {
    setChunkLogTarget(doc);
    setChunkLogs(await listDocumentChunkLogs(doc.id));
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <Link className="breadcrumb" to="/admin/knowledge">
            <ArrowLeft size={14} />
            返回知识库
          </Link>
          <h1 className="admin-page-title">{kb?.name || "知识库文档"}</h1>
          <p className="admin-page-subtitle">{kb?.description || "上传文档、查看解析状态并触发切分"}</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="secondary" onClick={load} disabled={loading}>
            <RefreshCw size={16} />
            刷新
          </Button>
          <Button onClick={() => setUploadOpen(true)}>
            <FilePlus2 size={16} />
            上传文档
          </Button>
        </div>
      </header>

      <div className="admin-stat-grid">
        <StatCard icon={<FileText size={18} />} label="文档总数" value={stats.total} />
        <StatCard icon={<ShieldCheck size={18} />} label="解析成功" value={stats.success} />
        <StatCard icon={<Trash2 size={18} />} label="解析失败" value={stats.failed} />
        <StatCard icon={<RefreshCw size={18} />} label="待处理" value={stats.pending} />
      </div>

      <div className="toolbar">
        <div className="search-box">
          <Search size={16} />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索文档名称、状态、分块模式"
          />
        </div>
      </div>

      {filteredDocuments.length === 0 && !loading ? (
        <EmptyState title="暂无文档" description="上传文件后可触发切分并用于 RAG 检索。" />
      ) : (
        <div className="table-card">
          <table>
            <thead>
              <tr>
                <th>文档</th>
                <th>解析状态</th>
                <th>文档状态</th>
                <th>分块模式</th>
                <th>分块数</th>
                <th>文件大小</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredDocuments.map((doc) => (
                <tr key={doc.id}>
                  <td>
                    <Link className="table-title document-title-link" to={`/admin/knowledge/${kbId}/docs/${doc.id}`}>
                      <FileText size={16} />
                      {doc.title}
                    </Link>
                    {doc.failReason ? <small className="error-text">{doc.failReason}</small> : null}
                  </td>
                  <td>
                    <Badge value={doc.parseStatus} />
                  </td>
                  <td>
                    <Badge value={doc.status} />
                  </td>
                  <td>{doc.chunkMode || "-"}</td>
                  <td>{doc.chunkCount ?? 0}</td>
                  <td>{formatFileSize(doc.fileSize)}</td>
                  <td>{formatDate(doc.updateTime)}</td>
                  <td className="table-actions">
                    <Link className="icon-btn" to={`/admin/knowledge/${kbId}/docs/${doc.id}`} aria-label="查看详情" title="查看详情">
                      <FileText size={16} />
                    </Link>
                    <button className="icon-btn" type="button" onClick={() => openChunkLogs(doc)} aria-label="分块详情" title="分块详情">
                      <Clock3 size={16} />
                    </button>
                    <button
                      className={`icon-btn ${doc.status === "ENABLED" ? "" : "danger"}`}
                      type="button"
                      onClick={() => handleToggleStatus(doc)}
                      aria-label={doc.status === "ENABLED" ? "停用文档" : "启用文档"}
                      title={doc.status === "ENABLED" ? "停用文档" : "启用文档"}
                    >
                      <Power size={16} />
                    </button>
                    <button className="icon-btn" type="button" onClick={() => handleSplit(doc.id)} aria-label="切分" title="切分">
                      <Scissors size={16} />
                    </button>
                    <button className="icon-btn danger" type="button" onClick={() => handleDelete(doc.id)} aria-label="删除" title="删除">
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <UploadDialog kbId={kbId} open={uploadOpen} onClose={() => setUploadOpen(false)} onDone={load} />
      <ChunkLogDialog target={chunkLogTarget} logs={chunkLogs} onClose={() => setChunkLogTarget(null)} />
    </section>
  );
}

function StatCard({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
  return (
    <div className="admin-stat-card">
      <div>
        <div className="admin-stat-label">{label}</div>
        <div className="admin-stat-value">{value}</div>
      </div>
      <div className="admin-stat-icon">{icon}</div>
    </div>
  );
}

function formatCost(value?: number | null) {
  if (!value) return "0ms";
  if (value < 1000) return `${value}ms`;
  return `${(value / 1000).toFixed(2)}s`;
}

function ChunkLogDialog({
  target,
  logs,
  onClose
}: {
  target: KnowledgeDocument | null;
  logs: KnowledgeDocumentChunkLog[];
  onClose: () => void;
}) {
  const latest = logs[0];
  return (
    <Modal title="分块详情" open={Boolean(target)} onClose={onClose}>
      <div className="chunk-log-modal">
        <p className="modal-subtitle">文档 [{target?.title || "-"}] 的分块执行日志</p>
        {latest ? (
          <>
            <div className="chunk-log-summary">
              <Badge value={latest.status} />
              <span>{latest.operationType === "REBUILD" ? "重新分块" : "直接分块"}</span>
              <strong>{latest.chunkCount ?? 0} 块</strong>
            </div>
            <div className="chunk-cost-grid">
              <CostCard label="解析与分块" value={formatCost(latest.splitCostMillis)} />
              <CostCard label="向量化写入" value={formatCost(latest.vectorCostMillis)} />
              <CostCard label="总耗时" value={formatCost(latest.totalCostMillis)} highlight />
            </div>
            <p className="chunk-log-time">
              执行时间 {formatDate(latest.startTime)} ~ {formatDate(latest.endTime)}
            </p>
            {latest.errorMessage ? <p className="error-text">{latest.errorMessage}</p> : null}
          </>
        ) : (
          <EmptyState title="暂无分块日志" description="触发切分后会在这里展示耗时与分块数量。" />
        )}
      </div>
    </Modal>
  );
}

function CostCard({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className={`chunk-cost-card ${highlight ? "highlight" : ""}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function UploadDialog({
  kbId,
  open,
  onClose,
  onDone
}: {
  kbId: string;
  open: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [chunkMode, setChunkMode] = useState("GENERAL");
  const [chunkSize, setChunkSize] = useState("800");
  const [overlapSize, setOverlapSize] = useState("120");
  const [visibilityScope, setVisibilityScope] = useState("INTERNAL");
  const [minRankLevel, setMinRankLevel] = useState(10);
  const [authorizedDepartmentIds, setAuthorizedDepartmentIds] = useState("");

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      toast.error("请选择文件");
      return;
    }
    const nextChunkSize = Number(chunkSize);
    const nextOverlapSize = Number(overlapSize);
    if (!Number.isFinite(nextChunkSize) || nextChunkSize <= 0) {
      toast.error("请填写有效的切片大小");
      return;
    }
    if (!Number.isFinite(nextOverlapSize) || nextOverlapSize < 0) {
      toast.error("请填写有效的重叠区域");
      return;
    }
    await uploadDocument({
      file,
      knowledgeBaseId: kbId,
      chunkMode,
      chunkConfig: JSON.stringify({ chunkSize: nextChunkSize, overlap: nextOverlapSize }),
      visibilityScope,
      minRankLevel,
      authorizedDepartmentIds: authorizedDepartmentIds
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean)
        .map((item) => Number(item))
        .filter((item) => Number.isFinite(item))
    });
    toast.success("文档已上传");
    setFile(null);
    setAuthorizedDepartmentIds("");
    onClose();
    onDone();
  }

  return (
    <Modal title="上传文档" open={open} onClose={onClose}>
      <form className="form-stack upload-form" onSubmit={handleSubmit}>
        <div className="form-field">
          <span className="form-field-label">文件</span>
          <div className="file-picker">
            <input
              id="knowledge-upload-file"
              className="file-picker-input"
              type="file"
              onChange={(event) => setFile(event.target.files?.[0] || null)}
              required
            />
            <label htmlFor="knowledge-upload-file" className="file-picker-button">
              选择文件
            </label>
            <span className="file-picker-name">{file?.name || "未选择任何文件"}</span>
          </div>
        </div>
        <label>
          分块模式
          <input value={chunkMode} onChange={(event) => setChunkMode(event.target.value)} />
        </label>
        <label>
          切片大小
          <input
            list="chunk-size-options"
            inputMode="numeric"
            value={chunkSize}
            onChange={(event) => setChunkSize(event.target.value)}
            placeholder="例如 800"
          />
          <small className="field-hint">建议范围 512 - 1200，常用值可直接选，也可手填。</small>
          <datalist id="chunk-size-options">
            <option value="256" />
            <option value="512" />
            <option value="800" />
            <option value="1000" />
            <option value="1200" />
            <option value="1500" />
          </datalist>
        </label>
        <label>
          重叠区域
          <input
            list="overlap-size-options"
            inputMode="numeric"
            value={overlapSize}
            onChange={(event) => setOverlapSize(event.target.value)}
            placeholder="例如 120"
          />
          <small className="field-hint">建议范围 64 - 240，重叠过大容易增加重复内容。</small>
          <datalist id="overlap-size-options">
            <option value="64" />
            <option value="96" />
            <option value="120" />
            <option value="160" />
            <option value="200" />
            <option value="240" />
          </datalist>
        </label>
        <label>
          可见性
          <select value={visibilityScope} onChange={(event) => setVisibilityScope(event.target.value)}>
            <option value="INTERNAL">INTERNAL</option>
            <option value="SENSITIVE">SENSITIVE</option>
          </select>
        </label>
        <label>
          最低职级
          <input type="number" value={minRankLevel} onChange={(event) => setMinRankLevel(Number(event.target.value))} />
        </label>
        <label>
          授权部门 ID
          <input
            value={authorizedDepartmentIds}
            onChange={(event) => setAuthorizedDepartmentIds(event.target.value)}
            placeholder="多个部门 ID 用英文逗号分隔"
          />
        </label>
        <Button type="submit">上传</Button>
      </form>
    </Modal>
  );
}
