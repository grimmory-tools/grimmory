import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { translateSignal } from '@jsverse/transloco';
import { cn } from '../cn';
import { spinnerVariants, type SpinnerSize } from './app-spinner.variants';

@Component({
  selector: 'app-spinner',
  standalone: true,
  host: {
    class: 'inline-flex text-primary',
    role: 'status',
    '[attr.aria-label]': 'ariaLabel() || defaultAriaLabel()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="ringClass()"></span>`,
})
export class AppSpinnerComponent {
  readonly size = input<SpinnerSize>('md');
  readonly ariaLabel = input('');
  readonly styleClass = input('');

  protected readonly defaultAriaLabel = translateSignal('shared.ui.spinner.loading');
  protected readonly ringClass = computed(() => cn(spinnerVariants({ size: this.size() }), this.styleClass()));
}
