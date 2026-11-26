import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { api, canOperate, type Me, type Task, type TaskInput, type TaskKind, type TaskSchedule } from "../lib/api";
import { ago, clock, until } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Button, Segmented, Sheet, Toggle } from "../components/Controls";
import { I } from "../components/Icons";

const KINDS: { value: TaskKind; label: string; icon: React.ReactNode; c: string }[] = [
  { value: "restart", label: "재시작", icon: <I.restart size={18} />, c: "var(--orange)" },
  { value: "broadcast", label: "공지", icon: <I.megaphone size={18} />, c: "var(--blue)" },
  { value: "command", label: "명령어", icon: <I.terminal size={18} />, c: "var(--purple)" },
  { value: "stop", label: "끄기", icon: <I.stop size={16} />, c: "var(--red)" },
  { value: "start", label: "켜기", icon: <I.play size={16} />, c: "var(--green)" },
];
const DAYS = ["월", "화", "수", "목", "금", "토", "일"];

function describe(t: Task): string {
  if (t.schedule === "interval") return `${t.every_min}분마다`;
  if (t.schedule === "once") return t.run_at ? `${clock(t.run_at)} 한 번` : "한 번";
  const d = t.days === 127 ? "매일" : t.days === 31 ? "평일" : t.days === 96 ? "주말" : DAYS.filter((_, i) => t.days & (1 << i)).join("·");
  return `${d} ${t.at_time}`;
}

const blank = (): TaskInput => ({ name: "", kind: "restart", payload: "", schedule: "daily", at_time: "05:00", days: 127, every_min: 60, run_at: null, warn_minutes: 5, enabled: true });

