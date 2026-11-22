#!/usr/bin/env bash
# Development launcher: loads .env into the environment and runs a demo.
# Usage: ./scripts/run.sh [template|data]   (default: template)
# WARNING: .env is sourced as a shell script. Treat it as trusted local
# configuration and never put untrusted commands in it.
set -euo pipefail

DEMO="${1:-template}"
case "$DEMO" in
    template) MAIN_CLASS="com.github.otaviojava.rag.app.App" ;;
    data)     MAIN_CLASS="com.github.otaviojava.rag.app.DataApp" ;;
    *) echo "Usage: $0 [template|data]" >&2; exit 2 ;;
esac

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

ENV_FILE="$PROJECT_ROOT/.env"
PLACEHOLDER="replace-with-your-openai-api-key"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Missing .env file. Run: cp .env.example .env and set RAG_CHAT_API_KEY." >&2
    exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ -z "${RAG_CHAT_API_KEY:-}" || "$RAG_CHAT_API_KEY" == "$PLACEHOLDER" ]]; then
    echo "RAG_CHAT_API_KEY is not set in .env (or still has the placeholder value)." >&2
    exit 1
fi

set +e
mvn -q compile exec:java -Ddemo.main="$MAIN_CLASS"
status=$?
exit "$status"
