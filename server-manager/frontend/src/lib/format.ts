export function duration(sec: number | null | undefined): string {
  if (sec == null) return "—";
  const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60);
  if (d) return `${d}일 ${h}시간`;
  if (h) return `${h}시간 ${m}분`;
  if (m) return `${m}분`;
  return `${Math.max(0, Math.floor(sec))}초`;
}

export function ago(ts: number | null | undefined): string {
  if (!ts) return "—";
  const s = Math.floor(Date.now() / 1000 - ts);
  if (s < 10) return "방금";
  if (s < 60) return `${s}초 전`;
  if (s < 3600) return `${Math.floor(s / 60)}분 전`;
  if (s < 86400) return `${Math.floor(s / 3600)}시간 전`;
  if (s < 86400 * 30) return `${Math.floor(s / 86400)}일 전`;
  return new Date(ts * 1000).toLocaleDateString("ko-KR");
}

export function until(ts: number | null | undefined): string {
  if (!ts) return "—";
  const s = Math.floor(ts - Date.now() / 1000);
  if (s <= 0) return "곧";
  if (s < 60) return `${s}초 뒤`;
  if (s < 3600) return `${Math.floor(s / 60)}분 뒤`;
  if (s < 86400) return `${Math.floor(s / 3600)}시간 ${Math.floor((s % 3600) / 60)}분 뒤`;
  return `${Math.floor(s / 86400)}일 뒤`;
}

export function clock(ts: number): string {
  return new Date(ts * 1000).toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function device(ua: string | null): string {
  if (!ua) return "알 수 없는 기기";
  const os = /iPhone/.test(ua) ? "iPhone" : /iPad/.test(ua) ? "iPad" : /Android/.test(ua) ? "Android" : /Mac OS X/.test(ua) ? "Mac"
    : /Windows/.test(ua) ? "Windows" : /Linux/.test(ua) ? "Linux" : "기기";
  const br = /Edg\//.test(ua) ? "Edge" : /Chrome\//.test(ua) ? "Chrome" : /Firefox\//.test(ua) ? "Firefox" : /Safari\//.test(ua) ? "Safari" : "브라우저";
  return `${os} · ${br}`;
}

export const STATE_LABEL: Record<string, { text: string; c: string }> = {
  running: { text: "실행 중", c: "var(--green)" },
  starting: { text: "켜는 중", c: "var(--orange)" },
  stopping: { text: "끄는 중", c: "var(--orange)" },
  stopped: { text: "꺼짐", c: "var(--text-3)" },
  crashed: { text: "비정상 종료", c: "var(--red)" },
};

export const ACTION_LABEL: Record<string, string> = {
  login: "로그인", "login.fail": "로그인 실패", "server.start": "서버 켜기", "server.stop": "서버 끄기", "server.restart": "서버 재시작",
  console: "콘솔 명령", "player.kick": "강퇴", "player.ban": "차단", "player.pardon": "차단 해제", "player.op": "OP 주기", "player.deop": "OP 빼기",
  "task.add": "작업 추가", "task.edit": "작업 수정", "task.delete": "작업 삭제", "task.run": "작업 바로 실행",
  "task.restart": "예약 재시작", "task.stop": "예약 끄기", "task.start": "예약 켜기", "task.command": "예약 명령", "task.broadcast": "예약 공지",
  "user.add": "사용자 추가", "user.role": "권한 변경", "user.delete": "사용자 삭제", "password.change": "비밀번호 변경", "session.revoke": "기기 로그아웃",
};

export const ROLE_LABEL = { admin: "관리자", operator: "운영자", viewer: "보기 전용" } as const;
