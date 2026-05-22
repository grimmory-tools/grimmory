import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AppSidebarSectionComponent } from './app.sidebar-section.component';
import { Menu } from 'primeng/menu';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { CdkConnectedOverlay, CdkOverlayOrigin, ConnectedPosition } from '@angular/cdk/overlay';
import { BookDialogHelperService } from '../../../features/book/components/book-browser/book-dialog-helper.service';
import { UnifiedNotificationBoxComponent } from '../../components/unified-notification-popover/unified-notification-popover-component';
import { AppButtonDirective } from '../../components/button/app-button.directive';
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
import type { MenuItem } from 'primeng/api';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
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
import { MetadataProgressService } from '../../service/metadata-progress.service';
import { BookdropFileService } from '../../../features/bookdrop/service/bookdrop-file.service';
import { MetadataBatchProgressNotification, MetadataBatchStatus } from '../../model/metadata-batch-progress.model';
import { LibraryImportProgressService } from '../../service/library-import-progress.service';

const DOCUMENTATION_URL = 'https://grimmory.org/docs/getting-started';
const ABOVE_ALIGN_LEFT: ConnectedPosition[] = [
  { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -8 },
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
];
const RIGHT_ALIGN_TOP: ConnectedPosition[] = [
  { originX: 'end', originY: 'top', overlayX: 'start', overlayY: 'top', offsetX: 8 },
  { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'bottom', offsetX: 8 },
  { originX: 'start', originY: 'top', overlayX: 'end', overlayY: 'top', offsetX: -8 },
  { originX: 'start', originY: 'bottom', overlayX: 'end', overlayY: 'bottom', offsetX: -8 },
];
const ABOVE_ALIGN_CENTER: ConnectedPosition[] = [
  { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -8 },
  { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 8 },
];
const BELOW_ALIGN_CENTER: ConnectedPosition[] = [
  { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 8 },
  { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -8 },
];

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

  if (latestVersion[0] !== currentVersion[0]) return latestVersion[0] > currentVersion[0];
  if (latestVersion[1] !== currentVersion[1]) return latestVersion[1] > currentVersion[1];
  return latestVersion[2] > currentVersion[2];
}

