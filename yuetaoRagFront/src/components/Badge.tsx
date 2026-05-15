import { statusText } from "@/utils/format";

export function Badge({ value, tone }: { value?: string | null; tone?: "success" | "warning" | "danger" | "neutral" }) {
  const normalizedValue = String(value || "").toUpperCase();
  const normalized =
    tone ||
    (normalizedValue.includes("FAIL") || normalizedValue.includes("TIMEOUT") || normalizedValue.includes("DISABLED")
      ? "danger"
      : normalizedValue.includes("RUN") || normalizedValue.includes("PARS") || normalizedValue.includes("PENDING")
        ? "warning"
        : normalizedValue.includes("SUCCESS") || normalizedValue.includes("ENABLED")
          ? "success"
          : "neutral");
  return <span className={`badge badge-${normalized}`}>{statusText(value)}</span>;
}
