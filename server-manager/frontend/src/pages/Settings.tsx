import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { api, type AuditRow, type Me, type Role, type SessionRow, type UserRow } from "../lib/api";
import { ACTION_LABEL, ago, clock, device, ROLE_LABEL } from "../lib/format";
import { Glass, fadeUp } from "../components/Glass";
import { Button, Segmented, Sheet } from "../components/Controls";
import { I } from "../components/Icons";
import { NotifyCard } from "./Notify";

export function Activity({ tick }: { tick: number }) {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [f, setF] = useState<"all" | "server" | "login">("all");
  useEffect(() => { api.audit().then(setRows).catch(() => {}); }, [tick]);
  const shown = rows.filter((r) => f === "all" || (f === "server" ? !r.action.startsWith("login") && !r.action.startsWith("session") : r.action.startsWith("login") || r.action.startsWith("session")));
  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>활동 기록</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>누가 · 언제 · 어디서 무엇을 했는지</motion.p>
      <Glass className="card" {...fadeUp}>
        <div style={{ marginBottom: 10 }}>
          <Segmented size="sm" value={f} onChange={setF} options={[{ value: "all", label: "전체" }, { value: "server", label: "서버 조작" }, { value: "login", label: "로그인" }]} />
        </div>
        <div className="list">
          <AnimatePresence initial={false}>
            {shown.map((r) => (
              <motion.div key={r.id} layout initial={{ opacity: 0, y: -6 }} animate={{ opacity: 1, y: 0 }} className="list-item" style={{ alignItems: "flex-start" }}>
                <div style={{ width: 8, height: 8, borderRadius: 9, marginTop: 7, flex: "none", background: r.action.includes("fail") ? "var(--red)" : r.action.startsWith("server") ? "var(--orange)" : "var(--blue)" }} />
                <div className="grow">
                  <div><b style={{ fontWeight: 650 }}>{r.username ?? "?"}</b> <span className="muted">{ACTION_LABEL[r.action] ?? r.action}</span></div>
                  {r.detail && <div className="mono faint" style={{ fontSize: 12.5, wordBreak: "break-all" }}>{r.detail}</div>}
                </div>
                <div className="faint tnum" style={{ fontSize: 12, textAlign: "right", whiteSpace: "nowrap" }}>{clock(r.ts)}<div>{r.ip}</div></div>
              </motion.div>
            ))}
          </AnimatePresence>
          {!shown.length && <div className="empty">기록이 없습니다</div>}
        </div>
      </Glass>
    </div>
  );
}

