import {
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
  appSwitchRootVariants,
  appSwitchThumbVariants,
  appSwitchTrackVariants,
  type AppSwitchSize,
} from './app-switch.variants';

@Component({
  selector: 'app-switch',
  standalone: true,
  host: { class: 'inline-flex align-middle' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="rootClass()">
      <input
        #control
        type="checkbox"
        role="switch"
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
        (change)="onInputChange(control.checked)"
        (blur)="touched.set(true)" />
      <span [class]="trackClass()" aria-hidden="true"></span>
      <span [class]="thumbClass()" aria-hidden="true"></span>
    </span>
  `,
})
export class AppSwitchComponent implements FormCheckboxControl {
  readonly checked = model(false);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly size = input<AppSwitchSize>('md');
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

  protected readonly rootClass = computed(() => cn(appSwitchRootVariants({ size: this.size() }), this.styleClass()));
  protected readonly inputClass = invisibleControlInputClass;
  protected readonly trackClass = computed(() =>
    appSwitchTrackVariants({ disabled: this.isUnavailable(), invalid: this.showInvalid() }),
  );
  protected readonly thumbClass = computed(() =>
    appSwitchThumbVariants({ size: this.size(), disabled: this.isUnavailable() }),
  );

  private readonly control = viewChild<ElementRef<HTMLInputElement>>('control');

  protected onInputChange(checked: boolean): void {
    this.checked.set(checked);
  }

  focus(options?: FocusOptions): void {
    this.control()?.nativeElement.focus(options);
  }
}
