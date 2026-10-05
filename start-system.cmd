@echo off
chcp 65001 >nul
cd /d "%~dp0"
where docker >nul 2>nul
if errorlevel 1 (
  echo 未找到 Docker Desktop，请先安装并启动 Docker Desktop。
  pause
  exit /b 1
)
docker compose --env-file .env.docker up -d --build
if errorlevel 1 (
  echo 启动失败，请把上面的错误信息发给维护人员。
  pause
  exit /b 1
)
echo 系统已经启动。请在浏览器打开：http://127.0.0.1
pause
