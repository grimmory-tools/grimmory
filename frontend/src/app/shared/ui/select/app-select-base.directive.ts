import {
  booleanAttribute,
  computed,
  contentChild,
  Directive,
  inject,
  input,
  linkedSignal,
  model,
  output,
  type ElementRef,
  type Signal,
  viewChild,
} from '@angular/core';
import { Combobox } from '@angular/aria/combobox';
import { CdkConnectedOverlay, Overlay } from '@angular/cdk/overlay';

import { cn } from '../cn';
import {
  connectedOverlayPanelClass,
  connectedOverlayPositions,
  connectedOverlayScrollStrategy,
  refreshConnectedOverlayPosition,
} from '../connected-overlay';
import { APP_FIELD } from '../field/app-field.context';
import {
  overlayListEmptyItemClass,
  overlayListOptionClass,
  overlayListRootClass,
  overlayListSectionLabelClass,
  overlayListSeparatorClass,
  overlayListSurfaceClass,
} from '../overlay-list.styles';
import {
  appSelectLabelVariants,
  appSelectTriggerVariants,
  type AppSelectSize,
  type AppSelectVariant,
} from './app-select.variants';
import { AppSelectOptionTemplateDirective } from './app-select.templates';
import { type SelectCompareWith, type SelectOption, type SelectOptionGroup } from './app-select.options';

const FILTER_TEXT_EDITING_KEYS = new Set([' ', 'Spacebar', 'Backspace', 'Delete', 'Home', 'End']);
const defaultCompareWith = <T>(optionValue: T, selectedValue: T): boolean => Object.is(optionValue, selectedValue);
const UNGROUPED_GROUP_TRACK_KEY = Symbol('app-select-ungrouped');

interface RenderedSelectOptionGroup<T> extends SelectOptionGroup<T> {
  readonly trackKey: unknown;
}

@Directive()
export abstract class AppSelectBaseDirective<T> {
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly size = input<AppSelectSize>('md');
  readonly variant = input<AppSelectVariant>('outlined');
  readonly options = input<readonly SelectOption<T>[]>([]);
  readonly groups = input<readonly SelectOptionGroup<T>[]>([]);
  readonly filter = input(false, { transform: booleanAttribute });
  readonly filterPlaceholder = input('');
  readonly showClear = input(false, { transform: booleanAttribute });
  readonly placeholder = input('');
  readonly emptyMessage = input('');
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaLabelledBy = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');
  readonly compareWith = input<SelectCompareWith<T>>(defaultCompareWith);

  readonly cleared = output<void>();

