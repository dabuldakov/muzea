# Тесты и покрытие

## Как запускать

```bash
# все unit-тесты
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --offline :app:testDebugUnitTest

# HTML/XML-отчёт о покрытии
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:createDebugUnitTestCoverageReport
# результат: app/build/reports/coverage/test/debug/index.html
```

В CI (`.github/workflows/ci.yml`) эти шаги объединены: собирается отчёт о
покрытии, а `test-report` и `coverage-report` выкладываются артефактами.

## Текущее покрытие

Замер на 02.11.2026 (392 теста, 0 падений):

| Слой | Покрытие строк |
|------|----------------|
| `data/repository` | 82% |
| `data/mapper` | 100% |
| `data/model` | 88% |
| `domain` | 97% |
| `core` | 88% |
| `utils` | 40% |
| Весь `app` без сгенерированного кода | ~37% |
| Только UI (`ui/*`) | ~17% |

Низкий общий процент — это фрагменты, активности, адаптеры и
`databinding`/Hilt/Dagger. Логика (репозитории, ViewModel'и, мапперы,
форматтеры) покрыта существенно выше; UI проверяется вручную на эмуляторе.

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
