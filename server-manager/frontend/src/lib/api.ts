// 백엔드 API (쿠키 로그인). 상태를 바꾸는 요청에는 X-MCM 머리글 (다른 사이트의 몰래 요청 막기)

export type Role = "admin" | "operator" | "viewer";
export interface Me { username: string; role: Role; version?: string }

export type ServerState = "stopped" | "starting" | "running" | "stopping" | "crashed";

export interface Status {
  checked_at: number;
  online: boolean;
  latency_ms: number | null;
  version: string;
  motd: string;
  players_online: number;
  players_max: number;
  players: string[];
  tps: number | null;
  cpu: number | null;
  mem_mb: number | null;
  rcon: boolean;
  error: string;
  state: ServerState;
  managed: boolean;
  uptime: number | null;
  summary?: { uptime_24h: number | null; peak_24h: number | null; tps_24h: number | null; unique_24h: number };
  rcon_configured?: boolean;
  address?: string;
}

export interface MetricPoint {
  t: number; uptime_pct: number; players: number; players_peak: number;
  tps: number | null; latency_ms: number | null; cpu: number | null; mem_mb: number | null;
}

export interface RecentPlayer { name: string; last_join: number; online: number; sessions: number; playtime: number }

export type TaskKind = "restart" | "stop" | "start" | "command" | "broadcast";
export type TaskSchedule = "daily" | "interval" | "once";
export interface Task {
  id: number; name: string; kind: TaskKind; payload: string; schedule: TaskSchedule;
  at_time: string | null; days: number; every_min: number | null; run_at: number | null;
  warn_minutes: number; enabled: number | boolean; next_run: number | null; last_run: number | null;
  running?: boolean; last?: { ts: number; ok: number; output: string } | null;
}
export type TaskInput = Omit<Task, "id" | "next_run" | "last_run" | "running" | "last" | "enabled"> & { enabled: boolean };

export interface AuditRow { id: number; ts: number; username: string | null; ip: string | null; action: string; detail: string | null }
export interface SessionRow { id: number; created_at: number; last_seen: number; ip: string | null; user_agent: string | null; current: boolean }
export interface UserRow { id: number; username: string; role: Role; created_at: number; last_login: number | null; devices: number }

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) { onUnauthorized = fn; }

async function req<T>(method: string, url: string, body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method,
    credentials: "same-origin",
    headers: { ...(body !== undefined ? { "Content-Type": "application/json" } : {}), ...(method !== "GET" ? { "X-MCM": "1" } : {}) },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && !url.endsWith("/login")) onUnauthorized();
  const text = await res.text();
  const data = text ? (() => { try { return JSON.parse(text); } catch { return text; } })() : null;
  if (!res.ok) {
    const msg = typeof data === "object" && data && "detail" in data
      ? (typeof data.detail === "string" ? data.detail : "입력값을 확인하세요")
      : `요청 실패 (${res.status})`;
    throw new ApiError(res.status, msg);
  }
  return data as T;
}

export const api = {
  login: (username: string, password: string) => req<Me>("POST", "/api/auth/login", { username, password }),
  logout: () => req("POST", "/api/auth/logout"),
  me: () => req<Me>("GET", "/api/auth/me"),
  changePassword: (current: string, next: string) => req("POST", "/api/auth/password", { current, new: next }),
  sessions: () => req<SessionRow[]>("GET", "/api/auth/sessions"),
  revokeSession: (id: number) => req("DELETE", `/api/auth/sessions/${id}`),

  status: () => req<Status>("GET", "/api/status"),
  power: (action: "start" | "stop" | "restart") => req<{ ok: boolean; message: string }>("POST", `/api/server/${action}`),
  console: (command: string) => req<{ output: string }>("POST", "/api/console", { command }),
  logs: () => req<string[]>("GET", "/api/logs"),

  players: () => req<{ online: string[]; recent: RecentPlayer[] }>("GET", "/api/players"),
  playerAction: (name: string, act: "kick" | "ban" | "pardon" | "op" | "deop", reason = "") =>
    req<{ output: string }>("POST", `/api/players/${encodeURIComponent(name)}/${act}`, { reason }),

  metrics: (range: string) => req<MetricPoint[]>("GET", `/api/metrics?range=${range}`),
  audit: () => req<AuditRow[]>("GET", "/api/audit?limit=300"),

  tasks: () => req<Task[]>("GET", "/api/tasks"),
  addTask: (t: TaskInput) => req<Task>("POST", "/api/tasks", t),
  editTask: (id: number, t: TaskInput) => req<Task>("PUT", `/api/tasks/${id}`, t),
  deleteTask: (id: number) => req("DELETE", `/api/tasks/${id}`),
  runTask: (id: number) => req<{ ok: boolean; output: string }>("POST", `/api/tasks/${id}/run`),

  users: () => req<UserRow[]>("GET", "/api/users"),
  addUser: (username: string, password: string, role: Role) => req("POST", "/api/users", { username, password, role }),
  setRole: (id: number, role: Role) => req("PUT", `/api/users/${id}`, { role }),
  deleteUser: (id: number) => req("DELETE", `/api/users/${id}`),
};

export const canOperate = (me: Me | null) => !!me && (me.role === "admin" || me.role === "operator");
