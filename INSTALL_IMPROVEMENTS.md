# Installation Process Improvements

## Summary of Changes

The installation and uninstallation processes have been significantly improved with better error handling, idempotency, and user experience.

## Key Improvements

### 1. **Resume Capability**
- If installation fails at any step, you can simply re-run `make install-ui` and it will resume from where it failed
- State is tracked in `/tmp/izykube-install-state`
- Automatically cleans up state file on successful completion

### 2. **Retry Logic**
- Each step automatically retries up to 2 times on failure
- 5-second delay between retries for transient failures
- Better handling of network-related issues

### 3. **Preflight Checks**
- Validates required tools (kubectl, helm, openssl) before starting
- Clear error messages if tools are missing
- Prevents wasted time on incomplete installations

### 4. **Better Idempotency**
- All install targets can be safely re-run
- Checks if components are already installed before attempting installation
- Uses `helm upgrade --install` instead of just `install`
- Proper handling of existing resources

### 5. **Improved Error Reporting**
- Shows last 15 lines of error logs on failure
- All logs saved to `/tmp/izykube-logs/` with clear naming
- Failed step is clearly highlighted
- Includes instructions for resuming

### 6. **Enhanced Timeouts**
- Increased timeouts for slower systems:
  - ArgoCD: 240s → 300s
  - Prometheus: 300s → 360s
  - Grafana: 180s → 240s
  - cert-manager: 180s → 240s

### 7. **Better Progress Feedback**
- Visual progress bar with percentage
- Clear step labels
- Unified log directory for all operations
- Quick reference summary on completion

### 8. **Improved Uninstall**
- Better handling of already-removed resources
- Proper cleanup of port-forward PIDs
- Confirmation prompt before deletion
- Logs all uninstall steps

## Usage

### Installation

```bash
# Interactive with progress bar (recommended)
make install-ui

# Non-interactive (CI/automation)
make install OLLAMA_MODEL=llama3

# Resume a failed installation
make install-ui
# (automatically detects previous failure and offers to resume)
```

### Uninstallation

```bash
# Interactive with confirmation
make uninstall-ui

# Non-interactive
make uninstall
```

## Troubleshooting

### View logs from last installation
```bash
ls -la /tmp/izykube-logs/
cat /tmp/izykube-logs/install-<target>.log
```

### Clear state and start fresh
```bash
rm -f /tmp/izykube-install-state
make install-ui
```

### Check what failed
```bash
# The install script shows the last 15 lines automatically
# For full log:
cat /tmp/izykube-logs/install-<failed-target>.log
```

## Technical Details

### Makefile Improvements

- **OLM**: Better CRD wait logic, checks if already installed
- **cert-manager**: Idempotent with upgrade logic
- **Internal CA**: Skips if CA secret already exists
- **Istio**: Checks if already installed, better istioctl handling
- **Prometheus/Grafana**: Uses `helm upgrade --install`, better timeout handling
- **ArgoCD**: Better secret handling, graceful fallback if secret doesn't exist

### Install Script Improvements

- State tracking with `/tmp/izykube-install-state`
- Retry mechanism (up to 3 attempts per step)
- Preflight validation
- Resume from failure point
- Unified log directory
- Better error messages
- Quick reference summary

### Uninstall Script Improvements

- Confirmation prompt
- Graceful handling of missing resources
- Unified log directory
- Better progress reporting

## Migration Notes

- No action required for existing installations
- New installations will automatically benefit from improvements
- Failed installations can be resumed instead of restarted

## Future Improvements

- [ ] Health check endpoint for monitoring installation
- [ ] Webhook notifications on completion/failure
- [ ] Multi-cluster installation support
- [ ] Backup/restore capability
- [ ] Installation profiles (minimal/full/custom)

