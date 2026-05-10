import { Component, Renderer2, RendererStyleFlags2, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppSidebarSectionComponent } from './app.sidebar-section.component';
import { MenuTrigger } from '@angular/aria/menu';
import { Popover } from 'primeng/popover';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
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
import { buildCreateActionNavItems } from '../navigation/nav-catalog';
import {
  buildHomeSection,
  buildLibrarySection,
  buildMagicShelfSection,
  buildShelfSection,
  buildToolsSection,
} from './sidebar-sections';
import { VersionService } from '../../service/version.service';

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

type SemanticVersion = readonly [number, number, number];

function parseSemanticVersion(version: string | undefined): SemanticVersion | null {
  const match = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(version?.trim() ?? '');
  return match ? [Number(match[1]), Number(match[2]), Number(match[3])] : null;
}

function formatVersionLabel(version: string): string {
  const value = version.trim();
  if (!value) return 'unknown';
  const semanticVersion = parseSemanticVersion(value);
  return semanticVersion ? `v${semanticVersion.join('.')}` : value;
}

function isSemanticVersion(version: string | undefined): boolean {
  return parseSemanticVersion(version) !== null;
}

function isNewerVersion(latest: string | undefined, current: string | undefined): boolean {
  const latestVersion = parseSemanticVersion(latest);
  const currentVersion = parseSemanticVersion(current);
  if (!latestVersion || !currentVersion) return false;

  for (let index = 0; index < latestVersion.length; index++) {
    if (latestVersion[index] !== currentVersion[index]) {
      return latestVersion[index] > currentVersion[index];
    }
  }

  return false;
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
    CdkTrapFocus,
    CdkConnectedOverlay,
    CdkOverlayOrigin,
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
  private readonly router = inject(Router);
  readonly layoutService = inject(LayoutService);
  private readonly userService = inject(UserService);
  private readonly versionService = inject(VersionService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly seriesDataService = inject(SeriesDataService);
  private readonly authorService = inject(AuthorService);
  private readonly t = inject(TranslocoService);
  private readonly renderer = inject(Renderer2);

  readonly currentUser = this.userService.currentUser;
  private readonly allAuthors = this.authorService.allAuthors;
  protected readonly activeLang = toSignal(this.t.langChanges$, { initialValue: this.t.getActiveLang() });
  protected readonly versionInfo = toSignal(this.versionService.getVersion(), { initialValue: null });
  protected readonly appVersionLabel = computed(() => formatVersionLabel(this.versionInfo()?.current ?? '...'));
  protected readonly updateAvailable = computed(() => {
    const version = this.versionInfo();
    return isSemanticVersion(version?.current)
      && isSemanticVersion(version?.latest)
      && isNewerVersion(version?.latest, version?.current);
  });
  private readonly translate = (key: string): string => this.t.translate(key);
  protected readonly userPopoverOpen = signal(false);
  protected readonly userPopoverOrigin = signal<CdkOverlayOrigin | null>(null);
  protected readonly canAccessReadingStats = computed(() => {
    const user = this.currentUser();
    return !!user && (user.permissions.admin || user.permissions.canAccessUserStats);
  });
  protected readonly canUploadBooks = computed(() => {
    const user = this.currentUser();
    return !!user && (user.permissions.admin || user.permissions.canUpload);
  });

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
      ...buildToolsSection(this.translate, this.currentUser()?.permissions ?? {}),
    ];
  });

  readonly addMenuItems = computed<MenuEntry[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const actions = buildCreateActionNavItems(this.translate, user.permissions, {
      createLibrary: () => this.dialogLauncherService.openLibraryCreateDialog(),
      createShelf: () => this.bookDialogHelperService.openShelfCreatorDialog(),
      createMagicShelf: () => this.dialogLauncherService.openMagicShelfCreateDialog(),
      uploadBook: () => this.dialogLauncherService.openFileUploadDialog(),
    });
    return this.toMenuEntries(actions);
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

  openSearch(): void {
    this.commandPaletteService.open();
  }

  closeMobileSidebar(): void {
    this.layoutService.closeMobileSidebar();
  }

  protected toggleUserPopover(origin: CdkOverlayOrigin): void {
    if (this.userPopoverOpen() && this.userPopoverOrigin() === origin) {
      this.closeUserPopover();
      return;
    }

    this.userPopoverOrigin.set(origin);
    this.userPopoverOpen.set(true);
  }

  protected closeUserPopover(): void {
    this.userPopoverOpen.set(false);
  }

  protected openDocumentation(): void {
    window.open(DOCUMENTATION_URL, '_blank', 'noopener,noreferrer');
    this.closeUserPopover();
  }

  protected openAccountSettings(): void {
    this.dialogLauncherService.openUserProfileDialog();
    this.closeUserPopover();
  }

  protected openSettings(): void {
    this.router.navigate(['/settings']);
    this.closeUserPopover();
  }

  protected openChangelogDialog(): void {
    this.dialogLauncherService.openVersionChangelogDialog();
    this.closeUserPopover();
  }

  protected openReadingStats(): void {
    this.router.navigate(['/reading-stats']);
    this.closeUserPopover();
  }

  protected openUploadDialog(): void {
    this.dialogLauncherService.openFileUploadDialog();
  }

  protected logout(): void {
    this.authService.logout();
    this.closeUserPopover();
  }

  protected onUserPopoverKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeUserPopover();
    }
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
