import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  model,
  viewChild,
  viewChildren,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Combobox, ComboboxInput, ComboboxPopupContainer } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { OverlayModule } from '@angular/cdk/overlay';
import { TranslocoPipe } from '@jsverse/transloco';
import { LucideLoaderCircle } from '@lucide/angular';

import { AppTagComponent } from '../tag/app-tag.component';
import { AppAutocompleteBaseDirective } from './app-autocomplete-base.directive';

@Component({
  selector: 'app-multi-autocomplete',
  standalone: true,
  imports: [
    OverlayModule,
    Combobox,
    ComboboxInput,
    ComboboxPopupContainer,
    Listbox,
    Option,
    AppTagComponent,
    TranslocoPipe,
    LucideLoaderCircle,
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
        @for (tag of value(); track tag) {
          <app-tag
            size="sm"
            [removable]="!isUnavailable()"
            [label]="tag"
            [removeLabel]="removeTagLabel() || ('shared.ui.autocomplete.removeTag' | transloco: { tag })"
            (remove)="removeChip(tag)" />
        }
        <input
          ngComboboxInput
          #input
          type="text"
          autocomplete="off"
          spellcheck="false"
          [(value)]="query"
          [attr.id]="resolvedInputId()"
          [attr.name]="name() || null"
          [placeholder]="placeholderText()"
          [attr.aria-label]="ariaLabel() || null"
          [attr.aria-invalid]="showInvalid() ? 'true' : null"
          [attr.aria-readonly]="readonly() ? 'true' : null"
          [attr.aria-busy]="pending() ? 'true' : null"
          [attr.aria-describedby]="resolvedDescribedBy()"
          [disabled]="disabled()"
          [required]="required()"
          [readonly]="readonly()"
          (input)="onType(input.value)"
          (keydown.enter)="onEnter($event)"
          (keydown.backspace)="onBackspace()"
          (blur)="touched.set(true)"
          [class]="innerInputClass" />
        @if (pending()) {
          <svg lucideLoaderCircle class="size-4 shrink-0 animate-spin text-text-muted" aria-hidden="true"></svg>
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
export class AppMultiAutocompleteComponent extends AppAutocompleteBaseDirective implements FormValueControl<string[]> {
  readonly value = model<string[]>([]);

  readonly removeTagLabel = input('');

  private readonly listbox = viewChild<Listbox<string>>(Listbox);
  protected readonly optionRefs = viewChildren(Option);

  protected readonly query = linkedSignal<string[], string>({
    source: this.value,
    computation: () => '',
  });

  protected readonly placeholderText = computed(() => (this.value().length ? '' : this.placeholder()));

  protected onType(text: string): void {
    if (this.isUnavailable()) return;
    this.complete.emit(text);
  }

  protected onEnter(event: Event): void {
    if (this.isUnavailable()) return;
    event.preventDefault();
    event.stopPropagation();
    const active = this.optionRefs().find((option) => option.active());
    if (active) {
      this.addChip(String(active.value()), true);
      return;
    }
    this.addChip(this.query());
  }

  protected onBackspace(): void {
    if (this.isUnavailable() || this.query()) return;
    const current = this.value();
    if (!current.length) return;
    this.value.set(current.slice(0, -1));
    this.touched.set(true);
  }

  protected onSelect(values: readonly string[]): void {
    if (this.isUnavailable() || !values.length) return;
    this.addChip(values[values.length - 1], true);
  }

  protected removeChip(tag: string): void {
    this.value.set(this.value().filter((existing) => existing !== tag));
    this.touched.set(true);
  }

  private addChip(text: string, fromSuggestion = false): void {
    if (this.isUnavailable()) return;
    const trimmed = text.trim();
    if (!trimmed) return;
    if (!fromSuggestion && !this.allowCustom() && !this.suggestions().includes(trimmed)) return;
    const current = this.value();
    if (current.includes(trimmed)) {
      this.clearTagInput();
      return;
    }
    this.value.set([...current, trimmed]);
    this.clearTagInput();
    this.touched.set(true);
    this.complete.emit('');
  }

  private clearTagInput(): void {
    this.combobox()?.close();
    this.listbox()?.values.set([]);
    this.query.set('');
  }
}
