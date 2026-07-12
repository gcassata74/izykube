from __future__ import annotations

import os
import queue
import signal
import subprocess
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


@dataclass(frozen=True)
class ProcessEvent:
    kind: str
    value: str | int | tuple[str, ...]


class ProcessRunner:
    def __init__(self) -> None:
        self.events: queue.Queue[ProcessEvent] = queue.Queue()
        self._process: subprocess.Popen[str] | None = None
        self._thread: threading.Thread | None = None
        self._lock = threading.Lock()

    @property
    def running(self) -> bool:
        with self._lock:
            return self._thread is not None and self._thread.is_alive()

    def start(self, commands: Sequence[Sequence[str]], cwd: Path) -> None:
        if self.running:
            raise RuntimeError("Another installer process is already running")
        normalized = tuple(tuple(command) for command in commands)
        self._thread = threading.Thread(
            target=self._run,
            args=(normalized, cwd),
            name="izykube-installer-runner",
            daemon=True,
        )
        self._thread.start()

    def terminate(self) -> None:
        with self._lock:
            process = self._process
        if process is None or process.poll() is not None:
            return
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            return

    def drain_events(self) -> list[ProcessEvent]:
        drained: list[ProcessEvent] = []
        while True:
            try:
                drained.append(self.events.get_nowait())
            except queue.Empty:
                return drained

    def _run(self, commands: tuple[tuple[str, ...], ...], cwd: Path) -> None:
        final_exit_code = 0
        try:
            for command in commands:
                self.events.put(ProcessEvent("command", command))
                process = subprocess.Popen(
                    command,
                    cwd=cwd,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT,
                    text=True,
                    encoding="utf-8",
                    errors="replace",
                    bufsize=1,
                    start_new_session=True,
                )
                with self._lock:
                    self._process = process
                assert process.stdout is not None
                with process.stdout:
                    for line in process.stdout:
                        self.events.put(ProcessEvent("output", line))
                final_exit_code = process.wait()
                with self._lock:
                    self._process = None
                self.events.put(ProcessEvent("command_exit", final_exit_code))
                if final_exit_code != 0:
                    break
        except Exception as exception:
            self.events.put(ProcessEvent("output", f"ERROR: {exception}\n"))
            final_exit_code = 1
        finally:
            with self._lock:
                self._process = None
            self.events.put(ProcessEvent("exit", final_exit_code))
