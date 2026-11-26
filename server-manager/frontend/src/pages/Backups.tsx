import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { api, canOperate, type BackupInfo, type Me } from "../lib/api";
import type { BackupProgress } from "../lib/live";
import { ago, bytes, clock } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Button, HoldButton, Sheet } from "../components/Controls";
import { I } from "../components/Icons";

const KIND = { auto: { t: "자동", c: "var(--blue)" }, manual: { t: "직접", c: "var(--purple)" }, safety: { t: "복원 전", c: "var(--orange)" } } as const;

export function Backups({ me, progress, tick, toast }: { me: Me; progress: BackupProgress | null; tick: number; toast: (ok: boolean, t: string) => void }) {
  const [info, setInfo] = useState<BackupInfo | null>(null);
  const [note, setNote] = useState("");
  const [sel, setSel] = useState<number | null>(null);
  const ops = canOperate(me);
  const admin = me.role === "admin";

  const load = () => api.backups().then(setInfo).catch(() => {});
  useEffect(() => { load(); }, [tick]);
  useEffect(() => { const t = setInterval(load, 20000); return () => clearInterval(t); }, []);

  const busy = progress?.phase || info?.busy;
  const b = info?.items.find((x) => x.id === sel);
  const used = info ? info.disk.total_bytes - info.disk.free_bytes : 0;

  return (
    <div>
      <div className="row wrap" style={{ justifyContent: "space-between" }}>
        <div>
          <motion.h1 className="page-title" {...fadeUp}>백업</motion.h1>
          <motion.p className="page-sub" {...fadeUp}>월드 · 플러그인 · 설정을 zip 으로 · 클릭 한 번으로 복원</motion.p>
        </div>
      </div>

      <div className="grid cols-3" style={{ marginBottom: 16 }}>
        <Glass className="card" {...fadeUp} style={{ gridColumn: "span 2" }}>
          <div className="card-t" style={{ marginBottom: 10 }}>지금 백업</div>
          <AnimatePresence mode="wait">
            {busy ? (
              <motion.div key="busy" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <div className="row" style={{ justifyContent: "space-between", marginBottom: 8 }}>
                  <b>{busy}</b><span className="tnum muted">{progress?.pct != null ? `${progress.pct.toFixed(0)}%` : ""}</span>
                </div>
                <div style={{ height: 8, borderRadius: 9, background: "color-mix(in srgb, var(--text-3) 25%, transparent)", overflow: "hidden" }}>
                  <motion.div animate={{ width: `${progress?.pct ?? 8}%` }} transition={{ type: "spring", stiffness: 120, damping: 20 }}
                    style={{ height: "100%", background: "var(--blue)", borderRadius: 9 }} />
                </div>
                <p className="faint" style={{ fontSize: 12.5, margin: "8px 0 0" }}>서버가 켜져 있으면 저장을 잠시 멈추고(save-off) 백업한 뒤 다시 켭니다. 플레이어는 계속 놀 수 있어요.</p>
              </motion.div>
            ) : (
              <motion.div key="idle" className="row" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                <input className="field grow" placeholder="메모 (선택, 예: 업데이트 전)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} disabled={!ops} />
                <Button tint="var(--blue)" disabled={!ops} onClick={async () => { try { await api.createBackup(note); setNote(""); } catch (e) { toast(false, (e as Error).message); } }}>
                  <I.plus size={18} />백업
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
          <div className="faint" style={{ fontSize: 12.5, marginTop: 12 }}>포함: {info?.include.join(" · ")}</div>
        </Glass>
        <Glass className="card" {...fadeUp}>
          <div className="card-t" style={{ marginBottom: 10 }}>저장 공간</div>
          <div style={{ fontSize: 26, fontWeight: 700 }}>{bytes(info?.disk.free_bytes)} <span className="faint" style={{ fontSize: 14 }}>남음</span></div>
          <div style={{ height: 8, borderRadius: 9, background: "color-mix(in srgb, var(--text-3) 25%, transparent)", overflow: "hidden", margin: "10px 0 8px" }}>
            <div style={{ height: "100%", width: `${info ? (used / info.disk.total_bytes) * 100 : 0}%`, background: info && info.disk.free_bytes < 5 * 1073741824 ? "var(--orange)" : "var(--green)" }} />
          </div>
          <div className="faint" style={{ fontSize: 12.5 }}>백업 {bytes(info?.disk.backups_bytes)} · 자동 백업은 최근 {info?.keep}개만 보관</div>
        </Glass>
      </div>

      <Glass className="card" {...fadeUp}>
        <div className="card-h"><div className="card-t">백업 목록</div><span className="faint" style={{ fontSize: 13 }}>예약 → 새 작업 → 백업 으로 자동 백업</span></div>
        <div className="list">
          <AnimatePresence initial={false}>
            {info?.items.map((x) => (
              <motion.div key={x.id} layout initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="list-item wrap">
                <div style={{ width: 38, height: 38, borderRadius: 12, display: "grid", placeItems: "center", flex: "none",
                  color: x.ok ? KIND[x.kind].c : "var(--red)", background: `color-mix(in srgb, ${x.ok ? KIND[x.kind].c : "var(--red)"} 15%, transparent)` }}>
                  {x.ok ? <I.cube size={19} /> : <I.xmark size={19} />}
                </div>
                <div className="grow" style={{ minWidth: 180 }}>
                  <div className="row" style={{ gap: 8 }}><b className="tnum" style={{ fontWeight: 650 }}>{clock(x.ts)}</b>
                    <span className="pill" style={{ ["--c" as string]: KIND[x.kind].c, height: 20, fontSize: 11 }}>{KIND[x.kind].t}</span>
                    {!x.ok && <span className="pill" style={{ ["--c" as string]: "var(--red)", height: 20, fontSize: 11 }}>실패</span>}</div>
                  <div className="faint" style={{ fontSize: 12.5 }}>{x.ok ? bytes(x.size) : ""} {x.note ? `· ${x.note}` : ""} {x.created_by ? `· ${x.created_by}` : ""} · {ago(x.ts)}</div>
                </div>
                {admin && x.ok && x.exists ? <Button className="sm" onClick={() => setSel(x.id)}>복원 · 관리</Button> : null}
              </motion.div>
            ))}
          </AnimatePresence>
          {info && !info.items.length && <div className="empty">아직 백업이 없습니다</div>}
        </div>
      </Glass>

      <Sheet open={!!b} onClose={() => setSel(null)} title={b ? `백업 ${clock(b.ts)}` : ""}>
        {b && (
          <div style={{ display: "grid", gap: 14 }}>
            <div className="muted">{bytes(b.size)} · {b.note}</div>
            <div className="glass thin" style={{ padding: 14, borderRadius: 16, fontSize: 13.5, lineHeight: 1.6 }}>
              <b>복원하면</b><br />① 서버를 끄고 ② <b>지금 상태를 먼저 백업</b>한 뒤 ③ 백업에 들어 있는 폴더 · 파일만 그 시점으로 되돌리고 ④ 켜져 있었다면 다시 켭니다.<br />
              <span className="faint">되돌린 뒤 문제가 있으면 '복원 전' 백업으로 다시 복원하면 됩니다.</span>
            </div>
            <HoldButton tint="var(--orange)" ms={1400} icon={<I.restart size={18} />} onConfirm={async () => {
              try { await api.restoreBackup(b.id); toast(true, "복원을 시작했습니다"); setSel(null); } catch (e) { toast(false, (e as Error).message); }
            }}>길게 눌러 이 시점으로 복원</HoldButton>
            <div className="row" style={{ marginTop: 18 }}>
              <a className="btn grow" href={`/api/backups/${b.id}/download`} download style={{ textDecoration: "none" }}>내려받기</a>
              <Button style={{ color: "var(--red)" }} onClick={async () => { if (confirm("이 백업을 지울까요?")) { await api.deleteBackup(b.id); setSel(null); load(); } }}><I.trash size={18} />삭제</Button>
            </div>
          </div>
        )}
      </Sheet>
    </div>
  );
}
