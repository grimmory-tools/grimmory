import {booleanAttribute, Directive, input} from '@angular/core';

export type AppButtonVariant = 'neutral' | 'primary' | 'danger';
export type AppButtonSize = 'sm' | 'md';

@Directive({
  selector: 'button[appButton], a[appButton]',
  standalone: true,
  host: {
    class: 'app-button',
    '[class.app-button-primary]': 'variant() === "primary"',
    '[class.app-button-danger]': 'variant() === "danger"',
    '[class.app-button-sm]': 'size() === "sm"',
    '[class.app-button-icon]': 'iconOnly()',
  },
})
export class AppButtonDirective {
  readonly variant = input<AppButtonVariant>('neutral');
  readonly size = input<AppButtonSize>('md');
  readonly iconOnly = input(false, {transform: booleanAttribute});
}
