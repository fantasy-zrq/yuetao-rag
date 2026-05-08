import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Clock3, RefreshCw, UserRound } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { getRagTraceDetail, type RagTraceDetail, type RagTraceNode } from "@/services/ragTraceService";
import { formatDate } from "@/utils/format";

function formatDuration(value?: number | null) {
  if (!value) return "0ms";
  if (value < 1000) return `${Math.round(value)}ms`;
  return `${(value / 1000).toFixed(2)}s`;
}

function normalizeStatus(value?: string | null) {
  return String(value || "").toLowerCase();
}

function toNumber(value?: string | number | null) {
  if (value === null || value === undefined || value === "") return 0;
  const n = typeof value === "number" ? value : Number(value);
  return Number.isFinite(n) ? n : 0;
}

function toTimestamp(value?: string | null) {
  if (!value) return 0;
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) ? timestamp : 0;
}

export function RagTraceDetailPage() {
  const { traceId = "" } = useParams();
  const requestRef = useRef(0);
  const [detail, setDetail] = useState<RagTraceDetail | null>(null);
  const [loading, setLoading] = useState(false);

  async function load() {
    if (!traceId) return;
    const requestId = ++requestRef.current;
    setLoading(true);
    try {
      const result = await getRagTraceDetail(traceId);
      if (requestRef.current !== requestId) return;
      setDetail(result);
    } catch (error) {
      if (requestRef.current !== requestId) return;
      toast.error((error as Error).message || "加载链路详情失败");
    } finally {
      if (requestRef.current !== requestId) return;
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [traceId]);

  const nodes = useMemo(() => detail?.nodes || [], [detail]);
  const run = detail?.run || null;
  const timeline = useMemo(() => {
    const normalized = nodes.map((node) => ({
      node,
      start: toTimestamp(node.startTime),
      end: toTimestamp(node.endTime),
      duration: toNumber(node.durationMs)
    }));
    const validStarts = normalized.map((item) => item.start).filter((value) => value > 0);
    const validEnds = normalized.map((item) => item.end).filter((value) => value > 0);
    const base = validStarts.length ? Math.min(...validStarts) : 0;
    const window = Math.max(
      toNumber(run?.durationMs),
      validStarts.length && validEnds.length ? Math.max(...validEnds) - base : 0,
      normalized.reduce((sum, item) => sum + Math.max(item.duration, 0), 0),
      1
    );
    return {
      windowDuration: window,
      rows: normalized.map((item) => ({
        ...item.node,
        durationMs: item.duration,
        leftPercent: base && item.start > 0 ? Math.min(94, ((item.start - base) / window) * 100) : 0,
        widthPercent: Math.max(0.8, (Math.max(item.duration, 1) / window) * 100),
        windowDuration: window
      }))
    };
  }, [nodes, run?.durationMs]);
  const stats = useMemo(() => {
    const success = nodes.filter((item) => normalizeStatus(item.status) === "success").length;
    const failed = nodes.filter((item) => normalizeStatus(item.status) === "failed").length;
    const running = nodes.filter((item) => normalizeStatus(item.status) === "running").length;
    const duration = timeline.windowDuration || toNumber(run?.durationMs);
    const avg = nodes.length ? Math.round(nodes.reduce((sum, node) => sum + toNumber(node.durationMs), 0) / nodes.length) : 0;
    return { success, failed, running, duration, avg };
  }, [nodes, run?.durationMs, timeline]);

  if (!traceId) {
    return <EmptyState title="缺少 Trace Id" description="请从链路追踪列表点击进入详情。" />;
  }

  return (
    <section className="admin-page trace-page">
      <header className="admin-page-header">
        <div>
          <Link className="breadcrumb" to="/admin/traces">
            <ArrowLeft size={14} />
            返回列表
          </Link>
          <h1 className="admin-page-title">RAG 链路详情</h1>
          <p className="admin-page-subtitle">
            {run?.traceName || "未命名链路"} · {run?.status || "-"}
          </p>
        </div>
        <div className="admin-page-actions">
          <Button variant="secondary" onClick={load} disabled={loading}>
            <RefreshCw size={16} />
            刷新
          </Button>
        </div>
      </header>
      <div className="trace-meta-row">
        <span>#{run?.traceId || traceId}</span>
        <span><Clock3 size={14} /> {formatDate(run?.startTime)}</span>
        <span><UserRound size={14} /> {run?.username || "-"}</span>
      </div>
      <div className="admin-stat-grid trace-detail-stats">
        <Stat label="总耗时" value={formatDuration(stats.duration)} />
        <Stat label="节点数" value={String(nodes.length)} />
        <Stat label="成功" value={String(stats.success)} />
        <Stat label="失败" value={String(stats.failed)} />
        <Stat label="平均耗时" value={formatDuration(stats.avg)} />
      </div>
      {nodes.length === 0 ? (
        <EmptyState title="暂无链路节点" description="当前 Trace 没有可展示的执行节点。" />
      ) : (
        <div className="trace-waterfall-card">
          <div className="trace-waterfall-header">
            <h2>执行时序</h2>
            <span>窗口 {formatDuration(stats.duration)}</span>
          </div>
          <div className="trace-waterfall-grid">
            {timeline.rows.map((node, index) => (
              <TraceRow key={`${node.nodeId}-${index}`} node={node} />
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="admin-stat-card">
      <div>
        <div className="admin-stat-label">{label}</div>
        <div className="admin-stat-value trace-stat-value">{value}</div>
      </div>
      <div className="admin-stat-icon">
        <Clock3 size={18} />
      </div>
    </div>
  );
}

function TraceRow({
  node
}: {
  node: RagTraceNode & { leftPercent: number; widthPercent: number; durationMs: number; windowDuration: number };
}) {
  const duration = toNumber(node.durationMs);
  return (
    <div className="trace-waterfall-row">
      <div className="trace-waterfall-node">
        <span className={`trace-dot is-${normalizeStatus(node.status)}`}></span>
        <span>{node.nodeName || node.nodeType || node.nodeId}</span>
      </div>
      <div className="trace-waterfall-track">
        <div className="trace-waterfall-slot" />
        <div
          className="trace-waterfall-bar"
          style={{ left: `${node.leftPercent}%`, width: `${Math.min(node.widthPercent, 100 - node.leftPercent)}%` }}
        />
      </div>
      <div className="trace-waterfall-duration">
        <strong>{formatDuration(duration)}</strong>
      </div>
    </div>
  );
}
