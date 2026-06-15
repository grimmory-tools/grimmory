import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Grid, GridCell, GridCellWidget, GridRow } from '@angular/aria/grid';
import { OverlayModule } from '@angular/cdk/overlay';
import { TranslocoPipe } from '@jsverse/transloco';

import {
  AppDatePickerBaseDirective,
  DATE_PICKER_TEMPLATE,
  type DayCell,
  type DaySelectionFlags,
} from './app-date-picker-base.directive';

export interface DatePickerRange {
  readonly start: string;
  readonly end: string;
}

const EMPTY_RANGE: DatePickerRange = { start: '', end: '' };
const NO_SELECTION: DaySelectionFlags = { point: false, spanStart: false, spanEnd: false, spanMiddle: false };

@Component({
  selector: 'app-date-range-picker',
  standalone: true,
  imports: [OverlayModule, Grid, GridRow, GridCell, GridCellWidget, TranslocoPipe],
  host: { class: 'block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: DATE_PICKER_TEMPLATE,
})
export class AppDateRangePickerComponent extends AppDatePickerBaseDirective implements FormValueControl<DatePickerRange> {
  readonly value = model<DatePickerRange>(EMPTY_RANGE);

  protected override readonly isRange = true;

  private readonly span = computed(() => {
    const range = this.value();
    const effStart = range.start;
    const effEnd = range.end || (this.pickingEnd() ? this.previewEnd() : '');
    const hasSpan = effStart !== '' && effEnd !== '' && effStart !== effEnd;
    const lo = hasSpan && effStart > effEnd ? effEnd : effStart;
    const hi = hasSpan && effStart > effEnd ? effStart : effEnd;
    return { hasSpan, lo, hi, effStart };
  });

  protected override readonly displayValue = computed(() => {
    const range = this.value();
    const start = this.formatDate(range.start);
    const end = this.formatDate(range.end);
    if (!start && !end) return '';
    return `${start || '…'} – ${end || '…'}`;
  });

  protected override dayFlags(iso: string): DaySelectionFlags {
    const { hasSpan, lo, hi, effStart } = this.span();
    if (hasSpan) {
      return { point: false, spanStart: iso === lo, spanEnd: iso === hi, spanMiddle: iso > lo && iso < hi };
    }
    if (effStart !== '' && iso === effStart) return { ...NO_SELECTION, point: true };
    return NO_SELECTION;
  }

  protected override pickDay(day: DayCell): void {
    const range = this.value();
    this.touched.set(true);
    this.clearPreview();
    if (!range.start || range.end || day.iso < range.start) {
      this.value.set({ start: day.iso, end: '' });
      return;
    }
    this.value.set({ start: range.start, end: day.iso });
    this.close(true);
  }

  protected override anchorIso(): string {
    return this.value().start;
  }

  protected override pickingEnd(): boolean {
    const range = this.value();
    return range.start !== '' && range.end === '';
  }
}
