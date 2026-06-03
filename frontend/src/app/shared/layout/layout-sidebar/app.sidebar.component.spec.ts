import { computed, Signal, signal, WritableSignal } from '@angular/core';
import { CdkOverlayOrigin } from '@angular/cdk/overlay';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageService } from 'primeng/api';

import { getTranslocoModule } from '../../../core/testing/transloco-testing';
import { BookDialogHelperService } from '../../../features/book/components/book-browser/book-dialog-helper.service';
import { BookService } from '../../../features/book/service/book.service';
import { LibraryHealthService } from '../../../features/book/service/library-health.service';
import { LibraryService } from '../../../features/book/service/library.service';
import { LibraryShelfMenuService } from '../../../features/book/service/library-shelf-menu.service';
import { ShelfService } from '../../../features/book/service/shelf.service';
import { AuthorService } from '../../../features/author-browser/service/author.service';
import { MagicShelfService } from '../../../features/magic-shelf/service/magic-shelf.service';
import { SeriesDataService } from '../../../features/series-browser/service/series-data.service';
import { UserService } from '../../../features/settings/user-management/user.service';
import { CommandPaletteService } from '../../../features/command-palette/command-palette.service';
import { BookdropFileService } from '../../../features/bookdrop/service/bookdrop-file.service';
import { AuthService } from '../../service/auth.service';
import { MetadataBatchProgressNotification, MetadataBatchStatus } from '../../model/metadata-batch-progress.model';
import { MetadataProgressService } from '../../service/metadata-progress.service';
import { LibraryImportProgressService } from '../../service/library-import-progress.service';
import { AppVersion, VersionService } from '../../service/version.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { LayoutService } from '../layout.service';

import { AppSidebarComponent } from './app.sidebar.component';

interface TestUser {
  name: string;
  username: string;
  permissions: Record<string, boolean>;
}

