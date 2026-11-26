-- Minecraft 서버 관리 도구 DB (SQLite)
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- 사용자: role = admin(모든 권한) · operator(서버 조작 · 콘솔 · 예약 작업) · viewer(보기만)
CREATE TABLE IF NOT EXISTS users (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    pw_hash     TEXT    NOT NULL,
    role        TEXT    NOT NULL DEFAULT 'viewer' CHECK (role IN ('admin', 'operator', 'viewer')),
    created_at  INTEGER NOT NULL,
    last_login  INTEGER
);

-- 로그인한 기기 (토큰은 SHA-256 해시로만 저장)
CREATE TABLE IF NOT EXISTS sessions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    token_hash  TEXT    NOT NULL UNIQUE,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  INTEGER NOT NULL,
    last_seen   INTEGER NOT NULL,
    expires_at  INTEGER NOT NULL,
    ip          TEXT,
    user_agent  TEXT,
    revoked     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);

-- 로그인 시도 (무차별 대입 막기)
CREATE TABLE IF NOT EXISTS login_attempts (
    ts       INTEGER NOT NULL,
    ip       TEXT    NOT NULL,
    username TEXT,
    success  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_attempts_ip_ts ON login_attempts(ip, ts);

-- 감사 기록: 누가 언제 어디서 무엇을 했는지
CREATE TABLE IF NOT EXISTS audit (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    ts       INTEGER NOT NULL,
    username TEXT,
    ip       TEXT,
    action   TEXT    NOT NULL,
    detail   TEXT
);
CREATE INDEX IF NOT EXISTS idx_audit_ts ON audit(ts);

-- 서버 상태 기록 (몇 초마다 한 줄)
CREATE TABLE IF NOT EXISTS metrics (
    ts          INTEGER NOT NULL,
    online      INTEGER NOT NULL,
    players     INTEGER NOT NULL DEFAULT 0,
    max_players INTEGER NOT NULL DEFAULT 0,
    tps         REAL,
    latency_ms  REAL,
    cpu         REAL,
    mem_mb      REAL
);
CREATE INDEX IF NOT EXISTS idx_metrics_ts ON metrics(ts);

-- 플레이어 접속 기록 (left_at 이 NULL 이면 지금 접속 중)
CREATE TABLE IF NOT EXISTS player_sessions (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT    NOT NULL COLLATE NOCASE,
    joined_at INTEGER NOT NULL,
    left_at   INTEGER
);
CREATE INDEX IF NOT EXISTS idx_player_sessions_name ON player_sessions(name);
CREATE INDEX IF NOT EXISTS idx_player_sessions_open ON player_sessions(left_at);

-- 예약 작업
-- kind: restart · stop · start · command(콘솔 명령) · broadcast(전체 공지)
-- schedule: daily(매일 at_time, days 요일 비트: 월=1 … 일=64) · interval(every_min 분마다) · once(run_at 한 번)
CREATE TABLE IF NOT EXISTS tasks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    kind         TEXT    NOT NULL CHECK (kind IN ('restart', 'stop', 'start', 'command', 'broadcast')),
    payload      TEXT    NOT NULL DEFAULT '',
    schedule     TEXT    NOT NULL CHECK (schedule IN ('daily', 'interval', 'once')),
    at_time      TEXT,
    days         INTEGER NOT NULL DEFAULT 127,
    every_min    INTEGER,
    run_at       INTEGER,
    warn_minutes INTEGER NOT NULL DEFAULT 0,
    enabled      INTEGER NOT NULL DEFAULT 1,
    next_run     INTEGER,
    last_run     INTEGER,
    created_by   TEXT
);

CREATE TABLE IF NOT EXISTS task_runs (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id INTEGER REFERENCES tasks(id) ON DELETE CASCADE,
    ts      INTEGER NOT NULL,
    ok      INTEGER NOT NULL,
    output  TEXT
);
CREATE INDEX IF NOT EXISTS idx_task_runs_task ON task_runs(task_id, ts);
