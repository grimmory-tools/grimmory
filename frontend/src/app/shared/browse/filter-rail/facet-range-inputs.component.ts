import {Component, DestroyRef, ElementRef, computed, inject, input, linkedSignal, output, signal} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideChevronDown} from '@lucide/angular';

import {cn} from '../../ui/cn';
import {connectedGroupClass, connectedItemClass} from '../../ui/connected-group';
import {AppButtonComponent} from '../../ui/button/app-button.component';
import {AppNumberInputComponent} from '../../ui/number-input/app-number-input.component';
import {FILE_SIZE_UNITS, fileSizeDisplayUnit, type FileSizeUnit} from '../../util/file-size';
import {type BrowseFilterRange, type BrowseFilterRangeCommit} from '../facets';

type RangeSide = 'min' | 'max';

const COMMIT_DEBOUNCE_MS = 400;
const DEFAULT_FILE_SIZE_UNIT = FILE_SIZE_UNITS[1];
const SIDES: readonly {side: RangeSide; labelKey: string; unitLabelKey: string}[] = [
  {side: 'min', labelKey: 'browse.rail.min', unitLabelKey: 'browse.rail.minUnit'},
  {side: 'max', labelKey: 'browse.rail.max', unitLabelKey: 'browse.rail.maxUnit'},
];

@Component({
  selector: 'app-browse-facet-range-inputs',
  imports: [TranslocoPipe, AppButtonComponent, AppNumberInputComponent, LucideChevronDown],
  host: {
    class: 'flex flex-col gap-1.5',
    '(focusout)': 'onFocusOut($event)',
  },
  template: `
    @for (box of sides; track box.side) {
      <label class="flex items-center gap-2" [for]="inputId(box.side)">
        <span class="w-8 shrink-0 text-text-muted">{{ box.labelKey | transloco }}</span>
        <span [class]="groupClass">
          <app-number-input
            class="min-w-0 flex-1"
            size="sm"
            clearable
            [steppers]="false"
            [inputId]="inputId(box.side)"
            [styleClass]="fileSize() ? joinedInputClass : ''"
            [value]="boxValue(box.side)"
            [placeholder]="placeholder(box.side)"
            (valueChange)="onEdit(box.side, $event)"
            (keydown.enter)="commitNow()" />
          @if (fileSize()) {
            <span
              class="relative shrink-0 rounded-r-md has-[select:hover]:brightness-95 dark:has-[select:hover]:brightness-110 has-[select:focus-visible]:outline-2 has-[select:focus-visible]:outline-offset-1 has-[select:focus-visible]:outline-primary">
              <app-button
                size="sm"
                iconPos="right"
                [styleClass]="unitButtonClass"
                [tabIndex]="-1"
                [label]="unit(box.side).label">
                <svg lucideChevronDown aria-hidden="true"></svg>
              </app-button>
              <select
                #unitSelect
                class="absolute inset-0 size-full cursor-pointer opacity-0"
                [attr.aria-label]="box.unitLabelKey | transloco"
                (change)="onUnitChange(box.side, unitSelect.value)">
                @for (option of unitOptions; track option) {
                  <option [value]="option" [selected]="option === unit(box.side).label">{{ option }}</option>
                }
              </select>
            </span>
          }
        </span>
      </label>
    }
  `,
})
export class BrowseFacetRangeInputsComponent<K extends string = string> {
  readonly facetKey = input.required<K>();
  readonly range = input.required<BrowseFilterRange>();
  readonly commit = output<BrowseFilterRangeCommit<K>>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly drafts = linkedSignal<BrowseFilterRange, Partial<Record<RangeSide, number | null>>>({
    source: this.range,
    computation: () => ({}),
  });
  private readonly pickedUnits = signal<Partial<Record<RangeSide, FileSizeUnit>>>({});
  private commitTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly sides = SIDES;
  protected readonly unitOptions = FILE_SIZE_UNITS.map(unit => unit.label);
  protected readonly fileSize = computed(() => this.range().fileSize);
  protected readonly groupClass = cn(connectedGroupClass, 'flex min-w-0 flex-1');
  protected readonly joinedInputClass = connectedItemClass({first: true, last: false});
  protected readonly unitButtonClass =
    cn(connectedItemClass({first: false, last: true}), 'w-14 justify-start gap-1 px-0 pl-3');

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.commitTimer));
  }

  protected inputId(side: RangeSide): string {
    return `${this.facetKey()}-${side}`;
  }

  protected unit(side: RangeSide): FileSizeUnit {
    const picked = this.pickedUnits()[side];
    if (picked) {
      return picked;
    }
    const bound = this.bound(side);
    return bound != null && bound > 0 ? fileSizeDisplayUnit(bound) : DEFAULT_FILE_SIZE_UNIT;
  }

  protected boxValue(side: RangeSide): number | null {
    return this.toBox(side, this.committed(side));
  }

  protected placeholder(side: RangeSide): string {
    const bound = this.bound(side);
    if (bound == null) {
      return '';
    }
    if (!this.fileSize()) {
      return `${bound}`;
    }
    const scaled = this.toBox(side, bound);
    return scaled != null && scaled > 0 ? `${scaled}` : '';
  }

  protected onUnitChange(side: RangeSide, label: string): void {
    this.commitNow();
    const unit = FILE_SIZE_UNITS.find(candidate => candidate.label === label) ?? DEFAULT_FILE_SIZE_UNIT;
    this.pickedUnits.update(current => ({...current, [side]: unit}));
  }

  protected onEdit(side: RangeSide, value: number | null): void {
    this.drafts.update(current => ({...current, [side]: value}));
    clearTimeout(this.commitTimer);
    this.commitTimer = setTimeout(() => this.commitNow(), COMMIT_DEBOUNCE_MS);
  }

  protected onFocusOut(event: FocusEvent): void {
    if (event.relatedTarget instanceof Node && this.host.nativeElement.contains(event.relatedTarget)) {
      return;
    }
    this.commitNow();
  }

  protected commitNow(): void {
    clearTimeout(this.commitTimer);
    const drafts = this.drafts();
    const minChanged = this.edited('min', drafts);
    const maxChanged = this.edited('max', drafts);
    if (!minChanged && !maxChanged) {
      return;
    }
    if (this.fileSize()) {
      this.pickedUnits.set({min: this.unit('min'), max: this.unit('max')});
    }
    this.commit.emit({
      key: this.facetKey(),
      min: minChanged ? this.fromBox('min', drafts.min ?? null) : this.committed('min'),
      max: maxChanged ? this.fromBox('max', drafts.max ?? null) : this.committed('max'),
    });
  }

  private edited(side: RangeSide, drafts: Partial<Record<RangeSide, number | null>>): boolean {
    return side in drafts && drafts[side] !== this.boxValue(side);
  }

  private committed(side: RangeSide): number | null {
    return side === 'min' ? this.range().min : this.range().max;
  }

  private bound(side: RangeSide): number | null {
    return side === 'min' ? this.range().boundsMin : this.range().boundsMax;
  }

  private toBox(side: RangeSide, value: number | null): number | null {
    if (value == null || !this.fileSize()) {
      return value;
    }
    return Math.round((value / this.unit(side).kb) * 100) / 100;
  }

  private fromBox(side: RangeSide, value: number | null): number | null {
    if (value == null || !this.fileSize()) {
      return value;
    }
    return Math.round(value * this.unit(side).kb);
  }
}
