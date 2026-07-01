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
import { LucideEye, LucideEyeOff, LucideLoaderCircle } from '@lucide/angular';
import { cn } from '../cn';
import { APP_FIELD } from '../field/app-field.context';
import { appInputVariants, type AppInputSize, type AppInputVariant } from './app-input.variants';

type AppInputType = 'text' | 'email' | 'password' | 'search' | 'tel' | 'url';

const ADORNMENT_CLASS = 'inline-flex shrink-0 items-center text-text-muted empty:hidden [&>svg]:size-4';
const REVEAL_TOGGLE_CLASS =
  'inline-flex size-7 shrink-0 items-center justify-center rounded-md text-text-muted ' +
  'transition-colors hover:text-text-strong ' +
  'focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-primary ' +
  'disabled:pointer-events-none disabled:opacity-50';

@Component({
  selector: 'app-input',
  standalone: true,
  imports: [LucideEye, LucideEyeOff, LucideLoaderCircle],
  host: { class: 'block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="boxClass()" [attr.aria-invalid]="showInvalid() ? 'true' : null">
      <span [class]="adornmentClass"><ng-content select="[appInputLeading]" /></span>
      <input
        #input
        [class]="inputClass"
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
        <svg lucideLoaderCircle class="size-4 shrink-0 animate-spin text-text-muted" aria-hidden="true"></svg>
      }
      @if (showRevealToggle()) {
        <button
          type="button"
          [class]="revealToggleClass"
          [disabled]="disabled()"
          [attr.aria-label]="revealAriaLabel()"
          [attr.aria-pressed]="passwordVisible()"
          (click)="passwordVisible.set(!passwordVisible())">
          @if (passwordVisible()) {
            <svg lucideEyeOff class="size-4" aria-hidden="true"></svg>
          } @else {
            <svg lucideEye class="size-4" aria-hidden="true"></svg>
          }
        </button>
      }
      <span [class]="adornmentClass"><ng-content select="[appInputTrailing]" /></span>
    </div>
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

  readonly keyedDown = output<KeyboardEvent>();
  readonly enterPressed = output<KeyboardEvent>();

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly adornmentClass = ADORNMENT_CLASS;
  protected readonly revealToggleClass = REVEAL_TOGGLE_CLASS;
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly patternAttr = computed(() => this.pattern()[0]?.source ?? null);

  protected readonly passwordVisible = signal(false);
  protected readonly showRevealToggle = computed(() => this.type() === 'password' && this.revealToggle());
  private readonly showPasswordLabel = translateSignal('shared.ui.input.showPassword');
  private readonly hidePasswordLabel = translateSignal('shared.ui.input.hidePassword');
  protected readonly resolvedType = computed(() =>
    this.showRevealToggle() && this.passwordVisible() ? 'text' : this.type(),
  );
  protected readonly revealAriaLabel = computed(() =>
    this.passwordVisible() ? this.hidePasswordLabel() : this.showPasswordLabel(),
  );
  protected readonly inputClass =
    'min-w-0 flex-1 border-0 bg-transparent p-0 text-text-strong outline-hidden placeholder:text-text-muted ' +
    '[&::-webkit-search-cancel-button]:hidden [&::-webkit-search-decoration]:hidden disabled:cursor-default';
  protected readonly boxClass = computed(() =>
    cn(
      appInputVariants({ size: this.size(), variant: this.variant() }),
      'flex items-center gap-2',
      this.disabled() && 'pointer-events-none opacity-50',
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

  focus(options?: FocusOptions): void {
    this.input()?.nativeElement.focus(options);
  }
}
