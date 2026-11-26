import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useState } from "react";
import { api, canOperate, type Me, type RecentPlayer, type Status } from "../lib/api";
import { ago, duration } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Avatar, Button, Segmented, Sheet } from "../components/Controls";
import { I } from "../components/Icons";

type Act = "kick" | "ban" | "pardon" | "op" | "deop";
const ACTS: { a: Act; label: string; c: string; reason?: boolean }[] = [
  { a: "kick", label: "강퇴", c: "var(--orange)", reason: true },
  { a: "ban", label: "차단", c: "var(--red)", reason: true },
  { a: "pardon", label: "차단 해제", c: "var(--green)" },
  { a: "op", label: "OP 주기", c: "var(--purple)" },
  { a: "deop", label: "OP 빼기", c: "var(--text-2)" },
];

export function Players({ me, status, toast }: { me: Me; status: Status | null; toast: (ok: boolean, t: string) => void }) {
  const [recent, setRecent] = useState<RecentPlayer[]>([]);
  const [tab, setTab] = useState<"online" | "all">("online");
  const [q, setQ] = useState("");
  const [sel, setSel] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const ops = canOperate(me);

  useEffect(() => {
    const load = () => api.players().then((d) => setRecent(d.recent)).catch(() => {});
    load();
    const t = setInterval(load, 15000);
    return () => clearInterval(t);
  }, [status?.players.length]);

  const online = status?.players ?? [];
  const list = useMemo(() => {
    const f = q.trim().toLowerCase();
    const rows: RecentPlayer[] = tab === "online"
      ? online.map((n) => recent.find((r) => r.name.toLowerCase() === n.toLowerCase()) ?? { name: n, last_join: 0, online: 1, sessions: 1, playtime: 0 })
      : recent;
    return f ? rows.filter((r) => r.name.toLowerCase().includes(f)) : rows;
  }, [tab, online, recent, q]);

  const act = async (a: Act) => {
    if (!sel) return;
    setBusy(true);
    try {
      const r = await api.playerAction(sel, a, reason);
      toast(true, r.output || "완료했습니다");
      setSel(null);
      setReason("");
    } catch (e) {
      toast(false, (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>플레이어</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>접속 확인 · 접속 기록 · 강퇴와 차단</motion.p>
      <Glass className="card" {...fadeUp}>
        <div className="row wrap" style={{ marginBottom: 12, gap: 12 }}>
          <Segmented value={tab} onChange={setTab} options={[{ value: "online", label: `접속 중 ${online.length}` }, { value: "all", label: "최근 접속" }]} />
          <div className="row grow" style={{ minWidth: 160 }}>
            <input className="field" style={{ height: 40 }} placeholder="닉네임 검색" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          {ops && <Button className="sm" onClick={() => { const n = prompt("닉네임"); if (n) setSel(n.trim()); }}><I.plus size={16} />닉네임으로</Button>}
        </div>
        <div className="list">
          <AnimatePresence initial={false}>
            {list.map((r) => (
              <motion.div key={r.name} layout initial={{ opacity: 0, x: -10 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 10 }} className="list-item">
                <Avatar name={r.name} />
                <div className="grow">
                  <div className="row" style={{ gap: 8 }}>
                    <b style={{ fontWeight: 650 }}>{r.name}</b>
                    {r.online ? <span className="dot" /> : null}
                  </div>
                  <div className="faint" style={{ fontSize: 12.5 }}>
                    {r.online ? "접속 중" : `마지막 접속 ${ago(r.last_join)}`} · 총 {duration(r.playtime)} · {r.sessions}회
                  </div>
                </div>
                {ops && <Button className="sm" onClick={() => setSel(r.name)}>관리</Button>}
              </motion.div>
            ))}
          </AnimatePresence>
          {!list.length && <div className="empty">{tab === "online" ? (status?.online ? "접속한 플레이어가 없습니다" : "서버가 꺼져 있습니다") : "기록이 없습니다"}</div>}
        </div>
      </Glass>

      <Sheet open={!!sel} onClose={() => setSel(null)} title={sel ?? ""}>
        {sel && (
          <div>
            <div className="row" style={{ gap: 14, marginBottom: 18 }}>
              <Avatar name={sel} size={56} />
              <div>
                <div style={{ fontSize: 20, fontWeight: 700 }}>{sel}</div>
                <div className="muted">{online.some((n) => n.toLowerCase() === sel.toLowerCase()) ? "지금 접속 중" : "접속 중이 아님"}</div>
              </div>
            </div>
            <label className="lbl">사유 (강퇴 · 차단 때 보임)</label>
            <input className="field" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="예: 비매너 행위" maxLength={200} />
            <div className="grid cols-2" style={{ marginTop: 16, gap: 10 }}>
              {ACTS.map((x) => (
                <Button key={x.a} disabled={busy} onClick={() => act(x.a)} style={{ color: x.c, height: 50 }}>{x.label}</Button>
              ))}
            </div>
          </div>
        )}
      </Sheet>
    </div>
  );
}
