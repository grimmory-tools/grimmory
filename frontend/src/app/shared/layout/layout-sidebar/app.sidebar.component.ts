import { Component, Renderer2, RendererStyleFlags2, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppSidebarSectionComponent } from './app.sidebar-section.component';
import { MenuTrigger } from '@angular/aria/menu';
import { Popover } from 'primeng/popover';
import { BookDialogHelperService } from '../../../features/book/components/book-browser/book-dialog-helper.service';
import { UnifiedNotificationBoxComponent } from '../../components/unified-notification-popover/unified-notification-popover-component';
import { MenuComponent } from '../../components/menu/menu.component';
import { MenuEntry } from '../../components/menu/menu-item.model';
import {
  ABOVE_ALIGN_LEFT,
  BELOW_ALIGN_LEFT,
  BESIDE_RIGHT_BOTTOM,
} from '../../components/menu/menu-positions';
import { LibraryService } from '../../../features/book/service/library.service';
import { LibraryHealthService } from '../../../features/book/service/library-health.service';
import { ShelfService } from '../../../features/book/service/shelf.service';
import { BookService } from '../../../features/book/service/book.service';
import { LibraryShelfMenuService } from '../../../features/book/service/library-shelf-menu.service';
import { UserService } from '../../../features/settings/user-management/user.service';
import { MagicShelfService } from '../../../features/magic-shelf/service/magic-shelf.service';
import { SeriesDataService } from '../../../features/series-browser/service/series-data.service';
import { AuthorService } from '../../../features/author-browser/service/author.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { CommandPaletteService } from '../../../features/command-palette/command-palette.service';
import { AuthService } from '../../service/auth.service';
import { LayoutService } from '../layout.service';
import { TranslocoDirective, TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Tooltip } from 'primeng/tooltip';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavItem, SidebarSection } from '../navigation/nav-item.model';
import { buildQuickActionNavItems, findPageNavItem } from '../navigation/nav-catalog';
import {
  buildHomeSection,
  buildLibrarySection,
  buildMagicShelfSection,
  buildShelfSection,
} from './sidebar-sections';

const DOCUMENTATION_URL = 'https://grimmory.org/docs/getting-started';

function computeInitials(name: string | null | undefined, username: string | null | undefined): string {
  const source = (name?.trim() || username?.trim() || '').trim();
  if (!source) return '';
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return parts[0][0].toUpperCase();
}

function detectSearchShortcut(userAgent: string): string {
  return /Mac|iPhone|iPad|iPod/i.test(userAgent) ? '⌘K' : 'Ctrl+K';
}

@Component({
  selector: 'app-sidebar',
  imports: [
    AppSidebarSectionComponent,
    Popover,
    UnifiedNotificationBoxComponent,
    MenuComponent,
    RouterLink,
    TranslocoDirective,
    TranslocoPipe,
    Tooltip,
    MenuTrigger,
  ],
  templateUrl: './app.sidebar.component.html',
  styleUrl: './app.sidebar.component.scss',
})
export class AppSidebarComponent {
  private readonly libraryService = inject(LibraryService);
  private readonly libraryHealthService = inject(LibraryHealthService);
  private readonly shelfService = inject(ShelfService);
  private readonly bookService = inject(BookService);
  private readonly libraryShelfMenuService = inject(LibraryShelfMenuService);
  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly commandPaletteService = inject(CommandPaletteService);
  private readonly bookDialogHelperService = inject(BookDialogHelperService);
  private readonly authService = inject(AuthService);
  readonly layoutService = inject(LayoutService);
  private readonly userService = inject(UserService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly seriesDataService = inject(SeriesDataService);
  private readonly authorService = inject(AuthorService);
  private readonly t = inject(TranslocoService);
  private readonly renderer = inject(Renderer2);

  readonly currentUser = this.userService.currentUser;
  private readonly allAuthors = this.authorService.allAuthors;
  private readonly activeLang = toSignal(this.t.langChanges$, { initialValue: this.t.getActiveLang() });
  private readonly translate = (key: string): string => this.t.translate(key);

  readonly searchShortcutLabel = detectSearchShortcut(
    typeof navigator !== 'undefined' ? navigator.userAgent : ''
  );

  readonly sections = computed<SidebarSection[]>(() => {
    this.activeLang();
    return [
      ...buildHomeSection(this.translate, {
        allBooks: this.bookService.books().length,
        series: this.seriesDataService.allSeries().length,
        authors: this.allAuthors()?.length ?? 0,
      }),
      ...buildLibrarySection(
        this.libraryService.libraries(),
        this.libraryService.bookCountByLibraryId(),
        this.layoutService.librarySort(),
        this.translate,
        { health: this.libraryHealthService, menuItems: this.libraryShelfMenuService },
      ),
      ...buildShelfSection(
        this.shelfService.shelves(),
        this.shelfService.bookCountByShelfId(),
        this.shelfService.unshelvedBookCount(),
        this.layoutService.shelfSort(),
        this.translate,
        { menuItems: this.libraryShelfMenuService },
      ),
      ...buildMagicShelfSection(
        this.magicShelfService.shelves(),
        this.magicShelfService.bookCountByMagicShelfId(),
        this.layoutService.magicShelfSort(),
        this.translate,
        { menuItems: this.libraryShelfMenuService },
      ),
    ];
  });

  readonly addMenuItems = computed<MenuEntry[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const actions = buildQuickActionNavItems(this.translate, user.permissions, {
      createLibrary: () => this.dialogLauncherService.openLibraryCreateDialog(),
      createShelf: () => this.bookDialogHelperService.openShelfCreatorDialog(),
      createMagicShelf: () => this.dialogLauncherService.openMagicShelfCreateDialog(),
      uploadBook: () => this.dialogLauncherService.openFileUploadDialog(),
    });
    const bookdrop = findPageNavItem('bookdrop', this.translate, user.permissions);
    return this.toMenuEntries(bookdrop ? [...actions, bookdrop] : actions);
  });

