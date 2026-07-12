from __future__ import annotations

import string
import unittest

from installer.i18n import DEFAULT_LANGUAGE, SUPPORTED_LANGUAGES, TRANSLATIONS, translate
from installer.task_catalog import TASKS


class TranslationsTest(unittest.TestCase):
    def test_all_languages_have_the_same_keys_and_placeholders(self) -> None:
        default_messages = TRANSLATIONS[DEFAULT_LANGUAGE]
        formatter = string.Formatter()

        for language in SUPPORTED_LANGUAGES:
            messages = TRANSLATIONS[language]
            self.assertEqual(default_messages.keys(), messages.keys())
            for key, default_text in default_messages.items():
                default_fields = {field for _, field, _, _ in formatter.parse(default_text) if field}
                translated_fields = {field for _, field, _, _ in formatter.parse(messages[key]) if field}
                self.assertEqual(default_fields, translated_fields, f"Placeholder mismatch: {language}.{key}")

    def test_every_task_has_translated_ui_text(self) -> None:
        for language in SUPPORTED_LANGUAGES:
            for task in TASKS:
                for suffix in ("title", "description", "warning"):
                    self.assertTrue(translate(language, f"task.{task.id}.{suffix}"))

    def test_unknown_language_uses_english(self) -> None:
        self.assertEqual(translate("unknown", "status.ready"), "Ready")


if __name__ == "__main__":
    unittest.main()
