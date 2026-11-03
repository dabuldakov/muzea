# Push-уведомления: переход в чат/новость и подавление в открытом чате

Статус: **реализовано** (app + chat backend + makeup backend), ждёт деплоя
бэкендов и проверки на двух устройствах.

Решения по вопросам из раздела 9:

1. Подавляем только **открытый** чат.
2. Одно уведомление на чат, новый текст заменяет старый.
3. Новости — только тем, у кого **автор новости есть в контактах**.
4. Тап по push чата открывает чат, скроллит к сообщению и подсвечивает его.

## 1. Текущее состояние

**Бэкенд чата**

- `PushNotificationService.sendMessageNotification(...)` отправляет push всем
  участникам, кроме автора.
- В FCM-data есть только `type=MESSAGE`, `senderId`, `senderName`, `messageText`.
- **Нет** `chatUuid`, `messageUuid`, `chatTitle`, аватара отправителя.
- Отправляется и `notification`, и `data`. В фоне Android показывает уведомление
  сам, в foreground вызывается `onMessageReceived`. (Так было до 1.7.15; сейчас
  только data — см. раздел 3.)
- Уведомление не группируется: каждое сообщение — отдельное.

**Android**

- `MuzeaMessagingService` всегда строит локальное уведомление на каждое
  сообщение, в том числе когда пользователь уже находится в этом чате.
- `PendingIntent` ведёт просто в `MainActivity` без параметров — открывается
  последняя вкладка/главная, а не конкретный чат.
- Идентификатор уведомления — текущее время, поэтому уведомления копятся.
- **Подавления в открытом чате нет вообще.**
- Push о новостях сейчас **не отправляется**: FCM есть только в чат-сервисе,
  makeup-бэкенд уведомления не шлёт. Токен устройства регистрируется только на
  chat-бэкенде.

## 2. Что хотим

1. Тап по push чата → открыть **конкретный чат**.
2. Тап по push новости → открыть **конкретную новость**.
3. Если пользователь уже в этом чате и приложение на экране — уведомление
   **не показывать** (сообщение и так появится в ленте).
4. Не плодить по уведомлению на каждое сообщение: одно уведомление на чат,
   новое сообщение заменяет предыдущее (с текстом последнего).
5. Новости — отдельный канал и переход в новость.

## 3. Контракт push-сообщения

### Чат

```json
{
  "type": "MESSAGE",
  "chatUuid": "...",
  "chatTitle": "Название чата или имя собеседника",
  "chatAvatar": "https://...",
  "messageUuid": "...",
  "senderUuid": "...",
  "senderId": "4",
  "senderName": "Анна",
  "messageText": "Привет"
}
```

- Push отправляется **только с data**, без блока `notification`. Иначе в фоне
  уведомление рисует система, и тап открывает приложение без data-extras:
  чат/сообщение не находятся. Приложение строит уведомление само и само
  задаёт `tag = chatUuid` (одно уведомление на чат).
- `AndroidConfig.priority = HIGH` — чтобы data-сообщение будило приложение
  в фоне.
- `messageUuid` нужен, чтобы при открытии чата можно было подсветить/проскроллить
  к сообщению (опционально).

### Новость

```json
{
  "type": "NEWS",
  "newsId": "123",
  "title": "Заголовок новости",
  "body": "Короткий текст"
}
```

## 4. Android: переход по тапу

Добавить в `MainActivity` обработку extras:

- `EXTRA_OPEN_CHAT_UUID`, `EXTRA_OPEN_CHAT_TITLE`, `EXTRA_OPEN_CHAT_AVATAR`;
- `EXTRA_OPEN_NEWS_ID`.

Обрабатывать в `onCreate` и `onNewIntent`:

- чат: выбрать вкладку «Чаты» и открыть `ChatConversationFragment`;
- новость: открыть `NewsDetailFragment` (через существующий `Navigator`).

`PendingIntent` для foreground-уведомлений — с этими extras.
Для фоновых уведомлений (их показывает система) FCM добавляет data в Intent
запуска; `MainActivity` прочитает те же ключи.

Нюанс: если приложение ещё не авторизовано, deep link можно отложить до
успешного входа, но push-токен выдаётся только после входа, так что это редкий
случай.

## 5. Подавление в открытом чате

Добавить простой трекер:

```kotlin
object ActiveChatTracker {
    @Volatile var chatUuid: String? = null
}
```

- `ChatConversationFragment.onStart()` — записать `chatUuid`;
- `onStop()` — очистить, если там всё ещё этот чат.

В `MuzeaMessagingService.onMessageReceived`:

```kotlin
if (data["type"] == "MESSAGE" &&
    ActiveChatTracker.chatUuid == data["chatUuid"]) {
    return // пользователь уже видит это сообщение
}
```

Это работает и когда экран погашен: activity уходит в `onStop`, трекер
очищается, уведомление показывается.

