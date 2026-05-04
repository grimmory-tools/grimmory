import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
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
  readonly desktopSidebarCollapsed = computed(() =>
    this.layoutService.isDesktop() && this.layoutService.sidebarCollapsed()
  );

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
    menu.toggle(event);
    this.menuOpen.update((open) => !open);
    event.stopPropagation();
  }

  closeContextMenu(): void {
    this.menuOpen.set(false);
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
