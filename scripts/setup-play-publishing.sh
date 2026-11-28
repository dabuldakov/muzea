#!/usr/bin/env bash
#
# Настройка автопубликации Android-релиза в Google Play.
#
# Скрипт берёт JSON-ключ сервисного аккаунта, проверяет его, кодирует в
# base64 и кладёт в GitHub Actions secret PLAY_SERVICE_ACCOUNT_JSON_B64.
# Сам ключ не печатается и не остаётся на диске.
#
# Требуется gh CLI с правами на Actions secrets репозитория.
#
# Использование:
#   ./scripts/setup-play-publishing.sh /path/to/play-service-account.json
#   ./scripts/setup-play-publishing.sh /path/to/key.json dabuldakov/muzea

set -euo pipefail

JSON_FILE="${1:-}"
REPO="${2:-dabuldakov/muzea}"

if [[ -z "$JSON_FILE" || ! -f "$JSON_FILE" ]]; then
  echo "Укажи путь к JSON-ключу сервисного аккаунта." >&2
  echo "  $0 /path/to/play-service-account.json [owner/repo]" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "Не найден gh CLI: https://cli.github.com" >&2
  exit 1
fi

if ! python3 - "$JSON_FILE" <<'PY'
import json, sys
path = sys.argv[1]
try:
    data = json.load(open(path, encoding="utf-8"))
except Exception as e:
    print(f"Не JSON: {e}", file=sys.stderr)
    sys.exit(1)
required = ["type", "project_id", "private_key", "client_email"]
missing = [k for k in required if not data.get(k)]
if missing:
    print("В JSON нет полей: " + ", ".join(missing), file=sys.stderr)
    sys.exit(1)
if data.get("type") != "service_account":
    print("Ожидается type=service_account", file=sys.stderr)
    sys.exit(1)
if not str(data["client_email"]).endswith(".iam.gserviceaccount.com"):
    print("client_email не похож на сервисный аккаунт", file=sys.stderr)
    sys.exit(1)
PY
then
  exit 1
fi

B64_FILE="$(mktemp)"
trap 'shred -u "$B64_FILE" 2>/dev/null || rm -f "$B64_FILE"' EXIT

# GNU base64 и BSD base64: без переносов строк.
if base64 --help 2>&1 | grep -q -- '-w'; then
  base64 -w0 < "$JSON_FILE" > "$B64_FILE"
else
  base64 < "$JSON_FILE" | tr -d '\n' > "$B64_FILE"
fi

echo "Репозиторий: $REPO"
echo "Настраиваю secret PLAY_SERVICE_ACCOUNT_JSON_B64..."
gh secret set PLAY_SERVICE_ACCOUNT_JSON_B64 --repo "$REPO" < "$B64_FILE"

echo "Настраиваю переменные трека..."
gh variable set PLAY_TRACK --repo "$REPO" --body "internal"
gh variable set PLAY_STATUS --repo "$REPO" --body "completed"

echo
echo "Готово. Проверь:"
echo "  gh secret list --repo $REPO | grep PLAY_SERVICE_ACCOUNT_JSON_B64"
echo "  gh variable list --repo $REPO | grep PLAY_"
echo
echo "Следующий тег v* соберёт AAB и загрузит его в Google Play."
echo "Первый AAB в приложение Play Console должен быть загружен вручную,"
echo "если приложение ещё не публиковалось."
