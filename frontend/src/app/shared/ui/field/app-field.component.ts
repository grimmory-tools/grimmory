import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import { FormField as SignalFormField, type FieldState } from '@angular/forms/signals';
import { cn } from '../cn';
import { APP_FIELD, appFieldErrorMessage, type AppFieldContext, type AppFieldLike } from './app-field.context';

let nextFieldId = 0;

@Component({
  selector: 'app-field',
  standalone: true,
  host: {
    '[class]': 'rootClass()',
    '[class.hidden]': 'hidden()',
    '(input)': 'editing.set(true)',
    '(change)': 'editing.set(true)',
    '(focusout)': 'editing.set(false)',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: APP_FIELD, useExisting: forwardRef(() => AppFieldComponent) }],
  template: `
    @if (label()) {
      <label
        class="inline-flex items-center text-sm font-medium text-text-strong"
        [attr.id]="labelId()"
        [attr.for]="controlId()">
        <span>{{ label() }}</span>
        @if (showRequiredMark()) {
          <span class="ml-0.5 text-danger" aria-hidden="true">*</span>
        }
      </label>
    }

    <ng-content />

    @if (showHint()) {
      <p [id]="hintId()" class="m-0 text-xs text-text-muted dark:text-text-secondary">{{ hint() }}</p>
    }
    @if (displayErrors().length) {
      <div [id]="errorId()" aria-live="polite" class="flex flex-col gap-0.5">
        @for (message of displayErrors(); track message) {
          <p class="m-0 text-xs text-danger">{{ message }}</p>
        }
      </div>
    } @else if (disabledReasonMessages().length) {
      <div [id]="disabledId()" role="status" class="flex flex-col gap-0.5">
        @for (message of disabledReasonMessages(); track message) {
          <p class="m-0 text-xs text-text-muted dark:text-text-secondary">{{ message }}</p>
        }
      </div>
    }
  `,
})
export class AppFieldComponent implements AppFieldContext {
  readonly field = input<AppFieldLike | undefined>(undefined);
  readonly label = input('');
  readonly hint = input('');
  readonly errors = input<string | readonly string[]>([]);
  readonly required = input(false, { transform: booleanAttribute });
  readonly fieldId = input('');
  readonly styleClass = input('');

  private readonly generatedId = `app-field-${++nextFieldId}`;
  protected readonly editing = signal(false);
  private readonly projectedFormField = contentChild(SignalFormField, { descendants: true });
  private readonly fieldState = computed<FieldState<unknown> | null>(
    () => this.field()?.() ?? this.projectedFormField()?.state() ?? null,
  );

  readonly controlId = computed(() => this.fieldId() || this.generatedId);
  readonly labelId = computed(() => (this.label() ? `${this.controlId()}-label` : null));

  protected readonly displayErrors = computed<readonly string[]>(() => {
    const state = this.fieldState();
    if (state) {
      if (!state.touched() || this.editing()) return [];
      return state.errors().map(appFieldErrorMessage);
    }

    const errors = this.errors();
    if (Array.isArray(errors)) return errors;
    return errors ? [errors] : [];
  });
  protected readonly disabledReasonMessages = computed<readonly string[]>(() =>
    (this.fieldState()?.disabledReasons() ?? [])
      .map(reason => reason.message)
      .filter((message): message is string => typeof message === 'string' && message.length > 0),
  );

  protected readonly showHint = computed(() => !!this.hint() && !this.disabledReasonMessages().length);
  protected readonly hintId = computed(() => `${this.controlId()}-hint`);
  protected readonly errorId = computed(() => `${this.controlId()}-error`);
  protected readonly disabledId = computed(() => `${this.controlId()}-disabled`);
  readonly describedById = computed(() => {
    const ids: string[] = [];
    if (this.showHint()) ids.push(this.hintId());
    if (this.displayErrors().length) ids.push(this.errorId());
    else if (this.disabledReasonMessages().length) ids.push(this.disabledId());
    return ids.length ? ids.join(' ') : null;
  });
  readonly validationVisible = computed(() => this.displayErrors().length > 0);

  protected readonly showRequiredMark = computed(() => this.fieldState()?.required() ?? this.required());
  protected readonly hidden = computed(() => this.fieldState()?.hidden() ?? false);
  protected readonly rootClass = computed(() => cn('flex flex-col gap-1.5', this.styleClass()));
}
