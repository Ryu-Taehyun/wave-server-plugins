import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { api, type FileDoc, type FileEntry, type Me, type PluginJar, type Staged } from "../lib/api";
import { ago, bytes, clock, lineDiff } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Button, HoldButton, Segmented, Sheet } from "../components/Controls";
import { I } from "../components/Icons";

function Diff({ a, b }: { a: string; b: string }) {
  const rows = useMemo(() => lineDiff(a, b), [a, b]);
  const changed = rows.filter((r) => r[0] !== " ").length;
  // 바뀐 줄 앞뒤 2줄만
  const keep = new Set<number>();
  rows.forEach((r, i) => { if (r[0] !== " ") for (let k = i - 2; k <= i + 2; k++) keep.add(k); });
  return (
    <div className="mono" style={{ fontSize: 12.5, lineHeight: 1.6, borderRadius: 14, overflow: "auto", maxHeight: "52vh", background: "rgba(0,0,0,.25)", padding: "8px 0" }}>
      {!changed && <div className="empty">바뀐 곳이 없습니다</div>}
      {rows.map((r, i) => keep.has(i) ? (
        <div key={i} style={{ padding: "0 12px", whiteSpace: "pre-wrap", background: r[0] === "+" ? "color-mix(in srgb, var(--green) 18%, transparent)" : r[0] === "-" ? "color-mix(in srgb, var(--red) 18%, transparent)" : undefined,
          color: r[0] === " " ? "var(--text-3)" : "var(--text)" }}>{r[0]} {r[1] || " "}</div>
      ) : keep.has(i - 1) ? <div key={i} className="faint" style={{ padding: "0 12px" }}>⋯</div> : null)}
    </div>
  );
}

