import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { api, canOperate, type Me, type MetricPoint, type Status } from "../lib/api";
import { duration, STATE_LABEL } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Avatar, Button, HoldButton, Num, Segmented } from "../components/Controls";
import { AreaChart } from "../components/Chart";
import { I } from "../components/Icons";

function Orb({ state }: { state: Status["state"] }) {
  const c = STATE_LABEL[state]?.c ?? "var(--text-3)";
  const busy = state === "starting" || state === "stopping";
  return (
    <div style={{ position: "relative", width: 64, height: 64, flex: "none" }}>
      <motion.div animate={{ scale: state === "running" ? [1, 1.25, 1] : busy ? [1, 1.4, 1] : 1, opacity: state === "stopped" ? 0.15 : [0.5, 0.15, 0.5] }}
        transition={{ duration: busy ? 1.1 : 2.6, repeat: Infinity, ease: "easeInOut" }}
        style={{ position: "absolute", inset: 8, borderRadius: "50%", background: c, filter: "blur(12px)", opacity: 0.5 }} />
      <div style={{ position: "absolute", inset: 10, borderRadius: "50%", background: `radial-gradient(circle at 35% 30%, #fff9, ${c} 45%, color-mix(in srgb, ${c} 55%, #000))`,
        boxShadow: `inset 0 -4px 10px rgba(0,0,0,.35), 0 0 22px ${c}` }} />
      {busy && <motion.div animate={{ rotate: 360 }} transition={{ duration: 1.2, repeat: Infinity, ease: "linear" }}
        style={{ position: "absolute", inset: 4, borderRadius: "50%", border: "2px solid transparent", borderTopColor: c }} />}
    </div>
  );
}

function Stat({ icon, label, children, sub, c }: { icon: React.ReactNode; label: string; children: React.ReactNode; sub?: React.ReactNode; c: string }) {
  return (
    <Glass className="card" interactive {...fadeUp}>
      <div className="row" style={{ gap: 8, color: c, marginBottom: 10 }}>
        <div style={{ width: 30, height: 30, borderRadius: 10, display: "grid", placeItems: "center", background: `color-mix(in srgb, ${c} 18%, transparent)` }}>{icon}</div>
        <span className="muted" style={{ fontSize: 13.5, fontWeight: 600 }}>{label}</span>
      </div>
      <div style={{ fontSize: 30, fontWeight: 700, letterSpacing: "-0.03em", lineHeight: 1.1 }}>{children}</div>
      {sub && <div className="faint" style={{ fontSize: 12.5, marginTop: 4 }}>{sub}</div>}
    </Glass>
  );
}

const RANGES = [{ value: "1h", label: "1시간" }, { value: "24h", label: "24시간" }, { value: "7d", label: "7일" }] as const;

