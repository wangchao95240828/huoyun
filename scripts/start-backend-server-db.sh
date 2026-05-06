#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${XQT_DB_ENV_FILE:-/tmp/xqt_saas_db.env}"

"${ROOT_DIR}/scripts/server-db-tunnel.sh"

if [[ ! -f "${ENV_FILE}" ]]; then
  "${ROOT_DIR}/scripts/fetch-server-db-env.sh"
fi

set -a
source "${ENV_FILE}"
set +a

export API_PORT="${API_PORT:-18103}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:15433/xqt_saas}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-xqt}"
export JWT_SECRET="${JWT_SECRET:-local-server-db-dev-secret-change-me}"

cd "${ROOT_DIR}/apps/backend"
exec env -u DEBUG ./mvnw spring-boot:run
