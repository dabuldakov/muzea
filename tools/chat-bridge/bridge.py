#!/usr/bin/env python3
"""Мост «чат Muzea <-> OpenCode».

Опрашивает один чат (по умолчанию «Баги») через /after/{timestamp},
передаёт новые сообщения в существующую сессию OpenCode и публикует
ответ ассистента обратно в чат.

Запуск:
  CHAT_TOKEN=mz_... OPENCODE_SESSION=ses_... python3 tools/chat-bridge/bridge.py

Переменные окружения:
  CHAT_BASE_URL       https://chat-muzea.su
  CHAT_TOKEN          service-токен бота (обязателен)
  CHAT_UUID           UUID чата; если не задан — ищем по CHAT_TITLE
  CHAT_TITLE          «Баги»
  ALLOWED_USERNAMES   пусто = все участники чата, кроме бота; можно ограничить списком
  OPENCODE_SESSION    ID сессии (обязателен)
  OPENCODE_URL        http://127.0.0.1:4096 (если поднят `opencode serve`)
  OPENCODE_CMD        opencode
  OPENCODE_AUTO       true — передать --auto (авто-подтверждение инструментов)
  POLL_MS             2500
  STATE_FILE          .chat-bridge-state.json
  REPLY_PREFIX        префикс ответа (по умолчанию пусто)
"""
from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone


def required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        print(f"Не задана переменная {name}", file=sys.stderr)
        sys.exit(1)
    return value


BASE_URL = os.environ.get("CHAT_BASE_URL", "https://chat-muzea.su").rstrip("/")
TOKEN = required("CHAT_TOKEN")
CHAT_UUID = os.environ.get("CHAT_UUID", "")
CHAT_TITLE = os.environ.get("CHAT_TITLE", "Баги")
ALLOWED = [
    name.strip().lower()
    for name in os.environ.get("ALLOWED_USERNAMES", "").split(",")
    if name.strip()
]
SESSION = required("OPENCODE_SESSION")
OPENCODE_URL = os.environ.get("OPENCODE_URL", "")
OPENCODE_CMD = os.environ.get("OPENCODE_CMD", "opencode")
AUTO = os.environ.get("OPENCODE_AUTO", "true") != "false"
POLL_SECONDS = int(os.environ.get("POLL_MS", "2500")) / 1000.0
STATE_FILE = os.environ.get("STATE_FILE", ".chat-bridge-state.json")
REPLY_PREFIX = os.environ.get("REPLY_PREFIX", "")


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def load_state() -> dict:
    try:
        with open(STATE_FILE, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {"lastTs": now_iso(), "processed": []}


STATE = load_state()


def save_state() -> None:
    STATE["processed"] = (STATE.get("processed") or [])[-300:]
    with open(STATE_FILE, "w", encoding="utf-8") as handle:
        json.dump(STATE, handle, ensure_ascii=False, indent=2)


def api(path: str, method: str = "GET", body: dict | None = None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    request = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read().decode("utf-8")
            return json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:300]
        raise RuntimeError(f"{method} {path}: {error.code} {detail}") from error


def resolve_chat() -> str:
    if CHAT_UUID:
        return CHAT_UUID
    chats = api("/api/chats")
    for chat in chats:
        if (chat.get("title") or "").lower() == CHAT_TITLE.lower():
            print(f"Чат: {chat['title']} ({chat['chatUuid']})")
            return chat["chatUuid"]
    raise RuntimeError(f"Чат «{CHAT_TITLE}» не найден")


def me() -> dict:
    user = api("/api/users/me")
    print(f"Бот: @{user.get('username')} (id={user.get('userId')})")
    return user


def allowed_sender(message: dict, bot: dict) -> bool:
    if message.get("senderId") == bot.get("userId"):
        return False
    if (message.get("senderName") or "").lower() == (bot.get("username") or "").lower():
        return False
    if message.get("isDeleted"):
        return False
    text = message.get("text") or ""
    if not text.strip():
        return False
    if not ALLOWED:
        return True
    return (message.get("senderName") or "").lower() in ALLOWED


def run_opencode(prompt: str) -> str:
    args = [OPENCODE_CMD, "run", "-s", SESSION, "--format", "json"]
    if OPENCODE_URL:
        args += ["--attach", OPENCODE_URL]
    if AUTO:
        args.append("--auto")
    args.append(prompt)

    completed = subprocess.run(
        args,
        capture_output=True,
        text=True,
        timeout=15 * 60,
    )
    if completed.returncode != 0:
        raise RuntimeError(
            f"OpenCode exited {completed.returncode}: {completed.stderr[-800:]}"
        )

    texts = []
    for line in completed.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
        except ValueError:
            continue
        if event.get("type") == "text" and event.get("part", {}).get("text"):
            texts.append(event["part"]["text"])
    return "\n".join(texts).strip()


def post_to_chat(chat_uuid: str, text: str) -> None:
    api(
        f"/api/messages/{chat_uuid}",
        method="POST",
        body={
            "text": REPLY_PREFIX + text,
            "messageType": "TEXT",
            "clientMessageId": str(uuid.uuid4()),
        },
    )


def handle_message(chat_uuid: str, bot: dict, message: dict) -> bool:
    if message.get("messageUuid") in (STATE.get("processed") or []):
        return True
    if not allowed_sender(message, bot):
        return True

    print(f"→ {message.get('senderName') or message.get('senderId')}: "
          f"{(message.get('text') or '')[:120]}")
    prompt = "\n".join([
        f"Сообщение из группового чата «{CHAT_TITLE}» в приложении Muzea.",
        f"Автор: @{message.get('senderName') or message.get('senderId')}.",
        f"Текст: {message.get('text')}",
        "",
        "Ответь пользователю в чат: кратко, по делу, без служебных префиксов.",
    ])

    try:
        answer = run_opencode(prompt)
        if not answer:
            print("OpenCode вернул пустой ответ", file=sys.stderr)
            return False
        post_to_chat(chat_uuid, answer)
        STATE["processed"] = [*(STATE.get("processed") or []), message["messageUuid"]]
        print(f"← ответ {len(answer)} символов")
        return True
    except Exception as error:  # noqa: BLE001 — мост не должен падать
        print(f"Ошибка обработки {message.get('messageUuid')}: {error}", file=sys.stderr)
        return False
    finally:
        save_state()


def poll(chat_uuid: str, bot: dict) -> None:
    since = urllib.parse.quote(STATE["lastTs"], safe="")
    messages = api(f"/api/messages/{chat_uuid}/after/{since}")
    for message in sorted(messages, key=lambda m: m.get("createdAt") or ""):
        # lastTs двигаем только после успешной обработки: иначе сообщение,
        # на котором OpenCode был занят/упал, потеряется.
        if not handle_message(chat_uuid, bot, message):
            return
        STATE["lastTs"] = message.get("createdAt") or STATE["lastTs"]


def main() -> None:
    chat_uuid = resolve_chat()
    bot = me()
    print(f"Сессия OpenCode: {SESSION}")
    print(f"Опрос каждые {int(POLL_SECONDS * 1000)} мс. Ctrl+C — остановить.")
    while True:
        try:
            poll(chat_uuid, bot)
        except Exception as error:  # noqa: BLE001
            print(f"poll: {error}", file=sys.stderr)
        save_state()
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nОстановка…")
