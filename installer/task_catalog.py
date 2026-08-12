from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable


PROJECT_NAME = "izykube"


class Action(str, Enum):
    INSTALL = "install"
    UNINSTALL = "uninstall"
    VERIFY = "verify"


@dataclass(frozen=True)
class Task:
    id: str
    group: str
    install_target: str | None = None
    uninstall_target: str | None = None
    verify_target: str | None = None
    kind: str = "make"


@dataclass(frozen=True)
class OperationStep:
    command: tuple[str, ...]
    verb: str
    component_key: str


TASKS: tuple[Task, ...] = (
    Task(
        id="complete",
        group="procedure",
        kind="complete",
    ),
    Task(
        id="stack",
        group="infrastructure",
        kind="stack",
    ),
    Task(
        id="addons",
        group="platform",
        install_target="install-cluster-addons",
        uninstall_target="uninstall-cluster-addons",
        verify_target="check-cluster-addons",
    ),
    Task(
        id="argocd",
        group="components",
        install_target="install-argocd",
        uninstall_target="uninstall-argocd",
        verify_target="check-argocd",
    ),
    Task(
        id="olm",
        group="components",
        install_target="install-olm",
        uninstall_target="uninstall-olm",
        verify_target="check-olm",
    ),
    Task(
        id="cert-manager",
        group="components",
        install_target="install-cert-manager",
        uninstall_target="uninstall-cert-manager",
        verify_target="check-cert-manager",
    ),
    Task(
        id="internal-ca",
        group="components",
        install_target="create-internal-ca",
        uninstall_target="uninstall-internal-ca",
        verify_target="check-internal-ca",
    ),
    Task(
        id="istio",
        group="components",
        install_target="install-istio",
        uninstall_target="uninstall-istio",
        verify_target="check-istio",
    ),
    Task(
        id="gateway",
        group="components",
        install_target="install-istio-gateway",
        uninstall_target="uninstall-istio-gateway",
        verify_target="check-istio-gateway",
    ),
    Task(
        id="prometheus",
        group="components",
        install_target="install-prometheus",
        uninstall_target="uninstall-prometheus",
        verify_target="check-prometheus",
    ),
    Task(
        id="grafana",
        group="components",
        install_target="install-grafana-release",
        uninstall_target="uninstall-grafana",
        verify_target="check-grafana",
    ),
)


def task_by_id(task_id: str) -> Task:
    for task in TASKS:
        if task.id == task_id:
            return task
    raise KeyError(f"Unknown installer task: {task_id}")


def compose_command(*arguments: str) -> tuple[str, ...]:
    return ("docker", "compose", "-p", PROJECT_NAME, *arguments)


def setup_make_command(target: str) -> tuple[str, ...]:
    return compose_command(
        "--profile",
        "setup",
        "run",
        "--build",
        "--rm",
        "setup-tools",
        "make",
        "--no-print-directory",
        target,
    )


def _make_step(target: str, verb: str, component: str) -> OperationStep:
    return OperationStep(setup_make_command(target), verb, f"task.{component}.title")


def _addon_steps(action: Action) -> tuple[OperationStep, ...]:
    if action is Action.INSTALL:
        targets = (
            ("install-argocd", "argocd"),
            ("install-olm", "olm"),
            ("create-internal-ca", "cert-ca"),
            ("install-istio-gateway", "istio-gateway"),
            ("install-prometheus", "prometheus"),
            ("install-grafana-release", "grafana"),
        )
        return tuple(_make_step(target, "install", component) for target, component in targets)
    if action is Action.UNINSTALL:
        targets = (
            ("uninstall-grafana", "grafana"),
            ("uninstall-prometheus", "prometheus"),
            ("delete-istio-system-db", "monitoring-namespace"),
            ("uninstall-istio", "istio-gateway"),
            ("uninstall-cert-manager", "cert-ca"),
            ("uninstall-olm", "olm"),
            ("uninstall-argocd", "argocd"),
        )
        return tuple(_make_step(target, "remove", component) for target, component in targets)
    targets = (
        ("check-argocd", "argocd"),
        ("check-olm", "olm"),
        ("check-cert-manager", "cert-manager"),
        ("check-internal-ca", "internal-ca"),
        ("check-istio", "istio"),
        ("check-istio-gateway", "gateway"),
        ("check-prometheus", "prometheus"),
        ("check-grafana", "grafana"),
    )
    return tuple(_make_step(target, "verify", component) for target, component in targets)


def operation_plan(task: Task, action: Action) -> tuple[OperationStep, ...]:
    if task.kind == "complete":
        if action is Action.INSTALL:
            stack = OperationStep(("make", "--no-print-directory", "start-stack"), "start", "task.stack.title")
            return (stack, *_addon_steps(action))
        if action is Action.UNINSTALL:
            stack = OperationStep(("make", "--no-print-directory", "stop-stack"), "stop", "task.stack.title")
            return (*_addon_steps(action), stack)
        stack = OperationStep(("make", "--no-print-directory", "check-stack"), "verify", "task.stack.title")
        return (stack, *_addon_steps(action))

    if task.kind == "stack":
        if action is Action.INSTALL:
            return (OperationStep(("make", "--no-print-directory", "start-stack"), "start", "task.stack.title"),)
        if action is Action.UNINSTALL:
            return (OperationStep(("make", "--no-print-directory", "stop-stack"), "stop", "task.stack.title"),)
        return (OperationStep(("make", "--no-print-directory", "check-stack"), "verify", "task.stack.title"),)

    if task.id == "addons":
        return _addon_steps(action)

    target = {
        Action.INSTALL: task.install_target,
        Action.UNINSTALL: task.uninstall_target,
        Action.VERIFY: task.verify_target,
    }[action]
    if not target:
        raise ValueError(f"Task {task.id} does not support {action.value}")
    verb = {Action.INSTALL: "install", Action.UNINSTALL: "remove", Action.VERIFY: "verify"}[action]
    return (_make_step(target, verb, task.id),)


def command_plan(task: Task, action: Action) -> tuple[tuple[str, ...], ...]:
    return tuple(step.command for step in operation_plan(task, action))


def make_targets(tasks: Iterable[Task] = TASKS) -> set[str]:
    targets: set[str] = set()
    for task in tasks:
        for target in (task.install_target, task.uninstall_target, task.verify_target):
            if target:
                targets.add(target)
    targets.add("delete-istio-system-db")
    return targets
