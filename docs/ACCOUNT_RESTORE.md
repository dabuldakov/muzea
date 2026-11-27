# Восстановление аккаунта в 30-дневный период

После запроса удаления аккаунт скрывается сразу, но остаётся в сервисах
на 30 дней. В этот срок поддержка может восстановить его. Через 30 дней
фоновые задачи удаляют данные окончательно.

## Что происходит сразу

- auth: пользователь помечается `is_deleted`, сессии и OTP-коды удаляются;
- chat: пользователь помечается `is_deleted`, push-токены удаляются, профиль
  скрывается, сообщения остаются;
- makeup: пользователь помечается `is_deleted`, профиль и контент скрываются;
- приложение очищает локальные токены и согласие.

## Как восстановить

Поддержка должна восстановить пользователя **во всех трёх сервисах**.
Идентификаторы разные:

| Сервис | Идентификатор | Эндпоинт |
|---|---|---|
| auth | `users.id` (UUID) | `POST /support/accounts/{id}/reactivate` |
| chat | `users.user_uuid` (UUID) | `POST /api/support/accounts/{userUuid}/reactivate` |
| makeup | `users.id` (bigint) | `POST /support/accounts/{id}/reactivate` |

Для auth и makeup нужен заголовок `X-Support-Token`, для chat — тоже
`X-Support-Token` (см. конфигурацию `support.token`).

### Порядок

1. Найти пользователя по email в auth DB.
2. Найти chat `user_uuid` по `auth_subject` (UUID из auth).
3. Найти makeup `id` по `auth_subject`.
4. Вызвать три эндпоинта реактивации.
5. Сообщить пользователю: нужно войти заново и принять согласие.

### SQL для поиска

```sql
-- auth
SELECT id, email, username, is_deleted, deleted_at
FROM users
WHERE lower(email) = lower(:email);

-- chat
SELECT user_id, user_uuid, username, is_deleted, deleted_at
FROM users
WHERE auth_subject = :authSubject;

-- makeup
SELECT id, username, is_deleted, deleted_at
FROM users
WHERE auth_subject = :authSubject;
```

## После 30 дней

Фоновые задачи:

- auth: `AccountPurgeScheduler`;
- chat: `AccountPurgeScheduler`;
- makeup: `AccountPurgeScheduler`.

Они удаляют аккаунты, у которых `is_deleted = true` и `deleted_at` старше
30 дней. Восстановление после этого невозможно.

## Важно

- При восстановлении пользователь должен заново принять согласие на обработку
  персональных данных. Приложение показывает экран согласия, если локальное
  согласие очищено.
- Если приложение не было удалено, локальные токены могли остаться; после
  реактивации может потребоваться повторный вход.
- Для России как ОРИ метаданные и содержание сообщений ведутся по 149-ФЗ
  (3 года/до 6 месяцев), а не только по 30-дневному окну.
