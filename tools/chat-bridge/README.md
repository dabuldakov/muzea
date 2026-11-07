# Чат-мост OpenCode

Связывает групповой чат Muzea (по умолчанию «Баги») с сессией OpenCode.

## Как работает

- раз в 2,5 секунды запрашивает `/api/messages/{chat}/after/{timestamp}`;
- отвечает на сообщения всех участников чата (кроме собственных); при необходимости ограничивается `ALLOWED_USERNAMES`;
- передаёт текст в `opencode run -s <session> --format json`;
- публикует ответ ассистента в чат от бота `opencode`;
- хранит `lastTs` и обработанные UUID в `.chat-bridge-state.json`.

## Настройка

1. В чат-бэкенде создан бот `opencode`, добавлен в чат, выпущен service-токен.
2. На машине с OpenCode:

```bash
export CHAT_TOKEN='mz_...'
export OPENCODE_SESSION='ses_...'
export OPENCODE_URL='http://127.0.0.1:4096'   # если запущен `opencode serve`
# export ALLOWED_USERNAMES='vovan,Morris,dabuldakov'  # по умолчанию — все участники
export CHAT_TITLE='Баги'

python3 tools/chat-bridge/bridge.py
```

`OPENCODE_AUTO=true` (по умолчанию) передаёт `--auto`: агент сам подтверждает
инструменты. Это опасно — мост должен слушать только доверенных пользователей
и работать на машине, к которой нет постороннего доступа.

## systemd (пример)

```ini
[Unit]
Description=Muzea chat bridge
After=network-online.target

[Service]
WorkingDirectory=%h/AndroidStudioProjects/git/muzea
EnvironmentFile=%h/.config/muzea-bridge.env
ExecStart=/usr/bin/python3 tools/chat-bridge/bridge.py
Restart=always

[Install]
WantedBy=default.target
```
