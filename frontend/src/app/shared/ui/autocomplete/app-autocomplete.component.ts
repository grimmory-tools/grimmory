import { ChangeDetectionStrategy, Component, linkedSignal, model } from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Combobox, ComboboxInput, ComboboxPopupContainer } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { OverlayModule } from '@angular/cdk/overlay';
import { TranslocoPipe } from '@jsverse/transloco';

import { AppAutocompleteBaseDirective } from './app-autocomplete-base.directive';

@Component({
  selector: 'app-autocomplete',
  standalone: true,
  imports: [
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
    <div
      ngCombobox
      #cb="ngCombobox"
      filterMode="manual"
      [readonly]="readonly()"
      [disabled]="disabled()"
      class="relative block w-full">
      <div
        #origin
        [class]="boxClass()"
        (pointerdown)="focusInputOnPointerdown($event, input)">
        <input
          ngComboboxInput
          #input
          type="text"
          autocomplete="off"
          spellcheck="false"
          [(value)]="query"
          [attr.id]="resolvedInputId()"
          [attr.name]="name() || null"
          [placeholder]="placeholder()"
          [attr.aria-label]="ariaLabel() || null"
          [attr.aria-invalid]="showInvalid() ? 'true' : null"
          [attr.aria-readonly]="readonly() ? 'true' : null"
          [attr.aria-busy]="pending() ? 'true' : null"
          [attr.aria-describedby]="resolvedDescribedBy()"
          [disabled]="disabled()"
          [required]="required()"
          [readonly]="readonly()"
          (input)="onType(input.value)"
          (blur)="onInputBlur()"
          [class]="innerInputClass" />
        @if (pending()) {
          <i class="pi pi-spinner pi-spin shrink-0 text-xs text-text-muted" aria-hidden="true"></i>
        }
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
            <ul
              ngListbox
              tabindex="-1"
              focusMode="activedescendant"
              selectionMode="explicit"
              [readonly]="readonly()"
              [disabled]="disabled()"
              (valuesChange)="onSelect($event)"
              [class]="listClass">
              @for (option of suggestions(); track option) {
                <li ngOption tabindex="-1" [value]="option" [label]="option" [class]="optionClass">
                  <span class="truncate leading-5">{{ option }}</span>
                </li>
              } @empty {
                <li [class]="emptyClass">{{ emptyMessage() || ('shared.ui.autocomplete.noResults' | transloco) }}</li>
              }
            </ul>
          </div>
        </ng-template>
      </ng-template>
    </div>
  `,
})
export class AppAutocompleteComponent extends AppAutocompleteBaseDirective implements FormValueControl<string> {
  readonly value = model('');

  protected readonly query = linkedSignal(() => this.value());

  protected onType(text: string): void {
    if (this.isUnavailable()) return;
    if (this.allowCustom() || !text.trim()) this.value.set(text);
    this.complete.emit(text);
  }

  protected onInputBlur(): void {
    this.touched.set(true);

    if (this.allowCustom()) return;

    queueMicrotask(() => {
      if (!this.allowCustom()) this.query.set(this.value());
    });
  }

  protected onSelect(values: readonly string[]): void {
    if (this.isUnavailable() || !values.length) return;
    const picked = values[values.length - 1];
    this.value.set(picked);
    this.query.set(picked);
    this.touched.set(true);
    this.combobox()?.close();
  }
}