@Component({
  selector: 'app-sidebar',
  imports: [
    AppSidebarSectionComponent,
    AppButtonDirective,
    Menu,
    UnifiedNotificationBoxComponent,
    RouterLink,
    TranslocoDirective,
    TranslocoPipe,
    Tooltip,
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
  private readonly destroyRef = inject(DestroyRef);
  private readonly metadataProgressService = inject(MetadataProgressService);
  private readonly bookdropFileService = inject(BookdropFileService);
  private readonly libraryImportProgressService = inject(LibraryImportProgressService);

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

  readonly addMenuItems = computed<MenuItem[]>(() => {
    this.activeLang();
    const user = this.currentUser();
    if (!user) return [];

    const actions = buildCreateActionNavItems(this.translate, user.permissions, {
      createLibrary: () => void this.dialogLauncherService.openLibraryCreateDialog().catch(() => undefined),
      createShelf: () => void this.bookDialogHelperService.openShelfCreatorDialog().catch(() => undefined),
      createMagicShelf: () => void this.dialogLauncherService.openMagicShelfCreateDialog().catch(() => undefined),
      uploadBook: () => void this.dialogLauncherService.openFileUploadDialog().catch(() => undefined),
    });
    return this.toMenuItems(actions);
  });

  readonly userInitials = computed(() => {
    const user = this.currentUser();
    return user ? computeInitials(user.name, user.username) : '';
  });

  protected readonly notificationsOpen = signal(false);
  protected readonly notificationPopoverOrigin = signal<CdkOverlayOrigin | null>(null);
  private readonly notificationPopoverMobilePositions = signal<ConnectedPosition[]>(ABOVE_ALIGN_CENTER);
  protected readonly addMenuOpen = signal(false);
  protected readonly aboveMenuPositions = ABOVE_ALIGN_LEFT;
  protected readonly notificationPopoverPositions = computed(() =>
    this.layoutService.isDesktop() ? RIGHT_ALIGN_TOP : this.notificationPopoverMobilePositions()
  );
  protected progressHighlight = false;
  protected completedTaskCount = 0;
  protected hasPendingBookdropFiles = false;
  protected hasActiveLibraryImport = false;

  private readonly latestTasks: Record<string, MetadataBatchProgressNotification> = {};

  constructor() {
    this.subscribeToMetadataProgress();

    this.metadataProgressService.activeTasks$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((tasks) => {
        this.replaceLatestTasks(tasks);
        this.updateCompletedTaskCount();
      });

    this.bookdropFileService.hasPendingFiles$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((hasPending) => {
        this.hasPendingBookdropFiles = hasPending;
        this.updateCompletedTaskCount();
      });

    this.libraryImportProgressService.hasActiveImport$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((hasActive) => {
        this.hasActiveLibraryImport = hasActive;
        this.updateCompletedTaskCount();
      });
  }

  openSearch(): void {
    this.commandPaletteService.open();
  }

  protected get shouldShowNotificationBadge(): boolean {
    return this.completedTaskCount > 0 && !this.progressHighlight;
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
    void this.dialogLauncherService.openUserProfileDialog().catch(() => undefined);
    this.closeUserPopover();
  }

  protected openSettings(): void {
    this.router.navigate(['/settings']);
    this.closeUserPopover();
  }

  protected openChangelogDialog(): void {
    void this.dialogLauncherService.openVersionChangelogDialog().catch(() => undefined);
    this.closeUserPopover();
  }

  protected openReadingStats(): void {
    this.router.navigate(['/reading-stats']);
    this.closeUserPopover();
  }

  protected openUploadDialog(): void {
    void this.dialogLauncherService.openFileUploadDialog().catch(() => undefined);
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

  protected onNotificationsPopoverKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.closeNotificationsPopover();
    }
  }

  private toMenuItems(items: readonly (NavItem | null | undefined)[]): MenuItem[] {
    return items.flatMap((item): MenuItem[] =>
      item ? [{
        label: item.label,
        icon: item.icon ? `pi ${item.icon}` : undefined,
        routerLink: item.routerLink,
        command: item.action,
      }] : []
    );
  }

  protected toggleAddMenu(event: MouseEvent, menu: Menu): void {
    menu.toggle(event);
  }

  protected toggleHeaderNotificationsPopover(origin: CdkOverlayOrigin): void {
    this.toggleNotificationsPopover(origin, BELOW_ALIGN_CENTER);
  }

  protected toggleFooterNotificationsPopover(origin: CdkOverlayOrigin): void {
    this.toggleNotificationsPopover(origin, ABOVE_ALIGN_CENTER);
  }

  private toggleNotificationsPopover(origin: CdkOverlayOrigin, mobilePositions = ABOVE_ALIGN_CENTER): void {
    if (this.notificationsOpen() && this.notificationPopoverOrigin() === origin) {
      this.closeNotificationsPopover();
      return;
    }

    this.notificationPopoverMobilePositions.set(mobilePositions);
    this.notificationPopoverOrigin.set(origin);
    this.notificationsOpen.set(true);
  }

  protected closeNotificationsPopover(): void {
    this.notificationsOpen.set(false);
    this.notificationPopoverOrigin.set(null);
  }

  private subscribeToMetadataProgress(): void {
    this.metadataProgressService.progressUpdates$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((progress) => {
        this.progressHighlight = progress.status === MetadataBatchStatus.IN_PROGRESS;
      });
  }

  private replaceLatestTasks(tasks: Record<string, MetadataBatchProgressNotification>): void {
    for (const key of Object.keys(this.latestTasks)) {
      delete this.latestTasks[key];
    }
    Object.assign(this.latestTasks, tasks);
  }

  private updateCompletedTaskCount(): void {
    const metadataTaskCount = Object.keys(this.latestTasks).length;
    const bookdropFileTaskCount = this.hasPendingBookdropFiles ? 1 : 0;
    const libraryImportTaskCount = this.hasActiveLibraryImport ? 1 : 0;
    this.completedTaskCount = metadataTaskCount + bookdropFileTaskCount + libraryImportTaskCount;
  }
}
