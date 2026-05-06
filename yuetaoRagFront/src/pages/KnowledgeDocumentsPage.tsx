import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { FileUp, Scissors, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { getKnowledgeBase } from "@/services/knowledgeBaseService";
import { deleteDocument, listDocuments, splitDocument, uploadDocument } from "@/services/knowledgeDocumentService";
import type { KnowledgeBase, KnowledgeDocument } from "@/types";
import { formatDate } from "@/utils/format";

export function KnowledgeDocumentsPage() {
  const { kbId = "" } = useParams();
  const [kb, setKb] = useState<KnowledgeBase | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [uploadOpen, setUploadOpen] = useState(false);

  async function load() {
    if (!kbId) return;
    setKb(await getKnowledgeBase(kbId));
    setDocuments(await listDocuments(kbId));
  }

  useEffect(() => {
    load().catch((error) => toast.error((error as Error).message || "加载文档失败"));
  }, [kbId]);

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

  return (
    <section className="page-surface">
      <header className="page-header">
        <div>
          <h1>{kb?.name || "知识库文档"}</h1>
          <p>上传文档、查看解析状态并触发切分。</p>
        </div>
        <Button onClick={() => setUploadOpen(true)}>
          <FileUp size={16} />
          上传文档
        </Button>
      </header>
      {documents.length === 0 ? (
        <EmptyState title="暂无文档" description="上传文件后可触发切分并用于 RAG 检索。" />
      ) : (
        <div className="table-card">
          <table>
            <thead>
              <tr>
                <th>文档</th>
                <th>解析状态</th>
                <th>分块模式</th>
                <th>状态</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id}>
                  <td>
                    <Link className="table-title" to={`/admin/knowledge/${kbId}/docs/${doc.id}`}>
                      {doc.title}
                    </Link>
                    {doc.failReason ? <small className="error-text">{doc.failReason}</small> : null}
                  </td>
                  <td>
                    <Badge value={doc.parseStatus} />
                  </td>
                  <td>{doc.chunkMode || "-"}</td>
                  <td>
                    <Badge value={doc.status} />
                  </td>
                  <td>{formatDate(doc.updateTime)}</td>
                  <td className="table-actions">
                    <button className="icon-btn" onClick={() => handleSplit(doc.id)} aria-label="切分">
                      <Scissors size={16} />
                    </button>
                    <button className="icon-btn danger" onClick={() => handleDelete(doc.id)} aria-label="删除">
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
    </section>
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
  const [chunkConfig, setChunkConfig] = useState('{"chunkSize":800,"overlap":120}');
  const [visibilityScope, setVisibilityScope] = useState("INTERNAL");
  const [minRankLevel, setMinRankLevel] = useState(10);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!file) {
      toast.error("请选择文件");
      return;
    }
    await uploadDocument({
      file,
      knowledgeBaseId: kbId,
      chunkMode,
      chunkConfig,
      visibilityScope,
      minRankLevel
    });
    toast.success("文档已上传");
    setFile(null);
    onClose();
    onDone();
  }

  return (
    <Modal title="上传文档" open={open} onClose={onClose}>
      <form className="form-stack" onSubmit={handleSubmit}>
        <label>
          文件
          <input type="file" onChange={(event) => setFile(event.target.files?.[0] || null)} required />
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
        <Button type="submit">上传</Button>
      </form>
    </Modal>
  );
}
