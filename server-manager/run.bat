@echo off
rem 처음 한 번: 가상 환경 · 패키지 · 화면 빌드. 그 뒤로는 바로 실행
cd /d "%~dp0"
if not exist .venv ( py -3 -m venv .venv )
.venv\Scripts\pip install -q -r backend\requirements.txt
if not exist frontend\dist\index.html ( pushd frontend & call npm ci & call npm run build & popd )
cd backend
if not exist manager.toml ( copy manager.example.toml manager.toml >nul & echo backend\manager.toml 을 만들었습니다. 고친 뒤 다시 실행하세요. & exit /b 0 )
if "%~1"=="" ( ..\.venv\Scripts\python -m mcmanager run ) else ( ..\.venv\Scripts\python -m mcmanager %* )
