#!/usr/bin/env bash
set -euo pipefail

SERVER_HOST="${XQT_DB_SERVER_HOST:-}"
SERVER_USER="${XQT_DB_SERVER_USER:-root}"
SSH_KEY="${XQT_DB_SSH_KEY:-$HOME/.ssh/id_ed25519}"
LOCAL_PORT="${XQT_DB_LOCAL_PORT:-15433}"
REMOTE_HOST="${XQT_DB_REMOTE_HOST:-127.0.0.1}"
REMOTE_PORT="${XQT_DB_REMOTE_PORT:-5432}"

if [[ -z "${SERVER_HOST}" ]]; then
  echo "XQT_DB_SERVER_HOST is required" >&2
  exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"${LOCAL_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "server database tunnel already listening on 127.0.0.1:${LOCAL_PORT}"
  exit 0
fi

ssh \
  -i "${SSH_KEY}" \
  -o BatchMode=yes \
  -o ExitOnForwardFailure=yes \
  -f -N \
  -L "${LOCAL_PORT}:${REMOTE_HOST}:${REMOTE_PORT}" \
  "${SERVER_USER}@${SERVER_HOST}"

echo "server database tunnel ready: 127.0.0.1:${LOCAL_PORT} -> ${SERVER_HOST}:${REMOTE_PORT}"
