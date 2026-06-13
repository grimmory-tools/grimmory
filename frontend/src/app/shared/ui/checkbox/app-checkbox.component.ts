import {
  afterRenderEffect,
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  type ElementRef,
  viewChild,
} from '@angular/core';
import { type FormCheckboxControl } from '@angular/forms/signals';
import { cn } from '../cn';
import { invisibleControlInputClass } from '../control.styles';
import { APP_FIELD } from '../field/app-field.context';
import {
  appCheckboxBoxVariants,
  appCheckboxIndicatorVariants,
  appCheckboxRootVariants,
  type AppCheckboxSize,
} from './app-checkbox.variants';

@Component({
  selector: 'app-checkbox',
  standalone: true,
  host: { class: 'inline-block align-middle' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="rootClass()">
      <input
        #checkbox
        type="checkbox"
        [class]="inputClass"
        [attr.id]="resolvedInputId()"
        [attr.name]="name() || null"
        [attr.aria-label]="ariaLabel() || null"
        [attr.aria-labelledby]="ariaLabelledBy() || null"
        [attr.aria-describedby]="resolvedDescribedBy()"
        [attr.aria-invalid]="showInvalid() ? 'true' : null"
        [attr.aria-readonly]="readonly() ? 'true' : null"
        [attr.aria-busy]="pending() ? 'true' : null"
        [checked]="checked()"
        [disabled]="isUnavailable()"
        [required]="required()"
        (change)="onInputChange(checkbox.checked)"
        (blur)="touched.set(true)" />
      <span [class]="boxClass()" aria-hidden="true"></span>
      <svg [class]="checkIndicatorClass()" viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path
          d="M12.5 4.75L6.75 10.5L3.5 7.25"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round" />
      </svg>
      <svg [class]="indeterminateIndicatorClass()" viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path d="M4 8H12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
    </span>
  `,
})
export class AppCheckboxComponent implements FormCheckboxControl {
  readonly checked = model(false);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly indeterminate = model(false);
  readonly size = input<AppCheckboxSize>('md');
  readonly inputId = input('');
  readonly ariaLabel = input('');
  readonly ariaLabelledBy = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());

  protected readonly rootClass = computed(() => cn(appCheckboxRootVariants({ size: this.size() }), this.styleClass()));
  protected readonly inputClass = invisibleControlInputClass;
  protected readonly boxClass = computed(() =>
    appCheckboxBoxVariants({ disabled: this.isUnavailable(), invalid: this.showInvalid() }),
  );
  protected readonly checkIndicatorClass = computed(() =>
    cn(
      appCheckboxIndicatorVariants({ size: this.size() }),
      this.checked() && !this.indeterminate() ? 'opacity-100' : 'opacity-0',
    ),
  );
  protected readonly indeterminateIndicatorClass = computed(() =>
    cn(appCheckboxIndicatorVariants({ size: this.size() }), this.indeterminate() ? 'opacity-100' : 'opacity-0'),
  );

  private readonly checkbox = viewChild<ElementRef<HTMLInputElement>>('checkbox');

  constructor() {
    afterRenderEffect({
      write: () => {
        const checkbox = this.checkbox()?.nativeElement;
        if (checkbox) checkbox.indeterminate = this.indeterminate();
      },
    });
  }

  protected onInputChange(checked: boolean): void {
    this.checked.set(checked);
    this.indeterminate.set(false);
  }

  focus(options?: FocusOptions): void {
    this.checkbox()?.nativeElement?.focus(options);
  }
}
