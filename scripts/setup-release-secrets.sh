#!/usr/bin/env bash
#
# Подготовка секретов репозитория для автоматической сборки релизов.
# Скрипт не сохраняет пароли на диск и не печатает их — только команды,
# которые нужно выполнить.
#
# Требуется gh CLI: https://cli.github.com
#
# Использование:
#   ./scripts/setup-release-secrets.sh [путь/к/keystore.jks] [путь/к/репозиторию]

set -euo pipefail

STORE_FILE="${1:-}"
REPO="${2:-dabuldakov/muzea}"

if [ -z "$STORE_FILE" ]; then
  echo "Путь к keystore не указан."
  read -r -p "Путь к .jks: " STORE_FILE
fi

if [ ! -f "$STORE_FILE" ]; then
  echo "Файл не найден: $STORE_FILE" >&2
  exit 1
fi

STORE_FILE="$(readlink -f "$STORE_FILE")"

if ! command -v gh > /dev/null; then
  echo "Не найден gh CLI. Установи: https://cli.github.com" >&2
  exit 1
fi

echo "Keystore: $STORE_FILE"
echo "Репозиторий: $REPO"
echo
echo "Дальше понадобятся пароль хранилища и данные ключа."
echo "Алиас можно посмотреть заранее: keytool -list -v -keystore $STORE_FILE"
echo

read -r -s -p "Пароль хранилища: " STORE_PASSWORD; echo
read -r -p "Алиас ключа: " KEY_ALIAS
read -r -s -p "Пароль ключа: " KEY_PASSWORD; echo

# Проверяем, что ключ открывается, до того как что-либо отправлять в GitHub.
if ! keytool -list -keystore "$STORE_FILE" \
      -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" > /dev/null 2>&1; then
  echo "Не удалось открыть ключ. Проверь пароль хранилища и алиас." >&2
  exit 1
fi

CRED_B64_FILE="$(mktemp)"
KEY_CRED_FILE="$(mktemp)"
trap 'shred -u "$CRED_B64_FILE" "$KEY_CRED_FILE" 2>/dev/null || rm -f "$CRED_B64_FILE" "$KEY_CRED_FILE"' EXIT

base64 -w 0 < "$STORE_FILE" > "$CRED_B64_FILE"
printf '%s\n' "$STORE_PASSWORD" > "$KEY_CRED_FILE"

echo
echo "Настраиваю secrets в $REPO ..."
gh secret set MUZEA_KEYSTORE_BASE64 --repo "$REPO" < "$CRED_B64_FILE"
gh secret set MUZEA_KEYSTORE_PASSWORD --repo "$REPO" < "$KEY_CRED_FILE"

gh secret set MUZEA_KEY_ALIAS   --repo "$REPO" --body "$KEY_ALIAS"
gh secret set MUZEA_KEY_PASSWORD --repo "$REPO" --body "$KEY_PASSWORD"

echo
echo "Готово. Secrets настроены:"
echo "  MUZEA_KEYSTORE_BASE64    файл ключа, закодированный в base64"
echo "  MUZEA_KEYSTORE_PASSWORD  пароль хранилища"
echo "  MUZEA_KEY_ALIAS          алиас ключа"
echo "  MUZEA_KEY_PASSWORD       пароль ключа"
echo
echo "Осталось выпустить релиз. Дальше — три команды:"
echo
echo "  1) Поднять версию в app/build.gradle.kts (versionCode и versionName)"
echo "  2) Закоммитить и запушить тег с тем же номером версии:"
echo
echo "       git add app/build.gradle.kts && git commit -m 'v1.0.2'"
echo "       git tag v1.0.2 && git push origin main --tags"
echo
echo "  3) Дождаться сборки на https://github.com/$REPO/actions"
echo "     и проверить https://github.com/$REPO/releases/latest"
echo
echo "Если сборка упадёт с «Не задан secret ...», секреты не применились:" >&2
echo "проверь, что репозиторий указан верно, и запусти скрипт заново." >&2