import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Clock3, RefreshCw, UserRound } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/Badge";
import { Button } from "@/components/Button";
import { EmptyState } from "@/components/EmptyState";
import { buildTraceOverview, buildTraceStageCards, type TraceStageCard } from "@/pages/ragTraceDetailView";
import { getRagTraceDetail, type RagTraceDetail } from "@/services/ragTraceService";
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

function TimeScale({ totalMs }: { totalMs: number }) {
  const ticks = [0, 25, 50, 75, 100];
  return (
    <div className="trace-waterfall-scale">
      {ticks.map((percent) => (
        <div
          className="trace-waterfall-scale__tick"
          key={percent}
          style={{ left: `${percent}%` }}
        >
          <span className="trace-waterfall-scale__line" />
          <span className="trace-waterfall-scale__label">{formatDuration((totalMs * percent) / 100)}</span>
        </div>
      ))}
    </div>
  );
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
  const overview = useMemo(() => buildTraceOverview(nodes), [nodes]);
  const stageCards = useMemo(() => buildTraceStageCards(nodes), [nodes]);
  const timeline = useMemo(() => {
    const normalized = stageCards.map((card) => {
      const start = card.present ? toTimestamp(card.startTime) : 0;
      const duration = card.present ? toNumber(card.durationMs) : 0;
      const end = card.present ? start + Math.max(duration, 1) : 0;
      return { card, start, end, duration };
    });
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
        ...item.card,
        durationMs: item.duration,
        leftPercent: item.card.present && base && item.start > 0 ? Math.min(94, ((item.start - base) / window) * 100) : 0,
        widthPercent: item.card.present ? Math.max(0.8, (Math.max(item.duration, 1) / window) * 100) : 0
      }))
    };
  }, [stageCards, run?.durationMs]);
  const stats = useMemo(() => {
    const success = stageCards.filter((item) => normalizeStatus(item.status) === "success").length;
    const failed = stageCards.filter((item) => normalizeStatus(item.status) === "failed").length;
    const running = stageCards.filter((item) => normalizeStatus(item.status) === "running").length;
    const duration = toNumber(run?.durationMs) || stageCards.reduce((sum, node) => sum + toNumber(node.durationMs), 0);
    const avg = stageCards.length ? Math.round(stageCards.reduce((sum, node) => sum + toNumber(node.durationMs), 0) / stageCards.length) : 0;
    return { success, failed, running, duration, avg };
  }, [stageCards, run?.durationMs]);
  const hasOverview = Boolean(
    overview.originalQuestion || overview.rewrittenQuestion || overview.intentType || overview.routeSource || overview.knowledgeBaseIds.length
  );

  if (!traceId) {
    return <EmptyState title="缺少 Trace Id" description="请从链路追踪列表点击进入详情。" />;
  }

  return (
    <section className="admin-page trace-page trace-detail-shell">
      <div className="trace-detail-head-card">
        <div className="trace-detail-head-content">
          <div className="trace-detail-head-row">
            <div className="trace-detail-head-title">
              <Link className="breadcrumb trace-detail-breadcrumb" to="/admin/traces">
                <ArrowLeft size={14} />
                返回列表
              </Link>
              <div className="trace-detail-head-h1">
                <span>{run?.traceName || "未命名链路"}</span>
                <Badge value={run?.status} />
              </div>
              <p className="trace-detail-head-subtitle">RAG 链路详情</p>
            </div>
            <div className="trace-detail-head-actions">
              <Button className="trace-detail-action-btn" variant="secondary" onClick={load} disabled={loading}>
                <RefreshCw size={16} />
                刷新
              </Button>
            </div>
          </div>
          <div className="trace-detail-meta-bar">
            <div className="trace-detail-meta-field">
              <span className="trace-detail-meta-key">Trace Id</span>
              <span className="trace-detail-meta-value is-mono">{run?.traceId || traceId}</span>
            </div>
            <div className="trace-detail-meta-field">
              <span className="trace-detail-meta-key">开始时间</span>
              <span className="trace-detail-meta-value">
                <Clock3 size={14} />
                {formatDate(run?.startTime)}
              </span>
            </div>
            <div className="trace-detail-meta-field">
              <span className="trace-detail-meta-key">用户</span>
              <span className="trace-detail-meta-value">
                <UserRound size={14} />
                {run?.username || "-"}
              </span>
            </div>
            {run?.conversationId ? (
              <div className="trace-detail-meta-field">
                <span className="trace-detail-meta-key">会话 ID</span>
                <span className="trace-detail-meta-value is-mono">{run.conversationId}</span>
              </div>
            ) : null}
            {run?.taskId ? (
              <div className="trace-detail-meta-field">
                <span className="trace-detail-meta-key">Task ID</span>
                <span className="trace-detail-meta-value is-mono">{run.taskId}</span>
              </div>
            ) : null}
          </div>
          {run?.errorMessage ? (
            <div className="trace-detail-error">
              <strong>执行出错</strong>
              <span>{run.errorMessage}</span>
            </div>
          ) : null}
        </div>
      </div>

      <div className="admin-stat-grid trace-detail-stats">
        <Stat label="总耗时" value={formatDuration(stats.duration)} />
        <Stat label="节点数" value={String(nodes.length)} />
        <Stat label="成功" value={String(stats.success)} />
        <Stat label="失败" value={String(stats.failed)} />
        <Stat label="平均耗时" value={formatDuration(stats.avg)} />
      </div>
      {hasOverview ? (
        <div className="trace-overview-card">
          <div className="trace-overview-header">
            <h2>关键上下文</h2>
          </div>
          <div className="trace-overview-grid">
            {overview.originalQuestion ? <OverviewField label="原问题" value={overview.originalQuestion} /> : null}
            {overview.rewrittenQuestion ? <OverviewField label="改写后问题" value={overview.rewrittenQuestion} /> : null}
            {overview.intentType ? <OverviewField label="最终意图" value={overview.intentType} /> : null}
            {overview.routeSource ? <OverviewField label="意图来源" value={overview.routeSource} /> : null}
            {overview.knowledgeBaseIds.length ? <OverviewField label="路由 KB" value={overview.knowledgeBaseIds.join(", ")} /> : null}
          </div>
        </div>
      ) : null}
      {nodes.length === 0 ? (
        <EmptyState title="暂无链路节点" description="当前 Trace 没有可展示的执行节点。" />
      ) : (
        <div className="trace-waterfall-card">
          <div className="trace-waterfall-header">
            <h2>执行时序</h2>
            <span>窗口 {formatDuration(timeline.windowDuration)}</span>
          </div>
          <div className="trace-waterfall-grid">
            <div className="trace-waterfall-grid-head">
              <span>步骤</span>
              <span>状态</span>
              <span>时间线</span>
              <span>耗时 / 时间</span>
            </div>
            <div className="trace-waterfall-grid-scale">
              <div />
              <div />
              <TimeScale totalMs={timeline.windowDuration} />
              <div />
            </div>
            {timeline.rows.map((card, index) => (
              <TraceRow key={`${card.key}-${index}`} card={card} />
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

function OverviewField({ label, value }: { label: string; value: string }) {
  return (
    <div className="trace-overview-field">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function TraceRow({
  card
}: {
  card: TraceStageCard & { leftPercent: number; widthPercent: number; durationMs: number };
}) {
  return (
    <div className="trace-waterfall-row">
      <div className="trace-waterfall-node">
        <span className={`trace-dot is-${normalizeStatus(card.status)} ${card.present ? "" : "is-empty"}`}></span>
        <div className="trace-waterfall-node__text">
          <strong>{card.title}</strong>
          <span>{card.stage}</span>
        </div>
      </div>
      <div className="trace-waterfall-status">
        <Badge value={card.status} tone={card.present ? undefined : "neutral"} />
      </div>
      <div className="trace-waterfall-track">
        <div className="trace-waterfall-slot" />
        {card.present ? (
          <div
            className={`trace-waterfall-bar is-${normalizeStatus(card.status)}`}
            style={{ left: `${card.leftPercent}%`, width: `${Math.min(card.widthPercent, 100 - card.leftPercent)}%` }}
          />
        ) : (
          <div className="trace-waterfall-bar trace-waterfall-bar--empty" />
        )}
      </div>
      <div className="trace-waterfall-duration">
        <strong>{card.present ? formatDuration(card.durationMs) : "未执行"}</strong>
        <span>{card.present ? formatDate(card.startTime) : "-"}</span>
      </div>
    </div>
  );
}
