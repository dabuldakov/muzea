#!/usr/bin/env bash
#
# Генерация списка изменений («что нового») для релиза.
#
# Проходит по коммитам от предыдущего тега до указанного и группирует их по
# conventional-commit префиксу (feat, fix, perf, ...). На выходе — Markdown:
# заголовок группы ("## …") и пункты ("- …"). Этот же формат разбирает клиент,
# показывая раскрывающийся список в диалоге обновления.
#
# Использование: generate_release_notes.sh <tag>
# Требует полной истории (actions/checkout с fetch-depth: 0).
set -euo pipefail

tag="${1:?Usage: generate_release_notes.sh <tag>}"

prev="$(git describe --tags --abbrev=0 --match 'v*' "${tag}^" 2>/dev/null || true)"
if [ -n "$prev" ]; then
  range="${prev}..${tag}"
else
  range="${tag}"
fi

# Порядок вывода групп.
order=(feat fix perf refactor docs test build ci style chore revert other)

declare -A heading=(
  [feat]="✨ Новое"
  [fix]="🐛 Исправления"
  [perf]="⚡ Быстродействие"
  [refactor]="♻️ Рефакторинг"
  [docs]="📝 Документация"
  [test]="✅ Тесты"
  [build]="🔧 Сборка"
  [ci]="🔧 CI"
  [style]="💅 Стиль"
  [chore]="🧹 Прочее"
  [revert]="⏪ Откаты"
  [other]="⏳ Разное"
)
declare -A items

while IFS= read -r subject; do
  [ -n "$subject" ] || continue

  # "feat(chat): текст" -> "feat"
  type="${subject%%:*}"
  type="${type%%(*}"
  type="$(printf '%s' "$type" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"

  # Версионные коммиты в список изменений не попадают.
  [ "$type" = "release" ] && continue

  [ -n "${heading[$type]:-}" ] || type="other"

  # Текст без префикса, с сохранением области: "chat: текст".
  text="$(printf '%s' "${subject#*:}" | sed -E 's/^[[:space:]]+//')"
  [ "$text" = "$subject" ] && text="$subject"

  scope=""
  [[ "$subject" == *"("* ]] && scope="$(printf '%s' "${subject#*(}" | sed -E 's/\).*//')"

  if [ -n "$scope" ] && [ "$text" != "$subject" ]; then
    line="${scope}: ${text}"
  else
    line="$text"
  fi
  # Первая буква заглавная (без locale-зависимых диапазонов sed).
  line="${line^}"

  items[$type]="${items[$type]:-}- ${line}"$'\n'
done < <(git log --no-merges --pretty=tformat:'%s' "$range")

for key in "${order[@]}"; do
  if [ -n "${items[$key]:-}" ]; then
    printf '## %s\n%s\n' "${heading[$key]}" "${items[$key]}"
  fi
done
