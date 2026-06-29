#!/usr/bin/env python3
"""IzyKube installer/uninstaller orchestrator.

Uses Make targets as atomic steps and provides a robust Python UI layer:
- progress bar
- per-step logs
- retry on install failures
- optional resume from the last failed install step
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from queue import Empty, Queue
from threading import Thread
from typing import List, Tuple

PROJECT_DIR = Path(__file__).resolve().parent.parent
LOG_DIR = Path("/tmp")
INSTALL_LOG_PREFIX = LOG_DIR / "izykube-install"
UNINSTALL_LOG_PREFIX = LOG_DIR / "izykube-uninstall"
STATE_FILE = LOG_DIR / "izykube-install-state.json"
WINDOW_ICON_PATH = PROJECT_DIR / "frontend" / "src" / "assets" / "images" / "logo" / "izylife.png"

INSTALL_STEPS: List[Tuple[str, str]] = [
    ("check-kube-connection", "Checking cluster connectivity"),
    ("install-olm", "Installing OLM"),
    ("create-internal-ca", "Installing cert-manager & CA"),
    ("install-istio-gateway", "Installing Istio & Gateway"),
    ("install-prometheus", "Installing Prometheus"),
    ("install-grafana-release", "Installing Grafana"),
    ("install-argocd", "Installing Argo CD"),
]

UNINSTALL_STEPS: List[Tuple[str, str]] = [
    ("uninstall-argocd", "Removing Argo CD"),
    ("uninstall-prometheus", "Removing Prometheus"),
    ("uninstall-grafana-release", "Removing Grafana"),
    ("uninstall-istio-gateway", "Removing Istio Gateway"),
    ("uninstall-internal-ca", "Removing internal CA"),
    ("uninstall-cert-manager", "Removing cert-manager"),
    ("uninstall-istio", "Removing Istio"),
    ("uninstall-olm", "Removing OLM"),
]


@dataclass
class StepResult:
    ok: bool
    log_file: Path
    label: str


def _default_model() -> str:
    app_yaml = PROJECT_DIR / "backend" / "src" / "main" / "resources" / "application.yaml"
    if not app_yaml.exists():
        return "llama3"
    for line in app_yaml.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("model:"):
            _, _, value = stripped.partition(":")
            value = value.strip()
            return value or "llama3"
    return "llama3"


def _run_make(target: str, log_file: Path, extra_env: dict[str, str] | None = None) -> int:
    env = os.environ.copy()
    if extra_env:
        env.update(extra_env)

    with log_file.open("w", encoding="utf-8") as logf:
        proc = subprocess.run(
            ["make", target],
            cwd=PROJECT_DIR,
            env=env,
            stdout=logf,
            stderr=subprocess.STDOUT,
            check=False,
            text=True,
        )
    return proc.returncode


def _run_make_stream(
    target: str,
    log_file: Path,
    on_line: callable | None = None,
    extra_env: dict[str, str] | None = None,
) -> int:
    env = os.environ.copy()
    if extra_env:
        env.update(extra_env)

    with log_file.open("w", encoding="utf-8") as logf:
        proc = subprocess.Popen(
            ["make", target],
            cwd=PROJECT_DIR,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        assert proc.stdout is not None
        for raw in proc.stdout:
            line = raw.rstrip("\n")
            logf.write(raw)
            if on_line is not None and line.strip():
                on_line(line)
        return proc.wait()


def _run_make_with_retries(
    target: str,
    label: str,
    log_file: Path,
    retries: int,
    on_line: callable | None = None,
    extra_env: dict[str, str] | None = None,
) -> StepResult:
    rc = 1
    for attempt in range(retries + 1):
        rc = _run_make_stream(target, log_file, on_line=on_line, extra_env=extra_env)
        if rc == 0:
            return StepResult(ok=True, log_file=log_file, label=label)
        if attempt < retries:
            time.sleep(4)
    return StepResult(ok=False, log_file=log_file, label=label)


def _tail(path: Path, count: int = 10) -> List[str]:
    if not path.exists():
        return ["(log file not found)"]
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return lines[-count:] if lines else ["(empty log)"]


def _save_state(step_index: int, model: str) -> None:
    STATE_FILE.write_text(json.dumps({"step_index": step_index, "model": model}), encoding="utf-8")


def _load_state() -> tuple[int, str] | None:
    if not STATE_FILE.exists():
        return None
    try:
        data = json.loads(STATE_FILE.read_text(encoding="utf-8"))
        step_index = int(data.get("step_index", 0))
        model = str(data.get("model", "llama3"))
        if step_index < 0:
            return None
        return step_index, model
    except Exception:
        return None


def _clear_state() -> None:
    try:
        STATE_FILE.unlink(missing_ok=True)
    except Exception:
        pass


def _gui_supported() -> bool:
    # On Linux, GUI requires DISPLAY or WAYLAND_DISPLAY.
    if sys.platform.startswith("linux") and not (os.environ.get("DISPLAY") or os.environ.get("WAYLAND_DISPLAY")):
        return False
    return True


def run_gui(command: str, dry_run: bool, retries: int, model_arg: str | None) -> int:
    if not _gui_supported():
        print("ERROR: GUI mode requested but DISPLAY is not set.")
        print("Run from a desktop session, or unset --gui to use terminal mode.")
        return 2

    try:
        from PySide6.QtCore import QTimer, Qt
        from PySide6.QtGui import QIcon
        from PySide6.QtWidgets import (
            QApplication,
            QHBoxLayout,
            QLabel,
            QLineEdit,
            QMessageBox,
            QPushButton,
            QProgressBar,
            QTextEdit,
            QVBoxLayout,
            QWidget,
        )
    except Exception:
        print("ERROR: GUI mode requested but PySide6 is not available.")
        print("Install it with: pip install -r requirements.txt")
        return 2

    queue: Queue[tuple[str, object]] = Queue()
    app = QApplication.instance() or QApplication(sys.argv)
    if WINDOW_ICON_PATH.exists():
        app.setWindowIcon(QIcon(str(WINDOW_ICON_PATH)))

    class InstallerWindow(QWidget):
        def __init__(self) -> None:
            super().__init__()
            self.running = False
            self.result_code = 0
            self.command = command
            self.is_install = self.command == "install"

            self.setWindowTitle("IzyKube Installer")
            if WINDOW_ICON_PATH.exists():
                self.setWindowIcon(QIcon(str(WINDOW_ICON_PATH)))
            self.resize(820, 520)

            main_layout = QVBoxLayout(self)

            title = "Install cluster addons" if self.is_install else "Uninstall cluster addons"
            main_layout.addWidget(QLabel(f"<h3>{title}</h3>"))

            top = QHBoxLayout()
            self.model_input = QLineEdit(model_arg or _default_model())
            if self.is_install:
                top.addWidget(QLabel("Ollama model:"))
                top.addWidget(self.model_input)
            main_layout.addLayout(top)

            self.status_label = QLabel("Ready")
            main_layout.addWidget(self.status_label)

            self.progress = QProgressBar()
            self.progress.setValue(0)
            main_layout.addWidget(self.progress)

            self.log = QTextEdit()
            self.log.setReadOnly(True)
            main_layout.addWidget(self.log)

            buttons = QHBoxLayout()
            self.start_btn = QPushButton("Start")
            self.close_btn = QPushButton("Close")
            self.start_btn.clicked.connect(self.start)
            self.close_btn.clicked.connect(self.close)
            # Install/uninstall starts automatically when window opens.
            # Keep only Close visible for a simpler UX.
            self.start_btn.setVisible(False)
            self.start_btn.setEnabled(False)
            buttons.addWidget(self.start_btn)
            buttons.addWidget(self.close_btn)
            main_layout.addLayout(buttons)

            self.timer = QTimer(self)
            self.timer.setInterval(120)
            self.timer.timeout.connect(self.poll_queue)
            self.timer.start()

            self._startup_pending = True
            self._confirm_retry_count = 0

        def showEvent(self, event) -> None:  # type: ignore[override]
            super().showEvent(event)
            if self._startup_pending:
                self._startup_pending = False
                # Start install immediately; ask confirmation only for uninstall.
                if self.is_install:
                    QTimer.singleShot(0, self.start)
                else:
                    QTimer.singleShot(200, self._show_uninstall_confirm_when_ready)

        def _show_uninstall_confirm_when_ready(self) -> None:
            handle = self.windowHandle()
            ready = self.isVisible() and (handle is None or handle.isExposed())
            if not ready and self._confirm_retry_count < 20:
                self._confirm_retry_count += 1
                QTimer.singleShot(150, self._show_uninstall_confirm_when_ready)
                return
            self._confirm_uninstall_start()

        def _confirm_uninstall_start(self) -> None:
            dialog = QMessageBox(self)
            dialog.setIcon(QMessageBox.Question)
            dialog.setWindowTitle("IzyKube")
            dialog.setText("Do you want to start uninstall now?")
            dialog.setStandardButtons(QMessageBox.Yes | QMessageBox.No)
            dialog.setDefaultButton(QMessageBox.No)
            dialog.setWindowModality(Qt.WindowModal)
            dialog.setOption(QMessageBox.DontUseNativeDialog, True)

            # Keep dialog visually inside the main window bounds.
            dialog.adjustSize()
            parent_geo = self.frameGeometry()
            rect = dialog.frameGeometry()
            target_x = parent_geo.x() + (parent_geo.width() - rect.width()) // 2
            target_y = parent_geo.y() + (parent_geo.height() - rect.height()) // 2
            min_x = parent_geo.left() + 8
            max_x = parent_geo.right() - rect.width() - 8
            min_y = parent_geo.top() + 32
            max_y = parent_geo.bottom() - rect.height() - 8
            x = max(min_x, min(target_x, max_x))
            y = max(min_y, min(target_y, max_y))
            dialog.move(x, y)

            self.raise_()
            self.activateWindow()
            answer = dialog.exec()
            if answer == QMessageBox.Yes:
                self.start()
            else:
                self.running = False
                self.status_label.setText("Waiting - close to exit")
                self.append_log("Operation not started (user declined confirmation).")

        def append_log(self, text: str) -> None:
            self.log.append(text)

        def worker_install(self) -> None:
            model = self.model_input.text().strip() or _default_model()
            start_step = 0

            state = _load_state()
            if state and not dry_run:
                saved_step, saved_model = state
                if saved_step < len(INSTALL_STEPS):
                    queue.put(("ask_resume", (saved_step, saved_model, model)))
                    return

            queue.put(("start_install", (start_step, model)))

        def run_install_steps(self, start_step: int, model: str) -> None:
            total = len(INSTALL_STEPS) + 1
            queue.put(("set_max", total))
            queue.put(("log", f"Installing with model: {model}"))

            for idx in range(start_step, len(INSTALL_STEPS)):
                target, label = INSTALL_STEPS[idx]
                queue.put(("progress", (idx, label)))
                log_file = INSTALL_LOG_PREFIX.with_name(f"{INSTALL_LOG_PREFIX.name}-{target}.log")

                if dry_run:
                    time.sleep(0.05)
                    continue

                queue.put(("log", f"[{label}]"))
                result = _run_make_with_retries(
                    target,
                    label,
                    log_file,
                    retries,
                    on_line=lambda line: queue.put(("log", line)),
                )
                if not result.ok:
                    _save_state(idx, model)
                    queue.put(("failed", (label, log_file, _tail(log_file, 12))))
                    return
                _save_state(idx + 1, model)

            queue.put(("progress", (len(INSTALL_STEPS), f"Pulling Ollama model: {model}")))
            if not dry_run:
                ollama_log = INSTALL_LOG_PREFIX.with_name(f"{INSTALL_LOG_PREFIX.name}-setup-ollama.log")
                queue.put(("log", "[Pulling Ollama model]"))
                rc = _run_make_stream(
                    "setup-ollama",
                    ollama_log,
                    on_line=lambda line: queue.put(("log", line)),
                    extra_env={"OLLAMA_MODEL": model},
                )
                if rc != 0:
                    queue.put(("log", f"WARNING: Ollama pull failed, see {ollama_log}"))

            _clear_state()
            queue.put(("done", f"Install complete. Logs: {INSTALL_LOG_PREFIX}-*.log"))

        def worker_uninstall(self) -> None:
            self.run_uninstall_steps()

        def run_uninstall_steps(self) -> None:
            total = len(UNINSTALL_STEPS)
            queue.put(("set_max", total))
            for idx, (target, label) in enumerate(UNINSTALL_STEPS):
                queue.put(("progress", (idx, label)))
                log_file = UNINSTALL_LOG_PREFIX.with_name(f"{UNINSTALL_LOG_PREFIX.name}-{target}.log")
                if dry_run:
                    time.sleep(0.05)
                    continue
                queue.put(("log", f"[{label}]"))
                _run_make_stream(target, log_file, on_line=lambda line: queue.put(("log", line)))

            if not dry_run:
                cleanup_log = UNINSTALL_LOG_PREFIX.with_name(f"{UNINSTALL_LOG_PREFIX.name}-namespace-cleanup.log")
                with cleanup_log.open("w", encoding="utf-8") as logf:
                    subprocess.run(
                        ["kubectl", "delete", "namespace", "istio-system-db", "--ignore-not-found=true"],
                        cwd=PROJECT_DIR,
                        stdout=logf,
                        stderr=subprocess.STDOUT,
                        check=False,
                        text=True,
                    )

            queue.put(("done", f"Uninstall complete. Logs: {UNINSTALL_LOG_PREFIX}-*.log"))

        def start(self) -> None:
            if self.running:
                return
            self.running = True
            self.start_btn.setEnabled(False)
            self.status_label.setText("Running...")

            if self.is_install:
                Thread(target=self.worker_install, daemon=True).start()
            else:
                Thread(target=self.worker_uninstall, daemon=True).start()

        def closeEvent(self, event) -> None:  # type: ignore[override]
            if self.running:
                answer = QMessageBox.question(
                    self,
                    "IzyKube",
                    "Process still running. Close window anyway?",
                    QMessageBox.Yes | QMessageBox.No,
                )
                if answer != QMessageBox.Yes:
                    event.ignore()
                    return
            event.accept()

        def poll_queue(self) -> None:
            try:
                while True:
                    event, payload = queue.get_nowait()
                    if event == "set_max":
                        self.progress.setMaximum(int(payload))
                    elif event == "progress":
                        idx, label = payload  # type: ignore[misc]
                        self.progress.setValue(int(idx))
                        self.status_label.setText(str(label))
                    elif event == "log":
                        self.append_log(str(payload))
                    elif event == "failed":
                        label, log_file, lines = payload  # type: ignore[misc]
                        self.running = False
                        self.status_label.setText("Failed")
                        self.append_log(f"FAILED: {label}")
                        self.append_log(f"Log: {log_file}")
                        self.append_log("Last lines:")
                        for line in lines:
                            self.append_log(f"  {line}")
                        QMessageBox.critical(self, "IzyKube", f"{label} failed.\nSee log:\n{log_file}")
                        self.result_code = 1
                    elif event == "done":
                        self.running = False
                        if self.is_install:
                            self.progress.setValue(len(INSTALL_STEPS) + 1)
                        else:
                            self.progress.setValue(len(UNINSTALL_STEPS))
                        self.status_label.setText("Done")
                        self.append_log(str(payload))
                        QMessageBox.information(self, "IzyKube", str(payload))
                    elif event == "ask_resume":
                        saved_step, saved_model, entered_model = payload  # type: ignore[misc]
                        answer = QMessageBox.question(
                            self,
                            "IzyKube",
                            f"Resume previous install from step {int(saved_step) + 1}?",
                            QMessageBox.Yes | QMessageBox.No,
                        )
                        if answer == QMessageBox.Yes:
                            Thread(
                                target=self.run_install_steps,
                                args=(int(saved_step), str(saved_model or entered_model)),
                                daemon=True,
                            ).start()
                        else:
                            _clear_state()
                            Thread(target=self.run_install_steps, args=(0, str(entered_model)), daemon=True).start()
                    elif event == "start_install":
                        start_step, model = payload  # type: ignore[misc]
                        Thread(target=self.run_install_steps, args=(int(start_step), str(model)), daemon=True).start()
            except Empty:
                pass

    window = InstallerWindow()
    window.show()
    app.exec()
    return window.result_code


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="IzyKube installer wrapper")
    parser.add_argument("command", choices=["install", "uninstall"], help="operation to run")
    parser.add_argument("--model", help="Ollama model (install only)")
    parser.add_argument("--retries", type=int, default=1, help="retries per install step (default: 1)")
    parser.add_argument("--dry-run", action="store_true", help="simulate steps without running make targets")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        return run_gui(
            command=args.command,
            dry_run=args.dry_run,
            retries=max(0, args.retries),
            model_arg=args.model,
        )
    except KeyboardInterrupt:
        print("\nInterrupted by user.")
        return 130


if __name__ == "__main__":
    raise SystemExit(main())

