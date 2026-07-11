import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  model,
  viewChild,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Combobox, ComboboxInput } from '@angular/aria/combobox';
import { LucideLoaderCircle } from '@lucide/angular';

import { AppAutocompleteBaseDirective } from './app-autocomplete-base.directive';
import { type AppAutocompleteOption } from './app-autocomplete-option';
import { AppAutocompletePopupComponent } from './app-autocomplete-popup.component';
import { AppAutocompleteSelectedTagsComponent } from './app-autocomplete-selected-tags.component';

@Component({
  selector: 'app-multi-autocomplete',
  standalone: true,
  imports: [Combobox, ComboboxInput, LucideLoaderCircle, AppAutocompletePopupComponent, AppAutocompleteSelectedTagsComponent],
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
        @if (selectedTags().length) {
          <app-autocomplete-selected-tags
            [tags]="selectedTags()"
            [size]="selectedTagSize()"
            [removable]="!isUnavailable()"
            [removeTagLabel]="removeTagLabel()"
            (remove)="removeChip($event)" />
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
          (focus)="onInputFocus()"
          (keydown.enter)="onEnter($event)"
          (keydown.backspace)="onBackspace()"
          (blur)="touched.set(true)"
          [class]="innerInputClass" />
        <svg
          lucideLoaderCircle
          class="size-4 shrink-0 text-text-muted motion-reduce:animate-none"
          [class.animate-spin]="pending()"
          [class.invisible]="!pending()"
          aria-hidden="true"></svg>
      </div>

      <app-autocomplete-popup
        [origin]="origin"
        [open]="cb.expanded()"
        [disabled]="disabled()"
        [readonly]="readonly()"
        [pending]="pending()"
        [loadingMore]="loadingMore()"
        [hasMore]="hasMore()"
        [errored]="errored()"
        [suggestions]="normalizedSuggestions()"
        [emptyMessage]="emptyMessage()"
        [optionTemplate]="optionTemplate()?.template ?? null"
        (optionSelected)="selectOption($event)"
        (loadMore)="loadMore.emit()" />
    </div>
  `,
})
export class AppMultiAutocompleteComponent extends AppAutocompleteBaseDirective implements FormValueControl<string[]> {
  readonly value = model<string[]>([]);

  readonly removeTagLabel = input('');

  private readonly popup = viewChild(AppAutocompletePopupComponent);
  private readonly chipLabels = new Map<string, string>();

  protected readonly query = linkedSignal<string[], string>({
    source: this.value,
    computation: () => '',
  });

  protected readonly selectedTags = computed<readonly AppAutocompleteOption[]>(() =>
    this.value().map((value) => ({
      value,
      label: this.chipLabels.get(value) ?? this.normalizedSuggestions().find((option) => option.value === value)?.label ?? value,
    })),
  );
  protected readonly placeholderText = computed(() => (this.value().length ? '' : this.placeholder()));

  protected onType(text: string): void {
    if (this.isUnavailable()) return;
    this.complete.emit(text);
  }

  protected onEnter(event: Event): void {
    if (this.isUnavailable()) return;
    event.preventDefault();
    event.stopPropagation();
    const active = this.popup()?.activeOption();
    if (active) {
      this.addChip(active);
      return;
    }
    const trimmed = this.query().trim();
    if (!trimmed) return;
    const match = this.normalizedSuggestions().find((option) => option.label === trimmed);
    if (match) {
      this.addChip(match);
      return;
    }
    if (this.allowCustom()) this.addChip({ value: trimmed, label: trimmed });
  }

  protected onBackspace(): void {
    if (this.isUnavailable() || this.query()) return;
    const current = this.value();
    if (!current.length) return;
    this.value.set(current.slice(0, -1));
    this.touched.set(true);
  }

  protected selectOption(option: AppAutocompleteOption): void {
    this.addChip(option);
  }

  protected removeChip(value: string): void {
    this.chipLabels.delete(value);
    this.value.set(this.value().filter((existing) => existing !== value));
    this.touched.set(true);
  }

  private addChip(option: AppAutocompleteOption): void {
    if (this.isUnavailable()) return;
    const current = this.value();
    if (current.includes(option.value)) {
      this.clearTagInput();
      return;
    }
    this.chipLabels.set(option.value, option.label);
    this.value.set([...current, option.value]);
    this.clearTagInput();
    this.touched.set(true);
    this.complete.emit('');
  }

  private clearTagInput(): void {
    this.closePopup();
    this.popup()?.clearSelection();
    this.query.set('');
  }
}
