import { statusText } from "@/utils/format";

export function Badge({ value, tone }: { value?: string | null; tone?: "success" | "warning" | "danger" | "neutral" }) {
  const normalized =
    tone ||
    (value?.includes("FAIL") || value?.includes("DISABLED")
      ? "danger"
      : value?.includes("PARS") || value?.includes("PENDING")
        ? "warning"
        : value?.includes("SUCCESS") || value?.includes("ENABLED")
          ? "success"
          : "neutral");
  return <span className={`badge badge-${normalized}`}>{statusText(value)}</span>;
}
