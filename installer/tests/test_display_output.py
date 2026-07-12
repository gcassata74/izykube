from __future__ import annotations

import unittest

from installer.display_output import activity_detail


class ActivityDetailTest(unittest.TestCase):
    def test_ignores_helm_separators(self) -> None:
        self.assertIsNone(activity_detail("########################################\n"))

    def test_ignores_openssl_progress_noise(self) -> None:
        self.assertIsNone(activity_detail("..+...+....++++++++++++++++++++++++++++++++++++\n"))

    def test_keeps_meaningful_output(self) -> None:
        self.assertEqual("Grafana installed", activity_detail("Grafana installed\n########\n"))


if __name__ == "__main__":
    unittest.main()
