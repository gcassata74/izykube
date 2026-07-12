from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from installer.paths import is_project_root


class PathsTest(unittest.TestCase):
    def test_project_root_requires_compose_and_makefile(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertFalse(is_project_root(root))
            root.joinpath("docker-compose.yml").touch()
            self.assertFalse(is_project_root(root))
            root.joinpath("Makefile").touch()
            self.assertTrue(is_project_root(root))


if __name__ == "__main__":
    unittest.main()
