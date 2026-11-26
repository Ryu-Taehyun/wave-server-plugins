import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { api, type Me, type UpdateStatus } from "../lib/api";
import type { UpdateProgress } from "../lib/live";
import { ago, bytes, clock } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Button, HoldButton, Segmented, Toggle } from "../components/Controls";
import { I } from "../components/Icons";

const P = (s?: string | null) => (s ? s[0].toUpperCase() + s.slice(1) : "");

export function Update({ me, progress, toast }: { me: Me; progress: UpdateProgress | null; toast: (ok: boolean, t: string) => void }) {
  const [st, setSt] = useState<UpdateStatus | null>(null);
  const [checking, setChecking] = useState(false);
  const [ver, setVer] = useState("");
  const admin = me.role === "admin";

  const load = () => api.update().then((d) => { setSt(d); setVer(d.settings.version); }).catch(() => {});
  useEffect(() => { load(); }, []);
  useEffect(() => { if (progress?.done) setTimeout(load, 800); }, [progress?.done]);

  const check = async () => {
    setChecking(true);
    try { await api.checkUpdate(); await load(); } catch (e) { toast(false, (e as Error).message); } finally { setChecking(false); }
  };
  const save = async (s: Partial<UpdateStatus["settings"]>) => {
    try { await api.saveUpdateSettings(s); await load(); } catch (e) { toast(false, (e as Error).message); }
  };
  const run = async (p: Promise<unknown>, t: string) => {
    try { await p; toast(true, t); } catch (e) { toast(false, (e as Error).message); }
  };

  const cur = st?.current;
  const chk = st?.last_check;
  const latest = chk?.latest;
  const busy = !!st?.busy || (!!progress && !progress.done);

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>서버 업데이트</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>새 Purpur · Paper 빌드 알림 · 버튼 하나로 백업 → 내려받기 → 교체 → 재시작 · 되돌리기</motion.p>

      <div className="grid cols-2" style={{ marginBottom: 16 }}>
        <Glass className="card" {...fadeUp}>
          <div className="muted" style={{ fontSize: 13.5, fontWeight: 600 }}>지금 버전</div>
          <div style={{ fontSize: 26, fontWeight: 700, margin: "6px 0 2px" }}>
            {cur?.project ? `${P(cur.project)} ${cur.mc ?? ""}` : "알 수 없음"} {cur?.build ? <span className="tnum" style={{ color: "var(--text-2)" }}>#{cur.build}</span> : null}
          </div>
          <div className="faint" style={{ fontSize: 12.5 }}>
            {cur?.jar_error ? <span style={{ color: "var(--red)" }}>{cur.jar_error}</span>
              : <>{cur?.jar} · {bytes(cur?.jar_size)} · {cur?.jar_mtime ? `${ago(cur.jar_mtime)} 바뀜` : "파일 없음"}</>}
          </div>
          {!cur?.raw && <div className="faint" style={{ fontSize: 12, marginTop: 6 }}>서버를 한 번 켜면 version_history.json 에서 빌드 번호를 읽습니다</div>}
        </Glass>

        <Glass className="card" {...fadeUp}>
          <div className="card-h" style={{ marginBottom: 6 }}>
            <div className="muted" style={{ fontSize: 13.5, fontWeight: 600 }}>최신 빌드</div>
            {chk && (chk.available ? <span className="pill" style={{ ["--c" as string]: "var(--blue)" }}>새 빌드 있음</span>
              : chk.available === false ? <span className="pill" style={{ ["--c" as string]: "var(--green)" }}>최신</span> : null)}
          </div>
          <div style={{ fontSize: 26, fontWeight: 700, marginBottom: 2 }}>
            {latest ? <>{P(latest.project)} {latest.version} <span className="tnum" style={{ color: chk?.available ? "var(--blue)" : "var(--text-2)" }}>#{latest.build}</span></> : "—"}
          </div>
          <div className="row" style={{ gap: 8 }}>
            <span className="faint grow" style={{ fontSize: 12.5 }}>{chk ? `${ago(chk.ts)} 확인${latest?.time ? ` · ${clock(latest.time)} 빌드` : ""}` : "아직 확인하지 않음"}</span>
            <Button className="sm" disabled={checking || busy} onClick={check}><I.restart size={15} />{checking ? "확인 중…" : "지금 확인"}</Button>
          </div>
        </Glass>
      </div>

      {(progress || st?.busy) && (
        <Glass className="card" {...fadeUp} style={{ marginBottom: 16 }}>
          <div className="row" style={{ marginBottom: 10 }}>
            <b className="grow" style={{ color: progress?.done ? (progress.ok ? "var(--green)" : "var(--red)") : undefined }}>{progress?.phase ?? `${st?.busy} 중…`}</b>
            {progress?.pct != null && <span className="tnum muted">{Math.round(progress.pct)}%</span>}
          </div>
          <div style={{ height: 8, borderRadius: 9, background: "color-mix(in srgb, var(--text-3) 25%, transparent)", overflow: "hidden" }}>
            <motion.div animate={{ width: `${progress?.pct ?? (progress?.done ? 100 : 8)}%` }} transition={{ type: "spring", stiffness: 120, damping: 24 }}
              style={{ height: "100%", borderRadius: 9, background: progress?.done && !progress.ok ? "var(--red)" : "var(--blue)" }} />
          </div>
        </Glass>
      )}

      {latest && chk?.available && (
        <Glass className="card" {...fadeUp} style={{ marginBottom: 16 }}>
          <div className="card-t" style={{ marginBottom: 8 }}>#{latest.build} 바뀐 점</div>
          <ul style={{ margin: "0 0 14px", paddingLeft: 18, fontSize: 14, lineHeight: 1.6 }}>
            {latest.changes.length ? latest.changes.map((c, i) => <li key={i}>{c}</li>) : <li className="faint">내용 없음</li>}
          </ul>
          <div className="faint" style={{ fontSize: 12.5, marginBottom: 12 }}>
            순서: {st?.settings.backup_first ? "월드 백업 → " : ""}내려받기 · {latest.algo.toUpperCase()} 확인 → 서버 끄기 → jar 교체 (옛 jar 보관) → 다시 켜기. 접속 중인 플레이어는 잠시 끊깁니다.
          </div>
          <HoldButton tint="var(--blue)" disabled={!admin || busy} icon={<I.download size={19} />} ms={1100}
            onConfirm={() => run(api.installUpdate(), "업데이트를 시작했습니다")}>
            {admin ? `길게 눌러 #${latest.build} 로 업데이트` : "관리자만 업데이트할 수 있습니다"}
          </HoldButton>
        </Glass>
      )}

      <div className="grid cols-2">
        <Glass className="card" {...fadeUp}>
          <div className="card-t" style={{ marginBottom: 4 }}>이전 버전으로 되돌리기</div>
          <div className="faint" style={{ fontSize: 12.5, marginBottom: 8 }}>업데이트할 때 바꾸기 전 jar 를 data/jar-backup 에 5개까지 보관합니다. 월드는 그대로입니다 (월드까지 되돌리려면 백업 화면).</div>
          <div className="list">
            {st?.backups.map((b, i) => (
              <div key={b.file} className="list-item">
                <div style={{ width: 34, height: 34, borderRadius: 10, display: "grid", placeItems: "center", flex: "none", background: "color-mix(in srgb, var(--text-3) 25%, transparent)" }}><I.cube size={18} /></div>
                <div className="grow" style={{ minWidth: 0 }}>
                  <b>{b.project ? `${P(b.project)} ${b.mc ?? ""} #${b.build ?? "?"}` : b.file}</b>
                  <div className="faint" style={{ fontSize: 12.5 }}>{ago(b.ts)} 보관 · {bytes(b.size)}{b.by ? ` · ${b.by}` : ""}</div>
                </div>
                <Button className="sm" disabled={!admin || busy} onClick={() => confirm(`${b.file} 로 되돌릴까요? 서버가 재시작됩니다.`) && run(api.rollback(b.file), "되돌리기를 시작했습니다")}>
                  <I.undo size={15} />{i === 0 ? "직전으로" : "되돌리기"}
                </Button>
              </div>
            ))}
            {!st?.backups.length && <div className="empty">보관된 이전 jar 가 없습니다</div>}
          </div>
        </Glass>

        <Glass className="card" {...fadeUp}>
          <div className="card-t" style={{ marginBottom: 12 }}>설정</div>
          <div style={{ display: "grid", gap: 14 }}>
            <div className="row">
              <span className="grow">서버 종류</span>
              <Segmented size="sm" value={st?.settings.project ?? "purpur"} onChange={(v) => admin && save({ project: v })}
                options={[{ value: "purpur", label: "Purpur" }, { value: "paper", label: "Paper" }]} />
            </div>
            <div className="row">
              <span className="grow">마인크래프트 버전</span>
              <input className="field tnum" style={{ width: 110, height: 36 }} value={ver} onChange={(e) => setVer(e.target.value.trim())} disabled={!admin} />
              <Button className="sm" disabled={!admin || ver === st?.settings.version} onClick={() => save({ version: ver })}>저장</Button>
            </div>
            <div className="row">
              <div className="grow">새 빌드 자동 확인<div className="faint" style={{ fontSize: 12.5 }}>6시간마다 · 새 빌드가 나오면 알림 (설치는 직접)</div></div>
              <Toggle on={!!st?.settings.auto_check} disabled={!admin} onChange={(v) => save({ auto_check: v })} />
            </div>
            <div className="row">
              <div className="grow">업데이트 전에 월드 백업<div className="faint" style={{ fontSize: 12.5 }}>백업에 실패하면 업데이트를 멈춥니다</div></div>
              <Toggle on={!!st?.settings.backup_first} disabled={!admin} onChange={(v) => save({ backup_first: v })} />
            </div>
          </div>
          {!!st?.history.length && (
            <div style={{ marginTop: 16 }}>
              <b style={{ fontSize: 14 }}>기록</b>
              {st.history.slice(0, 6).map((h) => (
                <div key={h.id} className="row faint" style={{ fontSize: 12.5, gap: 8, padding: "3px 0" }}>
                  <span className="grow">{h.action === "update.install" ? "업데이트" : h.action === "update.rollback" ? "되돌리기" : "설정"} · {h.username}{h.detail ? ` · ${h.detail}` : ""}</span>
                  <span>{ago(h.ts)}</span>
                </div>
              ))}
            </div>
          )}
        </Glass>
      </div>
    </div>
  );
}
