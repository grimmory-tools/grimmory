import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  type ElementRef,
  viewChildren,
} from '@angular/core';
import { type FormValueControl } from '@angular/forms/signals';
import { cn } from '../cn';
import { APP_FIELD } from '../field/app-field.context';
import {
  appRadioGroupDotVariants,
  appRadioGroupOptionVariants,
  appRadioGroupRootVariants,
  appRadioGroupSegmentClass,
  type AppRadioGroupSize,
  type AppRadioGroupVariant,
} from './app-radio-group.variants';

export interface RadioOption<T> {
  readonly value: T;
  readonly label: string;
  readonly description?: string;
  readonly disabled?: boolean;
}

const defaultCompareWith = <T>(optionValue: T, selectedValue: T): boolean => Object.is(optionValue, selectedValue);
let nextGroupId = 0;

@Component({
  selector: 'app-radio-group',
  standalone: true,
  host: { class: 'block' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="radiogroup"
      [class]="rootClass()"
      [attr.id]="resolvedGroupId()"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-labelledby]="resolvedLabelledBy()"
      [attr.aria-describedby]="resolvedDescribedBy()"
      [attr.aria-invalid]="showInvalid() ? 'true' : null"
      [attr.aria-readonly]="readonly() ? 'true' : null"
      [attr.aria-busy]="pending() ? 'true' : null">
      @for (option of options(); track option.value; let i = $index) {
        <label [class]="variant() === 'segmented' ? segmentClass(i) : optionClass()">
          <input
            #radio
            type="radio"
            class="peer sr-only"
            [name]="groupName()"
            [checked]="isSelected(option)"
            [disabled]="isUnavailable() || option.disabled === true"
            [required]="required()"
            (change)="onSelect(option)"
            (blur)="touched.set(true)" />
          @if (variant() === 'segmented') {
            <span class="truncate leading-none">{{ option.label }}</span>
          } @else {
            <span [class]="dotClass()" aria-hidden="true"></span>
            <span [class]="textBlockClass()">
              <span class="font-medium leading-5">{{ option.label }}</span>
              @if (option.description) {
                <span class="text-xs text-text-muted dark:text-text-secondary">{{ option.description }}</span>
              }
            </span>
          }
        </label>
      }
    </div>
  `,
})
export class AppRadioGroupComponent<T> implements FormValueControl<T | null> {
  readonly value = model<T | null>(null);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly options = input<readonly RadioOption<T>[]>([]);
  readonly variant = input<AppRadioGroupVariant>('list');
  readonly size = input<AppRadioGroupSize>('md');
  readonly groupId = input('');
  readonly ariaLabel = input('');
  readonly ariaLabelledBy = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');
  readonly compareWith = input<(optionValue: T, selectedValue: T) => boolean>(defaultCompareWith);

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  private readonly autoName = `app-radio-group-${++nextGroupId}`;
  private readonly radios = viewChildren<ElementRef<HTMLInputElement>>('radio');

  protected readonly groupName = computed(() => this.name() || this.autoName);
  protected readonly resolvedGroupId = computed(() => this.groupId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedLabelledBy = computed(
    () => this.ariaLabelledBy() || this.fieldContext?.labelId() || null,
  );
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));
  protected readonly isUnavailable = computed(() => this.disabled() || this.readonly());

  protected readonly rootClass = computed(() =>
    cn(appRadioGroupRootVariants({ variant: this.variant() }), this.styleClass()),
  );
  protected readonly optionClass = computed(() =>
    appRadioGroupOptionVariants({ variant: this.variant(), size: this.size() }),
  );
  protected readonly dotClass = computed(() =>
    appRadioGroupDotVariants({ size: this.size(), variant: this.variant() }),
  );
  protected readonly textBlockClass = computed(() =>
    cn(
      'flex min-w-0 flex-col gap-0.5 text-text-strong',
      this.variant() === 'card' && 'peer-checked:text-primary-text',
    ),
  );

  protected isSelected(option: RadioOption<T>): boolean {
    const value = this.value();
    return value != null && this.compareWith()(option.value, value);
  }

  protected segmentClass(index: number): string {
    const options = this.options();
    const value = this.value();
    const option = options[index];
    return appRadioGroupSegmentClass({
      size: this.size(),
      selected: value != null && option != null && this.compareWith()(option.value, value),
      first: index === 0,
      last: index === options.length - 1,
    });
  }

  protected onSelect(option: RadioOption<T>): void {
    this.value.set(option.value);
  }

  focus(options?: FocusOptions): void {
    const optionIndex = this.options().findIndex(option => this.isSelected(option) && !option.disabled);
    const fallbackIndex = this.options().findIndex(option => !option.disabled);
    this.radios()[optionIndex >= 0 ? optionIndex : fallbackIndex]?.nativeElement.focus(options);
  }
}
