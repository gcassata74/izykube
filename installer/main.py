from __future__ import annotations

import shlex
import shutil
import sys
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import filedialog, messagebox

import ttkbootstrap as ttk
from PIL import Image, ImageTk
from ttkbootstrap.style import Colors, ThemeDefinition

from installer.display_output import activity_detail
from installer.i18n import LANGUAGE_NAMES, detect_language, translate
from installer.paths import find_project_root, is_project_root
from installer.process_runner import ProcessRunner
from installer.task_catalog import Action, OperationStep, TASKS, Task, command_plan, operation_plan, task_by_id


def logo_path(project_root: Path) -> Path:
    bundle_root = getattr(sys, "_MEIPASS", None)
    if bundle_root:
        return Path(bundle_root, "assets", "izylife.png")
    return project_root / "frontend" / "src" / "assets" / "images" / "logo" / "izylife.png"


class InstallerApp:
    def __init__(self, root: tk.Tk, project_root: Path, language: str | None = None) -> None:
        self.root = root
        self.project_root = project_root
        self.language = language or detect_language()
        self.language_name = tk.StringVar(value=LANGUAGE_NAMES[self.language])
        self.runner = ProcessRunner()
        self.selected_task_id = "complete"
        self.task_status: dict[str, tuple[str, dict[str, object]]] = {
            task.id: ("status.not_run", {}) for task in TASKS
        }
        self.active_task_id = ""
        self.active_action: Action | None = None
        self.active_steps: tuple[OperationStep, ...] = ()
        self.step_states: list[str] = []
        self.current_step_index = -1
        self.log_visible = False
        self.current_status: tuple[str, dict[str, object]] = ("status.ready", {})
        self.status_text = tk.StringVar(value=self._t("status.ready"))
        self._closing = False

        self._configure_window()
        self._build_ui()
        self._populate_tasks()
        self._refresh_translations()
        self.root.after(100, self._poll_runner)
        self.root.after(300, self._initial_preflight)

    def _configure_window(self) -> None:
        self.root.title(self._t("app.title"))
        self.root.geometry("980x760")
        self.root.minsize(820, 650)
        self.root.protocol("WM_DELETE_WINDOW", self._request_close)
        style = ttk.Style()
        style.register_theme(
            ThemeDefinition(
                name="izykube",
                themetype="dark",
                colors=Colors(
                    primary="#21d9b3",
                    secondary="#74809a",
                    success="#21d9b3",
                    info="#66adff",
                    warning="#f6c85f",
                    danger="#ff6565",
                    light="#f2f6ff",
                    dark="#090f1d",
                    bg="#0c1221",
                    fg="#f2f6ff",
                    selectbg="#1a2440",
                    selectfg="#f2f6ff",
                    border="#34405c",
                    inputfg="#f2f6ff",
                    inputbg="#141c31",
                    active="#1a2440",
                ),
            )
        )
        style.theme_use("izykube")
        style.configure("Brand.TLabel", font=("DejaVu Sans", 17, "bold"), foreground="#f2f6ff")
        style.configure("Setup.TLabel", font=("DejaVu Sans", 9, "bold"), foreground="#21d9b3")
        style.configure("Subtitle.TLabel", foreground="#b7c2d8")
        style.configure("Installer.Treeview", rowheight=29)

    def _build_ui(self) -> None:
        container = ttk.Frame(self.root, padding=24)
        container.pack(fill=tk.BOTH, expand=True)

        header = ttk.Frame(container)
        header.pack(fill=tk.X)
        brand = ttk.Frame(header)
        brand.pack(side=tk.LEFT)
        with Image.open(logo_path(self.project_root)) as logo_source:
            logo_source = logo_source.convert("RGBA")
            self.logo_image = ImageTk.PhotoImage(logo_source.resize((125, 42), Image.Resampling.LANCZOS))
            icon_source = logo_source.crop((60, 40, 410, 390))
            self.window_icon = ImageTk.PhotoImage(icon_source.resize((32, 32), Image.Resampling.LANCZOS))
        self.root.iconphoto(True, self.window_icon)
        ttk.Label(brand, image=self.logo_image).pack(side=tk.LEFT)
        brand_text = ttk.Frame(brand)
        brand_text.pack(side=tk.LEFT, padx=(12, 0))
        self.title_label = ttk.Label(brand_text, style="Brand.TLabel")
        self.title_label.pack(anchor=tk.W)
        self.setup_label = ttk.Label(brand_text, style="Setup.TLabel")
        self.setup_label.pack(anchor=tk.W)

        language_box = ttk.Frame(header)
        language_box.pack(side=tk.RIGHT)
        self.language_label = ttk.Label(language_box, style="Subtitle.TLabel")
        self.language_label.pack(side=tk.LEFT, padx=(0, 8))
        self.language_combo = ttk.Combobox(
            language_box,
            textvariable=self.language_name,
            values=tuple(LANGUAGE_NAMES.values()),
            state="readonly",
            width=10,
        )
        self.language_combo.pack(side=tk.LEFT)
        self.language_combo.bind("<<ComboboxSelected>>", self._change_language)

        ttk.Separator(container, bootstyle="secondary").pack(fill=tk.X, pady=(18, 16))
        self.subtitle_label = ttk.Label(container, style="Subtitle.TLabel", font=("TkDefaultFont", 11))
        self.subtitle_label.pack(anchor=tk.W, pady=(0, 12))

        selection_card = ttk.Labelframe(container, padding=14, bootstyle="secondary")
        selection_card.pack(fill=tk.X)
        selection_card.columnconfigure(1, weight=1)
        self.selection_card = selection_card
        self.component_label = ttk.Label(selection_card, style="Subtitle.TLabel")
        self.component_label.grid(row=0, column=0, sticky=tk.W, padx=(0, 10))
        self.task_combo = ttk.Combobox(selection_card, state="readonly", width=38)
        self.task_combo.grid(row=0, column=1, sticky="ew")
        self.task_combo.bind("<<ComboboxSelected>>", self._on_selection)
        self.selected_status = ttk.Label(selection_card, padding=(10, 5), bootstyle="secondary-inverse")
        self.selected_status.grid(row=0, column=2, padx=(12, 0))
        self.description = ttk.Label(selection_card, wraplength=850, style="Subtitle.TLabel")
        self.description.grid(row=1, column=0, columnspan=3, sticky="ew", pady=(10, 12))

        actions = ttk.Frame(selection_card)
        actions.grid(row=2, column=0, columnspan=3, sticky="ew")
        self.install_button = ttk.Button(
            actions,
            command=lambda: self._run_selected(Action.INSTALL),
            bootstyle="primary",
            padding=(16, 8),
        )
        self.verify_button = ttk.Button(
            actions,
            command=lambda: self._run_selected(Action.VERIFY),
            bootstyle="secondary-outline",
            padding=(12, 8),
        )
        self.uninstall_button = ttk.Button(
            actions,
            command=lambda: self._run_selected(Action.UNINSTALL),
            bootstyle="danger-outline",
            padding=(12, 8),
        )
        self.cancel_button = ttk.Button(
            actions,
            state=tk.DISABLED,
            command=self._cancel,
            bootstyle="warning-outline",
            padding=(12, 8),
        )
        self.install_button.pack(side=tk.LEFT)
        self.verify_button.pack(side=tk.LEFT, padx=8)
        self.uninstall_button.pack(side=tk.LEFT)
        self.cancel_button.pack(side=tk.RIGHT)

        utilities = ttk.Frame(selection_card)
        utilities.grid(row=3, column=0, columnspan=3, sticky="ew", pady=(12, 0))
        self.preflight_button = ttk.Button(utilities, command=self._run_preflight, bootstyle="link")
        self.preflight_button.pack(side=tk.LEFT)
        self.open_button = ttk.Button(
            utilities,
            command=lambda: webbrowser.open("http://localhost:8090"),
            bootstyle="link",
        )
        self.open_button.pack(side=tk.LEFT, padx=8)
        self.save_log_button = ttk.Button(utilities, command=self._save_log, bootstyle="link")
        self.save_log_button.pack(side=tk.LEFT)

        activity_frame = ttk.Labelframe(container, padding=14, bootstyle="secondary")
        activity_frame.pack(fill=tk.BOTH, expand=True, pady=(14, 0))
        activity_frame.columnconfigure(0, weight=1)
        activity_frame.rowconfigure(4, weight=1)
        self.activity_frame = activity_frame
        self.current_activity = ttk.Label(activity_frame, font=("TkDefaultFont", 14, "bold"))
        self.current_activity.grid(row=0, column=0, sticky=tk.W)
        self.activity_detail = ttk.Label(activity_frame, style="Subtitle.TLabel", wraplength=850)
        self.activity_detail.grid(row=1, column=0, sticky="ew", pady=(2, 8))

        progress_row = ttk.Frame(activity_frame)
        progress_row.grid(row=3, column=0, sticky="ew")
        progress_row.columnconfigure(0, weight=1)
        self.progress = ttk.Progressbar(
            progress_row,
            mode="determinate",
            maximum=100,
            value=0,
            bootstyle="success-striped",
        )
        self.progress.grid(row=1, column=0, sticky="ew")
        self.step_counter = ttk.Label(progress_row, width=16, anchor=tk.E)
        self.step_counter.grid(row=0, column=0, sticky=tk.E, pady=(0, 4))

        self.steps_tree = ttk.Treeview(
            activity_frame,
            columns=("state", "phase"),
            show="headings",
            height=5,
            selectmode="none",
            bootstyle="primary",
        )
        self.steps_tree.column("state", width=130, anchor=tk.CENTER, stretch=False)
        self.steps_tree.column("phase", width=700, stretch=True)
        self.steps_tree.tag_configure("pending", foreground="#6c757d")
        self.steps_tree.tag_configure("running", foreground="#0d6efd")
        self.steps_tree.tag_configure("completed", foreground="#198754")
        self.steps_tree.tag_configure("failed", foreground="#dc3545")
        self.steps_tree.grid(row=4, column=0, sticky="nsew", pady=(8, 6))
        self.steps_tree.grid_remove()

        self.log_toggle_button = ttk.Button(
            activity_frame,
            command=self._toggle_log,
            bootstyle="secondary-outline",
        )
        self.log_toggle_button.grid(row=5, column=0, sticky=tk.W)
        self.log_frame = ttk.Labelframe(activity_frame, padding=6, bootstyle="secondary")
        self.log = tk.Text(
            self.log_frame,
            height=8,
            wrap=tk.NONE,
            background="#111827",
            foreground="#e5e7eb",
            insertbackground="white",
            font=("TkFixedFont", 10),
        )
        log_y = ttk.Scrollbar(self.log_frame, orient=tk.VERTICAL, command=self.log.yview)
        log_x = ttk.Scrollbar(self.log_frame, orient=tk.HORIZONTAL, command=self.log.xview)
        self.log.configure(yscrollcommand=log_y.set, xscrollcommand=log_x.set)
        self.log.grid(row=0, column=0, sticky="nsew")
        log_y.grid(row=0, column=1, sticky="ns")
        log_x.grid(row=1, column=0, sticky="ew")
        self.log_frame.columnconfigure(0, weight=1)
        self.log_frame.rowconfigure(0, weight=1)
        footer = ttk.Frame(container)
        footer.pack(fill=tk.X, pady=(10, 0))
        ttk.Label(footer, textvariable=self.status_text, bootstyle="secondary").pack(side=tk.LEFT)
        self.project_label = ttk.Label(footer, style="Subtitle.TLabel")
        self.project_label.pack(side=tk.RIGHT)

    def _t(self, key: str, **values: object) -> str:
        return translate(self.language, key, **values)

    def _set_status(self, key: str, **values: object) -> None:
        self.current_status = (key, values)
        self.status_text.set(self._t(key, **values))

    def _translated_status(self, status: tuple[str, dict[str, object]]) -> str:
        key, values = status
        return self._t(key, **values)

    def _step_label(self, step: OperationStep) -> str:
        return self._t(f"step.{step.verb}", component=self._t(step.component_key))

    def _step_state_label(self, state: str) -> str:
        marker = {"pending": "○", "running": "●", "completed": "✓", "failed": "✕"}[state]
        return f"{marker} {self._t(f'step.{state}')}"

    def _change_language(self, _event: object | None = None) -> None:
        selected_name = self.language_name.get()
        self.language = next(code for code, name in LANGUAGE_NAMES.items() if name == selected_name)
        self._refresh_translations()

    def _refresh_translations(self) -> None:
        self.root.title(self._t("app.title"))
        self.title_label.configure(text="IzyKube")
        self.setup_label.configure(text=self._t("app.setup"))
        self.subtitle_label.configure(text=self._t("app.subtitle"))
        self.language_label.configure(text=self._t("language.label"))
        self.selection_card.configure(text=self._t("selection.title"))
        self.component_label.configure(text=self._t("selection.component"))
        self.preflight_button.configure(text=self._t("toolbar.preflight"))
        self.open_button.configure(text=self._t("toolbar.open"))
        self.save_log_button.configure(text=self._t("toolbar.save_log"))
        self.install_button.configure(text=self._t("button.install"))
        self.uninstall_button.configure(text=self._t("button.uninstall"))
        self.verify_button.configure(text=self._t("button.verify"))
        self.cancel_button.configure(text=self._t("button.cancel"))
        self.activity_frame.configure(text=self._t("activity.title"))
        self.steps_tree.heading("state", text=self._t("tree.status"))
        self.steps_tree.heading("phase", text=self._t("tree.component"))
        self.log_frame.configure(text=self._t("activity.technical_details"))
        self.project_label.configure(text=f"{self._t('selection.project')}: {self.project_root}")
        self.log_toggle_button.configure(
            text=self._t("activity.hide_log" if self.log_visible else "activity.show_log")
        )
        self.status_text.set(self._translated_status(self.current_status))
        self.task_combo.configure(values=tuple(self._t(f"task.{task.id}.title") for task in TASKS))
        self.task_combo.current(next(index for index, task in enumerate(TASKS) if task.id == self.selected_task_id))

        if self.active_steps:
            for index, step in enumerate(self.active_steps):
                self.steps_tree.item(
                    f"step-{index}",
                    values=(self._step_state_label(self.step_states[index]), self._step_label(step)),
                    tags=(self.step_states[index],),
                )
            if self.current_step_index >= 0:
                self.current_activity.configure(text=self._step_label(self.active_steps[self.current_step_index]))
                self.step_counter.configure(
                    text=self._t("activity.step", current=self.current_step_index + 1, total=len(self.active_steps))
                )
            else:
                self.current_activity.configure(text=self._t("activity.waiting"))
                self.step_counter.configure(text=self._t("activity.step", current=0, total=len(self.active_steps)))
        else:
            self.current_activity.configure(text=self._t("activity.waiting"))
            self.activity_detail.configure(text=self._t("activity.waiting_detail"))
            self.step_counter.configure(text="")

        self._on_selection()

    def _toggle_log(self) -> None:
        self.log_visible = not self.log_visible
        if self.log_visible:
            self.log_frame.grid(row=6, column=0, sticky="nsew", pady=(6, 0))
        else:
            self.log_frame.grid_remove()
        self.log_toggle_button.configure(
            text=self._t("activity.hide_log" if self.log_visible else "activity.show_log")
        )

    def _prepare_steps(self, steps: tuple[OperationStep, ...]) -> None:
        self.active_steps = steps
        self.step_states = ["pending"] * len(steps)
        self.current_step_index = -1
        self.steps_tree.delete(*self.steps_tree.get_children())
        self.steps_tree.grid()
        for index, step in enumerate(steps):
            self.steps_tree.insert(
                "",
                tk.END,
                iid=f"step-{index}",
                values=(self._step_state_label("pending"), self._step_label(step)),
                tags=("pending",),
            )
        self.progress.configure(maximum=100, value=0, bootstyle="success-striped")
        self.current_activity.configure(text=self._t("activity.waiting"))
        self.activity_detail.configure(text=self._t("activity.starting_detail"))
        self.step_counter.configure(text=self._t("activity.step", current=0, total=len(steps)))

    def _update_step(self, index: int, state: str) -> None:
        self.step_states[index] = state
        self.steps_tree.item(
            f"step-{index}",
            values=(self._step_state_label(state), self._step_label(self.active_steps[index])),
            tags=(state,),
        )
        self.steps_tree.see(f"step-{index}")

    def _populate_tasks(self) -> None:
        self.task_combo.configure(values=tuple(self._t(f"task.{task.id}.title") for task in TASKS))
        self.task_combo.current(0)
        self._on_selection()

    def _selected_task(self) -> Task | None:
        return task_by_id(self.selected_task_id)

    def _refresh_selected_status(self) -> None:
        key, _values = self.task_status[self.selected_task_id]
        bootstyle = "secondary-inverse"
        if key in {"status.install_ok", "status.uninstall_ok", "status.verify_ok"}:
            bootstyle = "success-inverse"
        elif key == "status.exit_error":
            bootstyle = "danger-inverse"
        self.selected_status.configure(
            text=self._translated_status(self.task_status[self.selected_task_id]),
            bootstyle=bootstyle,
        )

    def _on_selection(self, _event: object | None = None) -> None:
        if _event is not None and self.task_combo.current() >= 0:
            self.selected_task_id = TASKS[self.task_combo.current()].id
        task = self._selected_task()
        description = self._t(f"task.{task.id}.description") if task else self._t("description.operational")
        self.description.configure(text=description)
        self._refresh_selected_status()
        if not self.runner.running:
            state = tk.NORMAL if task else tk.DISABLED
            self.install_button.configure(state=state)
            self.uninstall_button.configure(state=state)
            self.verify_button.configure(state=state)

    def _run_selected(self, action: Action) -> None:
        task = self._selected_task()
        if task is None:
            self.root.bell()
            return
        if action is Action.UNINSTALL:
            confirmed = messagebox.askyesno(
                self._t("dialog.uninstall.title"),
                self._t(f"task.{task.id}.warning"),
                icon=messagebox.WARNING,
                default=messagebox.NO,
                parent=self.root,
            )
            if not confirmed:
                return
        self._start_plan(task.id, action, operation_plan(task, action))

    def _start_plan(self, task_id: str, action: Action, steps: tuple[OperationStep, ...]) -> None:
        if self.runner.running:
            messagebox.showwarning(
                self._t("dialog.running.title"), self._t("dialog.running.message"), parent=self.root
            )
            return
        try:
            self.runner.start(tuple(step.command for step in steps), self.project_root)
        except Exception as exception:
            messagebox.showerror(self._t("dialog.start_failed"), str(exception), parent=self.root)
            return
        self.active_task_id = task_id
        self.active_action = action
        self._prepare_steps(steps)
        self._set_status("status.running")
        self._set_running_controls(True)

    def _run_preflight(self) -> None:
        if shutil.which("docker") is None:
            messagebox.showerror(
                self._t("dialog.docker_missing.title"),
                self._t("dialog.docker_missing.message"),
                parent=self.root,
            )
            return
        steps = (
            OperationStep(("docker", "info"), "verify", "component.docker-engine"),
            OperationStep(("docker", "compose", "version"), "verify", "component.docker-compose"),
            OperationStep(
                ("docker", "compose", "-p", "izykube", "config", "--quiet"),
                "verify",
                "component.compose-config",
            ),
        )
        self._start_plan("preflight", Action.VERIFY, steps)

    def _initial_preflight(self) -> None:
        if shutil.which("docker") is None:
            self._set_status("status.docker_missing")
            self._append_log(self._t("log.docker_missing"))
        else:
            self._set_status("status.docker_detected")

    def _poll_runner(self) -> None:
        for event in self.runner.drain_events():
            if event.kind == "command":
                command = tuple(str(part) for part in event.value)
                self._append_log(f"\n$ {shlex.join(command)}\n")
                self.current_step_index += 1
                self._update_step(self.current_step_index, "running")
                self.current_activity.configure(text=self._step_label(self.active_steps[self.current_step_index]))
                self.activity_detail.configure(text=self._t("activity.starting_detail"))
                self.step_counter.configure(
                    text=self._t(
                        "activity.step", current=self.current_step_index + 1, total=len(self.active_steps)
                    )
                )
            elif event.kind == "output":
                output = str(event.value)
                self._append_log(output)
                detail = activity_detail(output)
                if detail:
                    self.activity_detail.configure(text=detail)
            elif event.kind == "command_exit":
                command_exit_code = int(event.value)
                state = "completed" if command_exit_code == 0 else "failed"
                self._update_step(self.current_step_index, state)
                if command_exit_code == 0:
                    completed = self.current_step_index + 1
                    self.progress.configure(value=round(completed * 100 / len(self.active_steps)))
                else:
                    self.progress.configure(bootstyle="danger-striped")
            elif event.kind == "exit":
                self._finish_operation(int(event.value))
        if not self._closing:
            self.root.after(100, self._poll_runner)

    def _finish_operation(self, exit_code: int) -> None:
        self._set_running_controls(False)
        if exit_code == 0:
            self.progress.configure(bootstyle="success-striped", maximum=100, value=100)
        else:
            self.progress.configure(bootstyle="danger-striped")
        if self.active_task_id == "preflight":
            self._set_status("status.preflight_ok" if exit_code == 0 else "status.preflight_error")
        else:
            if exit_code == 0:
                status_key = {
                    Action.INSTALL: "status.install_ok",
                    Action.UNINSTALL: "status.uninstall_ok",
                    Action.VERIFY: "status.verify_ok",
                }[self.active_action or Action.VERIFY]
                task_status = (status_key, {})
                self._set_status("status.success")
            else:
                task_status = ("status.exit_error", {"exit_code": exit_code})
                self._set_status("status.error")
            self.task_status[self.active_task_id] = task_status
            if self.active_task_id == self.selected_task_id:
                self._refresh_selected_status()
        marker = self._t("log.completed" if exit_code == 0 else "log.error")
        self._append_log(f"\n=== {marker} (exit {exit_code}) ===\n")
        self.active_task_id = ""
        self.active_action = None

    def _set_running_controls(self, running: bool) -> None:
        normal_state = tk.DISABLED if running else tk.NORMAL
        for button in (self.install_button, self.uninstall_button, self.verify_button, self.preflight_button):
            button.configure(state=normal_state)
        self.task_combo.configure(state=tk.DISABLED if running else "readonly")
        self.cancel_button.configure(state=tk.NORMAL if running else tk.DISABLED)
        if not running:
            self._on_selection()

    def _cancel(self) -> None:
        if messagebox.askyesno(
            self._t("dialog.cancel.title"),
            self._t("dialog.cancel.message"),
            icon=messagebox.WARNING,
            default=messagebox.NO,
            parent=self.root,
        ):
            self.runner.terminate()
            self._set_status("status.cancelling")

    def _append_log(self, text: str) -> None:
        self.log.insert(tk.END, text)
        self.log.see(tk.END)

    def _save_log(self) -> None:
        target = filedialog.asksaveasfilename(
            parent=self.root,
            title=self._t("file.save_log"),
            defaultextension=".log",
            filetypes=(("Log", "*.log"), (self._t("file.all"), "*")),
        )
        if target:
            Path(target).write_text(self.log.get("1.0", tk.END), encoding="utf-8")

    def _request_close(self) -> None:
        if self.runner.running:
            confirmed = messagebox.askyesno(
                self._t("dialog.running.title"),
                self._t("dialog.close.message"),
                icon=messagebox.WARNING,
                default=messagebox.NO,
                parent=self.root,
            )
            if not confirmed:
                return
            self.runner.terminate()
        self._closing = True
        self.root.destroy()


def choose_project_root(root: tk.Tk, language: str) -> Path:
    try:
        return find_project_root()
    except FileNotFoundError:
        selected = filedialog.askdirectory(
            parent=root,
            title=translate(language, "file.select_project"),
            mustexist=True,
        )
        if selected and is_project_root(Path(selected)):
            return Path(selected).resolve()
        raise


def main() -> int:
    language = detect_language()
    root = ttk.Window(themename="darkly")
    root.withdraw()
    try:
        project_root = choose_project_root(root, language)
    except FileNotFoundError as exception:
        messagebox.showerror(translate(language, "dialog.project_missing"), str(exception), parent=root)
        root.destroy()
        return 1
    root.deiconify()
    InstallerApp(root, project_root, language)
    root.mainloop()
    return 0


def self_test() -> int:
    interpreter = tk.Tcl()
    if not interpreter.eval("info patchlevel"):
        return 1
    project_root = find_project_root()
    if not is_project_root(project_root):
        return 1
    for task in TASKS:
        for action in Action:
            command_plan(task, action)
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if "--self-test" in sys.argv else main())
