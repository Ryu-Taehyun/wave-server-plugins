# 마인크래프트 서버 관리 도구

폰 · 노트북 등 **어느 기기에서든 로그인해서** 서버를 켜고 끄고, 접속 상태를 확인하는 웹 대시보드입니다.
iOS **Liquid Glass** 디자인 (SF Pro / Pretendard 글꼴), 홈 화면에 앱처럼 설치할 수 있습니다.

| 부분 | 기술 |
|---|---|
| 백엔드 | **Python** (FastAPI) · RCON 프로토콜 · 서버 목록 핑(SLP) 직접 구현 · 서버 프로세스 관리 · 예약 작업 |
| DB | **SQL** (SQLite, `backend/mcmanager/schema.sql`) · 사용자 · 기기 세션 · 감사 기록 · 상태 기록 · 접속 기록 · 작업 |
| 화면 | **React + TypeScript + Motion** (`frontend/`) |
| 앱 설치 | **JavaScript** 서비스 워커 (`frontend/public/sw.js`) |

## 기능
- **개요**: 실행 상태 · 접속 가능 여부 · 접속자 · TPS · 응답 속도 · CPU · 메모리, 24시간 가동률 · 최고 동접, 접속자 · TPS 그래프
- **전원**: 켜기 / 재시작 / 끄기 (되돌리기 어려운 동작은 **길게 눌러** 실행), 크래시 시 자동 재시작 (10분에 3번까지)
- **콘솔**: 실시간 로그 (검색 · 색 구분) · RCON 명령 · 자주 쓰는 명령 버튼 · 명령 기록(↑ ↓)
- **플레이어**: 지금 접속 중 · 최근 접속 기록(총 플레이 시간 · 접속 횟수) · 강퇴 · 차단 · 차단 해제 · OP
- **예약 작업**: 매일 정해진 시각 · N분마다 · 한 번 — 재시작 · 끄기 · 켜기 · 명령어 · 공지. 재시작 전 게임 안 카운트다운(5분 · 1분 · 30초 · 10초 …)
- **운영 (WaveCore)**: 서버 전체 캐시 · 유료 캐시 · 부의 제왕 / 킬의 제왕 / 레벨 TOP 10, 플레이어 찾기(레벨 · 캐시 · 출석 · 제재 기록), 핫타임 바로 시작 · 끝내기, 전체 공지, 시즌 번호, **점검 모드 · 화이트리스트** 스위치
- **백업 · 복원**: 월드 · 플러그인 · 설정을 zip 으로 (켜진 채로도 save-off 후 안전하게), 예약 백업 · 보관 개수, **복원** (서버 끄기 → 지금 상태 먼저 백업 → 복원 → 다시 켜기), 내려받기, 다른 폴더에 사본
- **알림**: 디스코드 웹훅 + 휴대폰 푸시 — 크래시 · 예상하지 못한 꺼짐 · TPS 낮음 · 디스크 부족 · 백업 실패 · 새 기기 로그인 · 로그인 실패 반복 (종류별 켜기 · 기준 조절)
- **파일**: 설정 파일 편집 (저장 전 형식 검사 — **YAML 같은 키 두 번**까지 잡음, 바뀐 곳 보기, 이전 저장본 자동 보관 · 되돌리기), 플러그인 jar 올리기 · 끄기 · 삭제를 모아 두었다가 **서버를 끄고 → 바꾸고 → 켜서** 적용
- **진단**: 컴퓨터 CPU · 메모리 · 디스크, 요일 × 시간대별 접속자 히트맵, 로그의 오류를 묶어 **원인 플러그인 추정**, 크래시 리포트
- **보안**: 비밀번호 scrypt 해시 · 기기별 로그인(다른 기기 원격 로그아웃) · 로그인 시도 제한(10분 8번) · 권한 3단계 · 모든 조작 감사 기록 · 다른 사이트가 몰래 보내는 요청 차단
- **실시간**: WebSocket 으로 상태 · 로그 · 알림 (끊기면 자동 다시 연결)

## 1. 서버 준비 (server.properties)
```properties
enable-rcon=true
rcon.port=25575
rcon.password=길고-어려운-비밀번호
broadcast-rcon-to-ops=false
```
RCON 포트(25575)는 **외부에 열지 마세요** (이 도구가 같은 컴퓨터에서 접속합니다).

