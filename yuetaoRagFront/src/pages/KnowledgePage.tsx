import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { BookPlus, Pencil, Search, Trash2 } from "lucide-react";
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

  const filtered = useMemo(
    () => items.filter((item) => item.name.toLowerCase().includes(keyword.trim().toLowerCase())),
    [items, keyword]
  );

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
    <section className="page-surface">
      <header className="page-header">
        <div>
          <h1>知识库</h1>
          <p>基于当前 `/knowledge-bases` 接口管理知识集合。</p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <BookPlus size={16} />
          新建知识库
        </Button>
      </header>
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
                    <small>{item.description || "无描述"}</small>
                  </td>
                  <td>
                    <Badge value={item.status} />
                  </td>
                  <td>{item.embeddingModel || "-"}</td>
                  <td>{item.collectionName || "-"}</td>
                  <td>{formatDate(item.updateTime)}</td>
                  <td className="table-actions">
                    <button className="icon-btn" onClick={() => setEditing(item)} aria-label="重命名">
                      <Pencil size={16} />
                    </button>
                    <button className="icon-btn danger" onClick={() => handleDelete(item.id)} aria-label="删除">
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

function KnowledgeBaseForm({ open, onClose, onDone }: { open: boolean; onClose: () => void; onDone: () => void }) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [embeddingModel, setEmbeddingModel] = useState("text-embedding-v4");
  const [collectionName, setCollectionName] = useState("");

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await createKnowledgeBase({
      name,
      description,
      status: "ENABLED",
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
