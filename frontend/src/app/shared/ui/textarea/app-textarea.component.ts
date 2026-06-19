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
import { type FormValueControl } from '@angular/forms/signals';
import { cn } from '../cn';
import { APP_FIELD } from '../field/app-field.context';
import { appTextareaVariants } from './app-textarea.variants';

@Component({
  selector: 'app-textarea',
  standalone: true,
  host: { class: 'relative block w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <textarea
      #textarea
      [class]="textareaClass()"
      [attr.id]="resolvedInputId()"
      [attr.name]="name() || null"
      [attr.placeholder]="placeholder() || null"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-describedby]="resolvedDescribedBy()"
      [attr.aria-invalid]="showInvalid() ? 'true' : null"
      [attr.aria-busy]="pending() ? 'true' : null"
      [attr.maxlength]="maxLength()"
      [attr.minlength]="minLength()"
      [attr.rows]="rows()"
      [style.height]="initialHeight()"
      [style.min-height]="minHeight()"
      [value]="value()"
      [disabled]="disabled()"
      [readonly]="readonly()"
      [required]="required()"
      (input)="onInput(textarea)"
      (blur)="touched.set(true)"></textarea>
    @if (pending()) {
      <span class="pointer-events-none absolute right-3 top-2.5 inline-flex text-text-muted">
        <i class="pi pi-spinner pi-spin text-xs" aria-hidden="true"></i>
      </span>
    }
  `,
})
export class AppTextareaComponent implements FormValueControl<string> {
  readonly value = model('');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly pending = input(false, { transform: booleanAttribute });
  readonly touched = model(false);
  readonly name = input('');

  readonly inputId = input('');
  readonly placeholder = input('');
  readonly ariaLabel = input('');
  readonly ariaDescribedBy = input('');
  readonly styleClass = input('');
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly rows = input<string | number | null>(4);
  readonly maxLength = input<number | undefined>(undefined);
  readonly minLength = input<number | undefined>(undefined);
  readonly autoResize = input(false, { transform: booleanAttribute });

  private readonly fieldContext = inject(APP_FIELD, { optional: true });
  protected readonly resolvedInputId = computed(() => this.inputId() || this.fieldContext?.controlId() || null);
  protected readonly resolvedDescribedBy = computed(
    () => this.ariaDescribedBy() || this.fieldContext?.describedById() || null,
  );
  protected readonly showInvalid = computed(() => this.invalid() && (this.fieldContext?.validationVisible() ?? true));

  protected readonly textareaClass = computed(() =>
    cn(appTextareaVariants({ autoResize: this.autoResize() }), this.pending() && 'pr-10', this.styleClass()),
  );
  protected readonly minHeight = computed(() => {
    const rowCount = Number(this.rows());
    const rows = Number.isFinite(rowCount) && rowCount > 0 ? rowCount : 4;

    return `calc(${rows} * 1.25rem + 1rem)`;
  });
  protected readonly initialHeight = computed(() => (this.autoResize() ? null : this.minHeight()));

  private readonly textarea = viewChild<ElementRef<HTMLTextAreaElement>>('textarea');

  constructor() {
    afterRenderEffect({
      mixedReadWrite: () => {
        this.value();
        this.autoResize();
        this.resize();
      },
    });
  }

  protected onInput(textarea: HTMLTextAreaElement): void {
    if (this.disabled() || this.readonly()) {
      textarea.value = this.value();
      return;
    }
    this.value.set(textarea.value);
    this.resize();
  }

  focus(options?: FocusOptions): void {
    this.textarea()?.nativeElement.focus(options);
  }

  private resize(): void {
    if (!this.autoResize()) return;

    const textarea = this.textarea()?.nativeElement;
    if (!textarea) return;

    textarea.style.height = 'auto';
    textarea.style.height = `${textarea.scrollHeight}px`;
  }
}