function Editor({ path, toast, onBack }: { path: string; toast: (ok: boolean, t: string) => void; onBack: () => void }) {
  const [doc, setDoc] = useState<FileDoc | null>(null);
  const [text, setText] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [diff, setDiff] = useState(false);
  const [hist, setHist] = useState<{ id: number; ts: number; saved_by: string }[]>([]);
  const [old, setOld] = useState<{ ts: number; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const load = () => {
    api.readFile(path).then((d) => { setDoc(d); setText(d.text); setProblem(d.problem); }).catch((e) => toast(false, e.message));
    api.fileHistory(path).then(setHist).catch(() => {});
  };
  useEffect(load, [path]);

  const onChange = (v: string) => {
    setText(v);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => api.validateFile(path, v).then((r) => setProblem(r.problem)).catch(() => {}), 500);
  };
  const dirty = doc ? text !== doc.text : false;
  const save = async (force = false) => {
    if (!doc) return;
    setBusy(true);
    try {
      const r = await api.saveFile(path, text, doc.mtime, force);
      setDoc({ ...doc, text, mtime: r.mtime });
      setDiff(false);
      toast(true, "저장했습니다 · 플러그인은 다시 불러오거나 재시작해야 적용됩니다");
      api.fileHistory(path).then(setHist).catch(() => {});
    } catch (e) {
      toast(false, (e as Error).message);
    } finally {
      setBusy(false);
    }
  };
  const lines = text.split("\n").length;

  return (
    <Glass className="card" {...fadeUp} style={{ padding: 0, overflow: "hidden" }}>
      <div className="row wrap" style={{ padding: "12px 14px", gap: 10, borderBottom: "1px solid color-mix(in srgb, var(--stroke) 60%, transparent)" }}>
        <button className="btn icon ghost" onClick={onBack} aria-label="목록"><I.list size={19} /></button>
        <div className="grow mono" style={{ fontSize: 13.5, fontWeight: 600, wordBreak: "break-all" }}>{path}{dirty && <span style={{ color: "var(--orange)" }}> ●</span>}</div>
        {hist.length > 0 && <span className="faint" style={{ fontSize: 12.5 }}>저장 기록 {hist.length}</span>}
        <Button className="sm" disabled={!dirty} onClick={() => setDiff(true)}>바뀐 곳</Button>
        <Button className="sm" tint="var(--blue)" disabled={!dirty || busy || !!problem} onClick={() => save()}>저장</Button>
      </div>
      <AnimatePresence>
        {problem && (
          <motion.div initial={{ height: 0 }} animate={{ height: "auto" }} exit={{ height: 0 }} style={{ overflow: "hidden" }}>
            <div className="row" style={{ padding: "10px 14px", background: "color-mix(in srgb, var(--red) 14%, transparent)", color: "var(--red)", fontSize: 13.5 }}>
              <I.xmark size={16} /><span className="grow">{problem}</span>
              {dirty && <button className="btn sm ghost" style={{ color: "var(--text-2)" }} onClick={() => confirm("형식 오류가 있는 채로 저장하면 플러그인이 설정을 못 읽을 수 있습니다. 저장할까요?") && save(true)}>그래도 저장</button>}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
      <div style={{ display: "flex", background: "rgba(0,0,0,.18)" }}>
        <div className="mono faint" aria-hidden style={{ padding: "12px 8px 12px 12px", textAlign: "right", fontSize: 12.5, lineHeight: "20px", userSelect: "none", whiteSpace: "pre", minWidth: 44 }}>
          {Array.from({ length: Math.min(lines, 5000) }, (_, i) => i + 1).join("\n")}
        </div>
        <textarea className="mono" spellCheck={false} value={text} onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Tab") { e.preventDefault(); const t = e.currentTarget, s = t.selectionStart; onChange(text.slice(0, s) + "  " + text.slice(t.selectionEnd)); requestAnimationFrame(() => t.setSelectionRange(s + 2, s + 2)); }
            if ((e.ctrlKey || e.metaKey) && e.key === "s") { e.preventDefault(); if (dirty && !problem) save(); }
          }}
          style={{ flex: 1, minHeight: "58vh", border: 0, outline: "none", resize: "vertical", background: "transparent", padding: "12px 12px 12px 4px", fontSize: 12.5, lineHeight: "20px", whiteSpace: "pre", overflowWrap: "normal", overflowX: "auto", color: "var(--text)" }} />
      </div>
      {hist.length > 0 && (
        <div style={{ padding: "10px 14px" }}>
          <div className="faint" style={{ fontSize: 12.5, marginBottom: 6 }}>이전 저장본 (저장할 때마다 자동 보관)</div>
          <div className="row wrap" style={{ gap: 6 }}>
            {hist.slice(0, 8).map((h) => <Button key={h.id} className="sm" onClick={async () => { const r = await api.fileHistoryText(h.id); setOld({ ts: r.ts, text: r.text }); }}>{clock(h.ts)} · {h.saved_by}</Button>)}
          </div>
        </div>
      )}
      <Sheet open={diff} onClose={() => setDiff(false)} title="바뀐 곳" footer={<Button tint="var(--blue)" style={{ width: "100%", height: 50 }} disabled={busy || !!problem} onClick={() => save()}>저장</Button>}>
        {doc && <Diff a={doc.text} b={text} />}
      </Sheet>
      <Sheet open={!!old} onClose={() => setOld(null)} title={old ? `${clock(old.ts)} 저장본과 비교` : ""}
        footer={<Button style={{ width: "100%", height: 50 }} onClick={() => { if (old) onChange(old.text); setOld(null); toast(true, "편집기에 불러왔습니다 · 저장해야 적용됩니다"); }}>이 내용으로 되돌리기 (편집기에 불러오기)</Button>}>
        {old && <><div className="faint" style={{ fontSize: 12.5, marginBottom: 8 }}>- 그때 · + 지금 편집 중</div><Diff a={old.text} b={text} /></>}
      </Sheet>
    </Glass>
  );
}

