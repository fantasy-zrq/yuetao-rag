import type { PropsWithChildren } from "react";
import { X } from "lucide-react";

export function Modal({
  title,
  open,
  onClose,
  children
}: PropsWithChildren<{ title: string; open: boolean; onClose: () => void }>) {
  if (!open) return null;
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal-panel">
        <div className="modal-header">
          <h2>{title}</h2>
          <button className="icon-btn" type="button" onClick={onClose} aria-label="关闭">
            <X size={18} />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
