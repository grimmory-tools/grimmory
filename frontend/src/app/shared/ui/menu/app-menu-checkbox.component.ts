import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  model,
  output,
} from '@angular/core';
import { LucideCheck, LucideMinus } from '@lucide/angular';

import { AppMenuComponent } from './app-menu.component';
import { AppMenuAriaItemDirective } from './menu-aria-item.directive';
import { setupMenuItem } from './menu-item-setup';
import {
  checkIndicatorClass,
  checkIndicatorIconClass,
} from '../checkbox/check-indicator.styles';
import {
  appMenuItemRowClass,
  appMenuLabelClass,
  appMenuLeadingSlotClass,
  appMenuShortcutClass,
} from './menu.styles';

@Component({
  selector: 'app-menu-checkbox',
  standalone: true,
  imports: [LucideCheck, LucideMinus],
  hostDirectives: [{ directive: AppMenuAriaItemDirective, inputs: ['value', 'disabled'] }],
  host: {
    '[class]': 'rowClass',
    '[attr.role]': "'menuitemcheckbox'",
    '[attr.aria-checked]': "mixed() ? 'mixed' : checked()",
    '(click)': 'onEvent($event)',
    '(keydown)': 'onEvent($event)',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="leadingSlotClass">
      <span [class]="boxClass()" aria-hidden="true">
        @if (mixed()) {
          <svg lucideMinus [class]="checkClass" aria-hidden="true"></svg>
        } @else if (checked()) {
          <svg lucideCheck [class]="checkClass" aria-hidden="true"></svg>
        }
      </span>
    </span>
    <span [class]="labelClass" data-menu-label><ng-content /></span>
    @if (shortcut()) {
      <span [class]="shortcutClass" aria-hidden="true">{{ shortcut() }}</span>
    }
  `,
})
export class AppMenuCheckboxComponent {
  readonly checked = model(false);
  readonly mixed = model(false);
  readonly shortcut = input('');
  readonly closeOnSelect = input(false, { transform: booleanAttribute });
  readonly searchLabel = input('');

  readonly selected = output<boolean>();

  private readonly menuItem = inject(AppMenuAriaItemDirective);
  private readonly owner = inject(AppMenuComponent);

  protected readonly rowClass = appMenuItemRowClass('default');
  protected readonly leadingSlotClass = appMenuLeadingSlotClass;
  protected readonly checkClass = checkIndicatorIconClass;
  protected readonly labelClass = appMenuLabelClass;
  protected readonly shortcutClass = appMenuShortcutClass;
  protected readonly boxClass = computed(() => checkIndicatorClass(this.checked() || this.mixed()));

  constructor() {
    setupMenuItem(this.searchLabel);
  }

  protected onEvent(event: Event): void {
    if (event instanceof KeyboardEvent && (event.repeat || (event.key !== 'Enter' && event.key !== ' '))) return;
    event.stopPropagation();
    event.preventDefault();
    if (this.menuItem.disabled()) return;
    this.toggle();
  }

  private toggle(): void {
    const next = this.mixed() || !this.checked();
    this.mixed.set(false);
    this.checked.set(next);
    this.selected.emit(next);
    if (this.closeOnSelect()) this.owner.closeChain();
  }
}