function Plugins({ toast }: { toast: (ok: boolean, t: string) => void }) {
  const [d, setD] = useState<{ active: PluginJar[]; disabled: PluginJar[]; staged: Staged[] } | null>(null);
  const [busy, setBusy] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const load = () => api.plugins().then(setD).catch(() => {});
  useEffect(() => { load(); }, []);
  const stagedOf = (f: string) => d?.staged.find((s) => s.file === f || s.replaces?.includes(f));
  const OP = { install: "설치", disable: "끄기", enable: "켜기", remove: "삭제" } as const;

  const upload = async (files: FileList | null) => {
    if (!files?.length) return;
    setBusy(true);
    for (const f of Array.from(files)) {
      try { const s = await api.uploadPlugin(f); toast(true, `${s.name} ${s.version} 올림${s.replaces?.length ? ` (${s.replaces.join(", ")} 교체)` : ""}`); }
      catch (e) { toast(false, `${f.name}: ${(e as Error).message}`); }
    }
    setBusy(false);
    if (input.current) input.current.value = "";
    load();
  };

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <Glass className="card" {...fadeUp}
        onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); upload(e.dataTransfer.files); }}>
        <div className="card-h">
          <div><div className="card-t">플러그인 올리기</div><div className="faint" style={{ fontSize: 12.5 }}>jar 를 끌어다 놓거나 고르세요 · 같은 플러그인의 예전 버전은 자동으로 교체</div></div>
          <Button tint="var(--blue)" disabled={busy} onClick={() => input.current?.click()}><I.plus size={18} />jar 고르기</Button>
          <input ref={input} type="file" accept=".jar" multiple hidden onChange={(e) => upload(e.target.files)} />
        </div>
        <AnimatePresence>
          {!!d?.staged.length && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: "auto" }} exit={{ opacity: 0, height: 0 }}>
              <div className="sep" />
              <div style={{ fontWeight: 650, marginBottom: 6 }}>적용 대기 {d.staged.length}건</div>
              {d.staged.map((s) => (
                <div key={s.file} className="list-item" style={{ padding: "6px 2px" }}>
                  <span className="pill" style={{ ["--c" as string]: s.op === "remove" ? "var(--red)" : s.op === "install" ? "var(--green)" : "var(--orange)" }}>{OP[s.op]}</span>
                  <span className="grow mono" style={{ fontSize: 13 }}>{s.file}{s.replaces?.length ? <span className="faint"> ← {s.replaces.join(", ")}</span> : null}</span>
                  <button className="btn icon ghost" aria-label="취소" onClick={async () => { await api.unstagePlugin(s.file); load(); }}><I.xmark size={16} /></button>
                </div>
              ))}
              <div style={{ marginTop: 10 }}>
                <HoldButton tint="var(--blue)" icon={<I.restart size={18} />} onConfirm={async () => { try { await api.applyPlugins(); toast(true, "적용 중: 서버를 끄고 바꾼 뒤 다시 켭니다"); setTimeout(load, 4000); } catch (e) { toast(false, (e as Error).message); } }}>
                  길게 눌러 적용 (서버 재시작)
                </HoldButton>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </Glass>
      <Glass className="card" {...fadeUp}>
        <div className="card-h"><div className="card-t">설치된 플러그인</div><span className="pill">{d?.active.length ?? 0}개</span></div>
        <div className="list">
          {d?.active.map((p) => {
            const st = stagedOf(p.file);
            return (
              <div key={p.file} className="list-item wrap">
                <div className="grow" style={{ minWidth: 160 }}>
                  <b style={{ fontWeight: 650 }}>{p.name}</b> <span className="muted tnum">{p.version}</span>
                  {p.broken && <span className="pill" style={{ ["--c" as string]: "var(--red)", marginLeft: 6 }}>깨진 jar</span>}
                  <div className="faint mono" style={{ fontSize: 12 }}>{p.file} · {bytes(p.size)} · {ago(p.mtime)}</div>
                </div>
                {st ? <span className="pill" style={{ ["--c" as string]: "var(--orange)" }}>{OP[st.op]} 대기</span> : (
                  <div className="row" style={{ gap: 6 }}>
                    <Button className="sm" onClick={async () => { await api.stagePlugin("disable", p.file); load(); }}>끄기</Button>
                    <Button className="sm" style={{ color: "var(--red)" }} onClick={async () => { await api.stagePlugin("remove", p.file); load(); }}>삭제</Button>
                  </div>
                )}
              </div>
            );
          })}
          {d?.disabled.map((p) => (
            <div key={p.file} className="list-item" style={{ opacity: 0.6 }}>
              <div className="grow"><b>{p.name}</b> <span className="muted">{p.version} · 꺼져 있음</span></div>
              {stagedOf(p.file) ? <span className="pill" style={{ ["--c" as string]: "var(--orange)" }}>대기</span>
                : <Button className="sm" onClick={async () => { await api.stagePlugin("enable", p.file); load(); }}>켜기</Button>}
            </div>
          ))}
        </div>
        <p className="faint" style={{ fontSize: 12.5, margin: "10px 0 0" }}>서버가 켜진 채로 jar 를 바꾸면 'zip file closed' 오류가 납니다. 여기서 적용하면 서버를 끄고 바꾼 뒤 다시 켜며, 바뀐 jar 는 data/plugin-backup 에 보관됩니다.</p>
      </Glass>
    </div>
  );
}

