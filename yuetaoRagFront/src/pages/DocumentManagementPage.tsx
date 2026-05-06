import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { FileText, RefreshCw, Scissors, Search, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { listKnowledgeBases } from "@/services/knowledgeBaseService";
import { deleteDocument, listDocuments, splitDocument } from "@/services/knowledgeDocumentService";
import type { KnowledgeBase, KnowledgeDocument } from "@/types";
import { formatDate } from "@/utils/format";

interface DocumentRow {
  kb: KnowledgeBase;
  doc: KnowledgeDocument;
}

export function DocumentManagementPage() {
  const [items, setItems] = useState<DocumentRow[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);

  const filtered = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    if (!value) return items;
    return items.filter(({ kb, doc }) =>
      [kb.name, doc.title, doc.parseStatus, doc.status, doc.chunkMode]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(value))
    );
  }, [items, keyword]);

  async function load() {
    setLoading(true);
    try {
      const bases = await listKnowledgeBases();
      const documents = await Promise.all(
        bases.map(async (kb) => {
          try {
            const docs = await listDocuments(kb.id);
            return docs.map((doc) => ({ kb, doc }));
          } catch {
            return [];
          }
        })
      );
      setItems(documents.flat());
    } catch (error) {
      toast.error((error as Error).message || "加载文档失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleSplit(id: string) {
    await splitDocument(id);
    toast.success("已提交切分任务");
    load();
  }

  async function handleDelete(id: string) {
    if (!window.confirm("确认删除该文档？")) return;
    await deleteDocument(id);
    toast.success("已删除");
    load();
  }

  return (
    <section className="page-surface">
      <header className="page-header">
        <div>
          <h1>文档管理</h1>
          <p>集中查看全部知识库下的文档、解析状态和切分任务。</p>
        </div>
        <Button variant="secondary" onClick={load} disabled={loading}>
          <RefreshCw size={16} />
          刷新
        </Button>
      </header>
      <div className="toolbar">
        <div className="search-box">
          <Search size={16} />
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索文档、知识库或状态" />
        </div>
      </div>
      {filtered.length === 0 && !loading ? (
        <EmptyState title="暂无文档" description="进入知识库详情上传文件后，文档会集中显示在这里。" />
      ) : (
        <div className="table-card">
          <table>
            <thead>
              <tr>
                <th>文档</th>
                <th>所属知识库</th>
                <th>解析状态</th>
                <th>文档状态</th>
                <th>分块模式</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(({ kb, doc }) => (
                <tr key={doc.id}>
                  <td>
                    <Link className="table-title document-title-link" to={`/admin/knowledge/${kb.id}/docs/${doc.id}`}>
                      <FileText size={16} />
                      {doc.title}
                    </Link>
                    {doc.failReason ? <small className="error-text">{doc.failReason}</small> : null}
                  </td>
                  <td>
                    <Link className="table-title" to={`/admin/knowledge/${kb.id}`}>
                      {kb.name}
                    </Link>
                  </td>
                  <td>
                    <Badge value={doc.parseStatus} />
                  </td>
                  <td>
                    <Badge value={doc.status} />
                  </td>
                  <td>{doc.chunkMode || "-"}</td>
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
    </section>
  );
}
