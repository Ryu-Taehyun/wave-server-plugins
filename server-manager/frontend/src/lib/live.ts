// 실시간 연결 (WebSocket): 상태 · 로그 · 알림. 끊기면 점점 늦게 다시 연결
import { useEffect, useRef, useState } from "react";
import type { Status } from "./api";

export interface Toast { id: number; ok: boolean; text: string }
type Msg =
  | { type: "hello"; data: { status: Status; logs: string[] } }
  | { type: "status"; data: Status }
  | { type: "log"; data: string[] }
  | { type: "toast"; data: { ok: boolean; text: string } }
  | { type: "state"; data: { state: Status["state"] } }
  | { type: "audit"; data: unknown }
  | { type: "task"; data: { name: string; ok: boolean; output: string } };

const MAX_LOGS = 2000;

export function useLive(enabled: boolean) {
  const [status, setStatus] = useState<Status | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [connected, setConnected] = useState(false);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [auditTick, setAuditTick] = useState(0);
  const retry = useRef(0);

  const pushToast = (ok: boolean, text: string) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t.slice(-3), { id, ok, text }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200);
  };

  useEffect(() => {
    if (!enabled) return;
    let ws: WebSocket | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let ping: ReturnType<typeof setInterval> | undefined;
    let closed = false;

    const connect = () => {
      const proto = location.protocol === "https:" ? "wss" : "ws";
      ws = new WebSocket(`${proto}://${location.host}/api/ws`);
      ws.onopen = () => {
        retry.current = 0;
        setConnected(true);
        ping = setInterval(() => ws?.readyState === 1 && ws.send("ping"), 25000);
      };
      ws.onmessage = (ev) => {
        const m = JSON.parse(ev.data) as Msg;
        switch (m.type) {
          case "hello":
            setStatus(m.data.status);
            setLogs(m.data.logs);
            break;
          case "status":
            setStatus(m.data);
            break;
          case "state":
            setStatus((s) => (s ? { ...s, state: m.data.state } : s));
            break;
          case "log":
            setLogs((l) => {
              const n = l.concat(m.data);
              return n.length > MAX_LOGS ? n.slice(n.length - MAX_LOGS) : n;
            });
            break;
          case "toast":
            pushToast(m.data.ok, m.data.text);
            break;
          case "task":
            pushToast(m.data.ok, `예약 작업 '${m.data.name}' ${m.data.ok ? "완료" : "실패"}`);
            break;
          case "audit":
            setAuditTick((x) => x + 1);
            break;
        }
      };
      ws.onclose = (ev) => {
        setConnected(false);
        clearInterval(ping);
        if (closed || ev.code === 4401) return;
        const delay = Math.min(15000, 600 * 2 ** retry.current++);
        timer = setTimeout(connect, delay);
      };
    };
    connect();
    return () => {
      closed = true;
      clearTimeout(timer);
      clearInterval(ping);
      ws?.close();
    };
  }, [enabled]);

  return { status, setStatus, logs, connected, toasts, pushToast, auditTick };
}
