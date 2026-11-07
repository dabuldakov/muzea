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
  OPENCODE_URL        http://127.0.0.1:49374 (локальный сервис OpenCode)
  OPENCODE_PASSWORD   пароль сервиса; иначе берётся из ~/.config/opencode/service.json
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
import base64
import urllib.request
import uuid
from datetime import datetime, timezone


def required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        print(f"Не задана переменная {name}", file=sys.stderr)
        sys.exit(1)
    return value


def _service_password() -> str:
    try:
        with open(os.path.expanduser("~/.config/opencode/service.json")) as handle:
            return json.load(handle)["password"]
    except (OSError, ValueError, KeyError):
        return ""


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
OPENCODE_URL = os.environ.get("OPENCODE_URL", "http://127.0.0.1:49374").rstrip("/")
OPENCODE_USER = os.environ.get("OPENCODE_USER", "opencode")
OPENCODE_PASSWORD = os.environ.get("OPENCODE_PASSWORD") or _service_password()
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
    temp = f"{STATE_FILE}.tmp"
    with open(temp, "w", encoding="utf-8") as handle:
        json.dump(STATE, handle, ensure_ascii=False, indent=2)
    os.replace(temp, STATE_FILE)


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


def opencode_api(method: str, path: str, body: dict | None = None):
    """Прямой HTTP-запрос к локальному сервису OpenCode (без обрезки CLI)."""
    method = method.upper()
    data = json.dumps(body).encode("utf-8") if body is not None else None
    credentials = base64.b64encode(
        f"{OPENCODE_USER}:{OPENCODE_PASSWORD}".encode()
    ).decode()
    request = urllib.request.Request(
        f"{OPENCODE_URL}{path}",
        method=method,
        headers={
            "Authorization": f"Basic {credentials}",
            "Content-Type": "application/json",
        },
    )
    if data is not None:
        request.data = data
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read().decode("utf-8")
            return json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:500]
        raise RuntimeError(f"opencode {method} {path}: {error.code} {detail}") from error


def wait_for_reply(prompt_time: int, timeout_seconds: int = 900) -> str:
    """Ждёт финальный assistant-ответ после отправленного промпта.

    Запрашиваем только assistant-сообщения и небольшую страницу: полный
    список с reasoning бывает больше 100 КБ и обрезается CLI.
    """
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        response = opencode_api(
            "get",
            f"/api/session/{SESSION}/message?limit=10&type=assistant",
        )
        candidates = []
        for message in response["data"]:
            if message.get("finish") != "stop":
                continue
            created = (message.get("time") or {}).get("created") or 0
            if created < prompt_time:
                continue
            text = "\n".join(
                part.get("text", "")
                for part in (message.get("content") or [])
                if part.get("type") == "text"
            ).strip()
            if text:
                candidates.append((created, text))
        if candidates:
            return min(candidates, key=lambda item: item[0])[1]
        time.sleep(2)
    raise TimeoutError("OpenCode не ответил за 15 минут")


def session_is_idle() -> bool:
    try:
        active = opencode_api("get", "/api/session/active")
        return SESSION not in (active.get("data") or {})
    except Exception:
        return True


def send_prompt(prompt: str) -> int:
    waited = False
    while not session_is_idle():
        if not waited:
            print("OpenCode занят, ждём завершения текущего хода…")
            waited = True
        time.sleep(3)
    response = opencode_api(
        "post", f"/api/session/{SESSION}/prompt", {"text": prompt}
    )
    return response["data"]["time"]["created"]


def ask_session(prompt: str, message_uuid: str) -> str:
    """Отправляет prompt или продолжает ждать ответ на уже отправленный.

    Pending хранится в state: рестарт моста не должен повторно инжектить
    то же сообщение в сессию и плодить дубли ответов.
    """
    pending = STATE.get("pending") or {}
    if pending.get("messageUuid") == message_uuid:
        prompt_time = pending["promptTime"]
    else:
        prompt_time = send_prompt(prompt)
        STATE["pending"] = {
            "messageUuid": message_uuid,
            "promptTime": prompt_time,
        }
        save_state()
    try:
        return wait_for_reply(prompt_time)
    except TimeoutError:
        STATE.pop("pending", None)
        save_state()
        raise


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
        answer = ask_session(prompt, message['messageUuid'])
        if not answer:
            print("OpenCode вернул пустой ответ", file=sys.stderr)
            return False
        post_to_chat(chat_uuid, answer)
        STATE["processed"] = [*(STATE.get("processed") or []), message["messageUuid"]]
        STATE.pop("pending", None)
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
