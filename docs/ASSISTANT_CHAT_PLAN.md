# План: чат-ассистент «Помощник»

Статус: **future / не в текущем релизе**. Сохранено как архитектурный план.

## 1. Цель

У каждого пользователя есть закреплённый чат «Помощник», в котором ассистент:

- отвечает на вопросы о приложении;
- помогает оставить заявку на доработку;
- ищет информацию в интернете и даёт ссылки;
- ведёт отдельную сессию на пользователя.

## 2. Решение по архитектуре

Для MVP — модуль `com.chat.server.assistant` внутри `chat`-сервиса.
При росте нагрузки вынести в отдельный `assistant-service`.

Причины:

- сообщения, чаты и участники уже есть в chat;
- можно писать ответ ассистента в той же БД;
- проще деплой и отладка;
- меньше инфраструктуры.

## 3. Чат по умолчанию

- Системный пользователь `muzea_assistant`, `is_bot = true`.
- Новый тип чата `ASSISTANT`.
- Чат создаётся при провижининге пользователя; для существующих — backfill.
- Чат закреплён, его нельзя удалить, покинуть или переименовать.
- В API: `isAssistant`, `assistantTyping`.

## 4. Поток сообщения

```
клиент → chat-service
  → сообщение пользователя
  → assistant_jobs (PENDING) в той же транзакции
  → worker забирает job (SKIP LOCKED)
  → собирает контекст
  → DeepSeek + tools
  → ответ от muzea_assistant
  → job DONE / FAILED + retry
```

`assistant_jobs`: id, chat_id, user_id, user_message_uuid, status, attempts,
last_error, created_at, updated_at.

## 5. Сессия

DeepSeek API не хранит сессию. Сессия — наша:

- `assistant_sessions`: chat_id, user_id, summary, last_context_message_id,
  model, system_prompt_version.
- `assistant_turns`: user_message_uuid, assistant_message_uuid, tokens,
  latency, status, error.
- Контекст: system prompt + summary + последние N сообщений + tool results.
- Старые сообщения суммировать, чтобы не раздувать токены.

## 6. Tools

### Заявка на доработку

`create_feature_request(title, description, steps, expected, platform, app_version)`

Таблица `feature_requests`: id, uuid, user_id, chat_id, title, description,
steps, expected, platform, app_version, status, github_issue_url, created_at.

Статусы: `NEW`, `REVIEWED`, `PLANNED`, `REJECTED`, `DONE`.
Для MVP — БД + уведомление поддержке; GitHub Issue позже.

### Веб-поиск

Абстракция `WebSearchProvider`:

- DeepSeek Search/Fetch, если доступны в тарифе;
- иначе Brave Search / Tavily / Yandex Search API.

Ограничения:

- 2–3 поиска на ответ;
- результаты — недоверенные данные;
- цитаты в ответе;
- кэш 10 минут;
- таймаут 10 секунд;
- SSRF/allowlist для fetch.

## 7. Лимиты и стоимость

- DeepSeek V4.1 Flash / `deepseek-chat` (уточнить актуальный ID).
- 20 сообщений/день на пользователя.
- 8k input / 2k output, 3 tool-раунда.
- Один активный job на пользователя.
- Общий дневной/месячный бюджет.
- Kill switch.

## 8. Приватность

- Текст сообщений уходит в DeepSeek — трансграничная передача.
- Нужны отдельное согласие на AI-обработку, политика, Data safety, App Privacy.
- Не отправлять email, телефон, вложения и другие чаты.
- По возможности редактировать PII перед отправкой.
- Не логировать содержимое; только токены, latency, tool calls.
- Retention: контекст 90 дней, заявки 1 год; удаление с аккаунтом.

## 9. Клиенты

Android/iOS:

- «Помощник» первым в списке, иконка бота;
- приветствие и подсказки;
- «Помощник печатает…»;
- заявка с подтверждением и номером;
- источники поиска ссылками;
- скрыть жалобы/блокировки/удаление чата.

## 10. Этапы

1. ADR, выбор модели/поиска, согласие на AI.
2. `muzea_assistant`, тип `ASSISTANT`, default-чат, приветствие.
3. `assistant_jobs`, DeepSeek-клиент, контекст.
4. Tool заявки + таблица.
5. Web search + цитаты + лимиты.
6. Typing, метрики, админ-просмотр заявок, kill switch.

## 11. Открытые вопросы

- Модуль в chat или отдельный сервис.
- DeepSeek Search/Fetch или сторонний поиск.
- Заявки только в БД или сразу GitHub Issues.
- AI-согласие: отдельный экран или раскрытие в чате.
- Стриминг токенов или «печатает…» + финальное сообщение.
