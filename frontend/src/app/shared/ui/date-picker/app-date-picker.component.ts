import { ChangeDetectionStrategy, Component, computed, model } from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Grid, GridCell, GridCellWidget, GridRow } from '@angular/aria/grid';
import { OverlayModule } from '@angular/cdk/overlay';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideCalendar, LucideChevronLeft, LucideChevronRight, LucideLoaderCircle } from '@lucide/angular';

import {
  AppDatePickerBaseDirective,
  DATE_PICKER_TEMPLATE,
  type DayCell,
  type DaySelectionFlags,
} from './app-date-picker-base.directive';

const NO_SELECTION: DaySelectionFlags = { point: false, spanStart: false, spanEnd: false, spanMiddle: false };

@Component({
  selector: 'app-date-picker',
  standalone: true,
  imports: [OverlayModule, Grid, GridRow, GridCell, GridCellWidget, TranslocoPipe, LucideCalendar, LucideChevronLeft, LucideChevronRight, LucideLoaderCircle],
  host: { class: 'block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: DATE_PICKER_TEMPLATE,
})
export class AppDatePickerComponent extends AppDatePickerBaseDirective implements FormValueControl<string> {
  readonly value = model('');

  protected override readonly isRange = false;

  protected override readonly displayValue = computed(() => this.formatDate(this.value()));

  protected override dayFlags(iso: string, outside: boolean): DaySelectionFlags {
    const value = this.value();
    if (outside || value === '' || iso !== value) return NO_SELECTION;
    return { ...NO_SELECTION, point: true };
  }

  protected override pickDay(day: DayCell): void {
    this.value.set(day.iso);
    this.touched.set(true);
    this.close(true);
  }

  protected override anchorIso(): string {
    return this.value();
  }

  protected override pickingEnd(): boolean {
    return false;
  }
}
