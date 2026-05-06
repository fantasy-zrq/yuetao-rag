import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ExternalLink, Save } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { getDocument, updateDocument } from "@/services/knowledgeDocumentService";
import type { KnowledgeDocument } from "@/types";
import { formatDate, formatFileSize } from "@/utils/format";

export function DocumentDetailPage() {
  const { kbId = "", docId = "" } = useParams();
  const [doc, setDoc] = useState<KnowledgeDocument | null>(null);
  const [title, setTitle] = useState("");
  const [chunkMode, setChunkMode] = useState("");
  const [chunkConfig, setChunkConfig] = useState("");
  const [visibilityScope, setVisibilityScope] = useState("INTERNAL");
  const [minRankLevel, setMinRankLevel] = useState(10);

  async function load() {
    if (!docId) return;
    const data = await getDocument(docId);
    setDoc(data);
    setTitle(data.title || "");
    setChunkMode(data.chunkMode || "");
    setChunkConfig(data.chunkConfig || "");
    setVisibilityScope(data.visibilityScope || "INTERNAL");
    setMinRankLevel(data.minRankLevel || 10);
  }

  useEffect(() => {
    load().catch((error) => toast.error((error as Error).message || "加载文档详情失败"));
  }, [docId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await updateDocument({
      id: docId,
      title,
      chunkMode,
      chunkConfig,
      visibilityScope,
      minRankLevel
    });
    toast.success("文档已更新");
    load();
  }

  return (
    <section className="page-surface">
      <header className="page-header">
        <div>
          <Link className="breadcrumb" to={`/admin/knowledge/${kbId}`}>
            知识库文档
          </Link>
          <h1>{doc?.title || "文档详情"}</h1>
          <p>当前后端未暴露 chunk 列表接口，本页展示文档元数据和可更新配置。</p>
        </div>
        {doc?.storageUrl ? (
          <a className="btn btn-secondary" href={doc.storageUrl} target="_blank" rel="noreferrer">
            <ExternalLink size={16} />
            打开原文
          </a>
        ) : null}
      </header>
      <div className="detail-grid">
        <div className="detail-panel">
          <h2>文档状态</h2>
          <dl className="meta-list">
            <dt>解析状态</dt>
            <dd>
              <Badge value={doc?.parseStatus} />
            </dd>
            <dt>文档状态</dt>
            <dd>
              <Badge value={doc?.status} />
            </dd>
            <dt>MIME</dt>
            <dd>{doc?.mimeType || "-"}</dd>
            <dt>文件大小</dt>
            <dd>{formatFileSize(doc?.fileSize)}</dd>
            <dt>存储对象</dt>
            <dd>{doc?.storageKey || "-"}</dd>
            <dt>更新时间</dt>
            <dd>{formatDate(doc?.updateTime)}</dd>
            <dt>失败原因</dt>
            <dd>{doc?.failReason || "-"}</dd>
          </dl>
        </div>
        <form className="detail-panel form-stack" onSubmit={handleSubmit}>
          <h2>配置</h2>
          <label>
            标题
            <input value={title} onChange={(event) => setTitle(event.target.value)} required />
          </label>
          <label>
            分块模式
            <input value={chunkMode} onChange={(event) => setChunkMode(event.target.value)} />
          </label>
          <label>
            分块配置 JSON
            <textarea value={chunkConfig} onChange={(event) => setChunkConfig(event.target.value)} />
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
          <Button type="submit">
            <Save size={16} />
            保存配置
          </Button>
        </form>
      </div>
    </section>
  );
}
