#!/bin/sh
set -eu

export HOME=/home/app
export CODEX_HOME="${CODEX_HOME:-/home/app/.codex}"
export XDG_CACHE_HOME="${XDG_CACHE_HOME:-$CODEX_HOME/cache}"
export XDG_CONFIG_HOME="${XDG_CONFIG_HOME:-$CODEX_HOME/config}"
export XDG_DATA_HOME="${XDG_DATA_HOME:-$CODEX_HOME/data}"
export XDG_STATE_HOME="${XDG_STATE_HOME:-$CODEX_HOME/state}"
export TMPDIR="${TMPDIR:-$CODEX_HOME/tmp}"
export NODE_TLS_REJECT_UNAUTHORIZED="${NODE_TLS_REJECT_UNAUTHORIZED:-0}"

mkdir -p /app/data /home/app/.codex
rm -rf "$CODEX_HOME/tmp/arg0" "$TMPDIR/arg0"
mkdir -p "$XDG_CACHE_HOME" "$XDG_CONFIG_HOME" "$XDG_DATA_HOME" "$XDG_STATE_HOME" "$TMPDIR"
chown -R app:app /app/data /home/app/.codex "$TMPDIR"

exec su -m app -s /bin/sh -c 'exec /opt/java/openjdk/bin/java -jar /app/news-wiki.jar'
