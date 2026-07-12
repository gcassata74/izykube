from __future__ import annotations

import re
import unittest
from pathlib import Path

from installer.task_catalog import (
    Action,
    TASKS,
    command_plan,
    make_targets,
    operation_plan,
    setup_make_command,
    task_by_id,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]


class TaskCatalogTest(unittest.TestCase):
    def test_task_ids_are_unique(self) -> None:
        task_ids = [task.id for task in TASKS]
        self.assertEqual(len(task_ids), len(set(task_ids)))

    def test_all_make_targets_exist(self) -> None:
        makefile = PROJECT_ROOT.joinpath("Makefile").read_text(encoding="utf-8")
        missing = [target for target in make_targets() if not re.search(rf"^{re.escape(target)}\s*:", makefile, re.MULTILINE)]
        self.assertEqual([], missing)

    def test_component_commands_use_ephemeral_setup_tools(self) -> None:
        command = command_plan(task_by_id("grafana"), Action.INSTALL)[0]
        self.assertEqual(command, setup_make_command("install-grafana-release"))
        self.assertIn("--rm", command)
        self.assertIn("setup-tools", command)

    def test_complete_install_starts_compose_then_addons(self) -> None:
        commands = command_plan(task_by_id("complete"), Action.INSTALL)
        self.assertEqual("up", commands[0][4])
        self.assertEqual(
            ["install-olm", "create-internal-ca", "install-istio-gateway", "install-prometheus", "install-grafana-release"],
            [command[-1] for command in commands[1:]],
        )

    def test_complete_uninstall_keeps_volumes(self) -> None:
        commands = command_plan(task_by_id("complete"), Action.UNINSTALL)
        self.assertEqual("uninstall-grafana", commands[0][-1])
        self.assertEqual(("docker", "compose", "-p", "izykube", "down"), commands[-1])

    def test_addon_plan_exposes_visible_progress_steps(self) -> None:
        steps = operation_plan(task_by_id("addons"), Action.VERIFY)
        self.assertEqual(7, len(steps))
        self.assertEqual("verify", steps[0].verb)
        self.assertEqual("task.olm.title", steps[0].component_key)


if __name__ == "__main__":
    unittest.main()
