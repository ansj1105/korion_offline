#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/seed_user1_offline_history.sql"

if [[ $# -ge 1 && -n "${1:-}" ]]; then
  DB_URL="$1"
elif [[ -n "${DATABASE_URL:-}" ]]; then
  DB_URL="${DATABASE_URL}"
else
  echo "usage: $0 <database-url>"
  echo "or set DATABASE_URL before running"
  exit 1
fi

psql "${DB_URL}" -f "${SQL_FILE}"
