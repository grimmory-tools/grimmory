import {
  booleanAttribute,
  computed,
  contentChild,
  Directive,
  inject,
  input,
  model,
  output,
  type ElementRef,
  viewChild,
} from '@angular/core';
import { Combobox } from '@angular/aria/combobox';

import { APP_FIELD } from '../field/app-field.context';
import { type AppInputSize } from '../input/app-input.variants';
import { type TagSize } from '../tag/app-tag.variants';
import {
  toAutocompleteOption,
  type AppAutocompleteOption,
  type AppAutocompleteSuggestion,
} from './app-autocomplete-option';
import { AppAutocompleteOptionTemplateDirective } from './app-autocomplete-option-template.directive';
import { autocompleteBoxVariants } from './app-autocomplete.variants';

@Directive()
export abstract class AppAutocompleteBaseDirective {
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly loadingMore = input(false, { transform: booleanAttribute });
  readonly hasMore = input(false, { transform: booleanAttribute });
  readonly errored = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly allowCustom = input(true, { transform: booleanAttribute });
  readonly suggestions = input<readonly AppAutocompleteSuggestion[]>([]);
  readonly size = input<AppInputSize>('md');
  readonly placeholder = input('');
  readonly emptyMessage = input('');
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');

  readonly complete = output<string>();
  readonly opened = output<void>();
  readonly loadMore = output<void>();

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly combobox = viewChild(Combobox);
  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');
  protected readonly optionTemplate = contentChild(AppAutocompleteOptionTemplateDirective);

  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());
  protected readonly normalizedSuggestions = computed<readonly AppAutocompleteOption[]>(() =>
    this.suggestions().map(toAutocompleteOption),
  );
  protected readonly selectedTagSize = computed<TagSize>(() => (this.size() === 'sm' ? 'sm' : 'md'));
  protected readonly innerInputClass =
    'min-w-[6rem] flex-1 border-0 bg-transparent p-0 text-inherit outline-hidden placeholder:text-text-muted disabled:cursor-default';

  protected readonly boxClass = computed(() =>
    autocompleteBoxVariants({ size: this.size(), disabled: this.disabled(), invalid: this.showInvalid() }),
  );

  protected focusInputOnPointerdown(event: PointerEvent, input: HTMLInputElement): void {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest('button')) return;
    input.focus();
  }

  protected onInputFocus(): void {
    if (this.isUnavailable()) return;
    this.opened.emit();
  }

  protected closePopup(): void {
    this.combobox()?.close();
  }

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}
