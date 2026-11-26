import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { api, canOperate, type Me, type Ranked, type Status, type WaveOverview, type WavePlayer } from "../lib/api";
import { ago, duration, won } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Avatar, Button, Segmented, Sheet, Toggle } from "../components/Controls";
import { I } from "../components/Icons";

function Rank({ title, rows, unit, c }: { title: string; rows: Ranked[]; unit: string; c: string }) {
  return (
    <Glass className="card" {...fadeUp}>
      <div className="card-t" style={{ marginBottom: 8 }}>{title}</div>
      <div className="list">
        {rows.map((r, i) => (
          <div key={r.name} className="list-item" style={{ padding: "8px 2px" }}>
            <span className="tnum" style={{ width: 22, fontWeight: 700, color: i < 3 ? c : "var(--text-3)" }}>{i + 1}</span>
            <Avatar name={r.name} size={26} />
            <span className="grow" style={{ fontWeight: 600 }}>{r.name}</span>
            <span className="tnum muted">{won(r.value)}{unit}</span>
          </div>
        ))}
        {!rows.length && <div className="empty">기록 없음</div>}
      </div>
    </Glass>
  );
}

export function Operations({ me, status, toast }: { me: Me; status: Status | null; toast: (ok: boolean, t: string) => void }) {
  const [w, setW] = useState<WaveOverview | null>(null);
  const [mins, setMins] = useState("60");
  const [mult, setMult] = useState("2");
  const [msg, setMsg] = useState("");
  const [season, setSeason] = useState("");
  const [wl, setWl] = useState("");
  const [q, setQ] = useState("");
  const [player, setPlayer] = useState<WavePlayer | null>(null);
  const ops = canOperate(me);
  const online = !!status?.online;

  const load = () => api.wave().then((d) => { setW(d); setSeason(String(d.season ?? "")); }).catch(() => {});
  useEffect(() => { load(); const t = setInterval(load, 15000); return () => clearInterval(t); }, []);

  const run = async (p: Promise<unknown>, ok: string) => {
    try { await p; toast(true, ok); setTimeout(load, 600); } catch (e) { toast(false, (e as Error).message); }
  };
  const hot = w?.hot_time.status;

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>운영</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>WaveCore · 핫타임 · 공지 · 점검 모드 · 화이트리스트</motion.p>
      {w && !w.available && <Glass className="card" style={{ marginBottom: 16 }}><div className="empty">서버 폴더에서 plugins/WaveCore 를 찾지 못했습니다 (manager.toml 의 [wave] plugin_dir)</div></Glass>}

      <div className="grid cols-4" style={{ marginBottom: 16 }}>
        {[
          { l: "WaveCore 플레이어", v: won(w?.players), s: `최근 7일 ${won(w?.active_7d)}명` },
          { l: "서버 전체 캐시", v: won(w?.economy.total), s: `1인 평균 ${won(w?.economy.avg)}` },
          { l: "유료 캐시 합계", v: won(w?.economy.paid_total), s: `최고 보유 ${won(w?.economy.richest)}` },
          { l: "업적 보상 수령", v: won(w?.achievements), s: `시즌 ${w?.season ?? "—"}` },
        ].map((x) => (
          <Glass key={x.l} className="card" {...fadeUp}>
            <div className="muted" style={{ fontSize: 13.5, fontWeight: 600 }}>{x.l}</div>
            <div className="tnum" style={{ fontSize: 26, fontWeight: 700, margin: "6px 0 2px" }}>{x.v}</div>
            <div className="faint" style={{ fontSize: 12.5 }}>{x.s}</div>
          </Glass>
        ))}
      </div>

      <div className="grid cols-2" style={{ marginBottom: 16 }}>
        {/* 핫타임 */}
        <Glass className="card" {...fadeUp}>
          <div className="card-h">
            <div className="card-t">핫타임</div>
            {hot?.active ? <span className="pill" style={{ ["--c" as string]: "var(--orange)" }}>진행 중 · x{hot.multiplier ?? ""} · {hot.left}</span>
              : <span className="pill" style={{ ["--c" as string]: "var(--text-3)" }}>{online ? "꺼짐" : "서버 꺼짐"}</span>}
          </div>
          <div className="faint" style={{ fontSize: 13, marginBottom: 12 }}>일정: {(w?.hot_time.schedule ?? []).join(" · ") || "없음"} (WaveCore config.yml)</div>
          <div className="row wrap">
            <input className="field tnum" style={{ width: 90 }} value={mins} onChange={(e) => setMins(e.target.value)} inputMode="numeric" /><span className="muted">분</span>
            <Segmented size="sm" value={mult} onChange={setMult} options={["1.5", "2", "3"].map((v) => ({ value: v, label: `x${v}` }))} />
            <div className="grow" />
            {hot?.active
              ? <Button disabled={!ops || !online} style={{ color: "var(--red)" }} onClick={() => run(api.hotTime("stop"), "핫타임을 끝냈습니다")}>끝내기</Button>
              : <Button tint="var(--orange)" disabled={!ops || !online} onClick={() => run(api.hotTime("start", Number(mins), Number(mult)), "핫타임을 시작했습니다")}>지금 시작</Button>}
          </div>
        </Glass>

        {/* 점검 · 화이트리스트 */}
        <Glass className="card" {...fadeUp}>
          <div className="list">
            <div className="list-item">
              <div className="grow"><b>점검 모드</b><div className="faint" style={{ fontSize: 12.5 }}>켜면 관리자 외 전원 추방 · 서버 목록에 점검 중 표시</div></div>
              <Toggle on={!!w?.maintenance} disabled={!ops || !online} onChange={(v) => { if (!v || confirm("점검 모드를 켜면 접속 중인 플레이어가 모두 추방됩니다. 켤까요?")) run(api.maintenance(v), v ? "점검 모드를 켰습니다" : "점검 모드를 껐습니다"); }} />
            </div>
            <div className="list-item">
              <div className="grow"><b>화이트리스트</b><div className="faint" style={{ fontSize: 12.5 }}>켜면 목록에 있는 사람만 접속 · {w?.whitelist.names.length ?? 0}명</div></div>
              <Toggle on={!!w?.whitelist.on} disabled={!ops || !online} onChange={(v) => run(api.whitelist(v ? "on" : "off"), v ? "화이트리스트를 켰습니다" : "화이트리스트를 껐습니다")} />
            </div>
          </div>
          <div className="row" style={{ marginTop: 8 }}>
            <input className="field" style={{ height: 40 }} placeholder="닉네임 추가" value={wl} onChange={(e) => setWl(e.target.value)} />
            <Button className="sm" disabled={!ops || !online || !wl.trim()} onClick={() => { run(api.whitelist("add", wl.trim()), `${wl} 추가`); setWl(""); }}>추가</Button>
          </div>
          <div className="row wrap" style={{ gap: 6, marginTop: 10 }}>
            {w?.whitelist.names.map((n) => (
              <span key={n} className="pill" style={{ ["--c" as string]: "var(--text-2)", paddingRight: 4 }}>{n}
                {ops && <button className="btn ghost icon" style={{ width: 20, height: 20 }} aria-label="빼기" onClick={() => run(api.whitelist("remove", n), `${n} 뺌`)}><I.xmark size={12} /></button>}
              </span>
            ))}
          </div>
        </Glass>

        {/* 공지 */}
        <Glass className="card" {...fadeUp}>
          <div className="card-t" style={{ marginBottom: 10 }}>전체 공지</div>
          <form className="row" onSubmit={(e) => { e.preventDefault(); if (msg.trim()) { run(api.broadcast(msg.trim()), "공지했습니다"); setMsg(""); } }}>
            <input className="field" placeholder="게임 안 모두에게 보낼 말" value={msg} onChange={(e) => setMsg(e.target.value)} maxLength={300} disabled={!ops} />
            <Button tint="var(--blue)" type="submit" disabled={!ops || !online || !msg.trim()}><I.megaphone size={18} /></Button>
          </form>
        </Glass>

        {/* 시즌 */}
        <Glass className="card" {...fadeUp}>
          <div className="card-t" style={{ marginBottom: 4 }}>시즌 번호</div>
          <div className="faint" style={{ fontSize: 12.5, marginBottom: 10 }}>순위 칭호 · 트로피의 'S1' 표시. 바꾸면 WaveCore 설정을 다시 불러옵니다 (초기화는 하지 않음)</div>
          <div className="row">
            <span className="muted" style={{ fontWeight: 700 }}>S</span>
            <input className="field tnum" style={{ width: 100 }} value={season} onChange={(e) => setSeason(e.target.value.replace(/\D/g, ""))} inputMode="numeric" />
            <Button disabled={me.role !== "admin" || !season || Number(season) === w?.season}
              onClick={() => run(api.setSeason(Number(season)), `시즌 ${season} 으로 바꿨습니다`)}>바꾸기</Button>
          </div>
        </Glass>
      </div>

      <div className="grid cols-3" style={{ marginBottom: 16 }}>
        <Rank title="부의 제왕 (캐시)" rows={w?.top_money ?? []} unit="" c="var(--yellow)" />
        <Rank title="킬의 제왕 (처치)" rows={w?.top_kills ?? []} unit="킬" c="var(--red)" />
        <Rank title="레벨" rows={w?.top_level ?? []} unit="" c="var(--green)" />
      </div>

      <Glass className="card" {...fadeUp}>
        <div className="card-t" style={{ marginBottom: 10 }}>플레이어 찾기</div>
        <form className="row" onSubmit={async (e) => { e.preventDefault(); try { setPlayer(await api.wavePlayer(q.trim())); } catch (er) { toast(false, (er as Error).message); } }}>
          <input className="field" placeholder="닉네임" value={q} onChange={(e) => setQ(e.target.value)} />
          <Button type="submit" disabled={!q.trim()}><I.search size={18} /></Button>
        </form>
      </Glass>

      <Sheet open={!!player} onClose={() => setPlayer(null)} title={player?.name ?? ""}>
        {player && (
          <div style={{ display: "grid", gap: 14 }}>
            <div className="row" style={{ gap: 14 }}>
              <Avatar name={player.name} size={56} />
              <div><div style={{ fontSize: 20, fontWeight: 700 }}>{player.name}</div>
                <div className="muted">{player.active_title_text || "칭호 없음"} · 마지막 접속 {ago(player["last-seen"] ? player["last-seen"] / 1000 : null)}</div></div>
            </div>
            <div className="grid cols-3" style={{ gap: 8 }}>
              {[["레벨", player.level], ["캐시", won(player.money)], ["유료 캐시", won(player.exmoney)], ["처치", player.kills], ["출석", `${player["attend-total"] ?? 0}일 (연속 ${player["attend-streak"] ?? 0})`], ["업적 보상", player.achievements]].map(([l, v]) => (
                <div key={String(l)} className="glass thin" style={{ padding: "10px 12px", borderRadius: 14 }}>
                  <div className="faint" style={{ fontSize: 12 }}>{l}</div><b className="tnum">{v ?? "—"}</b>
                </div>
              ))}
            </div>
            <div><b>접속 시간</b> <span className="muted">{duration((player.stats?.playtime ?? 0) * 60)}</span></div>
            <div>
              <b>제재 기록</b> <span className="muted">경고 {player.warnings ?? 0}회</span>
              <div className="mono faint" style={{ fontSize: 12.5, marginTop: 6 }}>{player.sanctions.length ? player.sanctions.map((s, i) => <div key={i}>{s}</div>) : "없음"}</div>
            </div>
            <div className="faint mono" style={{ fontSize: 11.5 }}>{player.uuid}</div>
          </div>
        )}
      </Sheet>
    </div>
  );
}
