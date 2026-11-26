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
  sys_cpu?: number; sys_mem?: number; mem_total_gb?: number; mem_used_gb?: number; disk_free_gb?: number; disk_total_gb?: number;
}

export interface Backup { id: number; ts: number; file: string; size: number; ok: number; kind: "auto" | "manual" | "safety"; note: string | null; created_by: string | null; exists: boolean }
export interface BackupInfo { items: Backup[]; disk: { backups_bytes: number; free_bytes: number; total_bytes: number }; busy: string | null; include: string[]; keep: number }

export interface NotifySettings {
  tps_below: number; tps_minutes: number; disk_below_gb: number; down_minutes: number;
  events: Record<string, boolean>; kinds: Record<string, string>;
  discord_set: boolean; discord_hint: string; push_ready: boolean; vapid_public: string | null;
  my_devices: { id: number; user_agent: string | null; created_at: number }[];
}
export interface AlertRow { id: number; ts: number; kind: string; title: string; body: string | null }

export interface FileEntry { path: string; size: number; mtime: number }
export interface FileDoc { path: string; text: string; mtime: number; problem: string | null }
export interface PluginJar { file: string; size: number; mtime: number; name: string; version: string; main: string; broken?: boolean }
export interface Staged { op: "install" | "disable" | "enable" | "remove"; file: string; name?: string; version?: string; replaces?: string[] }

export interface Ranked { name: string; value: number; level: number }
export interface WaveOverview {
  available: boolean; players: number; active_7d: number; achievements: number;
  economy: { total: number; avg: number; paid_total: number; richest: number };
  top_money: Ranked[]; top_kills: Ranked[]; top_level: Ranked[];
  season: number | null; maintenance: boolean | null;
  hot_time: { enabled?: boolean; multiplier?: number; schedule?: string[]; status: { active: boolean | null; left?: string | null; multiplier?: number | null; text?: string } };
  whitelist: { on: boolean | null; names: string[] };
}
export interface WavePlayer {
  name: string; uuid: string; money?: number; "money-total"?: number; exmoney?: number; level?: number; exp?: number; kills?: number;
  "attend-streak"?: number; "attend-total"?: number; warnings?: number; "last-seen"?: number; titles?: string[]; active_title_text?: string;
  sanctions: string[]; achievements: number; stats?: Record<string, number>; sessions: { joined_at: number; left_at: number | null }[];
}
export interface ErrorGroup { plugin: string | null; exception: string; message: string; level: string; count: number; last: string; sample: string[] }
export interface Diagnostics {
  system: { sys_cpu?: number; sys_mem?: number; mem_total_gb?: number; mem_used_gb?: number; disk_free_gb?: number; disk_total_gb?: number };
  heatmap: { dow: number; hour: number; avg: number; peak: number }[];
  errors: ErrorGroup[]; crashes: { file: string; mtime: number; description: string }[];
}

export interface BanRow { uuid: string; name: string; reason: string; code: string; until: number; by: string; at: number }
export interface SanctionLog { time: string; kind: string; name: string; detail: string; by: string }
export interface SanctionOverview {
  bans: BanRow[]; mutes: { name: string; until: number; reason: string }[]; warnings: { name: string; warnings: number; last: string }[];
  history: SanctionLog[]; reports_open: number;
}
export type SanctionAction = "warn" | "warn_clear" | "tempban" | "ban" | "unban" | "mute" | "unmute" | "kick";
export interface Report { id: number; ts: number; reporter: string; target: string | null; reason: string | null; status: "open" | "done" | "dismissed"; handled_by: string | null; handled_at: number | null; note: string | null }
export type ChatChannel = "global" | "local" | "team" | "whisper" | "admin";
export interface ChatRow { id: number; ts: number; name: string; channel: ChatChannel; to_name: string | null; message: string; flagged: string | null }
export interface ChatSettings { words: string[]; block_ads: boolean; allow_domains: string[] }
export interface BuildInfo { project: string; version: string; build: number; time: number; changes: string[]; hash: string | null; algo: string; file: string; channel?: string }
export interface UpdateStatus {
  settings: { project: "purpur" | "paper"; version: string; auto_check: boolean; backup_first: boolean };
  current: { project: string | null; mc: string | null; build: number | null; raw: string | null; jar: string | null; jar_exists?: boolean; jar_size?: number; jar_mtime?: number; jar_error?: string };
  last_check: { ts: number; latest: BuildInfo; available: boolean | null } | null;
  busy: string | null;
  backups: { file: string; size: number; ts: number; project?: string; mc?: string; build?: number; raw?: string; by?: string }[];
  history: AuditRow[];
}

export interface MetricPoint {
  t: number; uptime_pct: number; players: number; players_peak: number;
  tps: number | null; latency_ms: number | null; cpu: number | null; mem_mb: number | null;
}

export interface RecentPlayer { name: string; last_join: number; online: number; sessions: number; playtime: number }

