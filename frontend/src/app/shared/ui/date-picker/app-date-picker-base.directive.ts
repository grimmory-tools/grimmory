import {
  afterRenderEffect,
  booleanAttribute,
  computed,
  Directive,
  inject,
  input,
  LOCALE_ID,
  model,
  signal,
  type ElementRef,
  type Signal,
  viewChild,
} from '@angular/core';
import { CdkConnectedOverlay, Overlay } from '@angular/cdk/overlay';

import { cn } from '../cn';
import {
  connectedOverlayPanelClass,
  connectedOverlayPositions,
  connectedOverlayScrollStrategy,
  refreshConnectedOverlayPosition,
} from '../connected-overlay';
import { APP_FIELD } from '../field/app-field.context';
import { overlayListSurfaceClass } from '../overlay-list.styles';
import { appInputVariants } from '../input/app-input.variants';
import {
  appDatePickerDayBase,
  appDatePickerDaySelected,
  appDatePickerDayToday,
  type AppDatePickerSize,
} from './app-date-picker.variants';

type WeekInfoLocale = Intl.Locale & { getWeekInfo?: () => { firstDay: number }; weekInfo?: { firstDay: number } };

interface YearMonth {
  readonly y: number;
  readonly m: number;
}

export interface DaySelectionFlags {
  readonly point: boolean;
  readonly spanStart: boolean;
  readonly spanEnd: boolean;
  readonly spanMiddle: boolean;
}

export interface DayCell extends DaySelectionFlags {
  readonly iso: string;
  readonly day: number;
  readonly today: boolean;
  readonly disabled: boolean;
  readonly gridSelected: boolean;
  readonly ariaLabel: string;
}

interface WeekRow {
  readonly key: string;
  readonly days: readonly DayCell[];
}

function pad2(value: number): string {
  return value < 10 ? `0${value}` : `${value}`;
}

function toIso(year: number, month: number, day: number): string {
  return `${year}-${pad2(month)}-${pad2(day)}`;
}

export function parseIso(value: string): YearMonth & { d: number } | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return null;
  const y = Number(match[1]);
  const m = Number(match[2]);
  const d = Number(match[3]);
  const probe = new Date(y, m - 1, d);
  if (probe.getFullYear() !== y || probe.getMonth() !== m - 1 || probe.getDate() !== d) return null;
  return { y, m, d };
}

function localeFirstDay(locale: string): number {
  try {
    const info = new Intl.Locale(locale) as WeekInfoLocale;
    const firstDay = info.getWeekInfo?.().firstDay ?? info.weekInfo?.firstDay;
    return typeof firstDay === 'number' ? firstDay : 1;
  } catch {
    return 1;
  }
}

function currentYearMonth(): YearMonth {
  const now = new Date();
  return { y: now.getFullYear(), m: now.getMonth() + 1 };
}

