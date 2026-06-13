import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  output,
  signal,
  type ElementRef,
  viewChild,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { translateSignal } from '@jsverse/transloco';
import { cn } from '../cn';
import { APP_FIELD } from '../field/app-field.context';
import { appInputVariants, type AppInputSize, type AppInputVariant } from './app-input.variants';

type AppInputType = 'text' | 'email' | 'password' | 'search' | 'tel' | 'url';

const TRAILING_ACTION_BUTTON_CLASS =
  'absolute inset-y-0 right-0 inline-flex w-10 items-center justify-center rounded-r-md text-text-muted ' +
  'transition-colors hover:text-text-strong ' +
  'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary ' +
  'disabled:pointer-events-none disabled:opacity-50';

@Component({
  selector: 'app-input',
  standalone: true,
  host: { class: 'relative block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (leadingIcon()) {
      <span class="pointer-events-none absolute inset-y-0 left-0 inline-flex w-10 items-center justify-center text-text-muted">
        <i [class]="leadingIcon()" aria-hidden="true"></i>
      </span>
    }
    <input
      #input
      [class]="inputClass()"
      [type]="resolvedType()"
      [attr.id]="resolvedInputId()"
      [attr.name]="name() || null"
      [attr.placeholder]="placeholder() || null"
      [attr.autocomplete]="autocomplete() || null"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-describedby]="resolvedDescribedBy()"
      [attr.aria-invalid]="showInvalid() ? 'true' : null"
      [attr.aria-busy]="pending() ? 'true' : null"
      [attr.maxlength]="maxLength()"
      [attr.minlength]="minLength()"
      [attr.pattern]="patternAttr()"
      [value]="value()"
      [disabled]="disabled()"
      [readonly]="readonly()"
      [required]="required()"
      (input)="onInput(input)"
      (blur)="touched.set(true)"
      (keydown)="keyedDown.emit($event)"
      (keyup.enter)="onEnterKeyup($event)" />
    @if (pending()) {
      <span
        class="pointer-events-none absolute inset-y-0 inline-flex w-10 items-center justify-center text-text-muted"
        [class.right-10]="showTrailingAction()"
        [class.right-0]="!showTrailingAction()">
        <i class="pi pi-spinner pi-spin text-xs" aria-hidden="true"></i>
      </span>
    }
    @if (showTrailingAction()) {
      <button
        type="button"
        [class]="trailingActionButtonClass"
        [disabled]="disabled()"
        [attr.aria-label]="trailingActionAriaLabel()"
        [attr.aria-pressed]="trailingActionPressed()"
        (click)="onTrailingActionClick($event)">
        <i [class]="trailingActionIcon()" aria-hidden="true"></i>
      </button>
    }
  `,
})
export class AppInputComponent implements FormValueControl<string> {
  readonly value = model('');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly size = input<AppInputSize>('md');
  readonly variant = input<AppInputVariant>('outlined');
  readonly type = input<AppInputType>('text');
  readonly inputId = input('');
  readonly placeholder = input('');
  readonly autocomplete = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly maxLength = input<number | undefined>(undefined);
  readonly minLength = input<number | undefined>(undefined);
  readonly pattern = input<readonly RegExp[]>([]);
  readonly revealToggle = input(false, { transform: booleanAttribute });
  readonly leadingIcon = input('');
  readonly trailingIcon = input('');
  readonly trailingAriaLabel = input('');
  readonly trailingPressed = input<boolean | 'false' | 'true' | 'mixed' | null>(null);

  readonly keyedDown = output<KeyboardEvent>();
  readonly enterPressed = output<KeyboardEvent>();
  readonly trailingClicked = output<MouseEvent>();

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly trailingActionButtonClass = TRAILING_ACTION_BUTTON_CLASS;
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly patternAttr = computed(() => this.pattern()[0]?.source ?? null);

  protected readonly passwordVisible = signal(false);
  protected readonly showRevealToggle = computed(() => this.type() === 'password' && this.revealToggle());
  protected readonly showTrailingAction = computed(() => this.showRevealToggle() || this.trailingIcon() !== '');
  private readonly showPasswordLabel = translateSignal('shared.ui.input.showPassword');
  private readonly hidePasswordLabel = translateSignal('shared.ui.input.hidePassword');
  protected readonly resolvedType = computed(() =>
    this.showRevealToggle() && this.passwordVisible() ? 'text' : this.type(),
  );
  protected readonly trailingActionIcon = computed(() => {
    if (this.showRevealToggle()) {
      return this.passwordVisible() ? 'pi pi-eye-slash' : 'pi pi-eye';
    }

    return this.trailingIcon();
  });
  protected readonly trailingActionAriaLabel = computed(() => {
    if (this.showRevealToggle()) {
      return this.passwordVisible() ? this.hidePasswordLabel() : this.showPasswordLabel();
    }

    return this.trailingAriaLabel() || null;
  });
  protected readonly trailingActionPressed = computed(() => {
    if (this.showRevealToggle()) {
      return this.passwordVisible();
    }

    return this.trailingPressed();
  });
  protected readonly trailingPaddingClass = computed(() => {
    const hasTrailingAction = this.showTrailingAction();
    const pending = this.pending();

    if (hasTrailingAction && pending) return 'pr-20';
    if (hasTrailingAction || pending) return 'pr-10';
    return '';
  });
  protected readonly inputClass = computed(() =>
    cn(
      appInputVariants({ size: this.size(), variant: this.variant() }),
      this.leadingIcon() && 'pl-10',
      this.trailingPaddingClass(),
      this.styleClass(),
    ),
  );

  private readonly input = viewChild<ElementRef<HTMLInputElement>>('input');

  protected onInput(input: HTMLInputElement): void {
    if (this.disabled() || this.readonly()) {
      input.value = this.value();
      return;
    }
    this.value.set(input.value);
  }

  protected onEnterKeyup(event: Event): void {
    this.enterPressed.emit(event as KeyboardEvent);
  }

  protected onTrailingActionClick(event: MouseEvent): void {
    if (this.showRevealToggle()) {
      this.passwordVisible.update(visible => !visible);
      return;
    }

    this.trailingClicked.emit(event);
  }

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}
