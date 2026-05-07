import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, ExternalLink, FileText, Power, Save, Shield } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { getDocument, listDocumentChunkLogs, toggleDocumentStatus, updateDocument, type KnowledgeDocumentChunkLog } from "@/services/knowledgeDocumentService";
import type { KnowledgeDocument } from "@/types";
import { formatDate, formatFileSize } from "@/utils/format";

function parseChunkConfig(value?: string | null) {
  const fallback = { chunkSize: "800", overlapSize: "120" };
  if (!value) return fallback;
  try {
    const parsed = JSON.parse(value);
    return {
      chunkSize: parsed?.chunkSize !== undefined ? String(parsed.chunkSize) : fallback.chunkSize,
      overlapSize:
        parsed?.overlap !== undefined
          ? String(parsed.overlap)
          : parsed?.overlapSize !== undefined
            ? String(parsed.overlapSize)
            : fallback.overlapSize
    };
  } catch {
    return fallback;
  }
}

export function DocumentDetailPage() {
  const { kbId = "", docId = "" } = useParams();
  const [doc, setDoc] = useState<KnowledgeDocument | null>(null);
  const [title, setTitle] = useState("");
  const [chunkMode, setChunkMode] = useState("");
  const [chunkSize, setChunkSize] = useState("800");
  const [overlapSize, setOverlapSize] = useState("120");
  const [visibilityScope, setVisibilityScope] = useState("INTERNAL");
  const [minRankLevel, setMinRankLevel] = useState(10);
  const [authorizedDepartmentIds, setAuthorizedDepartmentIds] = useState("");
  const [saving, setSaving] = useState(false);
  const [latestChunkLog, setLatestChunkLog] = useState<KnowledgeDocumentChunkLog | null>(null);

  async function load() {
    if (!docId) return;
    const [data, logs] = await Promise.all([getDocument(docId), listDocumentChunkLogs(docId).catch(() => [])]);
    setDoc(data);
    setTitle(data.title || "");
    setChunkMode(data.chunkMode || "");
    const chunkConfig = parseChunkConfig(data.chunkConfig);
    setChunkSize(chunkConfig.chunkSize);
    setOverlapSize(chunkConfig.overlapSize);
    setVisibilityScope(data.visibilityScope || "INTERNAL");
    setMinRankLevel(data.minRankLevel ?? 10);
    setAuthorizedDepartmentIds((data.authorizedDepartmentIds || []).map(String).join(","));
    setLatestChunkLog((logs || [])[0] || null);
  }

  useEffect(() => {
    load().catch((error) => toast.error((error as Error).message || "加载文档详情失败"));
  }, [docId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
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
    setSaving(true);
    try {
      await updateDocument({
        id: docId,
        title,
        chunkMode,
        chunkConfig: JSON.stringify({
          chunkSize: nextChunkSize,
          overlap: nextOverlapSize
        }),
        visibilityScope,
        minRankLevel,
        authorizedDepartmentIds: authorizedDepartmentIds
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean)
          .map((item) => Number(item))
          .filter((item) => Number.isFinite(item))
      });
      toast.success("文档已更新");
      await load();
    } catch (error) {
      toast.error((error as Error).message || "保存失败");
    } finally {
      setSaving(false);
    }
  }

  async function handleToggleStatus() {
    if (!doc) return;
    const nextStatus = doc.status === "ENABLED" ? "DISABLED" : "ENABLED";
    await toggleDocumentStatus(doc.id, nextStatus);
    toast.success(nextStatus === "ENABLED" ? "文档已启用" : "文档已停用");
    await load();
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <Link className="breadcrumb" to={`/admin/knowledge/${kbId}`}>
            <ArrowLeft size={14} />
            返回知识库文档
          </Link>
          <h1 className="admin-page-title">{doc?.title || "文档详情"}</h1>
          <p className="admin-page-subtitle">查看文档元数据，并更新分块、权限和访问配置</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="secondary" onClick={handleToggleStatus} disabled={!doc}>
            <Power size={16} />
            {doc?.status === "ENABLED" ? "停用文档" : "启用文档"}
          </Button>
          {doc?.storageUrl ? (
            <a className="btn btn-secondary" href={doc.storageUrl} target="_blank" rel="noreferrer">
              <ExternalLink size={16} />
              打开原文
            </a>
          ) : null}
        </div>
      </header>

      <div className="detail-grid">
        <div className="detail-panel">
          <div className="detail-panel-title">
            <FileText size={18} />
            <h2>文档状态</h2>
          </div>
          <dl className="meta-list">
            <dt>解析状态</dt>
            <dd>
              <Badge value={doc?.parseStatus} />
            </dd>
            <dt>文档状态</dt>
            <dd>
              <Badge value={doc?.status} />
            </dd>
            <dt>分块数</dt>
            <dd>{doc?.chunkCount ?? 0}</dd>
            <dt>来源类型</dt>
            <dd>{doc?.sourceType || "-"}</dd>
            <dt>MIME</dt>
            <dd>{doc?.mimeType || "-"}</dd>
            <dt>文件大小</dt>
            <dd>{formatFileSize(doc?.fileSize)}</dd>
            <dt>存储桶</dt>
            <dd>{doc?.storageBucket || "-"}</dd>
            <dt>存储对象</dt>
            <dd>{doc?.storageKey || "-"}</dd>
            <dt>ETag</dt>
            <dd>{doc?.storageEtag || "-"}</dd>
            <dt>创建时间</dt>
            <dd>{formatDate(doc?.createTime)}</dd>
            <dt>更新时间</dt>
            <dd>{formatDate(doc?.updateTime)}</dd>
            <dt>失败原因</dt>
            <dd>{doc?.failReason || "-"}</dd>
            <dt>最新分块</dt>
            <dd>
              {latestChunkLog ? (
                <div className="chunk-log-mini">
                  <div>
                    <Badge value={latestChunkLog.status} />
                    <span>{latestChunkLog.chunkCount ?? 0} 块</span>
                  </div>
                  <small>
                    {latestChunkLog.operationType === "REBUILD" ? "重新分块" : "直接分块"} · {formatDate(latestChunkLog.startTime)}
                  </small>
                </div>
              ) : (
                "-"
              )}
            </dd>
          </dl>
        </div>

        <form className="detail-panel form-stack" onSubmit={handleSubmit}>
          <div className="detail-panel-title">
            <Shield size={18} />
            <h2>配置</h2>
          </div>
          <label>
            标题
            <input value={title} onChange={(event) => setTitle(event.target.value)} required />
          </label>
          <label>
            分块模式
            <input value={chunkMode} onChange={(event) => setChunkMode(event.target.value)} />
          </label>
          <label>
            切片大小
            <input
              list="detail-chunk-size-options"
              inputMode="numeric"
              value={chunkSize}
              onChange={(event) => setChunkSize(event.target.value)}
              placeholder="例如 800"
            />
            <small className="field-hint">建议范围 512 - 1200，支持直接输入常用值。</small>
            <datalist id="detail-chunk-size-options">
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
              list="detail-overlap-size-options"
              inputMode="numeric"
              value={overlapSize}
              onChange={(event) => setOverlapSize(event.target.value)}
              placeholder="例如 120"
            />
            <small className="field-hint">建议范围 64 - 240，过大容易带来重复片段。</small>
            <datalist id="detail-overlap-size-options">
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
          <Button type="submit" disabled={saving}>
            <Save size={16} />
            {saving ? "保存中..." : "保存配置"}
          </Button>
        </form>
      </div>
    </section>
  );
}
