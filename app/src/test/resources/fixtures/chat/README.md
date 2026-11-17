# Shared API fixtures (Android + iOS)

Один и тот же JSON используется тестами Android и iOS. Это контракт между
клиентами и бэкендом, а не «тестовые модели»: поля повторяют реальные ответы
chat-service.

Правила:

- фикстуры не содержат клиентских полей (`peerDeleted`, локальные статусы);
- при изменении API сначала обновляется `docs/API_CONTRACT.md` и фикстура,
  затем контрактные тесты на обеих платформах;
- Android читает этот каталог из своего репозитория (копия/симлинк в
  `app/src/test/resources/fixtures`), iOS — через `#filePath` в
  `MuzeaTests/ContractFixtureTests.swift`;
- доменные модели платформ называются одинаково (`Chat`, `ChatMessage`,
  `ChatAttachment`, `ChatReceipt`), поэтому перенос тестов и логики
  механический.

Файлы:

- `chat/chats.json` — список чатов;
- `chat/messages_page.json` — страница сообщений с текстом, медиа и реакциями;
- `chat/receipts.json` — водяные знаки участников;
- `chat/message_statuses.json` — точные статусы по сообщению;
- `chat/attachment.json` — ответ загрузки вложения;
- `chat/participants.json` — участники группы;
- `chat/media_types.json` — golden-таблица типов/лимитов вложений;
- `chat/gif_search.json` — ответ Klipy.
