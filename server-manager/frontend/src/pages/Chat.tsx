import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { api, canOperate, type ChatChannel, type ChatRow, type ChatSettings, type Me } from "../lib/api";
import { Glass, fadeUp } from "../components/Glass";
import { Avatar, Button, Segmented, Sheet, Toggle } from "../components/Controls";
import { I } from "../components/Icons";
import { SanctionForm } from "./Sanctions";

const CH: Record<ChatChannel, { label: string; c: string }> = {
  global: { label: "전체", c: "var(--text-3)" },
  local: { label: "지역", c: "var(--green)" },
  team: { label: "팀", c: "var(--red)" },
  whisper: { label: "귓말", c: "var(--purple)" },
  admin: { label: "관리자", c: "var(--blue)" },
};
const time = (ts: number) => new Date(ts * 1000).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false });
const day = (ts: number) => new Date(ts * 1000).toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "short" });

function Line({ r, onName }: { r: ChatRow; onName: (n: string) => void }) {
  const ch = CH[r.channel] ?? CH.global;
  return (
    <motion.div layout="position" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.18 }}
      className="row" style={{ alignItems: "flex-start", gap: 10, padding: "7px 8px", borderRadius: 12,
        background: r.flagged ? "color-mix(in srgb, var(--red) 13%, transparent)" : r.channel === "admin" ? "color-mix(in srgb, var(--blue) 10%, transparent)" : undefined }}>
      <span className="faint tnum" style={{ fontSize: 11.5, width: 38, flex: "none", paddingTop: 3 }}>{time(r.ts)}</span>
      {r.channel !== "admin" ? <button onClick={() => onName(r.name)} style={{ border: 0, padding: 0, background: "none", flex: "none" }} aria-label={r.name}><Avatar name={r.name} size={24} /></button>
        : <div style={{ width: 24, height: 24, borderRadius: 8, display: "grid", placeItems: "center", background: "var(--blue)", color: "#fff", flex: "none" }}><I.shield size={14} /></div>}
      <div className="grow" style={{ minWidth: 0, lineHeight: 1.45 }}>
        {r.channel !== "global" && <span className="pill" style={{ ["--c" as string]: ch.c, height: 19, padding: "0 7px", fontSize: 11, marginRight: 6, verticalAlign: 1 }}>{ch.label}</span>}
        <button onClick={() => r.channel !== "admin" && onName(r.name)} style={{ border: 0, padding: 0, background: "none", font: "inherit", fontWeight: 650, color: "var(--text)", cursor: "pointer" }}>{r.name}</button>
        {r.to_name && <span className="muted"> → {r.to_name}</span>}
        <span className="muted"> : </span>
        <span style={{ overflowWrap: "anywhere" }}>{r.message}</span>
        {r.flagged && <div style={{ fontSize: 11.5, color: "var(--red)", fontWeight: 600 }}>⚠ {r.flagged}</div>}
      </div>
    </motion.div>
  );
}

