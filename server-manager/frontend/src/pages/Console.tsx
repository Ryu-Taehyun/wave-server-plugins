import { motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { api, canOperate, type Me } from "../lib/api";
import { Glass, fadeUp } from "../components/Glass";
import { Button, Toggle } from "../components/Controls";
import { I } from "../components/Icons";

const QUICK = ["list", "tps", "save-all", "weather clear", "time set day", "whitelist list"];

function color(line: string): string | undefined {
  if (/\b(ERROR|SEVERE)\b|Exception|at [\w.$]+\(/.test(line)) return "var(--red)";
  if (/\bWARN(ING)?\b/.test(line)) return "var(--orange)";
  if (/\[Manager\]/.test(line)) return "var(--teal)";
  if (/joined the game|left the game/.test(line)) return "var(--green)";
  if (/<[^>]+>/.test(line)) return "var(--text)";
  return undefined;
}

export function Console({ me, logs, toast }: { me: Me; logs: string[]; toast: (ok: boolean, t: string) => void }) {
  const [cmd, setCmd] = useState("");
  const [filter, setFilter] = useState("");
  const [follow, setFollow] = useState(true);
  const [hist, setHist] = useState<string[]>(() => JSON.parse(localStorage.getItem("mcm.hist") || "[]"));
  const [hi, setHi] = useState(-1);
  const [busy, setBusy] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const ops = canOperate(me);

  const shown = useMemo(() => {
    const f = filter.trim().toLowerCase();
    const l = f ? logs.filter((x) => x.toLowerCase().includes(f)) : logs;
    return l.slice(-1200);
  }, [logs, filter]);

  useEffect(() => {
    if (follow && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [shown, follow]);

  const send = async (c: string) => {
    c = c.trim();
    if (!c) return;
    setBusy(true);
    try {
      await api.console(c);
      const h = [c, ...hist.filter((x) => x !== c)].slice(0, 50);
      setHist(h);
      try { localStorage.setItem("mcm.hist", JSON.stringify(h)); } catch { /* 저장 못 해도 괜찮음 */ }
      setCmd("");
      setHi(-1);
    } catch (e) {
      toast(false, (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>콘솔</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>실시간 서버 로그 · 명령어는 RCON 으로 실행됩니다</motion.p>
      <Glass className="card" {...fadeUp} style={{ padding: 0, overflow: "hidden" }}>
        <div className="row wrap" style={{ padding: "12px 14px", gap: 10, borderBottom: "1px solid color-mix(in srgb, var(--stroke) 60%, transparent)" }}>
          <div className="row grow" style={{ minWidth: 180, gap: 8 }}>
            <I.search size={18} style={{ color: "var(--text-3)" }} />
            <input className="field" style={{ height: 36, background: "transparent", boxShadow: "none", padding: 0 }} placeholder="로그 검색" value={filter} onChange={(e) => setFilter(e.target.value)} />
          </div>
          <span className="faint" style={{ fontSize: 13 }}>자동 스크롤</span>
          <Toggle on={follow} onChange={setFollow} />
        </div>
        <div ref={box} className="mono" onWheel={(e) => e.deltaY < 0 && setFollow(false)}
          style={{ height: "min(58vh, 560px)", overflowY: "auto", padding: "12px 16px", fontSize: 12.5, lineHeight: 1.65, background: "rgba(0,0,0,.28)", color: "var(--text-2)", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
          {shown.length ? shown.map((l, i) => <div key={i} style={{ color: color(l) }}>{l}</div>) : <div className="empty">로그가 없습니다</div>}
        </div>
        {ops && (
          <div style={{ padding: 12 }}>
            <div className="row wrap" style={{ gap: 6, marginBottom: 10 }}>
              {QUICK.map((q) => <Button key={q} className="sm mono" onClick={() => send(q)} disabled={busy}>/{q}</Button>)}
            </div>
            <form className="row" onSubmit={(e) => { e.preventDefault(); send(cmd); }}>
              <div className="grow" style={{ position: "relative" }}>
                <span className="mono faint" style={{ position: "absolute", left: 16, top: 13 }}>/</span>
                <input className="field mono" style={{ paddingLeft: 28 }} placeholder="명령어 (예: say 안녕하세요)" value={cmd}
                  onChange={(e) => setCmd(e.target.value)} autoCapitalize="none" autoComplete="off" spellCheck={false}
                  onKeyDown={(e) => {
                    if (e.key === "ArrowUp" && hist.length) { e.preventDefault(); const n = Math.min(hist.length - 1, hi + 1); setHi(n); setCmd(hist[n]); }
                    if (e.key === "ArrowDown") { e.preventDefault(); const n = hi - 1; setHi(n); setCmd(n >= 0 ? hist[n] : ""); }
                  }} />
              </div>
              <Button tint="var(--blue)" className="icon" type="submit" disabled={busy || !cmd.trim()} aria-label="보내기" style={{ width: 48, height: 48 }}><I.send size={20} /></Button>
            </form>
          </div>
        )}
      </Glass>
    </div>
  );
}
