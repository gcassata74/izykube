# IzyKube Setup

Standalone Python/Tkinter desktop installer. It orchestrates Docker Compose and an ephemeral `setup-tools` container; it does not call the IzyKube Java backend or Angular frontend.

This installer is IzyKube orchestration code. Docker, Compose, Kubernetes, Helm, kubectl, Istio, cert-manager, OLM, Prometheus, Grafana, Python/Tk, and ttkbootstrap are external components rather than IzyKube-owned implementations. See the [capability evidence matrix](../docs/product/capability-evidence-matrix.md) for the audited classification.

The interface supports Italian and English. It initially follows the operating system locale and can be changed at any time from the language selector in the toolbar.

The desktop UI uses the MIT-licensed `ttkbootstrap` toolkit. Long procedures are displayed as individual phases with a determinate progress bar, current activity, and a technical log hidden by default.

## Runtime architecture

```text
IzyKubeSetup
  ├── docker compose up/down/ps
  └── docker compose run --rm setup-tools make <target>
                                           ├── kubectl
                                           ├── helm
                                           └── openssl
```

The host only needs Docker Engine with the Compose plugin. Kubernetes and Helm tooling run inside `setup-tools`.

## Development run

The source version requires Python with Tkinter:

```bash
sudo apt install python3 python3-tk
python3 -m pip install -r installer/requirements.txt
make setup-gui
```

## Standalone build

Tkinter/Tcl/Tk and the Python interpreter are collected by PyInstaller into the resulting executable.

```bash
make setup-gui-build
```

The build itself runs in Docker, so the build computer also needs no local Python or Tk installation.

Output:

```text
dist/IzyKubeSetup
```

Headless package verification:

```bash
dist/IzyKubeSetup --self-test
```

The destination machine does not need Python or Tcl/Tk. The executable must remain inside the IzyKube distribution, or be launched with `IZYKUBE_HOME` pointing to a directory containing `docker-compose.yml` and `Makefile`.

## Task list

| Group | Task | Install | Uninstall | Verify |
|---|---|---|---|---|
| Complete | Configured orchestration workflow | Compose up + `install-cluster-addons` | `uninstall-cluster-addons` + Compose down | Compose ps + `check-cluster-addons` |
| Infrastructure | Docker stack | Compose up | Compose down | Compose ps |
| Platform | Kubernetes addons | `install-cluster-addons` | `uninstall-cluster-addons` | `check-cluster-addons` |
| Components | OLM | `install-olm` | `uninstall-olm` | `check-olm` |
| Components | cert-manager | `install-cert-manager` | `uninstall-cert-manager` | `check-cert-manager` |
| Components | Internal CA | `create-internal-ca` | `uninstall-internal-ca` | `check-internal-ca` |
| Components | Istio | `install-istio` | `uninstall-istio` | `check-istio` |
| Components | Gateway | `install-istio-gateway` | `uninstall-istio-gateway` | `check-istio-gateway` |
| Components | Prometheus | `install-prometheus` | `uninstall-prometheus` | `check-prometheus` |
| Components | Grafana | `install-grafana-release` | `uninstall-grafana` | `check-grafana` |

Uninstall operations require confirmation. Compose down preserves volumes; destructive volume deletion is intentionally not exposed.

## Tests

```bash
python3 -m unittest discover -s installer/tests -v
```

The local CA trust-store step remains an explicit host administration action:

```bash
sudo -v && make install-ca-local
```
