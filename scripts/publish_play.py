#!/usr/bin/env python3
"""Публикация AAB в Google Play через официальный Play Developer API.

Используется в CI (release.yml) после сборки AAB. Авторизация — сервисный
аккаунт Google Cloud с доступом в Play Console.

Если файл сервисного аккаунта не задан (нет секрета), скрипт НЕ падает:
печатает предупреждение и завершается с кодом 0, чтобы релиз в GitHub
продолжал работать без обязательной настройки Play.

Документация API: https://developers.google.com/android-publisher/api-ref/rest
"""

import argparse
import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
MAX_NOTES = 500


def parse_args():
    p = argparse.ArgumentParser(description="Upload AAB to Google Play")
    p.add_argument("--aab", required=True, help="Путь к .aab")
    p.add_argument("--package", required=True, help="applicationId, например com.cyber.muzea")
    p.add_argument("--track", default="internal", help="internal/alpha/beta/production")
    p.add_argument("--status", default="completed", help="completed/draft/halted/inProgress")
    p.add_argument("--user-fraction", type=float, default=None, help="Доля для inProgress (0..1)")
    p.add_argument("--service-account", default=os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", ""))
    p.add_argument("--notes-file", default="", help="Файл с release notes")
    return p.parse_args()


def access_token(sa_path: str) -> str:
    creds = service_account.Credentials.from_service_account_file(sa_path, scopes=[SCOPE])
    creds.refresh(Request())
    return creds.token


def fail(message: str, response=None):
    print(f"::error::{message}", file=sys.stderr)
    if response is not None:
        print(f"HTTP {response.status_code}: {response.text}", file=sys.stderr)
    sys.exit(1)


def main():
    args = parse_args()

    if not args.service_account or not os.path.isfile(args.service_account):
        print("Play publish: сервисный аккаунт не задан — пропускаем публикацию в Google Play.")
        return 0
    if not os.path.isfile(args.aab):
        fail(f"AAB не найден: {args.aab}")

    token = access_token(args.service_account)
    headers = {"Authorization": f"Bearer {token}"}
    pkg = args.package

    # 1) Новая правка (edit).
    r = requests.post(f"{API}/{pkg}/edits", headers=headers, json={}, timeout=60)
    if not r.ok:
        fail("Не удалось создать edit (проверь доступ сервисного аккаунта в Play Console)", r)
    edit_id = r.json()["id"]
    print(f"edit={edit_id}")

    # 2) Загрузка AAB.
    with open(args.aab, "rb") as fh:
        r = requests.post(
            f"{UPLOAD_API}/{pkg}/edits/{edit_id}/bundles?uploadType=media",
            headers={**headers, "Content-Type": "application/octet-stream"},
            data=fh,
            timeout=600,
        )
    if not r.ok:
        fail("Не удалось загрузить AAB", r)
    version_code = str(r.json()["versionCode"])
    print(f"versionCode={version_code}")

    # 3) Трек и release notes.
    release = {"versionCodes": [version_code], "status": args.status}
    if args.status == "inProgress":
        fraction = args.user_fraction if args.user_fraction is not None else 0.1
        release["userFraction"] = max(0.0, min(1.0, fraction))

    if args.notes_file and os.path.isfile(args.notes_file):
        text = open(args.notes_file, encoding="utf-8").read().strip()
        if text:
            text = text[:MAX_NOTES]
            release["releaseNotes"] = [
                {"language": "ru-RU", "text": text},
                {"language": "en-US", "text": text},
            ]

    r = requests.put(
        f"{API}/{pkg}/edits/{edit_id}/tracks/{args.track}",
        headers=headers,
        json={"track": args.track, "releases": [release]},
        timeout=60,
    )
    if not r.ok:
        fail(f"Не удалось обновить трек {args.track}", r)

    # 4) Фиксация правки (отправка на ревью для closed/production).
    r = requests.post(f"{API}/{pkg}/edits/{edit_id}:commit", headers=headers, timeout=60)
    if not r.ok:
        fail("Не удалось закоммитить edit", r)

    print(
        f"Google Play: опубликовано {pkg} versionCode={version_code} "
        f"track={args.track} status={args.status}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