export function Chat({ me, live, online, toast }: { me: Me; live: ChatRow[]; online: boolean; toast: (ok: boolean, t: string) => void }) {
  const [base, setBase] = useState<ChatRow[]>([]);
  const [filter, setFilter] = useState<"all" | "flagged">("all");
  const [q, setQ] = useState("");
  const [name, setName] = useState("");
  const [found, setFound] = useState<ChatRow[] | null>(null);
  const [msg, setMsg] = useState("");
  const [to, setTo] = useState("");
  const [sel, setSel] = useState<string | null>(null);
  const [cfgOpen, setCfgOpen] = useState(false);
  const [follow, setFollow] = useState(true);
  const box = useRef<HTMLDivElement>(null);
  const ops = canOperate(me);

  useEffect(() => { api.chat({ limit: 300 }).then((r) => setBase(r.reverse())).catch(() => {}); }, []);

  // 실시간 줄 합치기 (처음 불러온 것과 겹치는 id 는 빼기)
  const rows = useMemo(() => {
    const last = base.length ? base[base.length - 1].id : 0;
    const all = base.concat(live.filter((r) => r.id > last));
    return filter === "flagged" ? all.filter((r) => r.flagged) : all;
  }, [base, live, filter]);
  const shown = found ?? rows;

  useEffect(() => {
    const el = box.current;
    if (el && follow && !found) el.scrollTop = el.scrollHeight;
  }, [shown.length, follow, found]);

  const search = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!q.trim() && !name.trim()) { setFound(null); return; }
    try { setFound((await api.chat({ q: q.trim(), name: name.trim(), flagged: filter === "flagged", limit: 300 })).reverse()); }
    catch (er) { toast(false, (er as Error).message); }
  };
  const more = async () => {
    const first = shown[0];
    if (!first) return;
    const older = (await api.chat({ before: first.id, q: q.trim(), name: name.trim(), flagged: filter === "flagged", limit: 200 })).reverse();
    if (!older.length) return toast(true, "더 이전 기록이 없습니다");
    found ? setFound([...older, ...found]) : setBase((b) => [...older, ...b]);
  };
  const send = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!msg.trim()) return;
    try { await api.say(msg.trim(), to.trim()); setMsg(""); setFollow(true); }
    catch (er) { toast(false, (er as Error).message); }
  };

  let lastDay = "";
  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>채팅</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>게임 채팅만 따로 · 관리자로 답장 · 금지어 · 광고 감지</motion.p>

      <Glass className="card" {...fadeUp} style={{ padding: 0, overflow: "hidden" }}>
        <form className="row wrap" onSubmit={search} style={{ padding: "14px 16px 10px", gap: 8 }}>
          <Segmented size="sm" value={filter} onChange={(v) => { setFilter(v); setFound(null); }} options={[{ value: "all", label: "전체" }, { value: "flagged", label: "⚠ 감지" }]} />
          <input className="field" style={{ height: 36, width: 130 }} placeholder="닉네임" value={name} onChange={(e) => setName(e.target.value)} />
          <input className="field grow" style={{ height: 36, minWidth: 120 }} placeholder="단어 검색" value={q} onChange={(e) => setQ(e.target.value)} />
          <Button className="sm" type="submit" aria-label="검색"><I.search size={16} /></Button>
          {found && <Button className="sm" type="button" onClick={() => { setFound(null); setQ(""); setName(""); }}>실시간으로</Button>}
          {me.role === "admin" && <Button className="sm" type="button" onClick={() => setCfgOpen(true)} aria-label="금지어 설정"><I.gear size={16} /></Button>}
        </form>
        <div className="row" style={{ padding: "0 16px 8px", fontSize: 12.5, gap: 8 }}>
          {found ? <span className="muted">검색 결과 {found.length}줄</span>
            : <><span className="dot" style={{ ["--c" as string]: online ? "var(--green)" : "var(--text-3)" }} /><span className="muted">{online ? "실시간" : "서버 꺼짐"} · {rows.length}줄</span></>}
          <div className="grow" />
          <button className="btn sm ghost" onClick={more} type="button">이전 기록</button>
        </div>
        <div ref={box} onScroll={(e) => { const el = e.currentTarget; setFollow(el.scrollHeight - el.scrollTop - el.clientHeight < 40); }}
          style={{ height: "min(62vh, 640px)", overflowY: "auto", padding: "4px 8px 8px", borderTop: "1px solid color-mix(in srgb, var(--stroke) 55%, transparent)" }}>
          {shown.map((r) => {
            const d = day(r.ts);
            const sep = d !== lastDay;
            lastDay = d;
            return (
              <div key={r.id}>
                {sep && <div className="faint" style={{ textAlign: "center", fontSize: 11.5, margin: "10px 0 4px" }}>{d}</div>}
                <Line r={r} onName={setSel} />
              </div>
            );
          })}
          {!shown.length && <div className="empty">{found ? "찾는 채팅이 없습니다" : filter === "flagged" ? "감지된 채팅이 없습니다" : "아직 채팅이 없습니다"}</div>}
        </div>
        <AnimatePresence>
          {!follow && !found && (
            <motion.button initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }} className="btn sm"
              onClick={() => { setFollow(true); box.current && (box.current.scrollTop = box.current.scrollHeight); }}
              style={{ position: "relative", margin: "-46px auto 8px", display: "flex" }}>↓ 최신으로</motion.button>
          )}
        </AnimatePresence>
        {ops && (
          <form onSubmit={send} className="row" style={{ padding: 12, gap: 8, borderTop: "1px solid color-mix(in srgb, var(--stroke) 55%, transparent)" }}>
            <input className="field" style={{ width: 110, flex: "none" }} placeholder="받는 사람" value={to} onChange={(e) => setTo(e.target.value)} aria-label="귓속말 받는 사람 (비우면 전체)" />
            <input className="field grow" placeholder={to ? `${to} 님에게 귓속말` : `[관리자] ${me.username} 로 전체 채팅`} value={msg} onChange={(e) => setMsg(e.target.value)} maxLength={256} disabled={!online} />
            <Button type="submit" tint="var(--blue)" disabled={!online || !msg.trim()} aria-label="보내기"><I.send size={18} /></Button>
          </form>
        )}
      </Glass>

      <Sheet open={!!sel} onClose={() => setSel(null)} title={sel ?? ""}>
        {sel && (
          <div style={{ display: "grid", gap: 14 }}>
            <div className="row wrap" style={{ gap: 8 }}>
              <Button className="sm" onClick={() => { setName(sel); setQ(""); api.chat({ name: sel, limit: 300 }).then((r) => setFound(r.reverse())); setSel(null); }}><I.search size={15} />이 사람 채팅만</Button>
              {ops && <Button className="sm" onClick={() => { setTo(sel); setSel(null); }}><I.chat size={15} />귓속말</Button>}
            </div>
            {ops && <SanctionForm name={sel} toast={toast} onDone={() => setSel(null)} />}
          </div>
        )}
      </Sheet>
      {me.role === "admin" && <ChatSettingsSheet open={cfgOpen} onClose={() => setCfgOpen(false)} toast={toast} />}
    </div>
  );
}