describe('AppSidebarComponent', () => {
  let fixture: ComponentFixture<AppSidebarComponent>;
  let component: AppSidebarComponent;
  let commandPaletteService: { open: ReturnType<typeof vi.fn> };
  let currentUser: WritableSignal<TestUser | null>;
  let versionInfo: BehaviorSubject<AppVersion>;
  let activeTasks$: BehaviorSubject<Record<string, MetadataBatchProgressNotification>>;
  let progressUpdates$: BehaviorSubject<MetadataBatchProgressNotification>;
  let hasPendingFiles: WritableSignal<boolean>;
  let hasActiveImport: WritableSignal<boolean>;
  const sidebarCollapsed = signal(false);
  const isDesktop = signal(true);
  const currentPath = signal('/dashboard');
  const layoutService = {
    sidebarCollapsed,
    isDesktop,
    currentPath,
    desktopSidebarCollapsed: computed(() => isDesktop() && sidebarCollapsed()),
    librarySort: signal({ field: 'name', order: 'desc' }),
    shelfSort: signal({ field: 'name', order: 'asc' }),
    magicShelfSort: signal({ field: 'name', order: 'asc' }),
    closeMobileSidebar: vi.fn(),
  };

  beforeEach(() => {
    commandPaletteService = { open: vi.fn() };
    currentUser = signal<TestUser | null>(null);
    versionInfo = new BehaviorSubject<AppVersion>({ current: '1.2.3', latest: '1.2.3' });
    activeTasks$ = new BehaviorSubject<Record<string, MetadataBatchProgressNotification>>({});
    progressUpdates$ = new BehaviorSubject<MetadataBatchProgressNotification>({
      taskId: 'initial',
      completed: 0,
      total: 1,
      message: 'idle',
      status: MetadataBatchStatus.COMPLETED,
      review: false,
    });
    hasPendingFiles = signal(false);
    hasActiveImport = signal(false);

    TestBed.configureTestingModule({
      imports: [AppSidebarComponent, getTranslocoModule()],
      providers: [
        { provide: LibraryService, useValue: { libraries: signal([]), bookCountByLibraryId: signal(new Map()) } },
        { provide: LibraryHealthService, useValue: { isUnhealthy: vi.fn(() => false) } },
        { provide: ShelfService, useValue: { shelves: signal([]), bookCountByShelfId: signal(new Map()), unshelvedBookCount: signal(0) } },
        { provide: BookService, useValue: { books: signal([]) } },
        {
          provide: LibraryShelfMenuService,
          useValue: {
            initializeLibraryMenuItems: vi.fn(() => []),
            initializeShelfMenuItems: vi.fn(() => []),
            initializeMagicShelfMenuItems: vi.fn(() => []),
          },
        },
        {
          provide: DialogLauncherService,
          useValue: {
            openLibraryCreateDialog: vi.fn(() => Promise.resolve(null)),
            openMagicShelfCreateDialog: vi.fn(() => Promise.resolve(null)),
            openFileUploadDialog: vi.fn(() => Promise.resolve(null)),
          },
        },
        { provide: CommandPaletteService, useValue: commandPaletteService },
        { provide: BookDialogHelperService, useValue: { openShelfCreatorDialog: vi.fn(() => Promise.resolve(null)) } },
        { provide: AuthService, useValue: { logout: vi.fn() } },
        { provide: MetadataProgressService, useValue: { activeTasks$, progressUpdates$ } },
        { provide: BookdropFileService, useValue: { hasPendingFiles } },
        { provide: LibraryImportProgressService, useValue: { hasActiveImport } },
        { provide: VersionService, useValue: { getVersion: vi.fn(() => versionInfo) } },
        { provide: LayoutService, useValue: layoutService },
        { provide: UserService, useValue: { currentUser } },
        { provide: MagicShelfService, useValue: { shelves: signal([]), bookCountByMagicShelfId: signal(new Map()) } },
        { provide: SeriesDataService, useValue: { allSeries: signal([]) } },
        { provide: AuthorService, useValue: { allAuthors: signal([]) } },
        { provide: MessageService, useValue: { add: vi.fn() } },
      ],
    });

    TestBed.overrideComponent(AppSidebarComponent, { set: { template: '' } });

    fixture = TestBed.createComponent(AppSidebarComponent);
    component = fixture.componentInstance;
    layoutService.isDesktop.set(true);
    layoutService.currentPath.set('/dashboard');
    layoutService.closeMobileSidebar.mockReset();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('opens the command palette from the sidebar search trigger', () => {
    component.openSearch();

    expect(commandPaletteService.open).toHaveBeenCalled();
  });

  it('closes the mobile drawer from the sidebar header close action', () => {
    component.closeMobileSidebar();

    expect(layoutService.closeMobileSidebar).toHaveBeenCalled();
  });

  it('opens the notifications overlay from the selected origin', () => {
    const origin = {} as CdkOverlayOrigin;
    const sidebar = component as unknown as {
      notificationsOpen: () => boolean;
      notificationPopoverOrigin: () => CdkOverlayOrigin | null;
      toggleFooterNotificationsPopover(origin: CdkOverlayOrigin): void;
    };

    sidebar.toggleFooterNotificationsPopover(origin);

    expect(sidebar.notificationsOpen()).toBe(true);
    expect(sidebar.notificationPopoverOrigin()).toBe(origin);
  });

  it('closes the notifications overlay when the same origin is toggled again', () => {
    const origin = {} as CdkOverlayOrigin;
    const sidebar = component as unknown as {
      notificationsOpen: () => boolean;
      notificationPopoverOrigin: () => CdkOverlayOrigin | null;
      toggleFooterNotificationsPopover(origin: CdkOverlayOrigin): void;
    };

    sidebar.toggleFooterNotificationsPopover(origin);
    sidebar.toggleFooterNotificationsPopover(origin);

    expect(sidebar.notificationsOpen()).toBe(false);
    expect(sidebar.notificationPopoverOrigin()).toBeNull();
  });

  it('derives user initials from a multi-part display name', () => {
    currentUser.set({ name: 'Alex Bilbie', username: 'alex', permissions: {} });

    expect(component.userInitials()).toBe('AB');
  });

  it('falls back to the first initial when the display name is a single part', () => {
    currentUser.set({ name: '  Cher ', username: 'cher', permissions: {} });

    expect(component.userInitials()).toBe('C');
  });

  it('uses the username when the display name is empty', () => {
    currentUser.set({ name: '', username: 'alex', permissions: {} });

    expect(component.userInitials()).toBe('A');
  });

  it('marks settings active from the current layout path', () => {
    const sidebar = component as unknown as { isSettingsActive: () => boolean };

    expect(sidebar.isSettingsActive()).toBe(false);

    layoutService.currentPath.set('/settings');

    expect(sidebar.isSettingsActive()).toBe(true);
  });

  it('returns an empty string when no user is signed in', () => {
    currentUser.set(null);

    expect(component.userInitials()).toBe('');
  });

  it('normalizes semantic version labels with or without a leading v', () => {
    const sidebar = component as unknown as { appVersionLabel: () => string };

    versionInfo.next({ current: '1.2.3', latest: '1.2.3' });
    expect(sidebar.appVersionLabel()).toBe('v1.2.3');

    versionInfo.next({ current: 'v1.2.3', latest: '1.2.3' });
    expect(sidebar.appVersionLabel()).toBe('v1.2.3');

    versionInfo.next({ current: 'development', latest: 'v1.2.3' });
    expect(sidebar.appVersionLabel()).toBe('development');
  });

  it('compares update versions numerically after normalizing a leading v', () => {
    const sidebar = component as unknown as { updateAvailable: () => boolean };

    versionInfo.next({ current: '1.2.3', latest: 'v1.2.3' });
    expect(sidebar.updateAvailable()).toBe(false);

    versionInfo.next({ current: 'v1.10.0', latest: 'v1.2.0' });
    expect(sidebar.updateAvailable()).toBe(false);

    versionInfo.next({ current: '1.2.3', latest: 'v1.2.4' });
    expect(sidebar.updateAvailable()).toBe(true);
  });

  it('moves the notifications overlay when another origin is selected', () => {
    const firstOrigin = {} as CdkOverlayOrigin;
    const secondOrigin = {} as CdkOverlayOrigin;
    const sidebar = component as unknown as {
      notificationsOpen: () => boolean;
      notificationPopoverOrigin: () => CdkOverlayOrigin | null;
      toggleFooterNotificationsPopover(origin: CdkOverlayOrigin): void;
      toggleHeaderNotificationsPopover(origin: CdkOverlayOrigin): void;
    };

    sidebar.toggleFooterNotificationsPopover(firstOrigin);
    sidebar.toggleHeaderNotificationsPopover(secondOrigin);

    expect(sidebar.notificationsOpen()).toBe(true);
    expect(sidebar.notificationPopoverOrigin()).toBe(secondOrigin);
  });

  it('closes the notifications overlay when Escape is pressed inside the dialog', () => {
    const origin = {} as CdkOverlayOrigin;
    const event = new KeyboardEvent('keydown', { key: 'Escape' });
    const preventDefault = vi.spyOn(event, 'preventDefault');
    const sidebar = component as unknown as {
      notificationsOpen: () => boolean;
      notificationPopoverOrigin: () => CdkOverlayOrigin | null;
      toggleFooterNotificationsPopover(origin: CdkOverlayOrigin): void;
      onNotificationsPopoverKeydown(event: KeyboardEvent): void;
    };

    sidebar.toggleFooterNotificationsPopover(origin);
    sidebar.onNotificationsPopoverKeydown(event);

    expect(preventDefault).toHaveBeenCalled();
    expect(sidebar.notificationsOpen()).toBe(false);
    expect(sidebar.notificationPopoverOrigin()).toBeNull();
  });

  it('aggregates metadata tasks, library imports, and pending bookdrop files into the sidebar badge count', () => {
    const sidebar = component as unknown as {
      completedTaskCount: Signal<number>;
      shouldShowNotificationBadge: Signal<boolean>;
    };

    activeTasks$.next({
      taskA: {
        taskId: 'taskA',
        completed: 1,
        total: 2,
        message: 'Scanning',
        status: MetadataBatchStatus.COMPLETED,
        review: false,
      },
      taskB: {
        taskId: 'taskB',
        completed: 2,
        total: 2,
        message: 'Importing',
        status: MetadataBatchStatus.ERROR,
        review: true,
      },
    });
    hasPendingFiles.set(true);
    hasActiveImport.set(true);

    expect(sidebar.completedTaskCount()).toBe(4);
    expect(sidebar.shouldShowNotificationBadge()).toBe(true);
  });

  it('hides the badge while metadata progress is actively running', () => {
    const sidebar = component as unknown as {
      shouldShowNotificationBadge: Signal<boolean>;
    };

    activeTasks$.next({
      taskA: {
        taskId: 'taskA',
        completed: 1,
        total: 3,
        message: 'Updating metadata',
        status: MetadataBatchStatus.IN_PROGRESS,
        review: false,
      },
    });
    progressUpdates$.next({
      taskId: 'taskA',
      completed: 1,
      total: 3,
      message: 'Updating metadata',
      status: MetadataBatchStatus.IN_PROGRESS,
      review: false,
    });

    expect(sidebar.shouldShowNotificationBadge()).toBe(false);
  });
});
