import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  model,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Combobox, ComboboxInput, ComboboxPopupContainer } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { OverlayModule } from '@angular/cdk/overlay';
import { TranslocoPipe, translateSignal } from '@jsverse/transloco';
import { LucideCheck, LucideChevronDown, LucideLoaderCircle, LucideSearch, LucideX } from '@lucide/angular';

import { cn } from '../cn';
import { AppSelectBaseDirective } from './app-select-base.directive';
import { type SelectOption } from './app-select.options';

@Component({
  selector: 'app-multi-select',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    OverlayModule,
    Combobox,
    ComboboxInput,
    ComboboxPopupContainer,
    Listbox,
    Option,
    TranslocoPipe,
    LucideCheck,
    LucideChevronDown,
    LucideLoaderCircle,
    LucideSearch,
    LucideX,
  ],
  host: { class: 'block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div ngCombobox #cb="ngCombobox" [readonly]="true" [disabled]="disabled()" class="relative block h-full w-full">
      <div
        #origin
        [class]="triggerClass()"
        [attr.data-expanded]="cb.expanded() ? 'true' : null">
        <input
          ngComboboxInput
          #input
          readonly
          type="text"
          autocomplete="off"
          spellcheck="false"
          [value]="triggerLabel()"
          [attr.id]="resolvedInputId()"
          [attr.name]="name() || null"
          [attr.aria-label]="ariaLabel() || null"
          [attr.aria-labelledby]="ariaLabelledBy() || null"
          [attr.aria-invalid]="showInvalid() ? 'true' : null"
          [attr.aria-readonly]="readonly() ? 'true' : null"
          [attr.aria-busy]="pending() ? 'true' : null"
          [attr.aria-describedby]="resolvedDescribedBy()"
          [disabled]="disabled()"
          [required]="required()"
          (keydown)="onTriggerKeydown($event)"
          (blur)="onTriggerBlur($event)"
          class="absolute inset-0 z-10 h-full w-full cursor-pointer rounded-md border-0 bg-transparent p-0 text-transparent caret-transparent outline-hidden disabled:cursor-default" />

        <span aria-hidden="true" [class]="labelClass()" [class.text-text-muted]="!hasSelection()">
          {{ triggerLabel() }}
        </span>

        @if (showClear() && hasSelection() && !isUnavailable()) {
          <button
            type="button"
            [attr.aria-label]="'shared.ui.select.clearSelection' | transloco"
            class="relative z-20 flex h-full shrink-0 items-center justify-center border-0 bg-transparent p-0 text-text-muted transition-colors hover:text-text-strong"
            [class.w-9]="variant() !== 'bare'"
            [class.w-7]="variant() === 'bare'"
            (click)="clear($event)">
            <svg lucideX class="size-4" aria-hidden="true"></svg>
          </button>
        }

        <span
          class="flex h-full shrink-0 items-center justify-center text-text-muted"
          [class.w-9]="variant() !== 'bare'"
          [class.w-7]="variant() === 'bare'">
          @if (pending()) {
            <svg lucideLoaderCircle class="size-4 animate-spin" aria-hidden="true"></svg>
          } @else {
            <svg lucideChevronDown class="size-4 transition-transform" [class.rotate-180]="cb.expanded()" aria-hidden="true"></svg>
          }
        </span>
      </div>

      <ng-template ngComboboxPopupContainer>
        <ng-template
          [cdkConnectedOverlay]="{
            origin,
            usePopover: 'inline',
            matchWidth: true,
            positions: overlayPositions,
            viewportMargin: 8,
            push: true
          }"
          [cdkConnectedOverlayOpen]="cb.expanded()"
          [cdkConnectedOverlayScrollStrategy]="overlayScrollStrategy"
          (attach)="onOverlayAttach()">
          <div [class]="surfaceClass">
            @if (filter()) {
              <div class="flex h-9 items-center border-b border-border pl-3 pr-2 text-text-muted">
                <input
                  #filterInput
                  type="text"
                  [value]="filterText()"
                  (input)="filterText.set(filterInput.value)"
                  (keydown)="onFilterKeydown($event)"
                  [placeholder]="filterPlaceholder() || ('shared.ui.select.search' | transloco)"
                  [readonly]="readonly()"
                  class="h-full min-w-0 flex-1 border-0 bg-transparent px-0 pr-2 text-sm text-text-strong outline-hidden placeholder:text-text-muted focus:outline-hidden" />
                <svg lucideSearch class="size-4 shrink-0" aria-hidden="true"></svg>
              </div>
            }

            <ul
              ngListbox
              tabindex="-1"
              focusMode="activedescendant"
              selectionMode="explicit"
              [multi]="true"
              [values]="listboxValues()"
              [readonly]="readonly()"
              [disabled]="disabled()"
              (valuesChange)="onValuesChange($event)"
              [class]="listClass()">
              @for (grp of renderedGroups(); track grp.trackKey; let first = $first) {
                @if (grouped()) {
                  @if (!first) {
                    <li role="separator" [class]="separatorClass"></li>
                  }
                  <li role="presentation" [class]="sectionClass">{{ grp.label }}</li>
                }
                @for (option of grp.options; track option) {
                  <li
                    ngOption
                    tabindex="-1"
                    [value]="option.value"
                    [label]="option.label"
                    [disabled]="option.disabled === true"
                    [class]="optionClass">
                    <span [class]="checkboxClass(isSelected(option))">
                      @if (isSelected(option)) { <svg lucideCheck class="size-3 text-white" aria-hidden="true"></svg> }
                    </span>
                    @if (optionTemplate(); as tpl) {
                      <ng-container [ngTemplateOutlet]="tpl.template" [ngTemplateOutletContext]="{ $implicit: option }" />
                    } @else {
                      <span class="truncate leading-5">{{ option.label }}</span>
                    }
                  </li>
                }
              } @empty {
                <li [class]="emptyClass">{{ emptyMessage() || ('shared.ui.select.noResults' | transloco) }}</li>
              }
            </ul>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
})
export class AppMultiSelectComponent<T> extends AppSelectBaseDirective<T> implements FormValueControl<T[]> {
  readonly value = model<T[]>([]);

  private readonly selectedCount = computed(() => this.value().length);
  private readonly selectedCountParam = computed(() => String(this.selectedCount()));
  private readonly selectedCountLabel = translateSignal('shared.ui.select.selectedCount', {
    count: this.selectedCountParam,
  });

  protected override readonly hasSelection = computed(() => this.selectedCount() > 0);

  protected override readonly triggerLabel = computed(() =>
    this.selectedCount() ? this.selectedCountLabel() : this.placeholder(),
  );

  protected override readonly listboxValues = computed<T[]>(() => {
    return this.visibleOptionValuesForSelected(this.value());
  });

  protected override onValuesChange(values: readonly T[]): void {
    if (this.isUnavailable()) return;
    this.touched.set(true);
    const hiddenSelections = this.value().filter((selectedValue) => !this.isVisibleValue(selectedValue));
    this.value.set([...hiddenSelections, ...values]);
  }

  protected override clearValue(): void {
    this.value.set([]);
  }

  protected override isSelected(option: SelectOption<T>): boolean {
    return this.value().some((selectedValue) => this.valuesMatch(option.value, selectedValue));
  }

  protected checkboxClass(selected: boolean): string {
    return cn(
      'mr-2 flex size-4 shrink-0 items-center justify-center rounded-sm border transition-colors',
      selected ? 'border-primary bg-primary' : 'border-border bg-card',
    );
  }
}
