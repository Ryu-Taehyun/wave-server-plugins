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
  "backup.create": "백업 만들기", "backup.restore": "백업 복원", "backup.delete": "백업 삭제", "backup.download": "백업 내려받기",
  "file.save": "파일 저장", "plugin.upload": "플러그인 올리기", "plugin.apply": "플러그인 적용", "notify.settings": "알림 설정",
  "wave.hottime": "핫타임", "wave.season": "시즌 번호", "wave.maintenance": "점검 모드", whitelist: "화이트리스트", broadcast: "전체 공지", "task.backup": "예약 백업",
  "user.add": "사용자 추가", "user.role": "권한 변경", "user.delete": "사용자 삭제", "password.change": "비밀번호 변경", "session.revoke": "기기 로그아웃",
};

export const ROLE_LABEL = { admin: "관리자", operator: "운영자", viewer: "보기 전용" } as const;

export function bytes(n: number | null | undefined): string {
  if (n == null) return "—";
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1073741824) return `${(n / 1048576).toFixed(1)} MB`;
  return `${(n / 1073741824).toFixed(2)} GB`;
}

export const won = (n: number | null | undefined) => (n == null ? "—" : Math.round(n).toLocaleString("ko-KR"));

/** 줄 단위 비교 (LCS). 반환: [종류, 줄] 목록  ' ' 같음 · '-' 지움 · '+' 추가 */
export function lineDiff(a: string, b: string): [" " | "-" | "+", string][] {
  const A = a.split("\n"), B = b.split("\n");
  if (A.length * B.length > 4_000_000) return [["-", `(${A.length}줄)`], ["+", `(${B.length}줄) 파일이 커서 비교를 생략합니다`]];
  const n = A.length, m = B.length;
  const dp = Array.from({ length: n + 1 }, () => new Uint32Array(m + 1));
  for (let i = n - 1; i >= 0; i--) for (let j = m - 1; j >= 0; j--) dp[i][j] = A[i] === B[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
  const out: [" " | "-" | "+", string][] = [];
  let i = 0, j = 0;
  while (i < n && j < m) {
    if (A[i] === B[j]) { out.push([" ", A[i]]); i++; j++; }
    else if (dp[i + 1][j] >= dp[i][j + 1]) out.push(["-", A[i++]]);
    else out.push(["+", B[j++]]);
  }
  while (i < n) out.push(["-", A[i++]]);
  while (j < m) out.push(["+", B[j++]]);
  return out;
}