function ChatSettingsSheet({ open, onClose, toast }: { open: boolean; onClose: () => void; toast: (ok: boolean, t: string) => void }) {
  const [s, setS] = useState<ChatSettings | null>(null);
  const [words, setWords] = useState("");
  const [domains, setDomains] = useState("");
  useEffect(() => {
    if (open) api.chatSettings().then((x) => { setS(x); setWords(x.words.join("\n")); setDomains(x.allow_domains.join("\n")); }).catch(() => {});
  }, [open]);
  const save = async () => {
    try {
      const split = (t: string) => t.split(/[\n,]/).map((w) => w.trim()).filter(Boolean);
      const r = await api.saveChatSettings({ words: split(words), block_ads: s?.block_ads, allow_domains: split(domains) });
      toast(true, `금지어 ${r.words.length}개 저장했습니다`);
      onClose();
    } catch (e) { toast(false, (e as Error).message); }
  };
  return (
    <Sheet open={open} onClose={onClose} title="채팅 감지 설정" footer={<Button tint="var(--blue)" style={{ width: "100%", height: 48 }} onClick={save}>저장</Button>}>
      {s && (
        <div style={{ display: "grid", gap: 14 }}>
          <div>
            <label className="lbl">금지어 (줄마다 하나, 띄어 써서 피해도 잡음)</label>
            <textarea className="field" style={{ height: 140, padding: 12, resize: "vertical" }} value={words} onChange={(e) => setWords(e.target.value)} placeholder={"예:\n욕설1\n욕설2"} />
          </div>
          <div className="row">
            <div className="grow"><b>광고 감지</b><div className="faint" style={{ fontSize: 12.5 }}>IP 주소 · 다른 서버 주소(.kr .com .gg …)가 보이면</div></div>
            <Toggle on={s.block_ads} onChange={(v) => setS({ ...s, block_ads: v })} />
          </div>
          <div>
            <label className="lbl">광고로 보지 않을 주소 (우리 서버 · 디스코드 등)</label>
            <textarea className="field" style={{ height: 80, padding: 12, resize: "vertical" }} value={domains} onChange={(e) => setDomains(e.target.value)} placeholder="예: myserver.kr" />
          </div>
          <div className="faint" style={{ fontSize: 12.5 }}>감지되면 채팅 줄이 빨갛게 표시되고, 설정 → 알림의 "채팅 금지어 · 광고 감지"가 켜져 있으면 폰으로 알림이 갑니다. 채팅을 막지는 않습니다 (막는 건 WaveCore 의 몫).</div>
        </div>
      )}
    </Sheet>
  );
}