export const DATE_PICKER_TEMPLATE = `
    <div #origin class="relative block w-full">
      <input
        #trigger
        readonly
        type="text"
        autocomplete="off"
        spellcheck="false"
        [class]="triggerClass()"
        [value]="displayValue()"
        [attr.id]="resolvedInputId()"
        [attr.name]="name() || null"
        [attr.placeholder]="placeholder() || null"
        [attr.aria-label]="ariaLabel() || null"
        [attr.aria-describedby]="resolvedDescribedBy()"
        [attr.aria-invalid]="showInvalid() ? 'true' : null"
        [attr.aria-readonly]="readonly() ? 'true' : null"
        [attr.aria-busy]="pending() ? 'true' : null"
        aria-haspopup="dialog"
        [attr.aria-expanded]="open()"
        [disabled]="disabled()"
        [required]="required()"
        (click)="toggle()"
        (keydown)="onTriggerKeydown($event)"
        (blur)="onTriggerBlur($event)" />

      <span
        class="pointer-events-none absolute inset-y-0 right-0 inline-flex w-10 items-center justify-center text-text-muted">
        @if (pending()) {
          <svg lucideLoaderCircle class="size-4 animate-spin" aria-hidden="true"></svg>
        } @else {
          <svg lucideCalendar class="size-4" aria-hidden="true"></svg>
        }
      </span>
    </div>

    <ng-template
      [cdkConnectedOverlay]="{
        origin,
        usePopover: 'inline',
        positions: overlayPositions,
        viewportMargin: 8,
        push: true
      }"
      [cdkConnectedOverlayOpen]="open()"
      [cdkConnectedOverlayScrollStrategy]="overlayScrollStrategy"
      (attach)="onOverlayAttach()"
      (overlayKeydown)="onOverlayKeydown($event)"
      (overlayOutsideClick)="onOutsideClick($event)"
      (detach)="close()">
      <div
        role="dialog"
        [attr.aria-label]="monthYearLabel()"
        [class]="popupClass"
        (focusout)="onPopupFocusOut($event)">
        <div class="mb-2 flex items-center justify-between gap-1">
          <button type="button" [class]="navButtonClass" [attr.aria-label]="'shared.ui.datePicker.previousMonth' | transloco" (click)="shiftMonth(-1)">
            <svg lucideChevronLeft class="size-4" aria-hidden="true"></svg>
          </button>
          <span aria-live="polite" class="text-sm font-semibold text-text-strong">{{ monthYearLabel() }}</span>
          <button type="button" [class]="navButtonClass" [attr.aria-label]="'shared.ui.datePicker.nextMonth' | transloco" (click)="shiftMonth(1)">
            <svg lucideChevronRight class="size-4" aria-hidden="true"></svg>
          </button>
        </div>

        <table
          ngGrid
          colWrap="continuous"
          rowWrap="nowrap"
          [enableSelection]="true"
          [softDisabled]="false"
          [multi]="isRange"
          selectionMode="explicit"
          class="border-collapse"
          [attr.aria-label]="monthYearLabel()">
          <thead>
            <tr>
              @for (weekday of weekdays(); track weekday.key) {
                <th scope="col" class="p-0">
                  <span class="flex size-9 items-center justify-center text-xs font-medium text-text-muted">
                    <span class="sr-only">{{ weekday.long }}</span>
                    <span aria-hidden="true">{{ weekday.narrow }}</span>
                  </span>
                </th>
              }
            </tr>
          </thead>
          <tbody (mouseleave)="clearPreview()">
            @for (week of weeks(); track week.key) {
              <tr ngGridRow>
                @for (day of week.days; track day.iso) {
                  <td
                    ngGridCell
                    class="p-0"
                    [disabled]="day.disabled"
                    [selected]="day.gridSelected"
                    (selectedChange)="onCellSelected(day, $event)">
                    <button
                      ngGridCellWidget
                      type="button"
                      [disabled]="day.disabled"
                      [class]="dayClass(day)"
                      [attr.aria-label]="day.ariaLabel"
                      [attr.data-day]="day.day"
                      [attr.data-selected]="day.gridSelected ? '' : null"
                      [attr.data-today]="day.today ? '' : null"
                      (mouseenter)="onDayHover(day)">
                      {{ day.day }}
                    </button>
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    </ng-template>
  `;

@Directive()
export abstract class AppDatePickerBaseDirective {
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly size = input<AppDatePickerSize>('md');
  readonly placeholder = input('');
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');
  readonly minDate = input('');
  readonly maxDate = input('');
  readonly locale = input('');
  readonly firstDayOfWeek = input<number | null>(null);

  protected abstract readonly isRange: boolean;
  protected abstract readonly displayValue: Signal<string>;
  protected abstract dayFlags(iso: string, outside: boolean): DaySelectionFlags;
  protected abstract pickDay(day: DayCell): void;
  protected abstract anchorIso(): string;
  protected abstract pickingEnd(): boolean;

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  private readonly localeId = inject(LOCALE_ID);
  private readonly overlayService = inject(Overlay);
  private readonly originRef = viewChild<ElementRef<HTMLElement>>('origin');
  private readonly triggerRef = viewChild<ElementRef<HTMLInputElement>>('trigger');
  private readonly overlay = viewChild(CdkConnectedOverlay);