  readonly userInitials = computed(() => {
    const user = this.currentUser();
    return user ? computeInitials(user.name, user.username) : '';
  });

  protected readonly notificationsOpen = signal(false);

  protected readonly mobileMenuPositions = BELOW_ALIGN_LEFT;
  protected readonly aboveMenuPositions = ABOVE_ALIGN_LEFT;
  protected readonly footerMenuPositions = computed(() =>
    this.layoutService.desktopSidebarCollapsed() ? BESIDE_RIGHT_BOTTOM : ABOVE_ALIGN_LEFT,
  );

  readonly userMenuItems = computed<MenuEntry[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const items: MenuEntry[] = [];

    if (!user.permissions.demoUser) {
      items.push({
        label: this.t.translate('layout.menu.account'),
        icon: 'pi pi-user',
        action: () => this.dialogLauncherService.openUserProfileDialog(),
      });
    }

    items.push({
      label: this.t.translate('layout.menu.documentation'),
      icon: 'pi pi-info-circle',
      action: () => window.open(DOCUMENTATION_URL, '_blank', 'noopener,noreferrer'),
    });

    items.push({ separator: true });

    items.push({
      label: this.t.translate('layout.menu.logout'),
      icon: 'pi pi-sign-out',
      action: () => this.authService.logout(),
    });

    return items;
  });

  readonly moreMenuItems = computed<MenuEntry[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const perms = user.permissions;
    return this.toMenuEntries([
      findPageNavItem('settings', this.translate, perms),
      findPageNavItem('libraryStats', this.translate, perms),
      findPageNavItem('readingStats', this.translate, perms),
      findPageNavItem('metadataManager', this.translate, perms),
    ]);
  });

  openSearch(): void {
    this.commandPaletteService.open();
  }

  closeMobileSidebar(): void {
    this.layoutService.closeMobileSidebar();
  }

  private readonly anchorByOverlay = new WeakMap<Popover, { trigger: HTMLElement; placement: 'above' | 'below' }>();

  protected applySidebarOverlayPosition(overlay: Popover): void {
    const anchor = this.anchorByOverlay.get(overlay);
    const panel = overlay.container;
    if (!anchor || !panel) return;

    window.requestAnimationFrame(() => {
      this.positionSidebarOverlay(anchor, panel);
    });
  }

  private positionSidebarOverlay(
    anchor: { trigger: HTMLElement; placement: 'above' | 'below' },
    panel: HTMLElement,
  ): void {
    const rect = anchor.trigger.getBoundingClientRect();
    const panelWidth = panel.offsetWidth;
    const panelHeight = panel.offsetHeight;
    const left = Math.max(Math.min(rect.left, window.innerWidth - panelWidth - 8), 8);
    const maxTop = Math.max(window.innerHeight - panelHeight - 8, 8);

    const anchorAbove = anchor.placement === 'above' && !this.layoutService.desktopSidebarCollapsed();
    const requestedTop = anchorAbove
      ? rect.top - panelHeight - 8
      : rect.bottom + 8;
    const top = Math.max(Math.min(requestedTop, maxTop), 8);

    const flags = RendererStyleFlags2.DashCase;
    this.renderer.setStyle(panel, '--sidebar-popover-top', `${top}px`, flags);
    this.renderer.setStyle(panel, '--sidebar-popover-max-height', `${Math.max(window.innerHeight - top - 8, 0)}px`, flags);

    if (anchorAbove || !this.layoutService.isDesktop()) {
      this.renderer.setStyle(panel, '--sidebar-popover-left', `${left}px`, flags);
    } else {
      this.renderer.removeStyle(panel, '--sidebar-popover-left', flags);
    }
  }

  private toMenuEntries(items: readonly (NavItem | null | undefined)[]): MenuEntry[] {
    return items.flatMap((item): MenuEntry[] =>
      item ? [{
        label: item.label,
        icon: item.icon ? `pi ${item.icon}` : undefined,
        routerLink: item.routerLink,
        action: item.action,
      }] : []
    );
  }

  openSidebarOverlay(event: MouseEvent, overlay: Popover, placement: 'above' | 'below'): void {
    if (event.currentTarget instanceof HTMLElement) {
      this.anchorByOverlay.set(overlay, { trigger: event.currentTarget, placement });
    }
    overlay.toggle(event);
  }
}
