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
-- kind: restart · stop · start · command(콘솔 명령) · broadcast(전체 공지) · backup(백업)
-- schedule: daily(매일 at_time, days 요일 비트: 월=1 … 일=64) · interval(every_min 분마다) · once(run_at 한 번)
CREATE TABLE IF NOT EXISTS tasks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    kind         TEXT    NOT NULL CHECK (kind IN ('restart', 'stop', 'start', 'command', 'broadcast', 'backup')),
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

-- 키 · 값 설정 (알림 설정, 웹 푸시 키 등)
CREATE TABLE IF NOT EXISTS settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- 백업 (kind: auto 예약 · manual 직접 · safety 복원 직전 자동)
CREATE TABLE IF NOT EXISTS backups (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    ts       INTEGER NOT NULL,
    file     TEXT    NOT NULL,
    size     INTEGER NOT NULL DEFAULT 0,
    ok       INTEGER NOT NULL DEFAULT 1,
    kind     TEXT    NOT NULL DEFAULT 'manual',
    note     TEXT,
    created_by TEXT
);
CREATE INDEX IF NOT EXISTS idx_backups_ts ON backups(ts);

-- 웹 푸시 구독 (기기마다)
CREATE TABLE IF NOT EXISTS push_subs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    endpoint   TEXT    NOT NULL UNIQUE,
    p256dh     TEXT    NOT NULL,
    auth       TEXT    NOT NULL,
    user_agent TEXT,
    created_at INTEGER NOT NULL
);

-- 보낸 알림
CREATE TABLE IF NOT EXISTS alerts (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    ts    INTEGER NOT NULL,
    kind  TEXT    NOT NULL,
    title TEXT    NOT NULL,
    body  TEXT
);
CREATE INDEX IF NOT EXISTS idx_alerts_ts ON alerts(ts);

-- 설정 파일 수정 기록 (이전 내용은 data/file-history 에 저장)
CREATE TABLE IF NOT EXISTS file_history (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    ts        INTEGER NOT NULL,
    path      TEXT    NOT NULL,
    saved_by  TEXT,
    snapshot  TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_file_history_path ON file_history(path, ts);

-- 채팅 기록 (channel: global · local · team · whisper · admin, flagged: 금지어 · 광고 감지)
CREATE TABLE IF NOT EXISTS chat (
    id       INTEGER PRIMARY KEY AUTOINCREMENT,
    ts       INTEGER NOT NULL,
    name     TEXT    NOT NULL COLLATE NOCASE,
    channel  TEXT    NOT NULL DEFAULT 'global',
    to_name  TEXT,
    message  TEXT    NOT NULL,
    flagged  TEXT
);
CREATE INDEX IF NOT EXISTS idx_chat_ts ON chat(ts);
CREATE INDEX IF NOT EXISTS idx_chat_name ON chat(name, ts);

-- 신고 (/report 를 로그에서 읽음)
CREATE TABLE IF NOT EXISTS reports (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    ts         INTEGER NOT NULL,
    reporter   TEXT    NOT NULL,
    target     TEXT,
    reason     TEXT,
    status     TEXT    NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'done', 'dismissed')),
    handled_by TEXT,
    handled_at INTEGER,
    note       TEXT
);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status, ts);
