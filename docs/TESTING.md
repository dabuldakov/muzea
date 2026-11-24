# Тесты и покрытие

## Как запускать

```bash
# все unit-тесты
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline :app:testDebugUnitTest

# HTML/XML-отчёт о покрытии (Robolectric + offline JaCoCo)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:jacocoUnitTestReport
# результат: app/build/reports/jacoco/jacocoUnitTestReport/html/index.html

# гейт покрытия логики: 85% строк (Activities проверяет androidTest)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:jacocoTestCoverageVerification

# мутационное тестирование чистой логики чата
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:pitestPureLogic

# инструментальные тесты на эмуляторе/устройстве
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:connectedDebugAndroidTest
```

В CI (`.github/workflows/ci.yml`) эти шаги объединены: собирается отчёт о
покрытии, а `test-report` и `coverage-report` выкладываются артефактами.

## Текущее покрытие

Замер на 24.11.2026:

| Метрика | Значение | Гейт |
|---------|----------|------|
| Строки всего `app` без сгенерированного кода | ~83% | — |
| Логика без Activities | ~85% | **85%** (`jacocoTestCoverageVerification`) |
| Мутации `ChargeCalculator` + `MentionSuggestions` | ~86% | **60%** (`pitestPureLogic`) |

Низкий общий процент дают Activities и фрагменты — это UI-точки входа.
Их проверяет `connectedDebugAndroidTest` на эмуляторе (job `instrumented`).

## Что и как тестируется

- **Репозитории** — MockK-моками Retrofit-интерфейсов, без сети. Общие
  хелперы ответов: `data/TestResponses.kt` (`httpOk`, `httpError`).
  Обязательно проверяются успех, HTTP-ошибка, сетевое исключение и повтор
  после 401.
- **ViewModel'и** — `MainDispatcherRule` (`UnconfinedTestDispatcher`),
  `runTest`, проверка `StateFlow` и одноразовых `SharedFlow` (подписка до
  действия).
- **Кэши** — временные файлы + `GifCache(file)`, без Android-контекста.
- **SharedPreferences** — `FakeSharedPreferences` вместо Robolectric.
  Robolectric поднимает отдельный classloader, и JaCoCo не видит покрытие
  такого кода, поэтому тесты, которым нужен только `Context`, используют
  MockK + фейки. Robolectric оставлен там, где действительно нужен Android
  (например, ресурсы или `SimpleCache`).
- **Сеть** — `MockWebServer` для интерсепторов (`AuthInterceptor`,
  `TokenRefreshInterceptor`) и загрузчика превью ссылок.

## Правила для новых изменений

1. Новый публичный метод репозитория или ViewModel'и — минимум один тест на
   успешный сценарий и один на ошибку.
2. Ошибки не глотать: проверять, что наружу уходит `Resource.Error` с
   понятным текстом.
3. Для 401 проверять ровно один повтор и вызов `invalidate()`.
4. Не тянуть сеть и не использовать реальные токены в тестах.
5. Тесты не должны зависеть от порядка выполнения: общие объекты-кэши
   очищаются в `@Before`/`@After`.

## Будущие улучшения

### UI-тесты (Robolectric / Espresso)

Сейчас `ui/*` покрыт примерно на 17%: проверяются фильтры, форматтеры и
ViewModel'и, но не сами экраны. Это осознанно отложено — UI-тесты требуют
отдельной инфраструктуры (Hilt test runner, `FragmentScenario`, тестовые
данные) и дольше идут в CI.

План, когда дойдут руки:

1. **Инфраструктура**: `androidTest` с Hilt (`HiltTestApplication`,
   `@HiltAndroidTest`), `FragmentScenario` для фрагментов, fake-репозитории
   вместо моков сети, общие билдеры доменных моделей.
2. **Критичные сценарии в первую очередь**:
   - вход по коду (Login → Otp → Main) и экран согласия;
   - профиль: редактирование, удаление аккаунта (диалог и результат);
   - чат: отправка сообщения, долгое нажатие → жалоба/блокировка;
   - новость и видео: кнопка «Пожаловаться» и диалог причин;
   - нижняя навигация и восстановление вкладок.
3. **Негативные сценарии**: нет сети, 401, пустые списки, ошибка загрузки.
4. **CI**: отдельный job `connectedAndroidTest` на эмуляторе (API 34/36),
   артефакты — скриншоты и отчёт Espresso.
5. **Покрытие UI** считать отдельным отчётом: JaCoCo для
   `connectedAndroidTest` (или screenshot-тесты Robolectric), чтобы не
   смешивать с unit-покрытием.

Цель — не «100% всего», а защита критичных пользовательских путей: вход,
профиль, чат и жалобы на контент (требование Google Play к UGC).

