import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  model,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Combobox, ComboboxInput, ComboboxPopupContainer } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { OverlayModule } from '@angular/cdk/overlay';
import { TranslocoPipe } from '@jsverse/transloco';

import { AppSelectBaseDirective } from './app-select-base.directive';
import { AppSelectSelectedTemplateDirective } from './app-select.templates';
import { type SelectOption } from './app-select.options';

@Component({
  selector: 'app-select',
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
          @if (selectedTemplate(); as tpl) {
            @if (selectedOption(); as opt) {
              <ng-container [ngTemplateOutlet]="tpl.template" [ngTemplateOutletContext]="{ $implicit: opt }" />
            } @else {
              {{ placeholder() }}
            }
          } @else {
            {{ triggerLabel() }}
          }
        </span>

        @if (showClear() && hasSelection() && !isUnavailable()) {
          <button
            type="button"
            [attr.aria-label]="'shared.ui.select.clearSelection' | transloco"
            class="relative z-20 flex h-full shrink-0 items-center justify-center border-0 bg-transparent p-0 text-text-muted transition-colors hover:text-text-strong"
            [class.w-9]="variant() !== 'bare'"
            [class.w-7]="variant() === 'bare'"
            (click)="clear($event)">
            <i class="pi pi-times text-xs" aria-hidden="true"></i>
          </button>
        }

        <span
          class="flex h-full shrink-0 items-center justify-center text-text-muted"
          [class.w-9]="variant() !== 'bare'"
          [class.w-7]="variant() === 'bare'">
          @if (pending()) {
            <i class="pi pi-spinner pi-spin text-xs" aria-hidden="true"></i>
          } @else {
            <i class="pi pi-chevron-down text-xs transition-transform" [class.rotate-180]="cb.expanded()" aria-hidden="true"></i>
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
                <i class="pi pi-search shrink-0 text-xs" aria-hidden="true"></i>
              </div>
            }

            <ul
              ngListbox
              tabindex="-1"
              focusMode="activedescendant"
              selectionMode="explicit"
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
                    @if (optionTemplate(); as tpl) {
                      <ng-container [ngTemplateOutlet]="tpl.template" [ngTemplateOutletContext]="{ $implicit: option }" />
                    } @else {
                      <span class="truncate leading-5">{{ option.label }}</span>
                    }
                    @if (isSelected(option)) {
                      <i class="pi pi-check order-last ml-auto size-4 shrink-0 text-primary" aria-hidden="true"></i>
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
export class AppSelectComponent<T> extends AppSelectBaseDirective<T> implements FormValueControl<T | null> {
  readonly value = model<T | null>(null);

  protected readonly selectedTemplate = contentChild(AppSelectSelectedTemplateDirective);

  protected readonly selectedOption = computed<SelectOption<T> | null>(() => {
    const value = this.value();
    if (value == null) return null;
    return this.findOptionForValue(value);
  });

  protected override readonly hasSelection = computed(() => this.value() != null);

  protected override readonly triggerLabel = computed(() => this.selectedOption()?.label ?? this.placeholder());

  protected override readonly listboxValues = computed<T[]>(() => {
    const value = this.value();
    const option = value == null ? null : this.findVisibleOptionForValue(value);
    return option ? [option.value] : [];
  });

  protected override onValuesChange(values: readonly T[]): void {
    if (this.isUnavailable()) return;
    this.touched.set(true);

    if (values.length) {
      this.value.set(values[values.length - 1]);
      return;
    }

    const current = this.value();
    if (current != null && !this.isVisibleValue(current)) return;
    this.value.set(null);
  }

  protected override clearValue(): void {
    this.value.set(null);
  }

  protected override isSelected(option: SelectOption<T>): boolean {
    const value = this.value();
    return value != null && this.valuesMatch(option.value, value);
  }
}
