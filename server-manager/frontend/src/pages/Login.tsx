import { motion, useAnimationControls } from "motion/react";
import { useState } from "react";
import { api, type Me } from "../lib/api";
import { Glass } from "../components/Glass";
import { Button } from "../components/Controls";
import { I } from "../components/Icons";

export function Login({ onLogin }: { onLogin: (me: Me) => void }) {
  const [u, setU] = useState("");
  const [p, setP] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const shake = useAnimationControls();

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr("");
    try {
      onLogin(await api.login(u, p));
    } catch (ex) {
      setErr((ex as Error).message);
      shake.start({ x: [0, -12, 10, -8, 6, -3, 0], transition: { duration: 0.45 } });
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ minHeight: "100%", display: "grid", placeItems: "center", padding: 20 }}>
      <motion.div animate={shake} style={{ width: "min(400px, 100%)" }}>
        <Glass strong initial={{ opacity: 0, y: 24, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }} transition={{ type: "spring", stiffness: 220, damping: 26 }}
          style={{ padding: "34px 28px 28px", ["--blur" as string]: "40px" }}>
          <motion.div initial={{ scale: 0.6, rotate: -12, opacity: 0 }} animate={{ scale: 1, rotate: 0, opacity: 1 }} transition={{ type: "spring", stiffness: 260, damping: 16, delay: 0.1 }}
            style={{ width: 76, height: 76, borderRadius: 22, margin: "0 auto 18px", display: "grid", placeItems: "center", color: "#fff",
              background: "linear-gradient(145deg, #3d8bff, #7b4dff 55%, #00c2a8)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.5), 0 14px 30px -8px rgba(80,110,255,.7)" }}>
            <I.cube size={38} />
          </motion.div>
          <h1 style={{ textAlign: "center", fontSize: 26, fontWeight: 700, letterSpacing: "-0.03em", margin: "0 0 4px" }}>서버 관리</h1>
          <p className="muted" style={{ textAlign: "center", margin: "0 0 26px" }}>마인크래프트 서버에 로그인하세요</p>
          <form onSubmit={submit} style={{ display: "grid", gap: 12 }}>
            <input className="field" placeholder="아이디" autoComplete="username" autoCapitalize="none" value={u} onChange={(e) => setU(e.target.value)} required />
            <input className="field" placeholder="비밀번호" type="password" autoComplete="current-password" value={p} onChange={(e) => setP(e.target.value)} required />
            <motion.div initial={false} animate={{ height: err ? "auto" : 0, opacity: err ? 1 : 0 }} style={{ overflow: "hidden", color: "var(--red)", fontSize: 13.5, textAlign: "center" }}>
              {err}
            </motion.div>
            <Button tint="var(--blue)" type="submit" disabled={busy || !u || !p} style={{ height: 50, marginTop: 6, fontSize: 16 }}>
              {busy ? "확인 중…" : "로그인"}
            </Button>
          </form>
        </Glass>
        <p className="faint" style={{ textAlign: "center", fontSize: 12.5, marginTop: 16 }}>로그인한 기기는 설정 → 내 기기에서 관리할 수 있습니다</p>
      </motion.div>
    </div>
  );
}
