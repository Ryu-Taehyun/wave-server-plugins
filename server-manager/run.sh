#!/usr/bin/env bash
# 처음 한 번: 가상 환경 · 패키지 · 화면 빌드. 그 뒤로는 바로 실행
set -e
cd "$(dirname "$0")"
if [ ! -d .venv ]; then python3 -m venv .venv; fi
.venv/bin/pip install -q -r backend/requirements.txt
if [ ! -f frontend/dist/index.html ]; then (cd frontend && npm ci && npm run build); fi
cd backend
if [ ! -f manager.toml ]; then cp manager.example.toml manager.toml; echo "backend/manager.toml 을 만들었습니다. 서버 폴더 · 실행 명령 · RCON 비밀번호를 고친 뒤 다시 실행하세요."; exit 0; fi
exec ../.venv/bin/python -m mcmanager "${@:-run}"
