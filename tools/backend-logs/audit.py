#!/usr/bin/env python3
"""Аудит прод-логов Muzea за окно времени.

Скрипт рассчитан на запуск НА сервере приложений (168.222.194.206): Loki
доступен там на 127.0.0.1:3100 через SSH-туннель с машины мониторинга.
Ничего не меняет, только читает.

Пример:
    scp tools/backend-logs/audit.py root@168.222.194.206:/tmp/
    ssh root@168.222.194.206 python3 /tmp/audit.py --hours 24

Вывод — Markdown: объём по сервисам, ошибки, HTTP-коды, медленные запросы,
проверка покрытия и поиск персональных данных.
"""

from __future__ import annotations

import argparse
import collections
import json
import re
import subprocess
import time
import urllib.parse
import urllib.request

LOKI = "http://127.0.0.1:3100"
DEFAULT_APPS = "chat|makeup|auth|minio"
EXPECTED = {
    "chat": ["chat-backend-app-1", "chat-backend-nginx-1"],
    "makeup": ["makeup-backend-app-1", "makeup-backend-nginx-1"],
    "auth": ["muzea-auth-service-app-1", "muzea-auth-service-nginx-1"],
    "shared": ["shared-minio-minio-1"],
}


def get_json(url: str, timeout: int = 180):
    last = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=timeout) as response:
                return json.load(response)
        except Exception as error:  # noqa: BLE001
            last = error
            time.sleep(2 * (attempt + 1))
    raise last


def loki_query(query: str, start: int, end: int, limit: int = 2000, metric: bool = False):
    path = "query" if metric else "query_range"
    params = {"query": query}
    if metric:
        params["time"] = end // 1_000_000_000
    else:
        params.update({"start": start, "end": end, "limit": limit, "direction": "backward"})
    url = f"{LOKI}/loki/api/v1/{path}?" + urllib.parse.urlencode(params)
    return get_json(url).get("data", {}).get("result", [])


def series(match: str, start: int, end: int):
    url = f"{LOKI}/loki/api/v1/series?" + urllib.parse.urlencode({
        "match[]": match, "start": start, "end": end,
    })
    return get_json(url, timeout=120).get("data", [])


def lines(query: str, start: int, end: int, limit: int = 2000):
    out = []
    for stream in loki_query(query, start, end, limit):
        labels = stream.get("stream", {})
        for _, line in stream.get("values", []):
            out.append((labels, line))
    return out


def norm(message: str) -> str:
    message = re.sub(r"\b[0-9a-f]{8}-[0-9a-f-]{27,}\b", "<uuid>", message, flags=re.I)
    message = re.sub(r"\b\d{2,}\b", "<n>", message)
    message = re.sub(r"\s+", " ", message).strip()
    return message[:160]


