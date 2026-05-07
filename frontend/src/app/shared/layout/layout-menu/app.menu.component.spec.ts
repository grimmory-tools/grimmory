import { computed, signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

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
import { AuthService } from '../../service/auth.service';
import { VersionService } from '../../service/version.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { LayoutService } from '../layout.service';

import { AppMenuComponent } from './app.menu.component';

interface MenuOverlay {
  container: HTMLElement;
  toggle: (event: MouseEvent) => void;
}

function createRect(top: number, bottom: number, left: number): DOMRect {
  return {
    x: left,
    y: top,
    top,
    bottom,
    left,
    right: left + 40,
    width: 40,
    height: bottom - top,
    toJSON: () => ({}),
  } as DOMRect;
}

interface TestUser {
  name: string;
  username: string;
  permissions: Record<string, boolean>;
}

describe('AppMenuComponent', () => {
  let fixture: ComponentFixture<AppMenuComponent>;
  let component: AppMenuComponent;
  let commandPaletteService: { open: ReturnType<typeof vi.fn> };
  let currentUser: WritableSignal<TestUser | null>;
  const sidebarCollapsed = signal(false);
  const isDesktop = signal(true);
  const layoutService = {
    sidebarCollapsed,
    isDesktop,
    desktopSidebarCollapsed: computed(() => isDesktop() && sidebarCollapsed()),
    librarySort: signal({ field: 'name', order: 'desc' }),
    shelfSort: signal({ field: 'name', order: 'asc' }),
    magicShelfSort: signal({ field: 'name', order: 'asc' }),
    closeMobileSidebar: vi.fn(),
  };

  beforeEach(() => {
    commandPaletteService = { open: vi.fn() };
    currentUser = signal<TestUser | null>(null);

    TestBed.configureTestingModule({
      imports: [AppMenuComponent, getTranslocoModule()],
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
            openLibraryCreateDialog: vi.fn(),
            openMagicShelfCreateDialog: vi.fn(),
            openFileUploadDialog: vi.fn(),
          },
        },
        { provide: CommandPaletteService, useValue: commandPaletteService },
        { provide: BookDialogHelperService, useValue: { openShelfCreatorDialog: vi.fn() } },
        { provide: AuthService, useValue: { logout: vi.fn() } },
        { provide: LayoutService, useValue: layoutService },
        { provide: UserService, useValue: { currentUser } },
        { provide: MagicShelfService, useValue: { shelves: signal([]), bookCountByMagicShelfId: signal(new Map()) } },
        { provide: SeriesDataService, useValue: { allSeries: signal([]) } },
        { provide: AuthorService, useValue: { allAuthors: signal([]) } },
        { provide: VersionService, useValue: { getVersion: vi.fn(() => of(null)) } },
      ],
    });

    TestBed.overrideComponent(AppMenuComponent, { set: { template: '' } });

    fixture = TestBed.createComponent(AppMenuComponent);
    component = fixture.componentInstance;
    layoutService.isDesktop.set(true);
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

  it('anchors above-placement overlays from the trigger top edge when the sidebar is expanded', () => {
    const trigger = document.createElement('button');
    vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue(createRect(100, 148, 40));
    const container = document.createElement('div');
    const overlay = { container, toggle: vi.fn() };
    const menuHarness = component as unknown as { applySidebarOverlayPosition(overlay: MenuOverlay): void };

    component.openSidebarOverlay({ currentTarget: trigger } as unknown as MouseEvent, overlay as never, 'above');
    menuHarness.applySidebarOverlayPosition(overlay);

    expect(overlay.toggle).toHaveBeenCalled();
    expect(container.style.getPropertyValue('--sidebar-popover-top')).toBe('92px');
    expect(container.style.getPropertyValue('--sidebar-popover-left')).toBe('40px');
  });

  it('anchors below-placement overlays from the trigger bottom edge', () => {
    const trigger = document.createElement('button');
    vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue(createRect(220, 260, 24));
    const container = document.createElement('div');
    const overlay = { container, toggle: vi.fn() };
    const menuHarness = component as unknown as { applySidebarOverlayPosition(overlay: MenuOverlay): void };

    component.openSidebarOverlay({ currentTarget: trigger } as unknown as MouseEvent, overlay as never, 'below');
    menuHarness.applySidebarOverlayPosition(overlay);

    expect(container.style.getPropertyValue('--sidebar-popover-top')).toBe('268px');
    expect(container.style.getPropertyValue('--sidebar-popover-left')).toBe('');
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

  it('returns an empty string when no user is signed in', () => {
    currentUser.set(null);

    expect(component.userInitials()).toBe('');
  });

  it('anchors notifications above the trigger and keeps them inside the viewport on mobile', () => {
    layoutService.isDesktop.set(false);

    const trigger = document.createElement('button');
    vi.spyOn(trigger, 'getBoundingClientRect').mockReturnValue(createRect(220, 260, 980));
    const container = document.createElement('div');
    Object.defineProperty(container, 'offsetWidth', { value: 120, configurable: true });
    const overlay = { container, toggle: vi.fn() };
    const menuHarness = component as unknown as { applySidebarOverlayPosition(overlay: MenuOverlay): void };

    component.openSidebarOverlay({ currentTarget: trigger } as unknown as MouseEvent, overlay as never, 'above');
    menuHarness.applySidebarOverlayPosition(overlay);

    expect(container.style.getPropertyValue('--sidebar-popover-top')).toBe('212px');
    expect(container.style.getPropertyValue('--sidebar-popover-left')).toBe(`${window.innerWidth - 128}px`);
  });
});