export function Settings({ me, onLogout, toast, theme, setTheme }: {
  me: Me; onLogout: () => void; toast: (ok: boolean, t: string) => void; theme: string; setTheme: (t: "auto" | "dark" | "light") => void;
}) {
  const [sessions, setSessions] = useState<SessionRow[]>([]);
  const [users, setUsers] = useState<UserRow[]>([]);
  const [pw, setPw] = useState({ cur: "", n1: "", n2: "" });
  const [pwOpen, setPwOpen] = useState(false);
  const [nu, setNu] = useState<{ username: string; password: string; role: Role } | null>(null);
  const admin = me.role === "admin";

  const load = () => {
    api.sessions().then(setSessions).catch(() => {});
    if (admin) api.users().then(setUsers).catch(() => {});
  };
  useEffect(load, [admin]);

  const changePw = async () => {
    if (pw.n1 !== pw.n2) return toast(false, "새 비밀번호가 서로 다릅니다");
    try {
      await api.changePassword(pw.cur, pw.n1);
      toast(true, "비밀번호를 바꿨습니다 (다른 기기는 로그아웃됨)");
      setPw({ cur: "", n1: "", n2: "" });
      setPwOpen(false);
      load();
    } catch (e) {
      toast(false, (e as Error).message);
    }
  };

  return (
    <div>
      <motion.h1 className="page-title" {...fadeUp}>설정</motion.h1>
      <motion.p className="page-sub" {...fadeUp}>{me.username} · {ROLE_LABEL[me.role]}</motion.p>

      <div className="grid cols-2">
        <Glass className="card" {...fadeUp}>
          <div className="card-h"><div className="card-t">내 기기</div><span className="pill">{sessions.length}대</span></div>
          <div className="list">
            {sessions.map((s) => (
              <div key={s.id} className="list-item">
                <div style={{ width: 38, height: 38, borderRadius: 12, display: "grid", placeItems: "center", background: "color-mix(in srgb, var(--blue) 16%, transparent)", color: "var(--blue)" }}><I.device size={20} /></div>
                <div className="grow">
                  <div style={{ fontWeight: 600 }}>{device(s.user_agent)} {s.current && <span className="pill" style={{ height: 20, fontSize: 11 }}>이 기기</span>}</div>
                  <div className="faint" style={{ fontSize: 12.5 }}>{s.ip} · 마지막 사용 {ago(s.last_seen)}</div>
                </div>
                {!s.current && <Button className="sm" style={{ color: "var(--red)" }} onClick={async () => { await api.revokeSession(s.id); toast(true, "로그아웃시켰습니다"); load(); }}>로그아웃</Button>}
              </div>
            ))}
          </div>
        </Glass>

        <Glass className="card" {...fadeUp}>
          <div className="card-h"><div className="card-t">계정</div></div>
          <div className="list">
            <div className="list-item"><I.key size={20} /><span className="grow">비밀번호 변경</span><Button className="sm" onClick={() => setPwOpen(true)}>바꾸기</Button></div>
            <div className="list-item"><I.gear size={20} /><span className="grow">화면</span>
              <Segmented size="sm" value={theme as "auto"} onChange={setTheme} options={[{ value: "auto", label: "자동" }, { value: "dark", label: "어둡게" }, { value: "light", label: "밝게" }]} />
            </div>
            <div className="list-item"><I.device size={20} /><span className="grow">홈 화면에 추가<div className="faint" style={{ fontSize: 12.5 }}>Safari 공유 → 홈 화면에 추가 · Chrome 메뉴 → 앱 설치</div></span></div>
            <div className="list-item"><I.logout size={20} /><span className="grow">로그아웃</span><Button className="sm" style={{ color: "var(--red)" }} onClick={onLogout}>로그아웃</Button></div>
          </div>
        </Glass>

        <NotifyCard me={me} toast={toast} />

        {admin && (
          <Glass className="card" {...fadeUp} style={{ gridColumn: "1 / -1" }}>
            <div className="card-h"><div className="card-t">사용자</div><Button className="sm" onClick={() => setNu({ username: "", password: "", role: "operator" })}><I.plus size={16} />추가</Button></div>
            <div className="list">
              {users.map((u) => (
                <div key={u.id} className="list-item wrap">
                  <div className="grow" style={{ minWidth: 160 }}>
                    <div style={{ fontWeight: 600 }}>{u.username}</div>
                    <div className="faint" style={{ fontSize: 12.5 }}>마지막 로그인 {ago(u.last_login)} · 기기 {u.devices}대</div>
                  </div>
                  <Segmented size="sm" value={u.role} onChange={async (r: Role) => { try { await api.setRole(u.id, r); load(); } catch (e) { toast(false, (e as Error).message); } }}
                    options={(["admin", "operator", "viewer"] as Role[]).map((r) => ({ value: r, label: ROLE_LABEL[r] }))} />
                  {u.username !== me.username && <Button className="sm icon" style={{ color: "var(--red)" }} aria-label="삭제"
                    onClick={async () => { if (confirm(`${u.username} 을(를) 지울까요?`)) { await api.deleteUser(u.id); load(); } }}><I.trash size={17} /></Button>}
                </div>
              ))}
            </div>
            <p className="faint" style={{ fontSize: 12.5, margin: "10px 0 0" }}>관리자: 모든 권한 · 운영자: 서버 켜기/끄기 · 콘솔 · 플레이어 · 예약 작업 · 보기 전용: 상태와 로그만</p>
          </Glass>
        )}
      </div>

      <Sheet open={pwOpen} onClose={() => setPwOpen(false)} title="비밀번호 변경"
        footer={<Button tint="var(--blue)" style={{ width: "100%", height: 50 }} onClick={changePw} disabled={!pw.cur || !pw.n1}>바꾸기</Button>}>
        <div style={{ display: "grid", gap: 10 }}>
          <input className="field" type="password" placeholder="지금 비밀번호" autoComplete="current-password" value={pw.cur} onChange={(e) => setPw({ ...pw, cur: e.target.value })} />
          <input className="field" type="password" placeholder="새 비밀번호 (8자 이상, 글자 + 숫자)" autoComplete="new-password" value={pw.n1} onChange={(e) => setPw({ ...pw, n1: e.target.value })} />
          <input className="field" type="password" placeholder="새 비밀번호 확인" autoComplete="new-password" value={pw.n2} onChange={(e) => setPw({ ...pw, n2: e.target.value })} />
        </div>
      </Sheet>

      <Sheet open={!!nu} onClose={() => setNu(null)} title="사용자 추가"
        footer={nu && <Button tint="var(--blue)" style={{ width: "100%", height: 50 }} disabled={!nu.username || !nu.password}
          onClick={async () => { try { await api.addUser(nu.username, nu.password, nu.role); toast(true, "추가했습니다"); setNu(null); load(); } catch (e) { toast(false, (e as Error).message); } }}>추가</Button>}>
        {nu && (
          <div style={{ display: "grid", gap: 10 }}>
            <input className="field" placeholder="아이디" autoCapitalize="none" value={nu.username} onChange={(e) => setNu({ ...nu, username: e.target.value })} />
            <input className="field" type="password" placeholder="비밀번호" autoComplete="new-password" value={nu.password} onChange={(e) => setNu({ ...nu, password: e.target.value })} />
            <Segmented value={nu.role} onChange={(r: Role) => setNu({ ...nu, role: r })} options={(["admin", "operator", "viewer"] as Role[]).map((r) => ({ value: r, label: ROLE_LABEL[r] }))} />
          </div>
        )}
      </Sheet>
    </div>
  );
}