export function Tasks({ me, toast }: { me: Me; toast: (ok: boolean, t: string) => void }) {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [edit, setEdit] = useState<{ id?: number; t: TaskInput } | null>(null);
  const [busy, setBusy] = useState(false);
  const [, tick] = useState(0);
  const ops = canOperate(me);

  const load = () => api.tasks().then(setTasks).catch(() => {});
  useEffect(() => {
    load();
    const a = setInterval(load, 20000), b = setInterval(() => tick((x) => x + 1), 1000);
    return () => { clearInterval(a); clearInterval(b); };
  }, []);

  const save = async () => {
    if (!edit) return;
    setBusy(true);
    try {
      if (edit.id) await api.editTask(edit.id, edit.t);
      else await api.addTask(edit.t);
      toast(true, "저장했습니다");
      setEdit(null);
      load();
    } catch (e) {
      toast(false, (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const toggle = async (t: Task, on: boolean) => {
    const { id, next_run, last_run, running, last, ...rest } = t;
    void next_run; void last_run; void running; void last;
    try {
      await api.editTask(id, { ...rest, enabled: on });
      load();
    } catch (e) {
      toast(false, (e as Error).message);
    }
  };

  const set = (p: Partial<TaskInput>) => setEdit((e) => (e ? { ...e, t: { ...e.t, ...p } } : e));
  const t = edit?.t;

  return (
    <div>
      <div className="row wrap" style={{ justifyContent: "space-between" }}>
        <div>
          <motion.h1 className="page-title" {...fadeUp}>예약 작업</motion.h1>
          <motion.p className="page-sub" {...fadeUp}>정해진 시간에 재시작 · 공지 · 명령어 실행</motion.p>
        </div>
        {ops && <Button tint="var(--blue)" onClick={() => setEdit({ t: blank() })}><I.plus size={18} />새 작업</Button>}
      </div>

      <div className="grid cols-2">
        <AnimatePresence initial={false}>
          {tasks.map((x) => {
            const k = KINDS.find((k) => k.value === x.kind)!;
            return (
              <Glass key={x.id} layout className="card" initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: x.enabled ? 1 : 0.6, scale: 1 }} exit={{ opacity: 0, scale: 0.9 }}>
                <div className="row" style={{ alignItems: "flex-start" }}>
                  <div style={{ width: 42, height: 42, borderRadius: 14, display: "grid", placeItems: "center", color: k.c, background: `color-mix(in srgb, ${k.c} 18%, transparent)`, flex: "none" }}>{k.icon}</div>
                  <div className="grow">
                    <div style={{ fontWeight: 650, fontSize: 16 }}>{x.name}</div>
                    <div className="muted" style={{ fontSize: 13.5 }}>{k.label} · {describe(x)}{x.warn_minutes && (x.kind === "restart" || x.kind === "stop") ? ` · ${x.warn_minutes}분 전부터 알림` : ""}</div>
                  </div>
                  {ops && <Toggle on={!!x.enabled} onChange={(v) => toggle(x, v)} />}
                </div>
                {x.payload && <div className="mono faint" style={{ fontSize: 12.5, marginTop: 10, whiteSpace: "pre-wrap", overflow: "hidden", maxHeight: 60 }}>{x.payload}</div>}
                <div className="sep" />
                <div className="row" style={{ fontSize: 13 }}>
                  <div className="grow">
                    {x.running ? <span className="pill" style={{ ["--c" as string]: "var(--orange)" }}>진행 중 (카운트다운)</span>
                      : x.enabled && x.next_run ? <span>다음 실행 <b className="tnum">{until(x.next_run)}</b> <span className="faint">({clock(x.next_run)})</span></span>
                      : <span className="faint">꺼져 있음</span>}
                    {x.last && <div className="faint">마지막 {ago(x.last.ts)} · {x.last.ok ? "성공" : "실패"}</div>}
                  </div>
                  {ops && (
                    <div className="row" style={{ gap: 6 }}>
                      <Button className="sm" onClick={async () => { const r = await api.runTask(x.id).catch((e) => ({ ok: false, output: e.message })); toast(r.ok, r.ok ? `실행했습니다: ${x.name}` : r.output); load(); }}>지금 실행</Button>
                      <Button className="sm" onClick={() => { const { id, next_run, last_run, running, last, enabled, ...rest } = x; void next_run; void last_run; void running; void last; setEdit({ id, t: { ...rest, enabled: !!enabled } }); }}>편집</Button>
                    </div>
                  )}
                </div>
              </Glass>
            );
          })}
        </AnimatePresence>
      </div>
      {!tasks.length && <Glass className="card"><div className="empty">예약 작업이 없습니다. 매일 새벽 재시작 같은 작업을 만들어 보세요.</div></Glass>}

      <Sheet open={!!edit} onClose={() => setEdit(null)} title={edit?.id ? "작업 편집" : "새 작업"}
        footer={t && (
          <div className="row">
            {edit?.id && <Button style={{ color: "var(--red)" }} onClick={async () => { if (!confirm("이 작업을 지울까요?")) return; await api.deleteTask(edit.id!); setEdit(null); load(); }}><I.trash size={18} /></Button>}
            <Button tint="var(--blue)" className="grow" style={{ height: 50 }} disabled={busy || !t.name.trim()} onClick={save}>저장</Button>
          </div>
        )}>
        {t && (
          <div style={{ display: "grid", gap: 16 }}>
            <div>
              <label className="lbl">이름</label>
              <input className="field" value={t.name} onChange={(e) => set({ name: e.target.value })} placeholder="예: 새벽 재시작" maxLength={60} />
            </div>
            <div>
              <label className="lbl">할 일</label>
              <div className="row wrap" style={{ gap: 8 }}>
                {KINDS.map((k) => (
                  <motion.button key={k.value} whileTap={{ scale: 0.94 }} onClick={() => set({ kind: k.value })} className="btn sm"
                    style={{ color: t.kind === k.value ? "#fff" : k.c, background: t.kind === k.value ? k.c : undefined }}>{k.icon}{k.label}</motion.button>
                ))}
              </div>
            </div>
            {(t.kind === "broadcast" || t.kind === "command") && (
              <div>
                <label className="lbl">{t.kind === "broadcast" ? "공지 내용" : "명령어 (한 줄에 하나)"}</label>
                <textarea className={`field ${t.kind === "command" ? "mono" : ""}`} value={t.payload} onChange={(e) => set({ payload: e.target.value })}
                  placeholder={t.kind === "broadcast" ? "핫타임이 시작되었습니다!" : "save-all\nweather clear"} />
              </div>
            )}
            <div>
              <label className="lbl">언제</label>
              <Segmented value={t.schedule} onChange={(v: TaskSchedule) => set({ schedule: v })} options={[{ value: "daily", label: "매일" }, { value: "interval", label: "반복" }, { value: "once", label: "한 번" }]} />
            </div>
            {t.schedule === "daily" && (
              <>
                <input className="field tnum" type="time" value={t.at_time ?? "05:00"} onChange={(e) => set({ at_time: e.target.value })} />
                <div className="row" style={{ gap: 6 }}>
                  {DAYS.map((d, i) => {
                    const on = !!(t.days & (1 << i));
                    return <motion.button key={d} whileTap={{ scale: 0.9 }} onClick={() => set({ days: (t.days ^ (1 << i)) || t.days })} className="btn"
                      style={{ flex: 1, height: 40, padding: 0, background: on ? "var(--blue)" : undefined, color: on ? "#fff" : "var(--text-2)" }}>{d}</motion.button>;
                  })}
                </div>
              </>
            )}
            {t.schedule === "interval" && (
              <div className="row">
                <input className="field tnum" type="number" min={1} max={10080} value={t.every_min ?? 60} onChange={(e) => set({ every_min: Number(e.target.value) })} />
                <span className="muted" style={{ whiteSpace: "nowrap" }}>분마다</span>
              </div>
            )}
            {t.schedule === "once" && (
              <input className="field" type="datetime-local"
                value={t.run_at ? new Date(t.run_at * 1000 - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16) : ""}
                onChange={(e) => set({ run_at: Math.floor(new Date(e.target.value).getTime() / 1000) })} />
            )}
            {(t.kind === "restart" || t.kind === "stop") && (
              <div>
                <label className="lbl">미리 알림 (게임 안 카운트다운)</label>
                <Segmented size="sm" value={String(t.warn_minutes)} onChange={(v) => set({ warn_minutes: Number(v) })}
                  options={[0, 1, 5, 10, 15].map((m) => ({ value: String(m), label: m ? `${m}분 전` : "없음" }))} />
              </div>
            )}
            <div className="row">
              <span className="grow">사용</span>
              <Toggle on={t.enabled} onChange={(v) => set({ enabled: v })} />
            </div>
          </div>
        )}
      </Sheet>
    </div>
  );
}
