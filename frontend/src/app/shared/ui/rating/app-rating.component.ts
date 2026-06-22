import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  numberAttribute,
  signal,
  type ElementRef,
  viewChild,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { TranslocoPipe, translateSignal } from '@jsverse/transloco';
import { LucideLoaderCircle } from '@lucide/angular';

import { APP_FIELD } from '../field/app-field.context';

const STAR_PATH =
  'M10.788 3.21c.448-1.077 1.976-1.077 2.424 0l2.082 5.006 5.404.434c1.164.093 1.636 1.545.749 2.305l-4.117 3.527 1.257 5.273c.271 1.136-.964 2.033-1.96 1.425L12 18.354 7.373 21.18c-.996.608-2.231-.29-1.96-1.425l1.257-5.273-4.117-3.527c-.887-.76-.415-2.212.749-2.305l5.404-.434 2.082-5.005Z';

const STAR_SIZES: Record<'sm' | 'md' | 'lg', string> = { sm: '14px', md: '18px', lg: '22px' };

interface Star {
  readonly value: number;
  readonly fill: number;
  readonly hidden: boolean;
}

@Component({
  selector: 'app-rating',
  standalone: true,
  imports: [TranslocoPipe, LucideLoaderCircle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex' },
  template: `
    @if (readonly()) {
      <span
        #readonlyRoot
        class="rating"
        role="img"
        tabindex="-1"
        [attr.aria-label]="readonlyLabel()"
        [attr.id]="resolvedInputId()"
        [attr.aria-describedby]="resolvedDescribedBy()"
        [attr.aria-invalid]="showInvalid() ? 'true' : null"
        [attr.aria-busy]="pending() ? 'true' : null"
        [class.opacity-50]="disabled()"
        [style.--star-size]="starSize()">
        @for (star of starList(); track $index) {
          <span class="star" [style.--fill]="star.fill + '%'">
            <svg class="star-base" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="path" /></svg>
            <svg class="star-fill fill-amber-400 dark:fill-yellow-400" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="path" /></svg>
          </span>
        }
        @if (showValue() && value() > 0) {
          <span class="rating-label">{{ valueLabel() }}</span>
        }
        @if (pending()) {
          <svg lucideLoaderCircle class="ml-1 size-4 animate-spin text-text-muted" aria-hidden="true"></svg>
        }
      </span>
    } @else {
      <div
        class="rating editable"
        [class.expandable]="expandable()"
        [class.expanded]="isExpanded()"
        [class.opacity-50]="disabled()"
        [style.--star-size]="starSize()"
        (mouseenter)="onEnter()"
        (mouseleave)="onLeave()">
        <input
          #input
          type="range"
          class="range"
          min="0"
          [max]="stars()"
          [step]="step()"
          [value]="value()"
          [disabled]="disabled()"
          [required]="required()"
          [attr.id]="resolvedInputId()"
          [attr.name]="name() || null"
          [attr.aria-label]="ariaLabel() || null"
          [attr.aria-describedby]="resolvedDescribedBy()"
          [attr.aria-invalid]="showInvalid() ? 'true' : null"
          [attr.aria-busy]="pending() ? 'true' : null"
          [attr.aria-valuetext]="valueText()"
          (input)="onRange(input.value)"
          (change)="touched.set(true)" />
        @for (star of starList(); track $index) {
          <button
            type="button"
            class="star"
            tabindex="-1"
            [attr.aria-label]="'shared.ui.rating.setRatingTo' | transloco: { rating: formatRating(star.value), max: stars() }"
            [class.hidden-star]="star.hidden"
            [style.--fill]="star.fill + '%'"
            [disabled]="disabled()"
            (mouseenter)="onStarHover(star.value)"
            (click)="onStarClick(star.value)">
            <svg class="star-base" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="path" /></svg>
            <svg class="star-fill fill-amber-400 dark:fill-yellow-400" viewBox="0 0 24 24" aria-hidden="true"><path [attr.d]="path" /></svg>
          </button>
        }
        @if (expandable()) {
          <span class="rating-label">{{ expandableLabel() }}</span>
        } @else if (showValue() && value() > 0) {
          <span class="rating-label">{{ valueLabel() }}</span>
        }
        @if (pending()) {
          <svg lucideLoaderCircle class="ml-1 size-4 animate-spin text-text-muted" aria-hidden="true"></svg>
        }
      </div>
    }
  `,
  styles: `
    .rating {
      position: relative;
      display: inline-flex;
      align-items: center;
      gap: 2px;
      line-height: 0;
    }
    .editable:has(.range:focus-visible) {
      outline: 2px solid var(--color-primary);
      outline-offset: 3px;
      border-radius: 4px;
    }
    .star {
      position: relative;
      display: inline-flex;
      width: var(--star-size);
      height: var(--star-size);
      line-height: 0;
      flex-shrink: 0;
    }
    button.star {
      appearance: none;
      border: 0;
      margin: 0;
      padding: 0;
      background: none;
      color: inherit;
      font: inherit;
    }
    .editable .star:not(.hidden-star):not(:disabled) {
      cursor: pointer;
    }
    .star svg {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      display: block;
    }
    .star-base {
      fill: var(--color-surface-hover);
    }
    .star-fill {
      clip-path: inset(0 calc(100% - var(--fill, 0%)) 0 0);
    }
    .expandable .star {
      overflow: hidden;
      transition: width 0.2s ease, margin-left 0.2s ease, opacity 0.2s ease;
    }
    .expandable .star svg {
      width: var(--star-size);
    }
    .hidden-star {
      width: 0;
      opacity: 0;
      pointer-events: none;
    }
    .range {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      margin: 0;
      opacity: 0;
      pointer-events: none;
    }
    .rating-label {
      margin-left: 6px;
      font-size: 13px;
      font-variant-numeric: tabular-nums;
      color: var(--color-text-muted);
      white-space: nowrap;
      line-height: 1;
    }
  `,
})
export class AppRatingComponent implements FormValueControl<number> {
  readonly value = model(0);
  readonly stars = input(5, { transform: numberAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly expandable = input(false, { transform: booleanAttribute });
  readonly showValue = input(false, { transform: booleanAttribute });
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');

  protected readonly path = STAR_PATH;
  protected readonly formatRating = formatRating;
  protected readonly starSize = computed(() => STAR_SIZES[this.size()]);
  protected readonly step = computed(() => (this.expandable() ? 0.5 : 1));

  private readonly expanded = signal(false);
  private readonly hoverValue = signal<number | null>(null);
  protected readonly isExpanded = computed(() => this.expandable() && this.expanded());
  protected readonly preview = computed(() => this.hoverValue() ?? this.value());

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));

  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');
  private readonly readonlyRoot = viewChild<ElementRef<HTMLElement>>('readonlyRoot');

  protected readonly starList = computed<Star[]>(() => {
    const count = this.stars();
    const preview = this.preview();

    if (this.expandable()) {
      const expanded = this.isExpanded();
      return Array.from({ length: count * 2 }, (_, i): Star => {
        const pos = i + 1;
        if (expanded) {
          return { value: pos / 2, fill: preview * 2 >= pos ? 100 : 0, hidden: false };
        }
        if (pos > count) return { value: pos / 2, fill: 0, hidden: true };
        return { value: pos, fill: clampFill(preview - (pos - 1)), hidden: false };
      });
    }

    return Array.from({ length: count }, (_, i): Star => {
      const pos = i + 1;
      const fill = this.readonly() ? clampFill(preview - i) : preview >= pos ? 100 : 0;
      return { value: pos, fill, hidden: false };
    });
  });

  protected readonly valueLabel = computed(() => `${formatRating(this.value())} / ${this.stars()}`);
  private readonly valueTextParams = computed(() => ({ rating: formatRating(this.value()), max: this.stars() }));
  protected readonly valueText = translateSignal('shared.ui.rating.valueText', this.valueTextParams);

  protected readonly readonlyLabel = computed(() => this.ariaLabel() || this.valueText());

  protected readonly expandableLabel = computed(() => {
    const preview = this.preview();
    if (this.isExpanded()) return `(${preview * 2} / ${this.stars() * 2})`;
    return preview > 0 ? `(${formatRating(preview)} / ${this.stars()})` : '';
  });

  focus(options?: FocusOptions): void {
    (this.input()?.nativeElement ?? this.readonlyRoot()?.nativeElement)?.focus(options);
  }

  protected onRange(raw: string): void {
    if (raw.trim() === '') return;

    const nextValue = Number(raw);
    if (!Number.isFinite(nextValue)) return;

    this.value.set(Math.max(0, Math.min(this.stars(), nextValue)));
  }

  protected onEnter(): void {
    if (this.expandable() && !this.disabled()) this.expanded.set(true);
  }

  protected onLeave(): void {
    if (!this.expandable()) return;
    this.expanded.set(false);
    this.hoverValue.set(null);
  }

  protected onStarHover(value: number): void {
    if (this.disabled()) return;
    if (this.expandable() && !this.isExpanded()) return;
    this.hoverValue.set(value);
  }

  protected onStarClick(value: number): void {
    if (this.disabled()) return;
    this.touched.set(true);

    if (this.expandable() && !this.isExpanded()) {
      this.expanded.set(true);
      return;
    }

    this.value.set(this.value() === value ? 0 : value);

    if (this.expandable()) {
      this.expanded.set(false);
      this.hoverValue.set(null);
    }
  }
}

function clampFill(units: number): number {
  return Math.round(Math.max(0, Math.min(1, units)) * 100);
}

function formatRating(value: number): string {
  return Number.isInteger(value) ? `${value}` : value.toFixed(1);
}
