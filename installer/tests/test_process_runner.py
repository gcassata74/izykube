from __future__ import annotations

import sys
import time
import unittest
from pathlib import Path

from installer.process_runner import ProcessRunner


class ProcessRunnerTest(unittest.TestCase):
    def test_runs_commands_in_order_and_captures_output(self) -> None:
        runner = ProcessRunner()
        commands = (
            (sys.executable, "-c", "print('first')"),
            (sys.executable, "-c", "print('second')"),
        )
        runner.start(commands, Path.cwd())

        events = self._wait_for_exit(runner)
        output = "".join(str(event.value) for event in events if event.kind == "output")
        command_exit_codes = [event.value for event in events if event.kind == "command_exit"]
        exit_codes = [event.value for event in events if event.kind == "exit"]

        self.assertIn("first", output)
        self.assertIn("second", output)
        self.assertEqual([0, 0], command_exit_codes)
        self.assertEqual([0], exit_codes)

    def test_stops_sequence_after_failure(self) -> None:
        runner = ProcessRunner()
        commands = (
            (sys.executable, "-c", "raise SystemExit(7)"),
            (sys.executable, "-c", "print('must-not-run')"),
        )
        runner.start(commands, Path.cwd())

        events = self._wait_for_exit(runner)
        commands_started = [event.value for event in events if event.kind == "command"]
        command_exit_codes = [event.value for event in events if event.kind == "command_exit"]
        exit_codes = [event.value for event in events if event.kind == "exit"]

        self.assertEqual(1, len(commands_started))
        self.assertEqual([7], command_exit_codes)
        self.assertEqual([7], exit_codes)

    def _wait_for_exit(self, runner: ProcessRunner) -> list:
        deadline = time.monotonic() + 5
        events = []
        while time.monotonic() < deadline:
            events.extend(runner.drain_events())
            if any(event.kind == "exit" for event in events):
                return events
            time.sleep(0.02)
        self.fail("Process runner did not finish")


if __name__ == "__main__":
    unittest.main()
