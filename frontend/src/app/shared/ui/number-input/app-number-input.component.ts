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
import { transformedValue, type FormValueControl, type ParseResult } from '@angular/forms/signals';
import { LucideChevronDown, LucideChevronUp } from '@lucide/angular';

import { cn } from '../cn';
import { APP_FIELD } from '../field/app-field.context';
import { appInputVariants, type AppInputSize } from '../input/app-input.variants';

@Component({
  selector: 'app-number-input',
  standalone: true,
  imports: [LucideChevronDown, LucideChevronUp],
  host: { class: 'relative block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <input
      #input
      [class]="inputClass()"
      type="number"
      inputmode="decimal"
      [attr.id]="resolvedInputId()"
      [attr.name]="name() || null"
      [attr.placeholder]="placeholder() || null"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-describedby]="resolvedDescribedBy()"
      [attr.aria-invalid]="showInvalid() ? 'true' : null"
      [attr.aria-busy]="pending() ? 'true' : null"
      [attr.min]="min()"
      [attr.max]="max()"
      [attr.step]="normalizedStep()"
      [value]="rawValue()"
      [disabled]="disabled()"
      [readonly]="readonly()"
      [required]="required()"
      (input)="onInput(input)"
      (blur)="touched.set(true)" />

    @if (unit() || !readonly()) {
      <div class="pointer-events-none absolute inset-y-0 right-0 flex items-stretch">
        @if (unit()) {
          <span class="flex select-none items-center pr-3 text-xs text-text-muted">{{ unit() }}</span>
        }
        @if (!readonly()) {
          <span
            class="pointer-events-auto my-px mr-px flex w-8 flex-col overflow-hidden rounded-r-[0.3rem] border-l border-border">
            <button
              type="button"
              tabindex="-1"
              aria-hidden="true"
              [class]="stepButtonClass"
              [disabled]="disabled() || atMax()"
              (click)="nudge(1)">
              <svg lucideChevronUp class="size-3" aria-hidden="true"></svg>
            </button>
            <button
              type="button"
              tabindex="-1"
              aria-hidden="true"
              [class]="stepButtonClass + ' border-t border-border'"
              [disabled]="disabled() || atMin()"
              (click)="nudge(-1)">
              <svg lucideChevronDown class="size-3" aria-hidden="true"></svg>
            </button>
          </span>
        }
      </div>
    }
  `,
})
export class AppNumberInputComponent implements FormValueControl<number | null> {
  readonly value = model<number | null>(null);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly size = input<AppInputSize>('md');
  readonly min = input<number | undefined>(undefined);
  readonly max = input<number | undefined>(undefined);
  readonly step = input(1, { transform: numberAttribute });
  readonly unit = input('');
  readonly inputId = input('');
  readonly placeholder = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');
  readonly readonly = input(false, { transform: booleanAttribute });

  protected readonly rawValue = transformedValue(this.value, {
    parse: parseNumberInput,
    format: value => value?.toString() ?? '',
  });

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly normalizedStep = computed(() => {
    const step = this.step();
    return Number.isFinite(step) && step > 0 ? step : 1;
  });

  protected readonly atMax = computed(() => {
    const max = this.max();
    const value = this.value();
    return max != null && value != null && value >= max;
  });
  protected readonly atMin = computed(() => {
    const min = this.min();
    const value = this.value();
    return min != null && value != null && value <= min;
  });

  protected readonly stepButtonClass =
    'flex flex-1 items-center justify-center text-text-muted transition-colors ' +
    'hover:bg-surface-hover hover:text-text-strong ' +
    'active:bg-[color-mix(in_srgb,var(--color-text)_14%,transparent)] active:text-text-strong ' +
    'disabled:pointer-events-none disabled:opacity-40';

  protected readonly inputPaddingClass = computed(() => {
    if (this.unit()) return 'pr-16';
    return this.readonly() ? '' : 'pr-9';
  });
  protected readonly inputClass = computed(() =>
    cn(
      appInputVariants({ size: this.size() }),
      '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none',
      this.inputPaddingClass(),
      this.styleClass(),
    ),
  );

  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');

  protected onInput(input: HTMLInputElement): void {
    if (this.disabled() || this.readonly()) {
      input.value = this.rawValue();
      return;
    }
    this.rawValue.set(input.value);
  }

  protected nudge(direction: 1 | -1): void {
    const base = this.value() ?? this.min() ?? 0;
    this.value.set(this.roundToStep(this.clamp(base + direction * this.normalizedStep())));
  }

  private clamp(n: number): number {
    const min = this.min();
    const max = this.max();
    if (min != null && n < min) return min;
    if (max != null && n > max) return max;
    return n;
  }

  private roundToStep(n: number): number {
    const decimals = (String(this.normalizedStep()).split('.')[1] ?? '').length;
    return decimals ? Number(n.toFixed(decimals)) : n;
  }

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}

function parseNumberInput(raw: string): ParseResult<number | null> {
  const trimmed = raw.trim();
  if (trimmed === '') return { value: null };

  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed)) return { error: { kind: 'parse' } };

  return { value: parsed };
}
