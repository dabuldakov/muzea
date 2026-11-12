#!/usr/bin/env python3
"""Тесты форматирования истории для промпта моста.

Запуск: python3 tools/chat-bridge/test_bridge.py
"""
import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent


def load_bridge():
    os.environ.setdefault("CHAT_TOKEN", "test-token")
    os.environ.setdefault("OPENCODE_SESSION", "ses_test")
    os.environ.setdefault(
        "STATE_FILE", str(Path(tempfile.mkdtemp()) / "bridge-state.json")
    )
    spec = importlib.util.spec_from_file_location("bridge_test_target", HERE / "bridge.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


bridge = load_bridge()


def message(uuid, sender, text):
    return {"messageUuid": uuid, "senderName": sender, "senderId": 1, "text": text}


class FormatHistoryTest(unittest.TestCase):

    def test_starts_after_last_bot_reply_and_skips_current(self):
        items = [
            message("1", "Morris", "old"),
            message("2", "opencode", "bot answer"),
            message("3", "dabuldakov", "follow up"),
            message("4", "Morris", "current"),
        ]

        lines = bridge.format_history(items, "opencode", "4")

        # Ответ бота тоже входит в контекст — с него начинается история.
        self.assertEqual(["@opencode: bot answer", "@dabuldakov: follow up"], lines)

    def test_uses_tail_when_bot_never_replied(self):
        items = [message(str(i), "Morris", f"m{i}") for i in range(20)]

        lines = bridge.format_history(items, "opencode", "19")

        self.assertEqual(15, len(lines))
        self.assertTrue(lines[0].endswith("m4"))
        self.assertTrue(lines[-1].endswith("m18"))

    def test_long_message_is_truncated_to_1500_chars(self):
        long_text = "x" * 5000

        lines = bridge.format_history([message("1", "Morris", long_text)], "opencode", "")

        self.assertEqual(len("@Morris: " + "x" * 1500), len(lines[0]))

    def test_newlines_are_collapsed(self):
        lines = bridge.format_history([message("1", "Morris", "a\nb")], "opencode", "")

        self.assertEqual("@Morris: a b", lines[0])

    def test_empty_messages_are_skipped(self):
        items = [message("1", "Morris", "   "), message("2", "Morris", "hi")]

        self.assertEqual(["@Morris: hi"], bridge.format_history(items, "opencode", ""))

    def test_total_history_is_capped_by_dropping_oldest(self):
        items = [message(str(i), "Morris", "y" * 1500) for i in range(15)]

        lines = bridge.format_history(items, "opencode", "")

        self.assertLessEqual(sum(len(line) + 1 for line in lines), 12000)
        self.assertTrue(lines[-1].endswith("y" * 1500))
        self.assertLess(len(lines), 15)


class ContinuationTest(unittest.TestCase):
    """Продолжения ответа: агент может ответить ещё раз уже после первого ответа."""

    def setUp(self):
        bridge.STATE.clear()
        bridge.STATE.update({"lastReplyTs": 1000, "processed": []})

    @staticmethod
    def reply(created, text, finish="stop"):
        return {
            "finish": finish,
            "time": {"created": created},
            "content": [{"type": "text", "text": text}],
        }

    def test_assistant_replies_includes_tool_call_preamble(self):
        payload = {
            "data": [
                self.reply(3000, "b"),
                self.reply(2000, "a"),
                self.reply(500, "old"),
                self.reply(4000, "ухожу проверять", finish="tool-calls"),
                self.reply(4500, "   "),
            ]
        }
        with mock.patch.object(bridge, "opencode_api", return_value=payload):
            self.assertEqual(
                [(2000, "a"), (3000, "b"), (4000, "ухожу проверять")],
                bridge.assistant_replies(1000),
            )

    def test_duplicate_reply_is_detected(self):
        self.assertTrue(bridge.is_duplicate_reply(
            "Поправил — beta4 во вложении. • Убрал контур.",
            "Поправил — beta4 во вложении. - Убрал контур.",
        ))
        self.assertFalse(bridge.is_duplicate_reply(
            "Совсем другой ответ", "Поправил — beta4 во вложении.",
        ))

    def test_file_message_and_final_text_are_recognized_as_duplicate(self):
        self.assertTrue(bridge.same_prefix(
            "Нашёл обе причины — beta5 во вложении. Пункт один.",
            "Нашёл обе причины — beta5 во вложении. 1. Пункт один.",
        ))
        self.assertFalse(bridge.same_prefix("Совсем другой ответ", "Нашёл обе причины"))

    def test_streaming_message_without_finish_is_skipped(self):
        payload = {"data": [self.reply(2000, "part", finish=None)]}
        with mock.patch.object(bridge, "opencode_api", return_value=payload):
            self.assertEqual([], bridge.assistant_replies(1000))

    def test_forward_continuations_posts_new_reply_and_marks_it(self):
        posted = []
        payload = {"data": [self.reply(2000, "result")]}
        with mock.patch.object(bridge, "opencode_api", return_value=payload), \
                mock.patch.object(
                    bridge, "post_to_chat",
                    side_effect=lambda chat, text: posted.append((chat, text))
                ), \
                mock.patch.object(bridge, "save_state"):
            bridge.forward_continuations("chat-1")

        self.assertEqual([("chat-1", "result")], posted)
        self.assertEqual(2000, bridge.STATE["lastReplyTs"])

    def test_forward_continuations_does_not_repeat_old_reply(self):
        posted = []
        payload = {"data": [self.reply(1000, "already sent")]}
        with mock.patch.object(bridge, "opencode_api", return_value=payload), \
                mock.patch.object(bridge, "post_to_chat", side_effect=posted.append), \
                mock.patch.object(bridge, "save_state"):
            bridge.forward_continuations("chat-1")

        self.assertEqual([], posted)

    def test_wait_for_reply_returns_first_reply_with_time(self):
        with mock.patch.object(
            bridge, "assistant_replies", return_value=[(1234, "hi")]
        ):
            self.assertEqual((1234, "hi"), bridge.wait_for_reply(1000))


if __name__ == "__main__":
    unittest.main(verbosity=2)
