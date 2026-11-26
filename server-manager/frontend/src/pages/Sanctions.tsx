import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useState } from "react";
import { api, canOperate, type Me, type Report, type SanctionAction, type SanctionOverview } from "../lib/api";
import { ago, clock, until } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Avatar, Button, Segmented, Sheet } from "../components/Controls";
import { I } from "../components/Icons";

const ACTS: { a: SanctionAction; label: string; c: string; dur?: boolean; reason?: "need" | "opt"; hint: string }[] = [
  { a: "warn", label: "경고", c: "var(--yellow)", reason: "need", hint: "WaveCore 경고 누적 (설정한 횟수가 되면 자동 처벌)" },
  { a: "mute", label: "채팅 금지", c: "var(--orange)", dur: true, reason: "opt", hint: "정한 시간 동안 채팅 금지" },
  { a: "tempban", label: "임시 차단", c: "var(--red)", dur: true, reason: "need", hint: "정한 시간 동안 접속 금지 (이의 제기 코드 발급)" },
  { a: "ban", label: "영구 차단", c: "var(--red)", reason: "need", hint: "WaveCore serverban (영구)" },
  { a: "kick", label: "강퇴", c: "var(--text-2)", reason: "opt", hint: "지금 서버에서 내보냄" },
];
const UNDO: { a: SanctionAction; label: string }[] = [
  { a: "unmute", label: "채팅 금지 해제" }, { a: "unban", label: "차단 해제" }, { a: "warn_clear", label: "경고 모두 삭제" },
];
const DURS = ["30m", "1h", "6h", "1d", "3d", "7d", "1w"];
const DUR_LABEL: Record<string, string> = { "30m": "30분", "1h": "1시간", "6h": "6시간", "1d": "1일", "3d": "3일", "7d": "7일", "1w": "1주" };
const KIND_C: Record<string, string> = { 경고: "var(--yellow)", 뮤트: "var(--orange)", 임시밴: "var(--red)", 영구밴: "var(--red)", 차단해제: "var(--green)", 뮤트해제: "var(--green)", 경고삭제: "var(--green)" };