export function Overview({ me, status, setStatus, toast }: { me: Me; status: Status | null; setStatus: (s: Status) => void; toast: (ok: boolean, t: string) => void }) {
  const [range, setRange] = useState<"1h" | "24h" | "7d">("1h");
  const [series, setSeries] = useState<MetricPoint[]>([]);
  const [full, setFull] = useState<Status | null>(null);
  const ops = canOperate(me);

  useEffect(() => {
    let alive = true;
    const load = () => api.metrics(range).then((d) => alive && setSeries(d)).catch(() => {});
    load();
    const t = setInterval(load, 30000);
    return () => { alive = false; clearInterval(t); };
  }, [range]);

  useEffect(() => {
    api.status().then((s) => { setFull(s); setStatus(s); }).catch(() => {});
    const t = setInterval(() => api.status().then(setFull).catch(() => {}), 15000);   // 24시간 요약
    return () => clearInterval(t);
  }, [setStatus]);

  const s = status;
  const st = s?.state ?? "stopped";
  const label = STATE_LABEL[st];
  const power = async (a: "start" | "stop" | "restart") => {
    try {
      const r = await api.power(a);
      toast(true, r.message);
    } catch (e) {
      toast(false, (e as Error).message);
    }
  };
  const sum = full?.summary;

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>개요</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>{full?.address ? `서버 ${full.address}` : "서버 상태"} · {s?.version || "버전 확인 중"}</motion.p>

      {/* 상태 + 전원 */}
      <Glass strong className="card" {...fadeUp} style={{ padding: 22, marginBottom: 16 }}>
        <div className="row wrap" style={{ gap: 18 }}>
          <Orb state={st} />
          <div className="grow" style={{ minWidth: 200 }}>
            <div className="row" style={{ gap: 8 }}>
              <AnimatePresence mode="popLayout">
                <motion.span key={st} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -8 }}
                  style={{ fontSize: 26, fontWeight: 700, letterSpacing: "-0.03em" }}>{label.text}</motion.span>
              </AnimatePresence>
              {s?.online && <span className="pill" style={{ ["--c" as string]: "var(--green)" }}><span className="dot" />접속 가능</span>}
              {s && !s.managed && s.online && <span className="pill" style={{ ["--c" as string]: "var(--purple)" }}>외부에서 켠 서버</span>}
            </div>
            <div className="muted" style={{ marginTop: 2 }}>
              {s?.motd || (s?.online ? "" : s?.error || "서버가 꺼져 있습니다")}
              {s?.uptime ? ` · ${duration(s.uptime)}째 실행 중` : ""}
            </div>
          </div>
          {ops && (
            <div className="row" style={{ gap: 10, flex: "1 1 340px", maxWidth: 520 }}>
              {st === "stopped" || st === "crashed" ? (
                <Button tint="var(--green)" onClick={() => power("start")} style={{ flex: 1, height: 54, fontSize: 16 }}><I.play size={18} />서버 켜기</Button>
              ) : (
                <>
                  <HoldButton tint="var(--orange)" icon={<I.restart size={18} />} onConfirm={() => power("restart")} disabled={st !== "running"}>재시작</HoldButton>
                  <HoldButton tint="var(--red)" icon={<I.stop size={16} />} onConfirm={() => power("stop")} disabled={st === "stopping"}>끄기</HoldButton>
                </>
              )}
            </div>
          )}
        </div>
      </Glass>

      {/* 숫자 */}
      <div className="grid cols-4" style={{ marginBottom: 16 }}>
        <Stat icon={<I.people size={17} />} label="접속자" c="var(--blue)" sub={sum ? `오늘 최고 ${sum.peak_24h ?? 0}명 · ${sum.unique_24h}명 방문` : undefined}>
          <Num value={s?.online ? s.players_online : null} /><span className="faint" style={{ fontSize: 18 }}> / {s?.players_max || "—"}</span>
        </Stat>
        <Stat icon={<I.bolt size={17} />} label="TPS" c={s?.tps != null && s.tps < 18 ? "var(--orange)" : "var(--green)"} sub={sum?.tps_24h ? `24시간 평균 ${sum.tps_24h}` : s?.online && !s.rcon ? "RCON 연결 필요" : undefined}>
          <Num value={s?.online ? s.tps : null} digits={1} />
        </Stat>
        <Stat icon={<I.wifi size={17} />} label="응답 속도" c="var(--teal)" sub={sum?.uptime_24h != null ? `24시간 가동률 ${sum.uptime_24h}%` : undefined}>
          <Num value={s?.online ? s.latency_ms : null} digits={0} suffix=" ms" />
        </Stat>
        <Stat icon={<I.cpu size={17} />} label="CPU · 메모리" c="var(--purple)" sub={s?.managed ? "이 도구가 켠 서버" : "이 도구가 켠 서버만 측정"}>
          <span style={{ fontSize: 24 }}><Num value={s?.cpu} digits={0} suffix="%" /></span>
          <span className="faint" style={{ fontSize: 15 }}> · {s?.mem_mb == null ? "—" : s.mem_mb >= 1024 ? `${(s.mem_mb / 1024).toFixed(1)} GB` : `${Math.round(s.mem_mb)} MB`}</span>
        </Stat>
      </div>

      {/* 그래프 */}
      <div className="grid cols-2" style={{ marginBottom: 16 }}>
        <Glass className="card" {...fadeUp}>
          <div className="card-h">
            <div className="card-t">접속자</div>
            <Segmented size="sm" value={range} onChange={setRange} options={RANGES.map((r) => ({ ...r }))} />
          </div>
          <AreaChart data={series.map((p) => ({ t: p.t, v: p.players }))} color="#0a84ff" label="명" format={(v) => v.toFixed(v < 10 ? 1 : 0)} />
        </Glass>
        <Glass className="card" {...fadeUp}>
          <div className="card-h">
            <div className="card-t">TPS</div>
            <span className="faint" style={{ fontSize: 13 }}>20 이 가장 좋음</span>
          </div>
          <AreaChart data={series.map((p) => ({ t: p.t, v: p.tps }))} color="#30d158" max={20.5} format={(v) => v.toFixed(1)} label="TPS" />
        </Glass>
      </div>

      {/* 접속 중인 플레이어 */}
      <Glass className="card" {...fadeUp}>
        <div className="card-h">
          <div className="card-t">지금 접속 중</div>
          <span className="pill">{s?.players.length ?? 0}명</span>
        </div>
        {s && s.players.length ? (
          <motion.div layout style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
            <AnimatePresence>
              {s.players.map((n) => (
                <motion.div key={n} layout initial={{ opacity: 0, scale: 0.8 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.8 }}
                  className="glass thin" style={{ display: "flex", alignItems: "center", gap: 8, padding: "6px 12px 6px 6px", borderRadius: 999 }}>
                  <Avatar name={n} size={26} /><span style={{ fontWeight: 600, fontSize: 14 }}>{n}</span>
                </motion.div>
              ))}
            </AnimatePresence>
          </motion.div>
        ) : <div className="empty">{s?.online ? "접속한 플레이어가 없습니다" : "서버가 꺼져 있습니다"}</div>}
      </Glass>
    </div>
  );
}
