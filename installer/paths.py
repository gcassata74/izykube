from __future__ import annotations

import os
import sys
from pathlib import Path


def is_project_root(path: Path) -> bool:
    return path.joinpath("docker-compose.yml").is_file() and path.joinpath("Makefile").is_file()


def find_project_root() -> Path:
    candidates: list[Path] = []
    configured = os.environ.get("IZYKUBE_HOME")
    if configured:
        candidates.append(Path(configured))

    candidates.extend(
        (
            Path.cwd(),
            Path(__file__).resolve().parent.parent,
            Path(sys.executable).resolve().parent,
            Path(sys.executable).resolve().parent.parent,
        )
    )

    visited: set[Path] = set()
    for candidate in candidates:
        current = candidate.expanduser().resolve()
        for path in (current, *current.parents):
            if path in visited:
                continue
            visited.add(path)
            if is_project_root(path):
                return path
    raise FileNotFoundError("Unable to find docker-compose.yml and Makefile")
