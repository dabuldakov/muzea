# Автопубликация в Google Play

Документ описывает, как включить шаг «Опубликовать в Google Play» в
`.github/workflows/release.yml`.

## Настроено

- 28.11.2026: сервисный аккаунт добавлен в Play Console, API работает.
- GitHub secret `PLAY_SERVICE_ACCOUNT_JSON_B64` задан.
- `PLAY_TRACK=internal`, `PLAY_STATUS=completed`.
- Проверено: AAB `1.7.65` (`versionCode 194`) загружен в internal вручную.
- 28.11.2026: релиз `1.7.66` (`versionCode 195`) загружен в internal автоматически из workflow.
- 28.11.2026: релиз `1.7.67` (`versionCode 196`) загружен в internal автоматически.
- 28.11.2026: релиз `1.7.68` (`versionCode 197`) загружен в internal автоматически.
- Production через API отвечает `400 FAILED_PRECONDITION`: сначала нужно
  заполнить App content (Data safety, content rating, target audience, UGC),
  проверить Store listing и демо-доступ. После этого релиз можно продвинуть из
  internal в production.
- Следующие теги `v*` будут загружаться автоматически.

## Текущее состояние

- Приложение `com.cyber.muzea` зарегистрировано в Play Console.
- APK/AAB собираются и подписываются в CI.
- Шаг Play включён: `PLAY_SERVICE_ACCOUNT_JSON_B64` задан.
- `PLAY_TRACK=internal`, `PLAY_STATUS=completed`.
- Скрипт публикации: `scripts/publish_play.py`.
- Сейчас используется ключ `firebase-adminsdk-fbsvc@muzea-chat.iam.gserviceaccount.com`.
  Для CI лучше позже завести отдельный `github-play` без лишних прав и заменить секрет.

## 1. Сервисный аккаунт

1. Play Console → **Setup → API access**.
2. Привязать Google Cloud проект (или создать новый).
3. В Google Cloud включить **Google Play Android Developer API**.
4. Создать сервисный аккаунт, например `github-play`.
5. Создать JSON-ключ и скачать файл.
6. В Play Console → **Users and permissions** пригласить
   `github-play@...iam.gserviceaccount.com`:
   - доступ к приложению `com.cyber.muzea`;
   - права **Admin** на первом этапе (потом можно сузить до релизов).

Без приглашения в Play Console API вернёт `401/403` при создании edit.

## 2. GitHub secret и переменные

Через веб-интерфейс:

- **Settings → Secrets and variables → Actions**;
- secret `PLAY_SERVICE_ACCOUNT_JSON_B64` — JSON-ключ в base64;
- variables `PLAY_TRACK=internal`, `PLAY_STATUS=completed`.

Через CLI:

```bash
base64 -w0 play-service-account.json | gh secret set PLAY_SERVICE_ACCOUNT_JSON_B64
gh variable set PLAY_TRACK --body internal
gh variable set PLAY_STATUS --body completed
```

Или готовый скрипт:

```bash
./scripts/setup-play-publishing.sh /path/to/play-service-account.json
```

Скрипт проверяет JSON, кодирует его, ставит secret и переменные, не печатая
ключ.

## 3. Первый AAB

Если приложение уже публиковалось, API может загружать новые AAB.
Если нет — первый AAB нужно загрузить в Play Console вручную, чтобы у
приложения появился трек и bundle.

## 4. Треки и статусы

| Переменная | Значения | Смысл |
|---|---|---|
| `PLAY_TRACK` | `internal`, `alpha`, `beta`, `production` | трек загрузки |
| `PLAY_STATUS` | `completed`, `draft`, `inProgress`, `halted` | статус релиза |

- `internal` + `completed` — сборка сразу во внутреннем тестировании, без ревью.
- `production` + `completed` — 100% раскатка и отправка на ревью.
- `production` + `inProgress` — поэтапная раскатка (доля в скрипте).
- `draft` — черновик, не публикуется.

Для первого запуска лучше `internal`.

## 5. Проверка

После пуша тега `v*`:

1. Actions → **Release** → шаг «Опубликовать в Google Play».
2. В логе должно быть:

```text
Google Play: опубликовано com.cyber.muzea versionCode=... track=internal status=completed
```

3. Play Console → **Testing → Internal testing** — появилась сборка.

## 6. Ошибки

| Ошибка | Причина |
|---|---|
| `401` | ключ неверный или Google Play Android Developer API не включён |
| `403` | сервисный аккаунт не приглашён в Play Console или нет прав |
| `versionCode already used` | этот `versionCode` уже загружен; поднять версию |
| шаг молча пропущен | не задан `PLAY_SERVICE_ACCOUNT_JSON_B64` |

## 7. Безопасность

- JSON-ключ не коммитить и не печатать.
- В GitHub хранить только в secret.
- После настройки проверить, что ключ не попал в логи Actions.
- При компрометации удалить ключ в Google Cloud и создать новый.