## 2. 설치 · 실행
필요한 것: Python 3.11 이상, Node.js 20 이상 (화면 빌드 한 번), Java (마인크래프트 서버)

```bash
./run.sh                     # 처음: backend/manager.toml 을 만들고 멈춤 → 고친 뒤 다시
./run.sh create-user admin   # 관리자 만들기 (비밀번호 입력)
./run.sh                     # 실행 → http://<이 컴퓨터 IP>:8080
```
Windows 는 `run.bat` (같은 사용법, 명령 프롬프트에서 `run.bat create-user admin`).
- Python 3.11 이상이 필요합니다 (설치할 때 **Add python.exe to PATH** 체크).
- 배포용 zip 에는 화면(`frontend/dist`)이 미리 빌드되어 있어 **Node.js · npm 이 필요 없습니다.** (저장소에서 받았을 때만 npm 으로 빌드)
- Java 경로에 공백이 있으면 따옴표로 감싸세요: `start_command = '"C:\Program Files\Java\jdk-21\bin\java.exe" -Xmx8G -jar purpur.jar nogui'`

`backend/manager.toml` 에서 바꿀 것:
- `[server] dir` 서버 폴더, `start_command` 실행 명령 (예: `java -Xms4G -Xmx8G -jar purpur.jar nogui`)
- `[rcon] password` (또는 환경 변수 `MCM_RCON_PASSWORD`)

이미 켜져 있는 서버도 찾아서 상태 · 로그(`logs/latest.log`)를 보여 주고, RCON 으로 끄기 · 재시작할 수 있습니다.
CPU · 메모리는 이 도구가 켠 서버만 측정합니다.

휴대폰 **푸시 알림**은 HTTPS 주소(또는 localhost)에서만 동작합니다. iPhone 은 Safari 로 접속 → 공유 → 홈 화면에 추가 → 그 앱에서 설정 → 알림 → "이 기기로 푸시 알림" 을 켜세요.

## 3. 다른 기기에서 접속하기
- **같은 와이파이**: `http://<서버 컴퓨터 IP>:8080`
- **밖에서 (추천 순)**
  1. **Tailscale** 같은 VPN: 포트를 열 필요 없이 내 기기끼리만 접속
  2. **Cloudflare Tunnel** 등으로 HTTPS 주소 연결 → `manager.toml` 에서 `secure_cookies = true`, `trusted_proxy = true`
  3. 공유기 포트 포워딩은 **HTTPS 없이 쓰지 마세요** (비밀번호가 그대로 전송됨)
- **홈 화면에 추가**: iPhone Safari → 공유 → 홈 화면에 추가 / Android Chrome → 앱 설치 (HTTPS 에서 오프라인 화면 캐시)

## 4. 권한
| 권한 | 할 수 있는 것 |
|---|---|
| 관리자 | 전부 + 사용자 추가 · 삭제 · 권한 변경 |
| 운영자 | 서버 켜기 · 끄기 · 재시작, 콘솔, 플레이어 관리, 예약 작업 |
| 보기 전용 | 상태 · 그래프 · 로그 · 기록 보기만 |

명령어: `create-user <아이디> [--role admin|operator|viewer]`, `set-password <아이디>`, `users`

## 5. 바로 써 보기 (데모)
실제 서버 없이 가짜 서버(RCON · 핑 · 로그 · 플레이어 입장/퇴장 흉내)로 실행:
```bash
cd backend && python -m mcmanager demo    # http://localhost:8080 · demo / demo1234
```

## 6. 항상 켜 두기 (Linux systemd 예시)
```ini
# /etc/systemd/system/mc-manager.service
[Unit]
Description=Minecraft server manager
After=network.target

[Service]
User=mc
WorkingDirectory=/home/mc/server-manager/backend
ExecStart=/home/mc/server-manager/.venv/bin/python -m mcmanager run
Restart=on-failure

[Install]
WantedBy=multi-user.target
```
`sudo systemctl enable --now mc-manager` — 관리 도구를 다시 켜도 마인크래프트 서버는 꺼지지 않습니다(따로 실행됨).

## 개발
```bash
cd backend && pip install -r requirements-dev.txt && python -m pytest -q   # 테스트 (가짜 서버로 켜기 · 끄기 · RCON · 핑 · 로그인 · 권한)
cd frontend && npm install && MCM_API=http://127.0.0.1:8080 npm run dev      # 화면 개발 (API 는 백엔드로 넘김)
```
