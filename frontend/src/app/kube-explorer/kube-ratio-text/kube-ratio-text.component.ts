import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

@Component({
  selector: 'app-kube-ratio-text',
  templateUrl: './kube-ratio-text.component.html',
  styleUrls: ['./kube-ratio-text.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KubeRatioTextComponent {
  @Input() value: string | null | undefined;
  @Input() numerator: number | null | undefined;
  @Input() denominator: number | null | undefined;
  @Input() fallback = '-';

  get text(): string {
    const trimmed = (this.value ?? '').trim();
    if (trimmed) {
      return trimmed;
    }

    const hasNumerator = this.numerator !== null && this.numerator !== undefined;
    const hasDenominator = this.denominator !== null && this.denominator !== undefined;

    if (!hasNumerator && !hasDenominator) {
      return this.fallback;
    }

    const numerator = Number(this.numerator ?? 0);
    const denominator = Number(this.denominator ?? 0);
    return `${numerator}/${denominator}`;
  }
}

