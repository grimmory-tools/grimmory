import { ChangeDetectionStrategy, Component, computed, HostBinding, inject, Input, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { MenuService } from './app.menu.service';
import { NgClass } from '@angular/common';
import { Menu } from 'primeng/menu';
import { UserService } from '../../../features/settings/user-management/user.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { BookDialogHelperService } from '../../../features/book/components/book-browser/book-dialog-helper.service';
import { IconDisplayComponent } from '../../components/icon-display/icon-display.component';
import { Tooltip } from 'primeng/tooltip';
import { IconSelection } from '../../service/icon-picker.service';
import { TranslocoPipe } from '@jsverse/transloco';
import { NavItem } from './app.menu.component';

@Component({
  // Keep this attribute selector so recursive menu items remain valid <li> children.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: '[app-menuitem]',
  templateUrl: './app.menuitem.component.html',
  styleUrls: ['./app.menuitem.component.scss'],
  imports: [
    RouterLink,
    NgClass,
    Menu,
    IconDisplayComponent,
    Tooltip,
    TranslocoPipe
  ],
  animations: [
    trigger('children', [
      state('collapsed', style({
        height: '0'
      })),
      state('expanded', style({
        height: '*'
      })),
      transition('collapsed <=> expanded', animate('400ms cubic-bezier(0.86, 0, 0.07, 1)'))
    ])
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppMenuitemComponent implements OnInit {
  @Input() item!: NavItem;
  @Input() index!: number;
  @Input() @HostBinding('class.layout-root-menuitem') root!: boolean;
  @Input() parentKey!: string;
  @Input() menuKey!: string;

  menuOpen = false;
  key: string = '';
  expanded = true;

  private readonly menuService = inject(MenuService);
  private readonly userService = inject(UserService);
  private readonly dialogLauncher = inject(DialogLauncherService);
  private readonly bookDialogHelperService = inject(BookDialogHelperService);

  readonly isRouteActive = computed(() => {
    const route = this.item?.routerLink?.[0];
    if (!route) return false;
    return this.menuService.currentPath() === route;
  });

  readonly canManipulateLibrary = computed(() =>
    this.userService.currentUser()?.permissions.canManageLibrary ?? false
  );

  readonly admin = computed(() =>
    this.userService.currentUser()?.permissions.admin ?? false
  );

  ngOnInit() {
    const rootKey = this.menuKey ? this.menuKey + '-' : '';
    this.key = this.parentKey ? this.parentKey + '-' + this.index : rootKey + String(this.index);
  }

  toggleExpand() {
    this.expanded = !this.expanded;
  }

  openDialog(item: NavItem) {
    if (item.type === 'library' && this.canManipulateLibrary()) {
      this.dialogLauncher.openLibraryCreateDialog();
    }
    if (item.type === 'magicShelf') {
      this.dialogLauncher.openMagicShelfCreateDialog();
    }
    if (item.type === 'shelf') {
      this.bookDialogHelperService.openShelfCreatorDialog();
    }
  }

  formatCount(count: number | null | undefined): string {
    if (count == null) return '0';
    if (count >= 1000) return Math.floor(count / 1000) + 'K';
    return count.toString();
  }

  getIconSelection(): IconSelection | null {
    if (!this.item.icon) return null;

    return {
      type: this.item.iconType || 'PRIME_NG',
      value: this.item.icon
    };
  }

  hasContextMenu(): boolean {
    return (this.item.contextMenuItems?.length ?? 0) > 0;
  }

  shouldShowContextMenuButton(): boolean {
    return this.hasContextMenu()
      && (this.item.type !== 'library' || (this.admin() || this.canManipulateLibrary()));
  }
}
