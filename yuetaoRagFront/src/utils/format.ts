export function formatDate(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

export function formatConversationTime(value?: string | null) {
  if (!value) return "未提问";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const now = new Date();
  const dateStart = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const time = new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);

  if (dateStart === todayStart) return time;
  if (dateStart === todayStart - 24 * 60 * 60 * 1000) return `昨天 ${time}`;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

export function formatFileSize(value?: number | null) {
  if (!value) return "-";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

export function statusText(value?: string | null) {
  const map: Record<string, string> = {
    ENABLED: "启用",
    DISABLED: "停用",
    ACTIVE: "活跃",
    ARCHIVED: "归档",
    PENDING: "待处理",
    PARSING: "解析中",
    SUCCESS: "成功",
    FAILED: "失败"
  };
  return value ? map[value] || value : "-";
}
