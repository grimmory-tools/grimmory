import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideChevronRight, LucideDynamicIcon, LucideLoaderCircle, type LucideIconData } from '@lucide/angular';

import { AppMenuComponent } from './app-menu.component';
import { AppMenuAriaItemDirective } from './menu-aria-item.directive';
import { setupMenuItem } from './menu-item-setup';
import {
  appMenuBadgeSlotClass,
  appMenuIconClass,
  appMenuItemRowClass,
  appMenuLabelClass,
  appMenuLeadingSlotClass,
  appMenuShortcutClass,
  appMenuTrailingTextClass,
  appMenuSpinnerClass,
  appMenuSubmenuIconClass,
  appMenuTrailingIconClass,
  type AppMenuItemVariant,
} from './menu.styles';

@Component({
  selector: 'app-menu-item',
  standalone: true,
  imports: [NgTemplateOutlet, RouterLink, LucideDynamicIcon, LucideChevronRight, LucideLoaderCircle],
  hostDirectives: [{ directive: AppMenuAriaItemDirective, inputs: ['disabled', 'submenu'] }],
  host: {
    '[class]': 'rowClass()',
    '[attr.aria-disabled]': 'inert()',
    '(click)': 'onClick($event)',
    '(keydown)': 'onKeydown($event)',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (link() !== null) {
      <a
        #anchor
        [routerLink]="link()"
        [queryParams]="queryParams()"
        [target]="target() || undefined"
        [attr.rel]="target() === '_blank' ? 'noopener' : null"
        tabindex="-1"
        class="flex min-h-full w-full items-center gap-2 no-underline text-inherit outline-none">
        <ng-container [ngTemplateOutlet]="body" />
      </a>
    } @else {
      <ng-container [ngTemplateOutlet]="body" />
    }

    <ng-template #body>
      @if (loading()) {
        <span [class]="leadingSlotClass">
          <svg lucideLoaderCircle [class]="spinnerClass" aria-hidden="true"></svg>
        </span>
      } @else if (icon(); as iconData) {
        <span [class]="leadingSlotClass">
          <svg [lucideIcon]="iconData" [class]="iconClass" aria-hidden="true"></svg>
        </span>
      } @else if (badge()) {
        <span [class]="badgeSlotClass">{{ badge() }}</span>
      } @else if (inset()) {
        <span [class]="leadingSlotClass" aria-hidden="true"></span>
      }

      <span [class]="labelClass" data-menu-label><ng-content /></span>

      @if (trailingIcon(); as iconData) {
        <svg [lucideIcon]="iconData" [class]="trailingIconClass" aria-hidden="true"></svg>
      }
      @if (trailingText()) {
        <span [class]="trailingTextClass" aria-hidden="true">{{ trailingText() }}</span>
      }
      @if (shortcut()) {
        <span [class]="shortcutClass" aria-hidden="true">{{ shortcut() }}</span>
      }
      @if (menuItem.hasPopup()) {
        <svg lucideChevronRight [class]="submenuIconClass" aria-hidden="true"></svg>
      }
    </ng-template>
  `,
})
export class AppMenuItemComponent {
  readonly icon = input<LucideIconData | undefined>(undefined);
  readonly trailingIcon = input<LucideIconData | undefined>(undefined);
  readonly badge = input('');
  readonly loading = input(false, { transform: booleanAttribute });
  readonly inset = input(false, { transform: booleanAttribute });
  readonly shortcut = input('');
  readonly trailingText = input('');
  readonly variant = input<AppMenuItemVariant>('default');
  readonly closeOnSelect = input(true, { transform: booleanAttribute });
  readonly link = input<string | readonly unknown[] | null>(null);
  readonly target = input('');
  readonly queryParams = input<Record<string, unknown> | null>(null);
  readonly searchLabel = input('');

  readonly selected = output<void>();

  protected readonly menuItem = inject(AppMenuAriaItemDirective);
  private readonly owner = inject(AppMenuComponent);
  private readonly anchor = viewChild<ElementRef<HTMLAnchorElement>>('anchor');
  private suppressClick = false;

  protected readonly leadingSlotClass = appMenuLeadingSlotClass;
  protected readonly badgeSlotClass = appMenuBadgeSlotClass;
  protected readonly iconClass = appMenuIconClass;
  protected readonly spinnerClass = `${appMenuSpinnerClass} animate-spin`;
  protected readonly labelClass = appMenuLabelClass;
  protected readonly trailingIconClass = appMenuTrailingIconClass;
  protected readonly shortcutClass = appMenuShortcutClass;
  protected readonly trailingTextClass = appMenuTrailingTextClass;
  protected readonly submenuIconClass = appMenuSubmenuIconClass;
  protected readonly rowClass = computed(() => appMenuItemRowClass(this.variant()));
  protected readonly inert = computed(() => this.loading() || this.menuItem.disabled());

  constructor() {
    setupMenuItem(this.searchLabel);

    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const blockInertActivation = (event: Event) => {
      if (!this.inert()) return;
      const isActivation =
        event.type === 'click' ||
        (event instanceof KeyboardEvent && (event.key === 'Enter' || event.key === ' ' || event.key === 'ArrowRight'));
      if (isActivation) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    };
    host.addEventListener('keydown', blockInertActivation, true);
    host.addEventListener('click', blockInertActivation, true);

    inject(DestroyRef).onDestroy(() => {
      host.removeEventListener('keydown', blockInertActivation, true);
      host.removeEventListener('click', blockInertActivation, true);
      const submenu = this.menuItem.submenu();
      if (submenu && submenu.parent() === this.menuItem) {
        submenu.parent.set(undefined);
      }
    });
  }

  protected onClick(event: MouseEvent): void {
    if (this.menuItem.hasPopup()) return;
    event.stopPropagation();
    if (this.suppressClick) {
      this.suppressClick = false;
      return;
    }
    if (this.inert()) {
      event.preventDefault();
      return;
    }
    this.activate();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.repeat || (event.key !== 'Enter' && event.key !== ' ')) return;
    if (this.menuItem.hasPopup()) return;
    event.stopPropagation();
    event.preventDefault();
    if (this.inert()) return;
    if (this.link() !== null) {
      this.suppressClick = true;
      this.anchor()?.nativeElement.click();
    }
    this.activate();
  }

  private activate(): void {
    this.selected.emit();
    if (this.closeOnSelect()) this.owner.closeChain();
  }
}