MASKERS = [
    (re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"), "<email>"),
    (re.compile(r"(?i)bearer\s+[A-Za-z0-9._-]{10,}"), "Bearer <token>"),
    (re.compile(r"(?i)(password|passwd|pwd)\"?\s*[:=]\s*\"?[^\s\",}]{3,}"), "<password>"),
    (re.compile(r"(?:\+7|8)[\s(-]\d{3}[\s)-]\d{3}[\s-]\d{2}[\s-]\d{2}"), "<phone>"),
]


def mask(text: str) -> str:
    for pattern, replacement in MASKERS:
        text = pattern.sub(replacement, text)
    return text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--hours", type=int, default=24)
    parser.add_argument("--apps", default=DEFAULT_APPS)
    parser.add_argument("--db-container", default="chat-backend-postgres-1")
    parser.add_argument("--db", default="chat_db")
    args = parser.parse_args()

    end = int(time.time()) * 1_000_000_000
    start = end - args.hours * 3600 * 1_000_000_000
    apps = args.apps

    print(f"# Аудит логов за {args.hours} ч\n")
    print(f"Loki: {LOKI}, приложения: `{apps}`\n")

    volume = {}
    levels = {}
    # По одному сервису за запрос: общий regex-запрос по 24 часам иногда
    # упирается в таймаут Loki.
    for app_name in apps.split("|"):
        for item in loki_query(
            f'sum by (level) (count_over_time({{app="{app_name}"}}[{args.hours}h]))',
            start, end, metric=True,
        ):
            level = item["metric"].get("level", "unknown")
            value = int(float(item["value"][1]))
            volume[app_name] = volume.get(app_name, 0) + value
            levels.setdefault(app_name, {})[level] = value

    print("## Объём\n")
    print("| Сервис | Строк | error | warn | info |")
    print("|---|---|---|---|---|")
    for app in sorted(volume):
        lv = levels.get(app, {})
        print(f"| {app} | {volume[app]} | {lv.get('ERROR', 0)} | "
              f"{lv.get('WARN', 0)} | {lv.get('INFO', 0)} |")
    print()

    grouped = collections.Counter()
    for labels, line in lines(f'{{app=~"{apps}"}} | json | level=~"ERROR|WARN"', start, end, 3000):
        try:
            body = json.loads(line)
        except Exception:
            body = {"message": line}
        grouped[(labels.get("app"), body.get("level"), norm(body.get("message") or ""))] += 1
    print("## Топ ошибок и предупреждений\n")
    if not grouped:
        print("Нет.\n")
    else:
        print("| Сервис | Уровень | Повторов | Сообщение |")
        print("|---|---|---|---|")
        for (app, level, message), count in grouped.most_common(15):
            print(f"| {app} | {level} | {count} | {mask(message)} |")
        print()

    codes = {}
    paths = collections.Counter()
    slow = []
    request_re = re.compile(r"HTTP (\w+) (\S+) -> (\d{3}) \((\d+)ms")
    for labels, line in lines(f'{{app=~"{apps}"}} |= "HTTP "', start, end, 5000):
        match = request_re.search(line)
        if not match:
            continue
        method, path, code, ms = match.groups()
        app = labels.get("app", "?")
        codes.setdefault(app, collections.Counter())[code] += 1
        if code.startswith(("4", "5")):
            paths[(app, code, re.sub(r"/[0-9a-f-]{20,}", "/{id}", path))] += 1
        slow.append((int(ms), app, method, path, code))
    print("## HTTP-коды\n")
    for app in sorted(codes):
        print(f"- {app}: " + ", ".join(f"{code}={count}" for code, count in sorted(codes[app].items())))
    print()
    if paths:
        print("### 4xx/5xx по путям\n")
        print("| Сервис | Код | Путь | Запросов |")
        print("|---|---|---|---|")
        for (app, code, path), count in paths.most_common(12):
            print(f"| {app} | {code} | {path} | {count} |")
        print()
    print("### Медленные запросы\n")
    if slow:
        for ms, app, method, path, code in sorted(slow, reverse=True)[:10]:
            print(f"- {ms} ms — {app}: {method} {mask(path)} → {code}")
    else:
        print("Нет строк с длительностью.")
    print()

    print("## Покрытие источников\n")
    seen = set()
    for item in series(f'{{app=~"{apps}"}}', start, end):
        seen.add(item.get("container"))
    for service, containers in EXPECTED.items():
        for container in containers:
            state = "есть" if container in seen else "**нет логов**"
            print(f"- {service}: `{container}` — {state}")
    print()

    print("## Персональные данные в логах\n")
    findings = 0
    # Regex-скан по всей выдаче слишком тяжёл для Loki: смотрим последние 2 часа.
    pii_start = max(start, end - 2 * 3600 * 1_000_000_000)
    for name, pattern in [
        ("email", MASKERS[0][0]),
        ("bearer", MASKERS[1][0]),
        ("password", MASKERS[2][0]),
        ("phone", MASKERS[3][0]),
    ]:
        try:
            rows = lines(f'{{app=~"{apps}"}} |~ `{pattern.pattern}`', pii_start, end, 20)
        except Exception as error:  # noqa: BLE001
            print(f"- {name}: проверка не удалась ({error})")
            continue
        if rows:
            findings += 1
            print(f"- **{name}**: найдено в выдаче, пример после маскировки:")
            for labels, line in rows[:2]:
                print(f"  - `{labels.get('app')}`: {mask(line)[:200]}")
    if findings == 0:
        print("Не найдено.")
    print()

    print("## Отчёты об ошибках приложения (error_events)\n")
    sql = (
        "select app_version, level, tag, count(*) from error_events "
        f"where received_at > now() - interval '{args.hours} hours' "
        "group by 1,2,3 order by count desc limit 10"
    )
    try:
        out = subprocess.check_output(
            ["docker", "exec", args.db_container, "psql", "-U", "admin", "-d", args.db,
             "-tAc", sql],
            text=True,
        ).strip()
        print("```\n" + (out or "Отчётов нет.") + "\n```")
    except Exception as error:  # noqa: BLE001
        print(f"Не удалось прочитать error_events: {error}")


if __name__ == "__main__":
    main()
