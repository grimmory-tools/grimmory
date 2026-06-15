import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  numberAttribute,
  type ElementRef,
  viewChild,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';

import { APP_FIELD } from '../field/app-field.context';
import { invisibleControlInputClass, neutralControlBorderClass } from '../control.styles';

@Component({
  selector: 'app-slider',
  standalone: true,
  host: { class: 'block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="relative flex h-5 w-full touch-none select-none items-center"
      [class.opacity-50]="isUnavailable()"
      [style.--pct]="fraction()">
      <input
        #input
        type="range"
        [class]="inputClass"
        [attr.id]="resolvedInputId()"
        [attr.name]="name() || null"
        [attr.aria-label]="ariaLabel() || null"
        [attr.aria-describedby]="resolvedDescribedBy()"
        [attr.aria-invalid]="showInvalid() ? 'true' : null"
        [attr.aria-busy]="pending() ? 'true' : null"
        [min]="min() ?? 0"
        [max]="max() ?? 100"
        [step]="step()"
        [value]="value()"
        [disabled]="isUnavailable()"
        [required]="required()"
        [attr.aria-readonly]="readonly() ? 'true' : null"
        (input)="onInput(input.value)"
        (change)="touched.set(true)" />

      <div class="pointer-events-none absolute inset-x-0 h-1.5 rounded-full bg-border"></div>
      <div class="pointer-events-none absolute left-0 h-1.5 rounded-full bg-primary" [style.width]="thumbPosition"></div>
      <div
        [class]="thumbClass"
        [style.left]="thumbPosition"></div>
    </div>
  `,
})
export class AppSliderComponent implements FormValueControl<number> {
  readonly value = model(0);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly min = input<number | undefined>(undefined);
  readonly max = input<number | undefined>(undefined);
  readonly step = input(1, { transform: numberAttribute });
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');

  protected readonly inputClass = invisibleControlInputClass;
  protected readonly thumbPosition = 'calc(0.5rem + var(--pct) * (100% - 1rem))';
  protected readonly thumbClass =
    `pointer-events-none absolute top-1/2 size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border ${neutralControlBorderClass} ` +
    'bg-white shadow-control transition-shadow peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary';

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());

  protected readonly fraction = computed(() => {
    const min = this.min() ?? 0;
    const span = (this.max() ?? 100) - min;
    if (span <= 0) return 0;
    return Math.min(1, Math.max(0, (this.value() - min) / span));
  });

  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');

  protected onInput(raw: string): void {
    this.value.set(Number(raw));
  }

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}