Дополнительно можно не показывать уведомления **всех** чатов, пока приложение
на переднем плане (только звук/внутренний баннер), но это уже вопрос привычки —
по умолчанию предлагаю подавлять только текущий чат.

## 6. Группировка уведомлений

- В foreground: `NotificationCompat.MessagingStyle`, один notification id на
  `chatUuid`, история последних сообщений, `setGroup("chats")`.
- В background: FCM `tag=chatUuid` + `collapseKey` — система сама заменит
  уведомление чата.
- Отдельный summary-notification для группы чатов (опционально).

Это убирает «простыню» из уведомлений на каждое сообщение.

## 7. Новости: как отправлять (реализовано, вариант C с фильтром по контактам)

Новость уходит не всем: получатели — пользователи, у которых автор новости
есть в контактах. Топик `news` не используется, чтобы не рассылать новости
всем подписчикам.

Схема:

1. `makeup` после сохранения новости вызывает
   `POST https://chat-muzea.su/api/internal/push/news`
   (`NewsPushNotifier`, `@Async`, best-effort) с телом
   `{authorUsername, newsId, title, body}` и заголовком `X-Internal-Token`.
2. `chat` (`InternalPushController` → `NewsPushService`) проверяет токен,
   находит автора по username, берёт `user_id` из `contacts` по
   `contact_user_id = автор`, собирает активные FCM-токены и отправляет push
   на канал `news` с `tag = news_<id>`.
3. Если автор в chat-базе не найден или контактов нет — push тихо
   пропускается (логи).

Конфигурация:

- chat: `PUSH_INTERNAL_TOKEN` (`push.internal-token`);
- makeup: `CHAT_PUSH_TOKEN` (тот же секрет), `CHAT_PUSH_URL`
  (`push.chat-url`, по умолчанию `https://chat-muzea.su`).

Эндпоинты `/api/internal/**` открыты в Security, но защищены общим секретом:
без настроенного секрета или с неверным заголовком — `403`.

## 8. Оценка

| Работа | Оценка |
|--------|--------|
| Chat: chatUuid/messageUuid/chatTitle в push + tag/collapse | 0.5–1 день |
| Android: deep link в чат, PendingIntent, обработка extras | 0.5–1 день |
| Подавление в активном чате + трекер | 0.5 дня |
| Группировка/MessagingStyle | 0.5–1 день |
| Новости: топик + отправка из makeup + переход | 1–2 дня |
| Тесты и проверка на двух устройствах | 0.5–1 день |
| **Итого** | **3.5–5.5 дня** |

## 9. Что сделано в коде

**App**

- `utils/NotificationChannels` — каналы `chat_messages` и `news` создаются при
  старте (`MuzeaApplication`), иначе фоновые push пропадают.
- `utils/ActiveChatTracker` — UUID открытого чата, ставится в
  `ChatConversationFragment.onStart`, чистится в `onStop`.
- `utils/MuzeaMessagingService` — разбор `MESSAGE` / `NEWS` / `BROADCAST`,
  подавление открытого чата, `tag`/id по чату или новости.
- `MainActivity` — обработка extras в `onCreate` и `onNewIntent`; читает и свои
  ключи (foreground), и исходные FCM-ключи (фон).
- `ChatConversationFragment` — `highlightMessageUuid`: скролл к сообщению
  (при необходимости догружает историю) и подсветка foreground-слоем ~1.8 с.

**Chat backend**

- `FcmService`/`PushNotificationService`: data-only (`type`, `title`, `body`,
  `chatUuid`, `messageUuid`, `chatTitle`, `senderUuid`, `senderAvatar`),
  `priority = HIGH`.
- `newspush/NewsPushService` + `newspush/InternalPushController` (отдельный
  модуль, чтобы не тянуть `contacts` в `notification`).
- `ContactRepository.findUserIdsByContactUserId` — подписчики автора.
- `SecurityConfig`: `/api/internal/**` permitAll + проверка секрета в контроллере.

**Makeup backend**

- `news/NewsPushNotifier` — `RestClient`, `@Async`, вызов chat-эндпоинта после
  создания новости; ошибки только логируются.

## 10. Проверка перед релизом

1. Задать одинаковые `PUSH_INTERNAL_TOKEN` (chat) и `CHAT_PUSH_TOKEN` (makeup)
   в `.env` на сервере, перезапустить оба контейнера.
2. Два аккаунта на двух устройствах: A пишет B → push у B; тап открывает чат,
   скроллит и подсвечивает сообщение.
3. B открывает этот чат → следующие сообщения от A не показывают уведомление.
4. Одно уведомление на чат: несколько сообщений подряд дают одно уведомление
   с последним текстом.
5. Новость: публикует автор, который есть в контактах у B → push у B; у C без
   автора в контактах push нет; тап открывает новость.
