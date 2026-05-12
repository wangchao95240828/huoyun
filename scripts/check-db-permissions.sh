#!/usr/bin/env bash
# 一键执行权限自检脚本。需要：本地能 ssh 到数据库服务器，或本地隧道已建立。
# 三种用法：
#   1. 通过 SSH 隧道（推荐，需要 XQT_DB_SERVER_HOST）
#        XQT_DB_SERVER_HOST=1.2.3.4 ./scripts/check-db-permissions.sh
#   2. 直接对本地 PostgreSQL（先 docker compose up -d）
#        ./scripts/check-db-permissions.sh --local
#   3. 直接指定连接串
#        DB_URL='postgres://xqt:pwd@host:port/xqt_saas' ./scripts/check-db-permissions.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL_FILE="${ROOT_DIR}/scripts/check-db-permissions.sql"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql 未安装。建议：brew install libpq && brew link --force libpq" >&2
  echo "或在服务器上直接执行：" >&2
  echo "  psql -U xqt -d xqt_saas -f $SQL_FILE" >&2
  exit 2
fi

mode="${1:-tunnel}"
case "$mode" in
  --local)
    PGHOST=127.0.0.1 PGPORT=15432 PGUSER=xqt PGPASSWORD=xqt_dev_password PGDATABASE=xqt_saas \
      psql -f "$SQL_FILE"
    ;;
  --tunnel|tunnel)
    "${ROOT_DIR}/scripts/server-db-tunnel.sh"
    ENV_FILE="${XQT_DB_ENV_FILE:-/tmp/xqt_saas_db.env}"
    if [[ ! -f "$ENV_FILE" ]]; then
      "${ROOT_DIR}/scripts/fetch-server-db-env.sh"
    fi
    set -a; source "$ENV_FILE"; set +a
    PGHOST=127.0.0.1 PGPORT="${XQT_DB_LOCAL_PORT:-15433}" \
      PGUSER="${SPRING_DATASOURCE_USERNAME:-xqt}" \
      PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-}" \
      PGDATABASE=xqt_saas \
      psql -f "$SQL_FILE"
    ;;
  *)
    echo "unknown mode: $mode" >&2
    exit 1
    ;;
esac
