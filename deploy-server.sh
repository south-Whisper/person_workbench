#!/bin/sh

set -eu

cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "错误：服务器没有找到 Docker。请先安装 Docker。"
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "错误：服务器没有找到 Docker Compose。请安装 Docker Compose 插件。"
  exit 1
fi

if [ ! -f .env.docker ]; then
  echo "错误：缺少 .env.docker 配置文件。"
  echo "请先执行：cp .env.docker.example .env.docker"
  echo "然后按《服务器部署说明.md》填写服务器地址和三条随机密钥。"
  exit 1
fi

if grep -qE '服务器IP或域名|请改成|随机字符串' .env.docker; then
  echo "错误：.env.docker 里还有示例文字，请先填写真实服务器地址和随机密钥。"
  exit 1
fi

echo "[1/3] 正在检查部署配置……"
docker compose --env-file .env.docker config --quiet

target="${1:-all}"

echo "[2/3] 正在构建并启动：${target}……"
case "$target" in
  all)
    docker compose --env-file .env.docker up -d --build
    ;;
  backend)
    docker compose --env-file .env.docker up -d --build --no-deps backend
    ;;
  frontend)
    docker compose --env-file .env.docker up -d --build --no-deps frontend
    ;;
  *)
    echo "错误：只支持 all、backend 或 frontend。"
    echo "例如：./deploy-server.sh frontend"
    exit 1
    ;;
esac

echo "[3/3] 正在显示运行状态……"
docker compose --env-file .env.docker ps

echo
echo "部署命令已执行完成。"
echo "当 database、backend、frontend 都显示 healthy 后，即可用浏览器访问服务器。"
