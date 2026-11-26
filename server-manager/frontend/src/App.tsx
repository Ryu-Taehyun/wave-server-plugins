import { AnimatePresence, LayoutGroup, motion } from "motion/react";
import { useCallback, useEffect, useState } from "react";
import { api, setUnauthorizedHandler, type Me } from "./lib/api";
import { useLive } from "./lib/live";
import { STATE_LABEL } from "./lib/format";
import { Wallpaper } from "./components/Wallpaper";
import { Island } from "./components/Island";
import { Glass } from "./components/Glass";
import { I } from "./components/Icons";
import { Login } from "./pages/Login";
import { Overview } from "./pages/Overview";
import { Console } from "./pages/Console";
import { Players } from "./pages/Players";
import { Tasks } from "./pages/Tasks";
import { Activity, Settings } from "./pages/Settings";

type Page = "overview" | "console" | "players" | "tasks" | "activity" | "settings";
const NAV: { id: Page; label: string; icon: (p: { size?: number }) => React.ReactNode; mobile: boolean }[] = [
  { id: "overview", label: "개요", icon: I.home, mobile: true },
  { id: "console", label: "콘솔", icon: I.terminal, mobile: true },
  { id: "players", label: "플레이어", icon: I.people, mobile: true },
  { id: "tasks", label: "예약", icon: I.clock, mobile: true },
  { id: "activity", label: "활동", icon: I.shield, mobile: false },
  { id: "settings", label: "설정", icon: I.gear, mobile: true },
];

type Theme = "auto" | "dark" | "light";
function readTheme(): Theme {
  try { return (localStorage.getItem("mcm.theme") as Theme) || "auto"; } catch { return "auto"; }
}

export default function App() {
  const [me, setMe] = useState<Me | null | undefined>(undefined);
  const [page, setPage] = useState<Page>(() => (location.hash.slice(1) as Page) || "overview");
  const [theme, setThemeState] = useState<Theme>(readTheme);
  const live = useLive(!!me);

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null));
    api.me().then(setMe).catch(() => setMe(null));
  }, []);
  useEffect(() => { history.replaceState(null, "", `#${page}`); window.scrollTo({ top: 0 }); }, [page]);
  useEffect(() => {
    if (theme === "auto") document.documentElement.removeAttribute("data-theme");
    else document.documentElement.setAttribute("data-theme", theme);
  }, [theme]);

  const setTheme = (t: Theme) => {
    setThemeState(t);
    try { localStorage.setItem("mcm.theme", t); } catch { /* 저장 못 해도 괜찮음 */ }
  };
  const logout = useCallback(async () => {
    await api.logout().catch(() => {});
    setMe(null);
  }, []);

  if (me === undefined) return <Wallpaper />;
  if (!me) return (<><Wallpaper /><Login onLogin={setMe} /></>);

  const st = live.status?.state ?? "stopped";
  const content = (() => {
    switch (page) {
      case "overview": return <Overview me={me} status={live.status} setStatus={live.setStatus} toast={live.pushToast} />;
      case "console": return <Console me={me} logs={live.logs} toast={live.pushToast} />;
      case "players": return <Players me={me} status={live.status} toast={live.pushToast} />;
      case "tasks": return <Tasks me={me} toast={live.pushToast} />;
      case "activity": return <Activity tick={live.auditTick} />;
      case "settings": return (<><Settings me={me} onLogout={logout} toast={live.pushToast} theme={theme} setTheme={setTheme} /><div style={{ height: 16 }} /><div className="mobile-only"><Activity tick={live.auditTick} /></div></>);
    }
  })();

  return (
    <>
      <Wallpaper />
      <Island toasts={live.toasts} connected={live.connected} />
      <div className="shell">
        <aside className="side">
          <Glass className="side-inner" strong>
            <div className="row" style={{ padding: "4px 8px 18px", gap: 12 }}>
              <div style={{ width: 40, height: 40, borderRadius: 12, display: "grid", placeItems: "center", color: "#fff", background: "var(--blue)", boxShadow: "inset 0 1px 0 rgba(255,255,255,.35)" }}><I.cube size={22} /></div>
              <div>
                <div style={{ fontWeight: 700, letterSpacing: "-0.02em" }}>서버 관리</div>
                <div className="row" style={{ gap: 6, fontSize: 12.5 }}><span className="dot" style={{ ["--c" as string]: STATE_LABEL[st].c }} /><span className="muted">{STATE_LABEL[st].text}</span></div>
              </div>
            </div>
            <LayoutGroup id="side">
              {NAV.map((n) => (
                <button key={n.id} onClick={() => setPage(n.id)} className="row"
                  style={{ position: "relative", height: 44, padding: "0 12px", border: 0, background: "transparent", borderRadius: 14, fontWeight: 600, fontSize: 15, color: page === n.id ? "var(--text)" : "var(--text-2)", gap: 12 }}>
                  {page === n.id && <motion.span layoutId="side-active" className="glass strong" transition={{ type: "spring", stiffness: 500, damping: 38 }}
                    style={{ position: "absolute", inset: 0, borderRadius: 14, zIndex: -1, ["--blur" as string]: "12px" }} />}
                  <n.icon size={20} />{n.label}
                </button>
              ))}
            </LayoutGroup>
            <div className="grow" />
            <div className="row" style={{ padding: "8px", gap: 10 }}>
              <div className="grow" style={{ fontSize: 13 }}><b>{me.username}</b><div className="faint" style={{ fontSize: 12 }}>{live.connected ? "실시간 연결됨" : "연결 중…"}</div></div>
              <button className="btn icon ghost" onClick={logout} aria-label="로그아웃"><I.logout size={19} /></button>
            </div>
          </Glass>
        </aside>

        <main className="main">
          <AnimatePresence mode="wait">
            <motion.div key={page} initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.22 }}>
              {content}
            </motion.div>
          </AnimatePresence>
        </main>
      </div>

      <nav className="tabbar glass strong" aria-label="메뉴">
        <LayoutGroup id="tab">
          {NAV.filter((n) => n.mobile).map((n) => (
            <motion.button key={n.id} onClick={() => setPage(n.id)} whileTap={{ scale: 0.9 }}
              style={{ position: "relative", flex: 1, border: 0, background: "transparent", borderRadius: 999, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 2,
                color: page === n.id ? "var(--blue)" : "var(--text-2)", fontSize: 10.5, fontWeight: 600 }}>
              {page === n.id && <motion.span layoutId="tab-active" transition={{ type: "spring", stiffness: 500, damping: 36 }}
                style={{ position: "absolute", inset: 0, borderRadius: 999, zIndex: -1, background: "color-mix(in srgb, var(--glass-strong) 100%, transparent)", boxShadow: "inset 0 1px 0 var(--shine), inset 0 0 0 1px var(--stroke)" }} />}
              <n.icon size={23} />{n.label}
            </motion.button>
          ))}
        </LayoutGroup>
      </nav>
    </>
  );
}