/** 한 플레이어 제재 (플레이어 화면 · 신고 처리에서도 씀) */
export function SanctionForm({ name, initialReason = "", toast, onDone }: { name: string; initialReason?: string; toast: (ok: boolean, t: string) => void; onDone?: () => void }) {
  const [act, setAct] = useState<SanctionAction>("warn");
  const [dur, setDur] = useState("1d");
  const [reason, setReason] = useState(initialReason);
  const [busy, setBusy] = useState(false);
  const cur = ACTS.find((x) => x.a === act)!;
  const go = async (a: SanctionAction) => {
    setBusy(true);
    try {
      const r = await api.sanction(a, name, { duration: dur, reason });
      toast(true, r.output || "처리했습니다");
      onDone?.();
    } catch (e) {
      toast(false, (e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div className="row wrap" style={{ gap: 6 }}>
        {ACTS.map((x) => (
          <button key={x.a} onClick={() => setAct(x.a)} className="pill" style={{ ["--c" as string]: act === x.a ? x.c : "var(--text-3)", height: 32, border: 0, cursor: "pointer", fontSize: 13.5 }}>{x.label}</button>
        ))}
      </div>
      <div className="faint" style={{ fontSize: 12.5 }}>{cur.hint}</div>
      {cur.dur && (
        <div className="row wrap" style={{ gap: 6 }}>
          {DURS.map((d) => <button key={d} onClick={() => setDur(d)} className={`btn sm ${dur === d ? "tint" : ""}`} style={dur === d ? { ["--c" as string]: cur.c } : undefined}>{DUR_LABEL[d]}</button>)}
          <input className="field tnum" style={{ width: 84, height: 34 }} value={dur} onChange={(e) => setDur(e.target.value.trim())} aria-label="기간" />
        </div>
      )}
      <input className="field" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200}
        placeholder={cur.reason === "need" ? "사유 (필수, 플레이어에게 보임)" : "사유 (선택)"} />
      <Button tint={cur.c} disabled={busy || (cur.reason === "need" && !reason.trim())} onClick={() => go(act)} style={{ height: 48 }}>
        {name} 님 {cur.label}{cur.dur ? ` (${DUR_LABEL[dur] ?? dur})` : ""}
      </Button>
      <div className="row wrap" style={{ gap: 6 }}>
        {UNDO.map((x) => <Button key={x.a} className="sm" disabled={busy} onClick={() => go(x.a)}>{x.label}</Button>)}
      </div>
    </div>
  );
}

type Tab = "reports" | "bans" | "mutes" | "warnings" | "history";

export function Sanctions({ me, toast, reportTick }: { me: Me; toast: (ok: boolean, t: string) => void; reportTick: number }) {
  const [ov, setOv] = useState<SanctionOverview | null>(null);
  const [reports, setReports] = useState<Report[]>([]);
  const [rstatus, setRstatus] = useState<"open" | "all">("open");
  const [tab, setTab] = useState<Tab>("reports");
  const [sel, setSel] = useState<{ name: string; reason?: string; report?: Report } | null>(null);
  const [q, setQ] = useState("");
  const ops = canOperate(me);

  const load = () => {
    api.sanctions().then(setOv).catch(() => {});
    api.reports(rstatus).then(setReports).catch(() => {});
  };
  useEffect(() => { load(); const t = setInterval(load, 20000); return () => clearInterval(t); }, [rstatus, reportTick]);

  const setReport = async (r: Report, status: Report["status"], note = "") => {
    try { await api.setReport(r.id, status, note); toast(true, status === "done" ? "처리 완료로 표시했습니다" : status === "dismissed" ? "기각했습니다" : "다시 열었습니다"); load(); }
    catch (e) { toast(false, (e as Error).message); }
  };

  const history = useMemo(() => {
    const f = q.trim().toLowerCase();
    return (ov?.history ?? []).filter((h) => !f || h.name.toLowerCase().includes(f) || h.detail.toLowerCase().includes(f));
  }, [ov, q]);
  const personal = sel ? (ov?.history ?? []).filter((h) => h.name.toLowerCase() === sel.name.toLowerCase()) : [];

  const nameRow = (name: string, sub: React.ReactNode, right?: React.ReactNode, reason?: string) => (
    <motion.div key={name + String(sub)} layout initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="list-item">
      <Avatar name={name} />
      <div className="grow" style={{ minWidth: 0 }}>
        <b style={{ fontWeight: 650 }}>{name}</b>
        <div className="faint" style={{ fontSize: 12.5, overflowWrap: "anywhere" }}>{sub}</div>
      </div>
      {right}
      {ops && <Button className="sm" onClick={() => setSel({ name, reason })}>관리</Button>}
    </motion.div>
  );

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>제재</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>신고 처리 · 경고 · 채팅 금지 · 차단 (WaveCore 연동)</motion.p>

      <div className="grid cols-4" style={{ marginBottom: 16 }}>
        {[
          { l: "처리 안 한 신고", v: ov?.reports_open, c: "var(--orange)", t: "reports" as Tab },
          { l: "차단 중", v: ov?.bans.length, c: "var(--red)", t: "bans" as Tab },
          { l: "채팅 금지 중", v: ov?.mutes.length, c: "var(--yellow)", t: "mutes" as Tab },
          { l: "경고 받은 사람", v: ov?.warnings.length, c: "var(--blue)", t: "warnings" as Tab },
        ].map((x) => (
          <Glass key={x.l} className="card" interactive {...fadeUp} onClick={() => setTab(x.t)} style={{ cursor: "pointer" }}>
            <div className="muted" style={{ fontSize: 13.5, fontWeight: 600 }}>{x.l}</div>
            <div className="tnum" style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: x.v ? x.c : undefined }}>{x.v ?? "—"}</div>
          </Glass>
        ))}
      </div>

      <Glass className="card" {...fadeUp}>
        <div className="row wrap" style={{ marginBottom: 12, gap: 10 }}>
          <div style={{ overflowX: "auto", maxWidth: "100%" }}>
            <Segmented value={tab} onChange={setTab} size="sm" options={[
              { value: "reports", label: "신고" }, { value: "bans", label: "차단" }, { value: "mutes", label: "채팅 금지" },
              { value: "warnings", label: "경고" }, { value: "history", label: "기록" }]} />
          </div>
          <div className="grow" />
          {tab === "reports" && <Segmented size="sm" value={rstatus} onChange={setRstatus} options={[{ value: "open", label: "대기" }, { value: "all", label: "전체" }]} />}
          {tab === "history" && <input className="field" style={{ height: 36, width: 180 }} placeholder="닉네임 · 내용" value={q} onChange={(e) => setQ(e.target.value)} />}
          {ops && <Button className="sm" onClick={() => { const n = prompt("닉네임"); if (n?.trim()) setSel({ name: n.trim() }); }}><I.plus size={16} />제재하기</Button>}
        </div>
        <div className="list">
          <AnimatePresence initial={false}>
            {tab === "reports" && reports.map((r) => (
              <motion.div key={r.id} layout initial={{ opacity: 0, y: 6 }} animate={{ opacity: r.status === "open" ? 1 : 0.6, y: 0 }} exit={{ opacity: 0 }} className="list-item" style={{ alignItems: "flex-start" }}>
                <div style={{ width: 34, height: 34, borderRadius: 10, display: "grid", placeItems: "center", flex: "none", color: "#fff", background: r.status === "open" ? "var(--orange)" : "var(--text-3)" }}><I.flag size={18} /></div>
                <div className="grow" style={{ minWidth: 0 }}>
                  <div><b>{r.reporter}</b> <span className="faint">→</span> <b style={{ color: "var(--red)" }}>{r.target ?? "?"}</b> <span className="faint" style={{ fontSize: 12.5 }}>· {ago(r.ts)}</span></div>
                  <div style={{ fontSize: 14, marginTop: 2, overflowWrap: "anywhere" }}>{r.reason || <span className="faint">사유 없음</span>}</div>
                  {r.status !== "open" && <div className="faint" style={{ fontSize: 12 }}>{r.status === "done" ? "처리 완료" : "기각"} · {r.handled_by} · {ago(r.handled_at)}{r.note ? ` · ${r.note}` : ""}</div>}
                </div>
                {ops && (r.status === "open" ? (
                  <div className="row" style={{ gap: 6, flex: "none" }}>
                    {r.target && <Button className="sm" tint="var(--red)" onClick={() => setSel({ name: r.target!, reason: r.reason ?? "", report: r })}>제재</Button>}
                    <Button className="sm" onClick={() => setReport(r, "done")} aria-label="처리 완료"><I.check size={16} /></Button>
                    <Button className="sm" onClick={() => setReport(r, "dismissed")} aria-label="기각"><I.xmark size={16} /></Button>
                  </div>
                ) : <Button className="sm ghost" onClick={() => setReport(r, "open")}>다시 열기</Button>)}
              </motion.div>
            ))}
            {tab === "bans" && ov?.bans.map((b) => nameRow(b.name, <>{b.reason} · {b.until ? `${until(b.until)} 해제` : "영구"} · #{b.code} · {b.by} {ago(b.at)}</>,
              <span className="pill" style={{ ["--c" as string]: "var(--red)" }}>{b.until ? "임시" : "영구"}</span>))}
            {tab === "mutes" && ov?.mutes.map((m) => nameRow(m.name, <>{m.reason || "사유 없음"} · {until(m.until)} 풀림</>))}
            {tab === "warnings" && ov?.warnings.map((w) => nameRow(w.name, w.last || "—", <span className="pill tnum" style={{ ["--c" as string]: "var(--yellow)" }}>{w.warnings}회</span>))}
            {tab === "history" && history.map((h, i) => (
              <motion.div key={h.time + h.name + i} layout initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="list-item" style={{ padding: "10px 4px" }}>
                <span className="pill" style={{ ["--c" as string]: KIND_C[h.kind] ?? "var(--text-3)", flex: "none", minWidth: 64, justifyContent: "center" }}>{h.kind}</span>
                <div className="grow" style={{ minWidth: 0 }}>
                  <b>{h.name}</b> <span className="muted" style={{ overflowWrap: "anywhere" }}>{h.detail}</span>
                  <div className="faint" style={{ fontSize: 12 }}>{h.time} · {h.by}</div>
                </div>
              </motion.div>
            ))}
          </AnimatePresence>
          {((tab === "reports" && !reports.length) || (tab === "bans" && !ov?.bans.length) || (tab === "mutes" && !ov?.mutes.length)
            || (tab === "warnings" && !ov?.warnings.length) || (tab === "history" && !history.length)) && (
            <div className="empty">{tab === "reports" ? (rstatus === "open" ? "처리할 신고가 없습니다 👍" : "신고 기록이 없습니다") : "없습니다"}</div>
          )}
        </div>
      </Glass>

      <Sheet open={!!sel} onClose={() => setSel(null)} title={sel ? `${sel.name} 제재` : ""}>
        {sel && (
          <div style={{ display: "grid", gap: 16 }}>
            {sel.report && <div className="glass thin" style={{ padding: "10px 12px", borderRadius: 14, fontSize: 13.5 }}><b>{sel.report.reporter}</b> 님의 신고: {sel.report.reason || "사유 없음"}</div>}
            <SanctionForm name={sel.name} initialReason={sel.reason} toast={toast} onDone={() => {
              if (sel.report) setReport(sel.report, "done", "제재함");
              setSel(null);
              setTimeout(load, 500);
            }} />
            <div>
              <b>제재 기록</b>
              <div style={{ marginTop: 6, fontSize: 13 }}>
                {personal.length ? personal.slice(0, 15).map((h, i) => (
                  <div key={i} className="row" style={{ gap: 8, padding: "4px 0" }}>
                    <span className="pill" style={{ ["--c" as string]: KIND_C[h.kind] ?? "var(--text-3)", height: 22, fontSize: 11.5 }}>{h.kind}</span>
                    <span className="grow muted">{h.detail}</span><span className="faint" style={{ fontSize: 11.5 }}>{clock(Date.parse(h.time.replace(" ", "T")) / 1000)}</span>
                  </div>
                )) : <span className="faint">없음</span>}
              </div>
            </div>
          </div>
        )}
      </Sheet>
    </div>
  );
}
