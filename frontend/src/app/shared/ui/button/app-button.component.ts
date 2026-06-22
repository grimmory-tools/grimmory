import { booleanAttribute, ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { cn } from '../cn';
import { SPINNER_RING } from '../spinner/app-spinner.variants';
import { buttonVariants, type ButtonSize, type ButtonTone, type ButtonVariant } from './app-button.variants';

@Component({
  selector: 'app-button',
  standalone: true,
  host: {
    class: 'inline-block align-middle',
    '[class.w-full]': 'fluid()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [attr.id]="buttonId() || null"
      [class]="buttonClass()"
      [disabled]="disabled() || loading()"
      [type]="type()"
      [attr.name]="name() || null"
      [attr.value]="value() ?? null"
      [attr.form]="form() || null"
      [attr.title]="title() || null"
      [attr.tabindex]="tabIndex()"
      [attr.aria-label]="ariaLabel() || null"
      [attr.aria-busy]="loading() ? 'true' : null"
      [attr.aria-controls]="ariaControls() || null"
      [attr.aria-expanded]="ariaExpanded()"
      [attr.aria-haspopup]="ariaHasPopup()"
      [attr.aria-pressed]="ariaPressed()"
      (click)="clicked.emit($event)">
      @if (loading()) {
        <span [class]="loadingRingClass" aria-hidden="true"></span>
      }
      <span
        class="inline-flex shrink-0 empty:hidden [&>svg]:size-[1em] [&>svg]:shrink-0"
        [class.hidden]="loading()"
        [class.order-last]="iconPos() === 'right'">
        <ng-content />
      </span>
      @if (label()) {
        <span class="leading-none">{{ label() }}</span>
      }
    </button>
  `,
})
export class AppButtonComponent {
  readonly tone = input<ButtonTone>('neutral');
  readonly variant = input<ButtonVariant>('soft');
  readonly size = input<ButtonSize>('md');
  readonly iconOnly = input(false, { transform: booleanAttribute });
  readonly rounded = input(false, { transform: booleanAttribute });
  readonly fluid = input(false, { transform: booleanAttribute });
  readonly styleClass = input('');
  readonly buttonId = input('');
  readonly name = input('');
  readonly value = input<string | number | null>(null);
  readonly form = input('');
  readonly title = input('');
  readonly tabIndex = input<number | null>(null);
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly ariaControls = input('');
  readonly ariaExpanded = input<boolean | string | null>(null);
  readonly ariaHasPopup = input<boolean | 'false' | 'true' | 'menu' | 'listbox' | 'tree' | 'grid' | 'dialog' | null>(null);
  readonly ariaPressed = input<boolean | 'false' | 'true' | 'mixed' | null>(null);
  readonly iconPos = input<'left' | 'right'>('left');
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly type = input<'button' | 'submit' | 'reset'>('button');

  readonly clicked = output<MouseEvent>();

  protected readonly buttonClass = computed(() =>
    cn(
      buttonVariants({
        tone: this.tone(),
        variant: this.variant(),
        size: this.size(),
        iconOnly: this.iconOnly(),
        rounded: this.rounded(),
        fluid: this.fluid(),
      }),
      this.styleClass(),
    ),
  );
  protected readonly loadingRingClass = cn(SPINNER_RING, 'size-[1em] border-2 shrink-0');
}
