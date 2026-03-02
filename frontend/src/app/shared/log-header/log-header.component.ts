import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

export interface LogOption {
  label: string;
  value: boolean;
}

@Component({
  selector: 'app-log-header',
  templateUrl: './log-header.component.html',
  styleUrls: ['./log-header.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LogHeaderComponent {
  @Input() options: LogOption[] = [];
  @Input() selected: boolean | null = null;
  @Input() loading = false;
  @Input() disabled = false;

  @Output() selectedChange = new EventEmitter<boolean>();
  @Output() reload = new EventEmitter<void>();

  onSelectionChange(value: boolean): void {
    this.selectedChange.emit(value);
  }

  onReload(): void {
    if (this.loading || this.disabled) {
      return;
    }
    this.reload.emit();
  }
}
