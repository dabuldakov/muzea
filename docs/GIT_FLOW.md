# Git flow и релизы Android

Как изменения проходят путь от коммита до APK/AAB и GitHub Release.
Про подпись и секреты — ниже; iOS-процесс описан в
`muzea-ios/docs/GIT_FLOW.md`.

## Ветки

- **`main`** — production-линия. С 2026-11-24 соответствует `1.7.60`.
- **`release/<линия>`** — поддерживающая ветка линии `1.7.33`; теги `v*`
  ставятся на неё, пока линия поддерживается.
- **`beta/1.7.33-ui`** — сохранённые не выпущенные beta-UI изменения старой
  `main` (нео-брутализм, композер, темы). Их можно пересмотреть и влить
  отдельными PR.
- **`feature/<кратко>`**, **`fix/<кратко>`** — рабочие ветки под задачу.

Линия `1.7.33` закрыта merge-коммитом: `main` теперь равен production-коду
`1.7.60` (включая JDK 21, coverage/PIT/androidTest). CI на `main` зелёный.

## Что запускается автоматически

| Событие | Workflow | Где | Что делает |
|---|---|---|---|
| push/PR в `main` | **CI** (`ci.yml`) | Ubuntu | unit-тесты, coverage gate, PIT, Detekt, androidTest на эмуляторе |
| push тега `v*` | **Release** (`release.yml`) | Ubuntu | проверка `versionName` == тег, keystore, SHA-256 сертификата, `testDebugUnitTest`, Detekt, сборка APK/AAB, GitHub Release, опционально Google Play |
| вручную | **Release** | Ubuntu | то же по `inputs.tag` |

macOS-раннеры не тратятся. Перед релизом важно, чтобы `main`/релизная ветка
были зелёными.

## Окружение и секреты

- **JDK 21**: Gradle 8.11.1, AGP 8.10.1, Kotlin 2.1.21, Hilt/Dagger 2.56.2.
  Bytecode приложения по-прежнему target 17 (совместимость с Android), но
  сборка и CI идут на JDK 21.
- **Покрытие**: `jacocoTestCoverageVerification` требует 85% строк логики;
  Activities исключены из unit-гейта — их проверяет `connectedDebugAndroidTest`
  на эмуляторе (job `instrumented` в `ci.yml`).
- **Мутации**: `pitestPureLogic` гоняет PIT по чистой логике чата
  (`ChargeCalculator`, `MentionSuggestions`), порог — 60%.
- Секреты GitHub Actions: `MUZEA_KEYSTORE_BASE64`,
  `MUZEA_KEYSTORE_PASSWORD`, `MUZEA_KEY_ALIAS`, `MUZEA_KEY_PASSWORD`,
  `KLIPY_APP_KEY`; опционально `PLAY_SERVICE_ACCOUNT_JSON_B64`.
- `muzea.jks`, `keystore.properties` и пароли в git не добавлять.

## Релиз: пошагово

1. Убедиться, что рабочее дерево чистое, `./gradlew testDebugUnitTest`
   зелёный (или доверить проверку CI).
2. Поднять `versionCode` на 1 и `versionName` в `app/build.gradle.kts`.
3. Добавить секцию версии и записи в `docs/WORKLOG.md`; при необходимости
   обновить `docs/FEATURE_CATALOG.md`.
4. Коммит `release: X.Y.Z` (без кода области, в changelog не попадает).
5. `git push origin release/<линия>`.
6. Поставить **аннотированный** тег на релизный коммит и запушить:
   ```bash
   git tag -a vX.Y.Z -m "release: X.Y.Z"
   git push origin vX.Y.Z
   ```
7. Дождаться workflow **Release**. Он проверит `versionName`, восстановит
   ключ, проверит отпечаток, соберёт `muzea-X.Y.Z.apk`/`.aab` и создаст
   GitHub Release.
8. Проверить ссылку на сайте/в приложении:
   `https://muzea.su/downloads/muzea-X.Y.Z.apk`. Сайт показывает последний
   релиз через GitHub API, но ссылку всё равно проверить.
9. **Закрыть линию**: после успешного релиза — PR `release/<линия>` → `main`,
   merge `--no-ff`, тег сохранить. Прогнать CI на `main`. Ветку
   `release/<линия>` можно оставить как maintenance или удалить после
   согласования — тег и GitHub Release от неё не зависят.

## Если релиз упал

- **Не пересоздавать и не двигать тег.** Исправить причину, поднять
  patch-версию (`X.Y.Z+1`), новый `release:`-коммит и новый тег.
- Секреты/keystore проверять по логам шага «Проверить, что APK подписан...».
- Google Play можно пропустить: без `PLAY_SERVICE_ACCOUNT_JSON_B64` шаг
  молча завершается, GitHub Release всё равно публикуется.

## Связь с iOS

iOS и Android версионируются независимо. Общие изменения API/контрактов
сначала попадают в бэкенд, затем в оба клиента. iOS flow —
`muzea-ios/docs/GIT_FLOW.md`.
