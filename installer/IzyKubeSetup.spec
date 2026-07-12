from pathlib import Path

project_root = Path(SPECPATH).parent

a = Analysis(
    [str(project_root / "installer" / "main.py")],
    pathex=[str(project_root)],
    binaries=[],
    datas=[(str(project_root / "frontend" / "src" / "assets" / "images" / "logo" / "izylife.png"), "assets")],
    hiddenimports=["PIL._tkinter_finder"],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name="IzyKubeSetup",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
