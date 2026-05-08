import { NgTemplateOutlet } from '@angular/common';
import { Component, Renderer2, RendererStyleFlags2, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppMenuSectionComponent } from './app.menu-section.component';
import { Popover } from 'primeng/popover';
import { Menu } from 'primeng/menu';
import { BookDialogHelperService } from '../../../features/book/components/book-browser/book-dialog-helper.service';
import { UnifiedNotificationBoxComponent } from '../../components/unified-notification-popover/unified-notification-popover-component';
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
import { MessageService, type MenuItem } from 'primeng/api';
import { AppVersion, VersionService } from '../../service/version.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
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

function hasAppUpdate(version: AppVersion | null): boolean {
  return !!version && !!version.latest && version.latest !== version.current;
}

function detectSearchShortcut(userAgent: string): string {
  return /Mac|iPhone|iPad|iPod/i.test(userAgent) ? '⌘K' : 'Ctrl+K';
}

@Component({
  selector: 'app-menu',
  imports: [AppMenuSectionComponent, Popover, Menu, UnifiedNotificationBoxComponent, RouterLink, TranslocoDirective, TranslocoPipe, Tooltip, NgTemplateOutlet],
  templateUrl: './app.menu.component.html',
  styleUrl: './app.menu.component.scss',
})
export class AppMenuComponent {
  private readonly libraryService = inject(LibraryService);
  private readonly libraryHealthService = inject(LibraryHealthService);
  private readonly shelfService = inject(ShelfService);
  private readonly bookService = inject(BookService);
  private readonly libraryShelfMenuService = inject(LibraryShelfMenuService);
  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly commandPaletteService = inject(CommandPaletteService);
  private readonly bookDialogHelperService = inject(BookDialogHelperService);
  private readonly authService = inject(AuthService);
  private readonly messageService = inject(MessageService);
  readonly layoutService = inject(LayoutService);
  private readonly userService = inject(UserService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly seriesDataService = inject(SeriesDataService);
  private readonly authorService = inject(AuthorService);
  private readonly versionService = inject(VersionService);
  private readonly t = inject(TranslocoService);
  private readonly renderer = inject(Renderer2);

  readonly currentUser = this.userService.currentUser;
  readonly version = toSignal<AppVersion | null>(
    this.versionService.getVersion().pipe(catchError(() => of(null))),
    { initialValue: null },
  );
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

  readonly addMenuItems = computed<MenuItem[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const actions = buildQuickActionNavItems(this.translate, user.permissions, {
      createLibrary: () => this.dialogLauncherService.openLibraryCreateDialog().catch(err => this.handleDialogLoadError(err)),
      createShelf: () => this.bookDialogHelperService.openShelfCreatorDialog().catch(err => this.handleDialogLoadError(err)),
      createMagicShelf: () => this.dialogLauncherService.openMagicShelfCreateDialog().catch(err => this.handleDialogLoadError(err)),
      uploadBook: () => this.dialogLauncherService.openFileUploadDialog().catch(err => this.handleDialogLoadError(err)),
    });
    const bookdrop = findPageNavItem('bookdrop', this.translate, user.permissions);
    return this.toMenuItems(bookdrop ? [...actions, bookdrop] : actions);
  });

  readonly userInitials = computed(() => {
    const user = this.currentUser();
    return user ? computeInitials(user.name, user.username) : '';
  });

  readonly hasUpdate = computed(() => hasAppUpdate(this.version()));
  protected readonly addMenuOpen = signal(false);
  protected readonly notificationsOpen = signal(false);
  protected readonly moreMenuOpen = signal(false);

  readonly userMenuItems = computed<MenuItem[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const items: MenuItem[] = [];

    if (!user.permissions.demoUser) {
      items.push({
        label: this.t.translate('layout.menu.account'),
        icon: 'pi pi-user',
        command: () => this.dialogLauncherService.openUserProfileDialog(),
      });
    }

    items.push({
      label: this.t.translate('layout.menu.documentation'),
      icon: 'pi pi-info-circle',
      command: () => window.open(DOCUMENTATION_URL, '_blank', 'noopener,noreferrer'),
    });

    items.push({ separator: true });

    items.push({
      label: this.t.translate('layout.menu.logout'),
      icon: 'pi pi-sign-out',
      command: () => this.authService.logout(),
    });

    return items;
  });

  readonly moreMenuItems = computed<MenuItem[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const perms = user.permissions;
    return this.toMenuItems([
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

  private readonly anchorByOverlay = new WeakMap<Menu | Popover, { trigger: HTMLElement; placement: 'above' | 'below' }>();

  protected applySidebarOverlayPosition(overlay: Menu | Popover): void {
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

  private toMenuItems(items: readonly (NavItem | null | undefined)[]): MenuItem[] {
    return items.flatMap((item) =>
      item ? [{
        label: item.label,
        icon: item.icon ? `pi ${item.icon}` : undefined,
        routerLink: item.routerLink,
        command: item.action ? () => item.action?.() : undefined,
      }] : []
    );
  }

  openSidebarOverlay(event: MouseEvent, overlay: Menu | Popover, placement: 'above' | 'below'): void {
    if (event.currentTarget instanceof HTMLElement) {
      this.anchorByOverlay.set(overlay, { trigger: event.currentTarget, placement });
    }
    overlay.toggle(event);
  }
  private handleDialogLoadError(err: unknown) {
    console.error('Failed to load dialog', err);
    this.messageService.add({
      severity: 'error',
      summary: this.translate('common.error'),
      detail: this.translate('common.dialogLoadError')
    });
  }
}
