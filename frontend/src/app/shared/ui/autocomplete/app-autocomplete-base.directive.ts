import {
  booleanAttribute,
  computed,
  Directive,
  inject,
  input,
  model,
  output,
  type ElementRef,
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
import { type AppInputSize } from '../input/app-input.variants';
import {
  overlayListEmptyItemClass,
  overlayListOptionClass,
  overlayListRootClass,
  overlayListSurfaceClass,
} from '../overlay-list.styles';
import { autocompleteBoxVariants } from './app-autocomplete.variants';

@Directive()
export abstract class AppAutocompleteBaseDirective {
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly allowCustom = input(true, { transform: booleanAttribute });
  readonly suggestions = input<readonly string[]>([]);
  readonly size = input<AppInputSize>('md');
  readonly placeholder = input('');
  readonly emptyMessage = input('');
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');

  readonly complete = output<string>();

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  private readonly overlayService = inject(Overlay);
  protected readonly combobox = viewChild(Combobox);
  private readonly overlay = viewChild(CdkConnectedOverlay);
  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');

  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());

  protected readonly overlayPositions = connectedOverlayPositions;
  protected readonly overlayScrollStrategy = connectedOverlayScrollStrategy(this.overlayService);
  protected readonly innerInputClass =
    'min-w-[6rem] flex-1 border-0 bg-transparent p-0 text-inherit outline-hidden placeholder:text-text-muted disabled:cursor-default';
  protected readonly surfaceClass = cn(overlayListSurfaceClass, connectedOverlayPanelClass, 'box-border w-full p-0');
  protected readonly listClass = cn(overlayListRootClass, 'max-h-60 overflow-y-auto p-1');
  protected readonly optionClass = overlayListOptionClass;
  protected readonly emptyClass = overlayListEmptyItemClass;

  protected readonly boxClass = computed(() =>
    autocompleteBoxVariants({ size: this.size(), disabled: this.disabled(), invalid: this.showInvalid() }),
  );

  protected focusInputOnPointerdown(event: PointerEvent, input: HTMLInputElement): void {
    const target = event.target instanceof Element ? event.target : null;
    if (target?.closest('button')) return;
    input.focus();
  }

  protected onOverlayAttach(): void {
    refreshConnectedOverlayPosition(this.overlay());
  }

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}
