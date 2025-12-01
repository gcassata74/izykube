import { Component, ElementRef, EventEmitter, HostListener, Input, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FitAddon } from '@xterm/addon-fit';
import { IDisposable, Terminal } from '@xterm/xterm';
import { PodShellService } from '../services/pod-shell.service';

@Component({
  selector: 'app-pod-shell-dialog',
  templateUrl: './pod-shell-dialog.component.html',
  styleUrls: ['./pod-shell-dialog.component.scss']
})
export class PodShellDialogComponent implements OnChanges, OnDestroy {

  @Input() visible = false;
  @Input() namespace: string | null = null;
  @Input() podName: string | null = null;
  @Input() containerName?: string | null;
  @Output() closed = new EventEmitter<void>();
  @Output() visibleChange = new EventEmitter<boolean>();

  @ViewChild('terminalHost') terminalHost?: ElementRef<HTMLDivElement>;

  connectionError: string | null = null;
  private terminal?: Terminal;
  private fitAddon?: FitAddon;
  private socket?: WebSocket;
  private dataSubscription?: IDisposable;
  private hostInitialized = false;

  constructor(private podShellService: PodShellService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']) {
      if (this.visible) {
        setTimeout(() => this.initializeShell(), 0);
      } else {
        this.disposeShell();
      }
    }
  }

  ngOnDestroy(): void {
    this.disposeShell();
  }

  handleDialogHide(): void {
    this.visibleChange.emit(false);
    this.closed.emit();
    this.disposeShell();
  }

  onDialogVisibleChange(visible: boolean): void {
    if (!visible) {
      this.handleDialogHide();
    }
  }

  @HostListener('window:resize')
  handleResize(): void {
    this.fitAddon?.fit();
  }

  private initializeShell(): void {
    if (!this.visible || !this.namespace || !this.podName) {
      return;
    }

    if (!this.terminalHost) {
      setTimeout(() => this.initializeShell(), 50);
      return;
    }

    if (!this.terminal) {
      this.terminal = new Terminal({
        convertEol: true,
        cursorBlink: true,
        fontFamily: 'Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
        theme: {
          background: '#08111f',
          foreground: '#f5f5f5'
        }
      });
      this.fitAddon = new FitAddon();
      this.terminal.loadAddon(this.fitAddon);
    }

    if (this.terminalHost && !this.hostInitialized) {
      this.terminal.open(this.terminalHost.nativeElement);
      this.hostInitialized = true;
    }

    this.fitAddon?.fit();
    this.connectionError = null;
    this.connectSocket();
  }

  private connectSocket(): void {
    if (!this.namespace || !this.podName) {
      return;
    }

    this.socket?.close();
    this.socket = this.podShellService.createShellSocket(
      this.namespace,
      this.podName,
      this.containerName ?? undefined
    );

    this.socket.onopen = () => {
      this.attachTerminalListeners();
      this.terminal?.focus();
    };

    this.socket.onmessage = (event) => {
      if (typeof event.data === 'string') {
        this.terminal?.write(event.data);
      } else if (event.data instanceof Blob) {
        event.data.text().then(text => this.terminal?.write(text));
      }
    };

    this.socket.onerror = () => {
      this.connectionError = 'Unable to open shell, please check cluster connectivity or permissions.';
    };

    this.socket.onclose = (event: CloseEvent) => {
      this.dataSubscription?.dispose();
      this.dataSubscription = undefined;
      if (event.reason) {
        this.connectionError = event.reason;
      } else if (!this.connectionError) {
        this.connectionError = 'Shell session closed.';
      }
    };
  }

  private attachTerminalListeners(): void {
    if (!this.terminal) {
      return;
    }
    this.dataSubscription?.dispose();
    this.dataSubscription = this.terminal.onData(data => {
      if (this.socket?.readyState === WebSocket.OPEN) {
        this.socket.send(data);
      }
    });
  }

  private disposeShell(): void {
    this.socket?.close();
    this.socket = undefined;
    this.dataSubscription?.dispose();
    this.dataSubscription = undefined;
    this.connectionError = null;
    if (this.terminal) {
      this.terminal.clear();
    }
  }
}