export function Files({ me, toast }: { me: Me; toast: (ok: boolean, t: string) => void }) {
  const [tab, setTab] = useState<"config" | "plugins">("config");
  const [list, setList] = useState<FileEntry[]>([]);
  const [q, setQ] = useState("");
  const [open, setOpen] = useState<string | null>(null);
  useEffect(() => { if (me.role === "admin") api.files().then(setList).catch(() => {}); }, [me.role]);
  const shown = useMemo(() => {
    const f = q.toLowerCase();
    const prio = (p: string) => (p.includes("/") ? (p.startsWith("plugins/WaveCore") ? 1 : p.startsWith("config/") ? 2 : 3) : 0);
    return list.filter((x) => x.path.toLowerCase().includes(f)).sort((a, b) => prio(a.path) - prio(b.path) || a.path.localeCompare(b.path)).slice(0, 300);
  }, [list, q]);

  if (me.role !== "admin") return <Glass className="card"><div className="empty">파일 관리는 관리자만 쓸 수 있습니다</div></Glass>;
  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>파일</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>설정 파일 편집 (저장 전 형식 검사 · 자동 보관) · 플러그인 교체</motion.p>
      <div style={{ marginBottom: 16 }}><Segmented value={tab} onChange={(v) => { setTab(v); setOpen(null); }} options={[{ value: "config", label: "설정 파일" }, { value: "plugins", label: "플러그인" }]} /></div>
      {tab === "plugins" ? <Plugins toast={toast} /> : open ? <Editor path={open} toast={toast} onBack={() => setOpen(null)} /> : (
        <Glass className="card" {...fadeUp}>
          <input className="field" style={{ height: 42, marginBottom: 8 }} placeholder="파일 찾기 (예: WaveCore, server.properties)" value={q} onChange={(e) => setQ(e.target.value)} />
          <div className="list">
            {shown.map((f) => (
              <button key={f.path} className="list-item" onClick={() => setOpen(f.path)} style={{ border: 0, background: "transparent", textAlign: "left", width: "100%", borderTop: "1px solid color-mix(in srgb, var(--stroke) 55%, transparent)" }}>
                <I.terminal size={17} style={{ color: "var(--text-3)", flex: "none" }} />
                <span className="grow mono" style={{ fontSize: 13.5, wordBreak: "break-all" }}>{f.path}</span>
                <span className="faint" style={{ fontSize: 12, whiteSpace: "nowrap" }}>{bytes(f.size)} · {ago(f.mtime)}</span>
              </button>
            ))}
            {!shown.length && <div className="empty">파일이 없습니다</div>}
          </div>
        </Glass>
      )}
    </div>
  );
}
