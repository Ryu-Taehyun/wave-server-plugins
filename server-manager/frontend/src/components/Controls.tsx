import { AnimatePresence, motion, useAnimationControls } from "motion/react";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { I } from "./Icons";

// ---------------- 버튼 (눌렀을 때 탄성) ----------------
export function Button({ children, tint, className = "", ...p }: React.ButtonHTMLAttributes<HTMLButtonElement> & { tint?: string }) {
  return (
    <motion.button
      whileTap={{ scale: 0.94 }}
      transition={{ type: "spring", stiffness: 600, damping: 24 }}
      className={`btn ${tint ? "tint" : ""} ${className}`}
      style={tint ? ({ ["--c" as string]: tint } as React.CSSProperties) : undefined}
      {...(p as object)}
    >
      {children}
    </motion.button>
  );
}

// ---------------- 세그먼트 (iOS 의 미끄러지는 유리 손잡이) ----------------
export function Segmented<T extends string>({ value, options, onChange, size = "md" }: {
  value: T; options: { value: T; label: ReactNode }[]; onChange: (v: T) => void; size?: "sm" | "md";
}) {
  const id = useId();
  const h = size === "sm" ? 32 : 38;
  return (
    <div className="glass thin" style={{ display: "inline-flex", padding: 3, borderRadius: 999, gap: 2 }} role="tablist">
      {options.map((o) => (
        <button
          key={o.value}
          role="tab"
          aria-selected={value === o.value}
          onClick={() => onChange(o.value)}
          style={{ position: "relative", height: h, padding: size === "sm" ? "0 12px" : "0 16px", border: 0, background: "transparent", borderRadius: 999, fontWeight: 600, fontSize: size === "sm" ? 13 : 14, color: value === o.value ? "var(--text)" : "var(--text-2)", zIndex: 1 }}
        >
          {value === o.value && (
            <motion.span
              layoutId={`seg-${id}`}
              className="glass strong"
              style={{ position: "absolute", inset: 0, borderRadius: 999, zIndex: -1, ["--blur" as string]: "10px" }}
              transition={{ type: "spring", stiffness: 500, damping: 36 }}
            />
          )}
          {o.label}
        </button>
      ))}
    </div>
  );
}

