#!/usr/bin/env bash
# DEVELOPMENT ONLY: deletes and recreates the Qdrant "policies" collection
# (384 dimensions, cosine). Other collections and the Docker volume are untouched.
# Usage: QDRANT_URL=http://localhost:6333 ./scripts/reset-qdrant.sh
set -euo pipefail

QDRANT_URL="${QDRANT_URL:-http://localhost:6333}"
COLLECTION="policies"
URL="$QDRANT_URL/collections/$COLLECTION"

# Prints the HTTP status code; fails clearly if Qdrant is unreachable.
http_status() {
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' "$@")" || {
        echo "Cannot connect to Qdrant at $QDRANT_URL" >&2
        exit 1
    }
    echo "$code"
}

status="$(http_status "$URL")"

case "$status" in
    200)
        echo "Collection '$COLLECTION' exists at $QDRANT_URL and will be DELETED with all its data."
        read -r -p "Type '$COLLECTION' to confirm: " answer
        if [[ "$answer" != "$COLLECTION" ]]; then
            echo "Aborted." >&2
            exit 1
        fi
        status="$(http_status -X DELETE "$URL")"
        [[ "$status" == "200" ]] || { echo "Delete failed (HTTP $status)" >&2; exit 1; }
        echo "Deleted collection '$COLLECTION'."
        ;;
    404)
        echo "Collection '$COLLECTION' does not exist; creating it."
        ;;
    *)
        echo "Unexpected response from Qdrant (HTTP $status)" >&2
        exit 1
        ;;
esac

status="$(http_status -X PUT "$URL" \
    -H 'Content-Type: application/json' \
    -d '{"vectors": {"size": 384, "distance": "Cosine"}}')"
[[ "$status" == "200" ]] || { echo "Create failed (HTTP $status)" >&2; exit 1; }
echo "Created collection '$COLLECTION' (size 384, Cosine)."
