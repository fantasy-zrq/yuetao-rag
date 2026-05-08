import { Brain, Loader2 } from "lucide-react";

interface ThinkingIndicatorProps {
  content?: string;
  durationMs?: number | null;
}

export function ThinkingIndicator({ content, durationMs }: ThinkingIndicatorProps) {
  const seconds = durationMs ? Math.round(durationMs / 1000) : 0;
  return (
    <div className="thinking-indicator">
      <div className="thinking-header">
        <Loader2 size={16} className="thinking-spinner" />
        <span>正在深度思考...</span>
        {seconds > 0 ? (
          <span className="thinking-duration-badge">{seconds}秒</span>
        ) : null}
      </div>
      <div className="thinking-body">
        <Brain size={14} className="thinking-body-icon" />
        <p className="thinking-body-text">
          {content || ""}
          <span className="thinking-cursor-bar" />
        </p>
      </div>
    </div>
  );
}
