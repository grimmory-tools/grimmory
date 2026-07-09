import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, model, signal, viewChild } from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { Combobox, ComboboxInput } from '@angular/aria/combobox';
import { LucideLoaderCircle } from '@lucide/angular';

import { AppAutocompleteBaseDirective } from './app-autocomplete-base.directive';
import { type AppAutocompleteOption } from './app-autocomplete-option';
import { AppAutocompletePopupComponent } from './app-autocomplete-popup.component';
import { AppAutocompleteSelectedTagsComponent } from './app-autocomplete-selected-tags.component';

type AppAutocompleteSelectedDisplay = 'text' | 'tag';

@Component({
  selector: 'app-autocomplete',
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
            (remove)="clearSelectedTag(input)" />
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
          [attr.aria-readonly]="inputReadonly() ? 'true' : null"
          [attr.aria-busy]="pending() ? 'true' : null"
          [attr.aria-describedby]="resolvedDescribedBy()"
          [disabled]="disabled()"
          [required]="required()"
          [readonly]="inputReadonly()"
          (input)="onType(input.value)"
          (focus)="onInputFocus()"
          (keydown.enter)="onEnter($event)"
          (blur)="onInputBlur()"
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
export class AppAutocompleteComponent extends AppAutocompleteBaseDirective implements FormValueControl<string> {
  readonly value = model('');
  readonly selectedDisplay = input<AppAutocompleteSelectedDisplay>('text');
  readonly removeTagLabel = input('');

  private readonly popup = viewChild(AppAutocompletePopupComponent);
  private readonly committedOption = signal<AppAutocompleteOption | null>(null);

  protected readonly selectedLabel = computed(() => {
    const value = this.value();
    const committed = this.committedOption();
    if (committed && committed.value === value) return committed.label;
    return this.normalizedSuggestions().find((option) => option.value === value)?.label ?? value;
  });

  protected readonly query = linkedSignal({
    source: () => ({ display: this.selectedDisplay(), label: this.selectedLabel() }),
    computation: ({ display, label }) => (display === 'tag' && label.trim() ? '' : label),
  });
  protected readonly showSelectedTag = computed(() => this.selectedDisplay() === 'tag' && !!this.value().trim());
  protected readonly selectedTags = computed<readonly AppAutocompleteOption[]>(() =>
    this.showSelectedTag() ? [{ value: this.value(), label: this.selectedLabel() }] : [],
  );
  protected readonly inputReadonly = computed(() => this.readonly() || this.showSelectedTag());
  protected readonly placeholderText = computed(() => (this.showSelectedTag() ? '' : this.placeholder()));

  protected onType(text: string): void {
    if (this.isUnavailable() || this.showSelectedTag()) return;
    if (this.selectedDisplay() !== 'tag' && (this.allowCustom() || !text.trim())) this.value.set(text);
    this.complete.emit(text);
  }

  protected onEnter(event: Event): void {
    if (this.isUnavailable() || this.selectedDisplay() !== 'tag' || this.showSelectedTag()) return;
    event.preventDefault();
    event.stopPropagation();
    const active = this.popup()?.activeOption();
    if (active) {
      this.selectOption(active);
      return;
    }
    const trimmed = this.query().trim();
    if (!trimmed) return;
    const match = this.normalizedSuggestions().find((option) => option.label === trimmed);
    if (match) {
      this.selectOption(match);
      return;
    }
    if (this.allowCustom()) this.selectOption({ value: trimmed, label: trimmed });
  }

  protected override onInputFocus(): void {
    if (this.showSelectedTag()) return;
    super.onInputFocus();
  }

  protected onInputBlur(): void {
    this.touched.set(true);

    if (this.allowCustom()) return;

    queueMicrotask(() => {
      if (!this.allowCustom()) this.query.set(this.selectedLabel());
    });
  }

  protected selectOption(option: AppAutocompleteOption): void {
    if (this.isUnavailable()) return;
    this.committedOption.set(option);
    this.value.set(option.value);
    this.query.set(this.selectedDisplay() === 'tag' ? '' : option.label);
    this.touched.set(true);
    this.closePopup();
  }

  protected clearSelectedTag(input: HTMLInputElement): void {
    if (this.isUnavailable()) return;
    this.committedOption.set(null);
    this.value.set('');
    this.query.set('');
    this.touched.set(true);
    this.complete.emit('');
    queueMicrotask(() => input.focus());
  }
}