  private readonly initialView = currentYearMonth();
  protected readonly open = signal(false);
  protected readonly viewYear = signal(this.initialView.y);
  protected readonly viewMonth = signal(this.initialView.m);
  protected readonly previewEnd = signal('');

  private readonly todayIso = (() => {
    const today = new Date();
    return toIso(today.getFullYear(), today.getMonth() + 1, today.getDate());
  })();

  protected readonly overlayPositions = connectedOverlayPositions;
  protected readonly overlayScrollStrategy = connectedOverlayScrollStrategy(this.overlayService);
  protected readonly popupClass = cn(overlayListSurfaceClass, connectedOverlayPanelClass, 'w-auto p-3');
  protected readonly navButtonClass =
    'flex size-7 items-center justify-center rounded-md border-0 bg-transparent text-text-muted outline-hidden ' +
    'transition-colors hover:bg-surface-hover hover:text-text-strong ' +
    'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary';

  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());

  protected readonly triggerClass = computed(() =>
    cn(
      appInputVariants({ size: this.size() }),
      this.readonly() ? 'cursor-default select-text' : 'cursor-pointer select-none',
      'pr-10',
      this.styleClass(),
    ),
  );

  protected readonly resolvedLocale = computed(() => this.locale() || this.localeId || 'en');
  private readonly resolvedFirstDay = computed(() => this.firstDayOfWeek() ?? localeFirstDay(this.resolvedLocale()));

  protected readonly weekdays = computed(() => {
    const locale = this.resolvedLocale();
    const jsFirst = this.resolvedFirstDay() % 7;
    const longFmt = new Intl.DateTimeFormat(locale, { weekday: 'long' });
    const narrowFmt = new Intl.DateTimeFormat(locale, { weekday: 'narrow' });
    return Array.from({ length: 7 }, (_, index) => {
      const ref = new Date(2021, 7, 1 + ((jsFirst + index) % 7));
      return { key: index, long: longFmt.format(ref), narrow: narrowFmt.format(ref) };
    });
  });

  protected readonly monthYearLabel = computed(() =>
    new Intl.DateTimeFormat(this.resolvedLocale(), { month: 'long', year: 'numeric' }).format(
      new Date(this.viewYear(), this.viewMonth() - 1, 1),
    ),
  );

  protected readonly weeks = computed<WeekRow[]>(() => {
    const year = this.viewYear();
    const month = this.viewMonth();
    const jsFirst = this.resolvedFirstDay() % 7;
    const min = this.minDate();
    const max = this.maxDate();
    const fullFmt = new Intl.DateTimeFormat(this.resolvedLocale(), { dateStyle: 'full' });

    const firstWeekday = new Date(year, month - 1, 1).getDay();
    const leading = (firstWeekday - jsFirst + 7) % 7;
    const cursor = new Date(year, month - 1, 1 - leading);

    const rows: WeekRow[] = [];
    for (let week = 0; week < 6; week++) {
      const days: DayCell[] = [];
      for (let column = 0; column < 7; column++) {
        const cy = cursor.getFullYear();
        const cm = cursor.getMonth() + 1;
        const cd = cursor.getDate();
        const iso = toIso(cy, cm, cd);
        const outside = cm !== month;
        const outOfRange = (min !== '' && iso < min) || (max !== '' && iso > max);
        const disabled = outside || outOfRange;
        const flags = this.dayFlags(iso, outside);

        days.push({
          iso,
          day: cd,
          today: !outside && iso === this.todayIso,
          disabled,
          ...flags,
          gridSelected: !disabled && (flags.point || flags.spanStart || flags.spanEnd || flags.spanMiddle),
          ariaLabel: fullFmt.format(cursor),
        });
        cursor.setDate(cd + 1);
      }
      rows.push({ key: days[0].iso, days });
    }
    return rows;
  });

  constructor() {
    afterRenderEffect({
      mixedReadWrite: () => {
        if (this.open()) this.focusInitialDay();
      },
    });
  }

  protected dayClass(day: DayCell): string {
    const inSelection = day.point || day.spanStart || day.spanEnd || day.spanMiddle;
    return cn(
      appDatePickerDayBase,
      day.disabled && 'text-text-muted opacity-40',
      inSelection && appDatePickerDaySelected,
      (day.spanStart || day.spanEnd || day.spanMiddle) && 'rounded-none',
      !inSelection && day.today && appDatePickerDayToday,
    );
  }

  protected toggle(): void {
    if (this.isUnavailable()) return;
    if (this.open()) {
      this.close();
      return;
    }
    this.openPopup();
  }

  protected onCellSelected(day: DayCell, selected: boolean): void {
    if (this.isUnavailable() || day.disabled) return;
    if (!this.isRange && !selected) return;
    this.pickDay(day);
  }

  protected onDayHover(day: DayCell): void {
    if (day.disabled || !this.pickingEnd()) return;
    this.previewEnd.set(day.iso);
  }

  protected clearPreview(): void {
    this.previewEnd.set('');
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (this.isUnavailable()) return;
    if (this.open()) {
      if (event.key === 'Escape') {
        event.preventDefault();
        this.close(true);
      }
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      this.openPopup();
    }
  }

  protected onTriggerBlur(event: FocusEvent): void {
    if (this.disabled()) return;
    if (this.relatedTargetInPopup(event.relatedTarget)) return;
    this.touched.set(true);
  }

  protected onOverlayKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close(true);
    }
  }

  protected onOutsideClick(event: MouseEvent): void {
    const target = event.target;
    if (target instanceof Node && this.originRef()?.nativeElement.contains(target)) return;
    this.close();
  }

  protected onPopupFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget;
    if (next === null) return;
    if (next instanceof Node && this.originRef()?.nativeElement.contains(next)) return;
    if (this.relatedTargetInPopup(next)) return;
    this.close();
  }

  protected onOverlayAttach(): void {
    refreshConnectedOverlayPosition(this.overlay());
  }

  protected close(refocusTrigger = false): void {
    this.clearPreview();
    this.open.set(false);
    if (refocusTrigger) queueMicrotask(() => this.focus());
  }

  private openPopup(): void {
    const base = parseIso(this.anchorIso()) ?? currentYearMonth();
    this.viewYear.set(base.y);
    this.viewMonth.set(base.m);
    this.open.set(true);
  }

  protected shiftMonth(delta: number): void {
    const base = new Date(this.viewYear(), this.viewMonth() - 1 + delta, 1);
    this.viewYear.set(base.getFullYear());
    this.viewMonth.set(base.getMonth() + 1);
  }

  protected formatDate(iso: string): string {
    const parsed = parseIso(iso);
    if (!parsed) return '';
    return new Intl.DateTimeFormat(this.resolvedLocale(), { dateStyle: 'medium' }).format(
      new Date(parsed.y, parsed.m - 1, parsed.d),
    );
  }

  private relatedTargetInPopup(target: EventTarget | null): boolean {
    const overlayElement = this.overlay()?.overlayRef?.overlayElement;
    return target instanceof Node && overlayElement?.contains(target) === true;
  }

  private focusInitialDay(): void {
    const overlayElement = this.overlay()?.overlayRef?.overlayElement;
    if (!overlayElement) return;
    const target =
      overlayElement.querySelector<HTMLElement>('button[data-selected]') ??
      overlayElement.querySelector<HTMLElement>('button[data-today]:not([disabled])') ??
      overlayElement.querySelector<HTMLElement>('button[data-day]:not([disabled])');
    target?.focus();
  }

  focus(options?: FocusOptions): void {
    this.triggerRef()?.nativeElement.focus(options);
  }
}
