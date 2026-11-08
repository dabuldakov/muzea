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
REQUIRE_MENTION = os.environ.get("REQUIRE_MENTION", "true") != "false"


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def load_state() -> dict:
    try:
        with open(STATE_FILE, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, ValueError):
        return {
            "lastTs": now_iso(),
            "processed": [],
            # Время последнего ответа, уже опубликованного в чат.
            "lastReplyTs": int(time.time() * 1000),
        }


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
    if REQUIRE_MENTION:
        lowered = text.lower()
        is_trigger = (
            "@opencode" in lowered
            or lowered.lstrip().startswith("[msg.")
            or lowered.lstrip().startswith("/")
        )
        if not is_trigger:
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


def assistant_replies(since: int) -> list[tuple[int, str]]:
    """Финальные текстовые ответы assistant новее `since` (мс)."""
    response = opencode_api(
        "get",
        f"/api/session/{SESSION}/message?limit=20&type=assistant",
    )
    replies = []
    for message in response["data"]:
        if message.get("finish") != "stop":
            continue
        created = (message.get("time") or {}).get("created") or 0
        if created <= since:
            continue
        text = "\n".join(
            part.get("text", "")
            for part in (message.get("content") or [])
            if part.get("type") == "text"
        ).strip()
        if text:
            replies.append((created, text))
    return sorted(replies)


def wait_for_reply(prompt_time: int, timeout_seconds: int = 900) -> tuple[int, str]:
    """Ждёт первый финальный assistant-ответ после отправленного промпта.

    Запрашиваем только assistant-сообщения и небольшую страницу: полный
    список с reasoning бывает больше 100 КБ и обрезается CLI.
    """
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        replies = assistant_replies(prompt_time - 1)
        if replies:
            return replies[0]
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
        created, text = wait_for_reply(prompt_time)
        note_reply(created)
        return text
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


HISTORY_MESSAGE_LIMIT = 1500
HISTORY_TOTAL_LIMIT = 12000
HISTORY_WINDOW = 15


def format_history(messages: list[dict], bot_name: str, current_uuid: str) -> list[str]:
    """Строки истории с последнего ответа бота и до текущего сообщения."""
    messages = [m for m in messages if m.get("messageUuid") != current_uuid]
    bot_name = (bot_name or "").lower()
    last_bot = None
    for index, item in enumerate(messages):
        if (item.get("senderName") or "").lower() == bot_name:
            last_bot = index
    selected = messages[last_bot:] if last_bot is not None else messages[-HISTORY_WINDOW:]
    selected = selected[-HISTORY_WINDOW:]
    lines = []
    for item in selected:
        text = (item.get("text") or "").strip().replace("\n", " ")
        if not text:
            continue
        lines.append(
            f"@{item.get('senderName') or item.get('senderId')}: "
            f"{text[:HISTORY_MESSAGE_LIMIT]}"
        )
    # Не раздуваем промпт: старые строки уходят первыми.
    while lines and sum(len(line) + 1 for line in lines) > HISTORY_TOTAL_LIMIT:
        lines.pop(0)
    return lines


def chat_history(chat_uuid: str, bot: dict, current_uuid: str) -> list[str]:
    """История с последнего ответа бота: чтобы агент видел ход обсуждения."""
    try:
        response = api(f"/api/messages/{chat_uuid}?page=0&size=30")
    except Exception as error:  # noqa: BLE001
        print(f"history: {error}", file=sys.stderr)
        return []
    messages = list(reversed(response.get("content") or []))
    return format_history(messages, bot.get("username") or "", current_uuid)


def note_reply(created: int) -> None:
    """Запоминаем, до какого ответа чат уже получил продолжения."""
    if created > int(STATE.get("lastReplyTs") or 0):
        STATE["lastReplyTs"] = created


def forward_continuations(chat_uuid: str) -> None:
    """Досылает в чат ответы, появившиеся уже после первого ответа.

    Агент может отдать промежуточное «ушёл проверять» и закончить ход, а
    результат — отдельным сообщением после фоновой работы. Раньше мост
    забирал только первый ответ, и продолжение терялось.
    """
    since = int(STATE.get("lastReplyTs") or 0)
    if not since:
        return
    try:
        replies = assistant_replies(since)
    except Exception as error:  # noqa: BLE001 — мост не должен падать
        print(f"continuations: {error}", file=sys.stderr)
        return
    for created, text in replies:
        post_to_chat(chat_uuid, text)
        note_reply(created)
        save_state()
        print(f"← продолжение ответа {len(text)} символов")


def handle_message(chat_uuid: str, bot: dict, message: dict) -> bool:
    if message.get("messageUuid") in (STATE.get("processed") or []):
        return True
    if not allowed_sender(message, bot):
        return True

    print(f"→ {message.get('senderName') or message.get('senderId')}: "
          f"{(message.get('text') or '')[:120]}")
    history = chat_history(chat_uuid, bot, message.get("messageUuid"))
    prompt_parts = [f"Ты ведёшь групповой чат «{CHAT_TITLE}» в приложении Muzea."]
    if history:
        prompt_parts += ["", "История с твоего последнего ответа:"] + history
    prompt_parts += [
        "",
        f"Новое сообщение от @{message.get('senderName') or message.get('senderId')}: "
        f"{message.get('text')}",
        "",
        "Ответь пользователю в чат: кратко, по делу, без служебных префиксов.",
    ]
    prompt = "\n".join(prompt_parts)

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
    forward_continuations(chat_uuid)


def main() -> None:
    # Старый state без поля: начинаем с текущего момента, чтобы не
    # вывалить в чат всю прошлую переписку сессии.
    STATE.setdefault("lastReplyTs", int(time.time() * 1000))
    save_state()
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
