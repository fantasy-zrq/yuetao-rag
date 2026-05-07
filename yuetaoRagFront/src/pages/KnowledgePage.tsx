import { FormEvent, ReactNode, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { BookOpen, BookPlus, Database, FolderOpen, Layers3, Pencil, RefreshCw, Search, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase
} from "@/services/knowledgeBaseService";
import type { KnowledgeBase } from "@/types";
import { formatDate } from "@/utils/format";

function normalizeCollectionName(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]/g, "");
}

function createDefaultCollectionName() {
  return `kb${Date.now()}`;
}

export function KnowledgePage() {
  const [items, setItems] = useState<KnowledgeBase[]>([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<KnowledgeBase | null>(null);
  const [createOpen, setCreateOpen] = useState(false);

  const sortedItems = useMemo(
    () =>
      [...items].sort((left, right) => {
        const leftTime = new Date(left.updateTime || left.createTime || 0).getTime();
        const rightTime = new Date(right.updateTime || right.createTime || 0).getTime();
        return rightTime - leftTime;
      }),
    [items]
  );
  const filtered = useMemo(
    () =>
      sortedItems.filter((item) =>
        [item.name, item.description, item.status, item.embeddingModel, item.collectionName]
          .filter(Boolean)
          .some((field) => String(field).toLowerCase().includes(keyword.trim().toLowerCase()))
      ),
    [sortedItems, keyword]
  );
  const stats = useMemo(() => {
    const totalCount = items.length;
    const enabledCount = items.filter((item) => item.status === "ENABLED").length;
    const collectionCount = new Set(items.map((item) => item.collectionName).filter(Boolean)).size;
    const modelCount = new Set(items.map((item) => item.embeddingModel).filter(Boolean)).size;
    return { totalCount, enabledCount, collectionCount, modelCount };
  }, [items]);

  async function load() {
    setLoading(true);
    try {
      setItems(await listKnowledgeBases());
    } catch (error) {
      toast.error((error as Error).message || "加载知识库失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function handleDelete(id: string) {
    if (!window.confirm("确认删除该知识库？")) return;
    await deleteKnowledgeBase(id);
    toast.success("已删除");
    load();
  }

  return (
    <section className="admin-page">
      <header className="admin-page-header">
        <div>
          <h1 className="admin-page-title">知识库管理</h1>
          <p className="admin-page-subtitle">基于当前 `/knowledge-bases` 接口管理知识集合</p>
        </div>
        <div className="admin-page-actions">
          <Button variant="secondary" onClick={() => load()} disabled={loading}>
            <RefreshCw size={16} />
            刷新
          </Button>
          <Button onClick={() => setCreateOpen(true)}>
            <BookPlus size={16} />
            新建知识库
          </Button>
        </div>
      </header>
      <div className="admin-stat-grid">
        <StatCard icon={<Database size={18} />} label="知识库总数" value={stats.totalCount} />
        <StatCard icon={<BookOpen size={18} />} label="启用中的知识库" value={stats.enabledCount} />
        <StatCard icon={<FolderOpen size={18} />} label="集合数量" value={stats.collectionCount} />
        <StatCard icon={<Layers3 size={18} />} label="向量模型数" value={stats.modelCount} />
      </div>
      <div className="toolbar">
        <div className="search-box">
          <Search size={16} />
          <input value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="搜索知识库名称" />
        </div>
      </div>
      {filtered.length === 0 && !loading ? (
        <EmptyState title="暂无知识库" description="创建知识库后可进入详情上传文档。" />
      ) : (
        <div className="table-card">
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>描述</th>
                <th>状态</th>
                <th>向量模型</th>
                <th>Collection</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((item) => (
                <tr key={item.id}>
                  <td>
                    <Link className="table-title" to={`/admin/knowledge/${item.id}`}>
                      {item.name}
                    </Link>
                    <small>ID: {item.id}</small>
                  </td>
                  <td className="table-clamp">{item.description || "无描述"}</td>
                  <td>
                    <Badge value={item.status} />
                  </td>
                  <td>{item.embeddingModel || "-"}</td>
                  <td>{item.collectionName || "-"}</td>
                  <td>{formatDate(item.updateTime)}</td>
                  <td className="table-actions">
                    <Link className="icon-btn" to={`/admin/knowledge/${item.id}`} aria-label="查看文档" title="查看文档">
                      <FolderOpen size={16} />
                    </Link>
                    <button className="icon-btn" onClick={() => setEditing(item)} aria-label="重命名" title="重命名">
                      <Pencil size={16} />
                    </button>
                    <button className="icon-btn danger" onClick={() => handleDelete(item.id)} aria-label="删除" title="删除">
                      <Trash2 size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <KnowledgeBaseForm open={createOpen} onClose={() => setCreateOpen(false)} onDone={load} />
      <RenameForm item={editing} onClose={() => setEditing(null)} onDone={load} />
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

function KnowledgeBaseForm({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [embeddingModel, setEmbeddingModel] = useState("text-embedding-v4");
  const [collectionName, setCollectionName] = useState("");
  const [status, setStatus] = useState("ENABLED");

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await createKnowledgeBase({
      name,
      description,
      status,
      embeddingModel,
      collectionName: normalizeCollectionName(collectionName) || createDefaultCollectionName()
    });
    toast.success("知识库已创建");
    setName("");
    setDescription("");
    setCollectionName("");
    onClose();
    onDone();
  }

  return (
    <Modal title="新建知识库" open={open} onClose={onClose}>
      <form className="form-stack" onSubmit={handleSubmit}>
        <label>
          名称
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <label>
          描述
          <textarea value={description} onChange={(event) => setDescription(event.target.value)} />
        </label>
        <label>
          默认向量模型
          <input value={embeddingModel} onChange={(event) => setEmbeddingModel(event.target.value)} />
        </label>
        <label>
          状态
          <select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="ENABLED">ENABLED</option>
            <option value="DISABLED">DISABLED</option>
          </select>
        </label>
        <label>
          Collection 名称
          <input
            value={collectionName}
            onChange={(event) => setCollectionName(normalizeCollectionName(event.target.value))}
            placeholder="留空自动生成，仅支持小写字母和数字"
          />
        </label>
        <Button type="submit">保存</Button>
      </form>
    </Modal>
  );
}

function RenameForm({
  item,
  onClose,
  onDone
}: {
  item: KnowledgeBase | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [name, setName] = useState("");

  useEffect(() => {
    setName(item?.name || "");
  }, [item]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!item) return;
    await updateKnowledgeBase({ id: item.id, name });
    toast.success("已更新");
    onClose();
    onDone();
  }

  return (
    <Modal title="重命名知识库" open={Boolean(item)} onClose={onClose}>
      <form className="form-stack" onSubmit={handleSubmit}>
        <label>
          名称
          <input value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <Button type="submit">保存</Button>
      </form>
    </Modal>
  );
}
