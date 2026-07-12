from __future__ import annotations


DECORATIVE_CHARACTERS = frozenset("#-=+.*_ ")


def activity_detail(output: str, limit: int = 220) -> str | None:
    for line in reversed(output.splitlines()):
        detail = line.strip()
        if not detail:
            continue
        if len(detail) >= 6 and set(detail) <= DECORATIVE_CHARACTERS:
            continue
        return detail[-limit:]
    return None
