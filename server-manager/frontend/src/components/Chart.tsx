// 면적 그래프 (SVG). 값이 바뀌면 선이 부드럽게 바뀜
import { motion } from "motion/react";
import { useId, useMemo, useState } from "react";

export function AreaChart({ data, color, height = 150, max, format = (v) => String(v), label }: {
  data: { t: number; v: number | null }[]; color: string; height?: number; max?: number; format?: (v: number) => string; label?: string;
}) {
  const id = useId();
  const [hover, setHover] = useState<number | null>(null);
  const W = 600;
  const pts = data.filter((d) => d.v != null) as { t: number; v: number }[];
  const { path, area, top, xs } = useMemo(() => {
    if (pts.length < 2) return { path: "", area: "", top: max ?? 1, xs: [] as number[] };
    const t0 = pts[0].t, t1 = pts[pts.length - 1].t;
    const top = max ?? Math.max(1, ...pts.map((p) => p.v)) * 1.15;
    const X = (t: number) => ((t - t0) / Math.max(1, t1 - t0)) * W;
    const Y = (v: number) => height - 6 - (v / top) * (height - 16);
    const xy = pts.map((p) => [X(p.t), Y(p.v)] as const);
    let d = `M ${xy[0][0]} ${xy[0][1]}`;
    for (let i = 1; i < xy.length; i++) {
      const [x0, y0] = xy[i - 1], [x1, y1] = xy[i];
      const cx = (x0 + x1) / 2;
      d += ` C ${cx} ${y0}, ${cx} ${y1}, ${x1} ${y1}`;
    }
    return { path: d, area: `${d} L ${W} ${height} L 0 ${height} Z`, top, xs: xy.map((p) => p[0]) };
  }, [pts, height, max]);

  if (pts.length < 2) return <div className="empty" style={{ height }}>기록이 쌓이는 중입니다</div>;
  const hp = hover != null ? pts[hover] : null;
  return (
    <div style={{ position: "relative" }}>
      <svg viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" width="100%" height={height} style={{ display: "block", overflow: "visible" }}
        onPointerMove={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          const x = ((e.clientX - r.left) / r.width) * W;
          let best = 0;
          xs.forEach((v, i) => { if (Math.abs(v - x) < Math.abs(xs[best] - x)) best = i; });
          setHover(best);
        }}
        onPointerLeave={() => setHover(null)}>
        <defs>
          <linearGradient id={`g${id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity="0.45" />
            <stop offset="100%" stopColor={color} stopOpacity="0" />
          </linearGradient>
        </defs>
        {[0.25, 0.5, 0.75].map((f) => <line key={f} x1="0" x2={W} y1={height * f} y2={height * f} stroke="currentColor" strokeOpacity="0.07" />)}
        <path d={area} fill={`url(#g${id})`} />
        <motion.path d={path} fill="none" stroke={color} strokeWidth="2.5" vectorEffect="non-scaling-stroke" strokeLinecap="round"
          initial={{ pathLength: 0 }} animate={{ pathLength: 1 }} transition={{ duration: 0.9, ease: "easeOut" }}
          />
        {hover != null && <line x1={xs[hover]} x2={xs[hover]} y1="0" y2={height} stroke="currentColor" strokeOpacity=".25" vectorEffect="non-scaling-stroke" />}
      </svg>
      {hp && (
        <div className="glass strong" style={{ position: "absolute", top: -6, left: `${(xs[hover!] / W) * 100}%`, transform: "translate(-50%, -100%)", padding: "6px 10px", borderRadius: 12, fontSize: 12.5, whiteSpace: "nowrap", pointerEvents: "none" }}>
          <b className="tnum">{format(hp.v)}</b> <span className="muted">{label}</span>
          <div className="faint tnum">{new Date(hp.t * 1000).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" })}</div>
        </div>
      )}
      <div className="row faint tnum" style={{ justifyContent: "space-between", fontSize: 11.5, marginTop: 4 }}>
        <span>{new Date(pts[0].t * 1000).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}</span>
        <span>최고 {format(Math.max(...pts.map((p) => p.v)))}</span>
        <span>{new Date(pts[pts.length - 1].t * 1000).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}</span>
      </div>
      <span style={{ display: "none" }}>{top}</span>
    </div>
  );
}
