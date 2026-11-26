// 다이내믹 아일랜드처럼 위에서 늘어나는 알림 + 연결 상태
import { AnimatePresence, motion } from "motion/react";
import type { Toast } from "../lib/live";
import { I } from "./Icons";

export function Island({ toasts, connected }: { toasts: Toast[]; connected: boolean }) {
  const t = toasts[toasts.length - 1];
  const show = t || !connected;
  return (
    <div style={{ position: "fixed", top: "calc(10px + var(--safe-t))", left: 0, right: 0, display: "flex", justifyContent: "center", zIndex: 80, pointerEvents: "none" }}>
      <AnimatePresence>
        {show && (
          <motion.div
            key="island"
            layout
            initial={{ opacity: 0, scale: 0.6, y: -20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.6, y: -20 }}
            transition={{ type: "spring", stiffness: 420, damping: 30 }}
            style={{ background: "#000", color: "#fff", borderRadius: 999, padding: "10px 18px 10px 12px", display: "flex", alignItems: "center", gap: 10,
              boxShadow: "0 12px 30px rgba(0,0,0,.4), inset 0 0 0 1px rgba(255,255,255,.08)", maxWidth: "min(92vw, 520px)", fontSize: 14, fontWeight: 600 }}
          >
            <motion.div layout style={{ width: 26, height: 26, borderRadius: 999, display: "grid", placeItems: "center", flex: "none",
              background: t ? (t.ok ? "var(--green)" : "var(--red)") : "var(--orange)" }}>
              {t ? (t.ok ? <I.check size={16} /> : <I.xmark size={16} />) : <I.wifi size={16} />}
            </motion.div>
            <AnimatePresence mode="popLayout">
              <motion.span key={t ? t.id : "offline"} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }}
                style={{ whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {t ? t.text : "실시간 연결 끊김 · 다시 연결하는 중"}
              </motion.span>
            </AnimatePresence>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