  protected abstract readonly hasSelection: Signal<boolean>;
  protected abstract readonly triggerLabel: Signal<string>;
  protected abstract readonly listboxValues: Signal<T[]>;
  protected abstract onValuesChange(values: readonly T[]): void;
  protected abstract clearValue(): void;
  protected abstract isSelected(option: SelectOption<T>): boolean;

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  private readonly overlayService = inject(Overlay);
  private readonly combobox = viewChild(Combobox);
  private readonly overlay = viewChild(CdkConnectedOverlay);
  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');
  protected readonly optionTemplate = contentChild(AppSelectOptionTemplateDirective);

  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());

  protected readonly surfaceClass = cn(
    overlayListSurfaceClass,
    connectedOverlayPanelClass,
    'box-border flex w-full flex-col overflow-hidden p-0',
  );
  protected readonly overlayPositions = connectedOverlayPositions;
  protected readonly overlayScrollStrategy = connectedOverlayScrollStrategy(this.overlayService);
  protected readonly listClass = computed(() =>
    cn(
      overlayListRootClass,
      this.filter() ? 'max-h-[12.75rem] overflow-y-auto p-1' : 'max-h-60 overflow-y-auto p-1',
    ),
  );
  protected readonly sectionClass = cn(overlayListSectionLabelClass, 'text-text-muted');
  protected readonly separatorClass = overlayListSeparatorClass;
  protected readonly optionClass = overlayListOptionClass;
  protected readonly emptyClass = overlayListEmptyItemClass;

  protected readonly triggerClass = computed(() =>
    cn(
      appSelectTriggerVariants({
        size: this.size(),
        variant: this.variant(),
        invalid: this.showInvalid(),
        disabled: this.disabled(),
      }),
      this.styleClass(),
    ),
  );
  protected readonly labelClass = computed(() => appSelectLabelVariants({ size: this.size(), variant: this.variant() }));

  protected readonly grouped = computed(() => this.groups().length > 0);

  protected readonly renderedGroups = computed<readonly RenderedSelectOptionGroup<T>[]>(() => {
    if (!this.grouped()) {
      const options = this.options().filter((option) => this.matchesFilter(option));
      return options.length ? [{ label: '', options, trackKey: UNGROUPED_GROUP_TRACK_KEY }] : [];
    }

    const out: RenderedSelectOptionGroup<T>[] = [];
    for (const group of this.groups()) {
      const options = group.options.filter((option) => this.matchesFilter(option));
      if (options.length) out.push({ label: group.label, options, trackKey: group });
    }
    return out;
  });

  protected readonly flatOptions = computed<readonly SelectOption<T>[]>(() =>
    this.grouped() ? this.groups().flatMap((group) => group.options) : this.options(),
  );

  protected readonly filterText = linkedSignal<boolean, string>({
    source: () => this.combobox()?.expanded() ?? false,
    computation: (expanded, previous) => (expanded ? (previous?.value ?? '') : ''),
  });

  protected onTriggerKeydown(event: KeyboardEvent): void {
    const combobox = this.combobox();
    if (this.disabled() || !combobox || combobox.expanded()) return;
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp' && event.key !== 'Enter' && event.key !== ' ') return;

    event.preventDefault();
    event.stopPropagation();
    combobox.open();
  }

  protected onFilterKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape' || event.key === 'Enter' || event.key === 'Tab' || event.key.startsWith('Arrow')) return;

    if (event.key.length === 1 || event.ctrlKey || event.metaKey || FILTER_TEXT_EDITING_KEYS.has(event.key)) {
      event.stopPropagation();
    }
  }

  protected onTriggerBlur(event: FocusEvent): void {
    if (this.disabled()) return;
    const relatedTarget = event.relatedTarget;
    const overlayElement = this.overlay()?.overlayRef?.overlayElement;
    if (relatedTarget instanceof Node && overlayElement?.contains(relatedTarget)) return;
    this.touched.set(true);
  }

  protected clear(event: MouseEvent): void {
    event.stopPropagation();
    this.clearValue();
    this.touched.set(true);
    this.cleared.emit();
  }

  private matchesFilter(option: SelectOption<T>): boolean {
    if (!this.filter()) return true;
    const query = this.filterText().trim().toLowerCase();
    if (!query) return true;
    return option.label.toLowerCase().includes(query);
  }

  protected valuesMatch(optionValue: T, selectedValue: T): boolean {
    return this.compareWith()(optionValue, selectedValue);
  }

  protected findOptionForValue(value: T): SelectOption<T> | null {
    return this.flatOptions().find((option) => this.valuesMatch(option.value, value)) ?? null;
  }

  protected findVisibleOptionForValue(value: T): SelectOption<T> | null {
    for (const group of this.renderedGroups()) {
      const option = group.options.find((candidate) => this.valuesMatch(candidate.value, value));
      if (option) return option;
    }
    return null;
  }

  protected isVisibleValue(value: T): boolean {
    return this.findVisibleOptionForValue(value) != null;
  }

  protected visibleOptionValuesForSelected(values: readonly T[]): T[] {
    return this.renderedGroups()
      .flatMap((group) => group.options)
      .filter((option) => values.some((value) => this.valuesMatch(option.value, value)))
      .map((option) => option.value);
  }

  protected onOverlayAttach(): void {
    refreshConnectedOverlayPosition(this.overlay());
  }

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}