// ---------------- 스위치 ----------------
export function Toggle({ on, onChange, disabled }: { on: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button
      role="switch"
      aria-checked={on}
      disabled={disabled}
      onClick={() => onChange(!on)}
      style={{ width: 52, height: 32, borderRadius: 999, border: 0, padding: 3, display: "flex", justifyContent: on ? "flex-end" : "flex-start",
        background: on ? "var(--green)" : "color-mix(in srgb, var(--text-3) 45%, transparent)", transition: "background .25s", opacity: disabled ? 0.4 : 1,
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,.12)" }}
    >
      <motion.span layout transition={{ type: "spring", stiffness: 700, damping: 32 }}
        style={{ width: 26, height: 26, borderRadius: 999, background: "#fff", boxShadow: "0 3px 8px rgba(0,0,0,.3), 0 0 0 .5px rgba(0,0,0,.04)" }} />
    </button>
  );
}

// ---------------- 길게 눌러 확인 (끄기 · 재시작처럼 되돌리기 어려운 동작) ----------------
export function HoldButton({ children, onConfirm, tint, ms = 900, disabled, icon }: {
  children: ReactNode; onConfirm: () => void; tint: string; ms?: number; disabled?: boolean; icon?: ReactNode;
}) {
  const ctrl = useAnimationControls();
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const [hint, setHint] = useState(false);
  const start = () => {
    if (disabled) return;
    setHint(false);
    ctrl.start({ scaleX: 1, transition: { duration: ms / 1000, ease: "linear" } });
    timer.current = setTimeout(() => {
      ctrl.set({ scaleX: 0 });
      navigator.vibrate?.(18);
      onConfirm();
    }, ms);
  };
  const cancel = (early = true) => {
    if (timer.current) clearTimeout(timer.current);
    ctrl.start({ scaleX: 0, transition: { duration: 0.2 } });
    if (early) {
      setHint(true);
      setTimeout(() => setHint(false), 1600);
    }
  };
  return (
    <div style={{ position: "relative", flex: 1 }}>
      <motion.button
        className="btn"
        disabled={disabled}
        whileTap={{ scale: 0.95 }}
        onPointerDown={start}
        onPointerUp={() => cancel()}
        onPointerLeave={() => cancel(false)}
        onKeyDown={(e) => e.key === "Enter" && !e.repeat && start()}
        onKeyUp={(e) => e.key === "Enter" && cancel()}
        style={{ width: "100%", height: 54, color: tint, overflow: "hidden", fontSize: 16 }}
      >
        <motion.span initial={{ scaleX: 0 }} animate={ctrl} style={{ position: "absolute", inset: 0, transformOrigin: "left", background: `color-mix(in srgb, ${tint} 30%, transparent)`, zIndex: -1 }} />
        {icon}
        {children}
      </motion.button>
      <AnimatePresence>
        {hint && (
          <motion.div initial={{ opacity: 0, y: 4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
            style={{ position: "absolute", top: "100%", left: 0, right: 0, textAlign: "center", fontSize: 12, color: "var(--text-2)", marginTop: 6 }}>
            길게 눌러야 실행됩니다
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ---------------- 시트 (아래에서 올라오는 창, 아래로 끌어 닫기) ----------------
export function Sheet({ open, onClose, title, children, footer }: { open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode }) {
  useEffect(() => {
    if (!open) return;
    const k = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [open, onClose]);
  return (
    <AnimatePresence>
      {open && (
        <motion.div style={{ position: "fixed", inset: 0, zIndex: 60, display: "flex", alignItems: "flex-end", justifyContent: "center" }}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
          <motion.div onClick={onClose} style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,.38)", backdropFilter: "blur(2px)" }} />
          <motion.div
            className="glass strong"
            role="dialog"
            aria-label={title}
            drag="y"
            dragConstraints={{ top: 0, bottom: 0 }}
            dragElastic={{ top: 0, bottom: 0.6 }}
            onDragEnd={(_, i) => (i.offset.y > 120 || i.velocity.y > 600) && onClose()}
            initial={{ y: "100%" }} animate={{ y: 0 }} exit={{ y: "100%" }}
            transition={{ type: "spring", stiffness: 380, damping: 38 }}
            style={{ position: "relative", width: "min(560px, 100%)", maxHeight: "88vh", display: "flex", flexDirection: "column",
              borderRadius: "30px 30px 0 0", padding: "10px 20px calc(18px + var(--safe-b))", ["--blur" as string]: "40px" }}
          >
            <div style={{ width: 38, height: 5, borderRadius: 9, background: "var(--text-3)", margin: "0 auto 12px", opacity: 0.6 }} />
            <div className="row" style={{ marginBottom: 14 }}>
              <div className="card-t grow" style={{ fontSize: 19 }}>{title}</div>
              <button className="btn icon ghost" onClick={onClose} aria-label="닫기"><I.xmark size={20} /></button>
            </div>
            <div style={{ overflowY: "auto", margin: "0 -4px", padding: "2px 4px" }} onPointerDownCapture={(e) => e.stopPropagation()}>{children}</div>
            {footer && <div style={{ marginTop: 16 }}>{footer}</div>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

// ---------------- 숫자 (바뀔 때 부드럽게) ----------------
export function Num({ value, digits = 0, suffix = "" }: { value: number | null | undefined; digits?: number; suffix?: string }) {
  const text = value == null ? "—" : value.toFixed(digits) + suffix;
  return (
    <span className="tnum" style={{ display: "inline-block", position: "relative" }}>
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span key={text} initial={{ y: 10, opacity: 0, filter: "blur(4px)" }} animate={{ y: 0, opacity: 1, filter: "blur(0px)" }}
          exit={{ y: -10, opacity: 0, filter: "blur(4px)" }} transition={{ type: "spring", stiffness: 400, damping: 32 }} style={{ display: "inline-block" }}>
          {text}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

// ---------------- 플레이어 얼굴 ----------------
export function Avatar({ name, size = 34 }: { name: string; size?: number }) {
  const [err, setErr] = useState(false);
  const hue = [...name].reduce((a, c) => a + c.charCodeAt(0), 0) % 360;
  return err ? (
    <div style={{ width: size, height: size, borderRadius: 10, display: "grid", placeItems: "center", fontWeight: 700, fontSize: size * 0.42,
      background: `linear-gradient(140deg, hsl(${hue} 70% 55%), hsl(${(hue + 40) % 360} 70% 40%))`, color: "#fff", flex: "none" }}>
      {name[0]?.toUpperCase()}
    </div>
  ) : (
    <img src={`https://mc-heads.net/avatar/${encodeURIComponent(name)}/${size * 2}`} alt="" width={size} height={size} loading="lazy"
      onError={() => setErr(true)} style={{ borderRadius: 10, imageRendering: "pixelated", flex: "none", boxShadow: "0 2px 8px rgba(0,0,0,.3)" }} />
  );
}
