import { ChangeDetectionStrategy, Component, Renderer2, RendererStyleFlags2, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Menu } from 'primeng/menu';
import { Tooltip } from 'primeng/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';

import { UserService } from '../../../features/settings/user-management/user.service';
import { IconDisplayComponent } from '../../components/icon-display/icon-display.component';
import { IconSelection } from '../../service/icon-picker.service';
import { LayoutService } from '../layout.service';
import { SidebarLeaf } from '../navigation/nav-item.model';

@Component({
  // Attribute selector so the row renders into its host <li> inside a <ul>.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: '[app-menu-item-row]',
  templateUrl: './app.menu-item-row.component.html',
  styleUrls: ['./app.menu-item-row.component.scss'],
  imports: [
    RouterLink,
    Menu,
    IconDisplayComponent,
    Tooltip,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppMenuItemRowComponent {
  readonly item = input.required<SidebarLeaf>();
  readonly index = input.required<number>();
  readonly parentKey = input.required<string>();

  readonly menuOpen = signal(false);
  readonly key = computed(() => `${this.parentKey()}-${this.index()}`);

  private readonly userService = inject(UserService);
  readonly layoutService = inject(LayoutService);
  private readonly renderer = inject(Renderer2);
  private contextMenuTrigger: HTMLElement | null = null;

  readonly isRouteActive = computed(() => {
    const route = this.item().routerLink?.[0];
    if (!route) return false;
    return this.layoutService.currentPath() === route;
  });

  readonly canManipulateLibrary = computed(() =>
    this.userService.currentUser()?.permissions.canManageLibrary ?? false
  );

  readonly admin = computed(() =>
    this.userService.currentUser()?.permissions.admin ?? false
  );

  runAction(event: Event): void {
    event.preventDefault();
    this.item().action?.();
  }

  openContextMenu(event: Event, menu: Menu): void {
    this.contextMenuTrigger = event.currentTarget instanceof HTMLElement
      ? event.currentTarget
      : null;
    menu.toggle(event);
    this.menuOpen.update((open) => !open);
    event.stopPropagation();
  }

  positionContextMenu(menu: Menu): void {
    if (this.layoutService.isDesktop()) return;

    const trigger = this.contextMenuTrigger;
    const panel = menu.container;
    if (!trigger || !(panel instanceof HTMLElement)) return;

    const gutter = 8;
    const rect = trigger.getBoundingClientRect();
    const panelWidth = panel.offsetWidth;
    const panelHeight = panel.offsetHeight;
    const spaceAbove = rect.top - gutter;
    const spaceBelow = window.innerHeight - rect.bottom - gutter;
    const placeBelow = spaceBelow >= panelHeight || spaceBelow >= spaceAbove;
    const availableHeight = Math.max(placeBelow ? spaceBelow : spaceAbove, 0);
    const left = Math.max(Math.min(rect.right + gutter, window.innerWidth - panelWidth - gutter), gutter);
    const top = placeBelow
      ? rect.bottom + gutter
      : rect.top - Math.min(panelHeight, availableHeight) - gutter;

    const flags = RendererStyleFlags2.DashCase;
    this.renderer.setStyle(panel, '--sidebar-popover-left', `${left}px`, flags);
    this.renderer.setStyle(panel, '--sidebar-popover-top', `${Math.max(top, gutter)}px`, flags);
    this.renderer.setStyle(panel, '--sidebar-popover-max-height', `${availableHeight}px`, flags);
  }

  closeContextMenu(): void {
    this.menuOpen.set(false);
    this.contextMenuTrigger = null;
  }

  getIconSelection(): IconSelection | null {
    const item = this.item();
    if (!item.icon) return null;
    return {
      type: item.iconType || 'PRIME_NG',
      value: item.icon,
    };
  }

  hasContextMenu(): boolean {
    return (this.item().contextMenuItems?.length ?? 0) > 0;
  }

  shouldShowContextMenuButton(): boolean {
    const item = this.item();
    return this.hasContextMenu()
      && (item.type !== 'library' || (this.admin() || this.canManipulateLibrary()));
  }

  formatCount(count: number | null | undefined): string {
    if (!count) return '';
    if (count >= 1000) return Math.floor(count / 100) / 10 + 'K';
    return count.toString();
  }
}
