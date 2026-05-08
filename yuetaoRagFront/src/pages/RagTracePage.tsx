import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { Activity, Clock3, Layers, RefreshCw, Search, TrendingUp } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { Badge } from "@/components/Badge";
import { getRagTraceRuns, type PageResult, type RagTraceRun } from "@/services/ragTraceService";
import { formatDate } from "@/utils/format";

const PAGE_SIZE = 10;

function normalizeStatus(value?: string | null) {
  return String(value || "").toLowerCase();
}

function formatDuration(value?: number | null) {
  if (!value) return "0ms";
  if (value < 1000) return `${Math.round(value)}ms`;
  return `${(value / 1000).toFixed(2)}s`;
}

export function RagTracePage() {
  const navigate = useNavigate();
  const requestRef = useRef(0);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageData, setPageData] = useState<PageResult<RagTraceRun> | null>(null);
  const [loading, setLoading] = useState(false);

  async function load(nextPage = pageNo, nextTraceId = search) {
    const requestId = ++requestRef.current;
    setLoading(true);
    try {
      const result = await getRagTraceRuns({ current: nextPage, size: PAGE_SIZE, traceId: nextTraceId || undefined });
      if (requestRef.current !== requestId) return;
      setPageData(result);
    } catch (error) {
      if (requestRef.current !== requestId) return;
      toast.error((error as Error).message || "加载链路追踪失败");
    } finally {
      if (requestRef.current !== requestId) return;
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [pageNo, search]);

  const stats = useMemo(() => {
    const runs = pageData?.records || [];
    const durations = runs.map((item) => Number(item.durationMs || 0)).filter((value) => value > 0);
    const success = runs.filter((item) => normalizeStatus(item.status) === "success").length;
    const failed = runs.filter((item) => normalizeStatus(item.status) === "failed").length;
    const running = runs.filter((item) => normalizeStatus(item.status) === "running").length;
    const avg = durations.length ? Math.round(durations.reduce((sum, value) => sum + value, 0) / durations.length) : 0;
    const p95 = durations.length ? [...durations].sort((a, b) => a - b)[Math.ceil(durations.length * 0.95) - 1] || 0 : 0;
    return { success, failed, running, avg, p95 };
  }, [pageData]);

  const runs = pageData?.records || [];
  const current = pageData?.current || pageNo;
  const pages = pageData?.pages || 1;
  const total = pageData?.total || 0;

  return (
    <section className="admin-page trace-page">
      <header className="admin-page-header">
        <div>
          <h1 className="admin-page-title">链路追踪</h1>
          <p className="admin-page-subtitle">按 Trace Id 聚焦运行检索，点击记录进入详情页分析节点耗时</p>
        </div>
        <div className="trace-toolbar">
          <div className="search-box trace-search-box">
            <Search size={16} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索 Trace Id" />
          </div>
          <div className="trace-toolbar-actions">
            <Button onClick={() => { setPageNo(1); setSearch(query.trim()); }}>
              <Search size={16} />
              查询
            </Button>
            <Button variant="secondary" onClick={() => load(pageNo, search)} disabled={loading}>
              <RefreshCw size={16} />
              刷新
            </Button>
          </div>
        </div>
      </header>
      <div className="admin-stat-grid">
        <Stat label="成功 / 失败 / 运行中" value={`${stats.success} / ${stats.failed} / ${stats.running}`} icon={<Activity size={18} />} />
        <Stat label="成功率" value={runs.length ? `${Math.round((stats.success / runs.length) * 100)}%` : "0%"} icon={<TrendingUp size={18} />} />
        <Stat label="平均耗时" value={formatDuration(stats.avg)} icon={<Clock3 size={18} />} />
        <Stat label="P95 耗时" value={formatDuration(stats.p95)} icon={<Layers size={18} />} />
      </div>
      {runs.length === 0 && !loading ? (
        <EmptyState title="暂无链路数据" description="通过问答会话产生的 Trace 会显示在这里。" />
      ) : (
        <div className="table-card trace-table-card">
          <table>
            <thead>
              <tr>
                <th>Trace Name</th>
                <th>Trace Id</th>
                <th>会话ID</th>
                <th>用户名</th>
                <th>耗时</th>
                <th>状态</th>
                <th>执行时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <tr key={run.traceId}>
                  <td>{run.traceName || "-"}</td>
                  <td className="mono-cell">{run.traceId}</td>
                  <td className="mono-cell">{run.conversationId || "-"}</td>
                  <td>{run.username || "-"}</td>
                  <td>{formatDuration(run.durationMs)}</td>
                  <td><Badge value={run.status} /></td>
                  <td>{formatDate(run.startTime)}</td>
                  <td className="table-actions">
                    <button className="icon-btn" type="button" onClick={() => navigate(`/admin/traces/${encodeURIComponent(run.traceId)}`)} title="查看链路" aria-label="查看链路">
                      <Search size={16} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="trace-pagination">
            <span>第 {current}/{pages} 页，共 {total} 条</span>
            <div>
              <Button variant="secondary" disabled={current <= 1 || loading} onClick={() => setPageNo((prev) => Math.max(1, prev - 1))}>上一页</Button>
              <Button variant="secondary" disabled={current >= pages || loading} onClick={() => setPageNo((prev) => prev + 1)}>下一页</Button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

function Stat({ label, value, icon }: { label: string; value: string; icon: ReactNode }) {
  return (
    <div className="admin-stat-card">
      <div>
        <div className="admin-stat-label">{label}</div>
        <div className="admin-stat-value trace-stat-value">{value}</div>
      </div>
      <div className="admin-stat-icon">{icon}</div>
    </div>
  );
}
