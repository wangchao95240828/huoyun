#!/usr/bin/env bash
set -euo pipefail

SERVER_HOST="${XQT_DB_SERVER_HOST:-}"
SERVER_USER="${XQT_DB_SERVER_USER:-root}"
SSH_KEY="${XQT_DB_SSH_KEY:-$HOME/.ssh/id_ed25519}"
REMOTE_ENV="${XQT_DB_REMOTE_ENV:-/root/xqt_saas_db.env}"
LOCAL_ENV="${XQT_DB_ENV_FILE:-/tmp/xqt_saas_db.env}"

if [[ -z "${SERVER_HOST}" ]]; then
  echo "XQT_DB_SERVER_HOST is required" >&2
  exit 1
fi

scp -i "${SSH_KEY}" -o BatchMode=yes "${SERVER_USER}@${SERVER_HOST}:${REMOTE_ENV}" "${LOCAL_ENV}"
chmod 600 "${LOCAL_ENV}"

echo "server database env fetched to ${LOCAL_ENV}"