export type TaskKind = "restart" | "stop" | "start" | "command" | "broadcast" | "backup";
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

  backups: () => req<BackupInfo>("GET", "/api/backups"),
  createBackup: (note: string) => req("POST", "/api/backups", { note }),
  restoreBackup: (id: number) => req("POST", `/api/backups/${id}/restore`),
  deleteBackup: (id: number) => req("DELETE", `/api/backups/${id}`),

  notify: () => req<NotifySettings>("GET", "/api/notify"),
  saveNotify: (s: Partial<NotifySettings> & { discord_webhook?: string }) => req<NotifySettings>("PUT", "/api/notify", s),
  testNotify: () => req("POST", "/api/notify/test"),
  pushSubscribe: (sub: PushSubscriptionJSON) => req("POST", "/api/push/subscribe", sub),
  pushUnsubscribe: (sub: PushSubscriptionJSON) => req("POST", "/api/push/unsubscribe", sub),
  alerts: () => req<AlertRow[]>("GET", "/api/alerts"),

  files: () => req<FileEntry[]>("GET", "/api/files"),
  readFile: (path: string) => req<FileDoc>("GET", `/api/files/read?path=${encodeURIComponent(path)}`),
  validateFile: (path: string, text: string) => req<{ problem: string | null }>("POST", "/api/files/validate", { path, text }),
  saveFile: (path: string, text: string, mtime: number, force = false) => req<{ ok: boolean; mtime: number }>("PUT", "/api/files", { path, text, mtime, force }),
  fileHistory: (path: string) => req<{ id: number; ts: number; saved_by: string }[]>("GET", `/api/files/history?path=${encodeURIComponent(path)}`),
  fileHistoryText: (id: number) => req<{ path: string; ts: number; text: string }>("GET", `/api/files/history/${id}`),

  plugins: () => req<{ active: PluginJar[]; disabled: PluginJar[]; staged: Staged[] }>("GET", "/api/plugins"),
  uploadPlugin: async (file: File) => {
    const res = await fetch(`/api/plugins/upload?filename=${encodeURIComponent(file.name)}`, { method: "POST", headers: { "X-MCM": "1" }, body: file, credentials: "same-origin" });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new ApiError(res.status, data.detail || "올리지 못했습니다");
    return data as Staged;
  },
  stagePlugin: (op: Staged["op"], file: string) => req("POST", "/api/plugins/stage", { op, file }),
  unstagePlugin: (file: string) => req("DELETE", `/api/plugins/stage?file=${encodeURIComponent(file)}`),
  applyPlugins: () => req("POST", "/api/plugins/apply"),

  wave: () => req<WaveOverview>("GET", "/api/wave"),
  wavePlayer: (name: string) => req<WavePlayer>("GET", `/api/wave/player/${encodeURIComponent(name)}`),
  hotTime: (action: "start" | "stop", minutes = 60, multiplier = 2) => req<{ output: string }>("POST", "/api/wave/hottime", { action, minutes, multiplier }),
  broadcast: (text: string) => req("POST", "/api/wave/broadcast", { text }),
  setSeason: (number: number) => req<{ ok: boolean; reloaded: boolean }>("POST", "/api/wave/season", { number }),
  maintenance: (on: boolean) => req<{ output: string }>("POST", "/api/wave/maintenance", { on }),
  whitelist: (action: "on" | "off" | "add" | "remove", name = "") => req<{ output: string }>("POST", "/api/whitelist", { action, name }),
  diagnostics: () => req<Diagnostics>("GET", "/api/diagnostics"),

  sanctions: () => req<SanctionOverview>("GET", "/api/sanctions"),
  sanction: (action: SanctionAction, name: string, opts: { duration?: string; reason?: string; count?: string } = {}) =>
    req<{ output: string }>("POST", "/api/sanctions", { action, name, ...opts }),
  reports: (status: "open" | "done" | "dismissed" | "all" = "open") => req<Report[]>("GET", `/api/reports?status=${status}`),
  setReport: (id: number, status: Report["status"], note = "") => req("PUT", `/api/reports/${id}`, { status, note }),

  chat: (p: { q?: string; name?: string; flagged?: boolean; before?: number; limit?: number } = {}) => {
    const qs = new URLSearchParams(Object.entries(p).filter(([, v]) => v !== undefined && v !== "" && v !== false).map(([k, v]) => [k, String(v)]));
    return req<ChatRow[]>("GET", `/api/chat?${qs}`);
  },
  say: (message: string, to = "") => req<ChatRow>("POST", "/api/chat", { message, to }),
  chatSettings: () => req<ChatSettings>("GET", "/api/chat/settings"),
  saveChatSettings: (s: Partial<ChatSettings>) => req<ChatSettings>("PUT", "/api/chat/settings", s),

  update: () => req<UpdateStatus>("GET", "/api/update"),
  checkUpdate: () => req<NonNullable<UpdateStatus["last_check"]>>("POST", "/api/update/check"),
  saveUpdateSettings: (s: Partial<UpdateStatus["settings"]>) => req("PUT", "/api/update/settings", s),
  installUpdate: () => req("POST", "/api/update/install"),
  rollback: (file?: string) => req("POST", "/api/update/rollback", { file: file ?? null }),

  users: () => req<UserRow[]>("GET", "/api/users"),
  addUser: (username: string, password: string, role: Role) => req("POST", "/api/users", { username, password, role }),
  setRole: (id: number, role: Role) => req("PUT", `/api/users/${id}`, { role }),
  deleteUser: (id: number) => req("DELETE", `/api/users/${id}`),
};

export const canOperate = (me: Me | null) => !!me && (me.role === "admin" || me.role === "operator");
