import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AppMenuitemComponent } from './app.menuitem.component';
import { LibraryService } from '../../../features/book/service/library.service';
import { LibraryHealthService } from '../../../features/book/service/library-health.service';
import { ShelfService } from '../../../features/book/service/shelf.service';
import { BookService } from '../../../features/book/service/book.service';
import { LibraryShelfMenuService } from '../../../features/book/service/library-shelf-menu.service';
import { VersionService } from '../../service/version.service';
import { UserService } from '../../../features/settings/user-management/user.service';
import { MagicShelfService } from '../../../features/magic-shelf/service/magic-shelf.service';
import { SeriesDataService } from '../../../features/series-browser/service/series-data.service';
import { AuthorService } from '../../../features/author-browser/service/author.service';
import { DialogLauncherService } from '../../services/dialog-launcher.service';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { Slider } from 'primeng/slider';
import { FormsModule } from '@angular/forms';
import { Popover } from 'primeng/popover';
import { MessageService } from 'primeng/api';
import { LocalStorageService } from '../../service/local-storage.service';
import type { MenuItem } from 'primeng/api';

export type NavIconType = 'PRIME_NG' | 'CUSTOM_SVG';

export type NavItemType =
  | 'library' | 'shelf' | 'magicShelf'
  | 'allBooks' | 'series' | 'authors';

export interface NavItem {
  label: string;
  icon?: string;
  iconType?: NavIconType;
  routerLink?: string[];
  type?: NavItemType;
  group?: boolean;
  bookCount?: number;
  unhealthy?: boolean;
  items?: NavItem[];
  hasDropDown?: boolean;
  hasCreate?: boolean;
  contextMenuItems?: MenuItem[];
}

@Component({
  selector: 'app-menu',
  imports: [AppMenuitemComponent, TranslocoDirective, Slider, FormsModule, Popover],
  templateUrl: './app.menu.component.html',
  styleUrl: './app.menu.component.scss',
})
export class AppMenuComponent {
  private readonly libraryService = inject(LibraryService);
  private readonly libraryHealthService = inject(LibraryHealthService);
  private readonly shelfService = inject(ShelfService);
  private readonly bookService = inject(BookService);
  private readonly versionService = inject(VersionService);
  private readonly libraryShelfMenuService = inject(LibraryShelfMenuService);
  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly messageService = inject(MessageService);
  private readonly userService = inject(UserService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly seriesDataService = inject(SeriesDataService);
  private readonly authorService = inject(AuthorService);
  private readonly t = inject(TranslocoService);
  private readonly localStorageService = inject(LocalStorageService);

  private readonly activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  private readonly currentUser = this.userService.currentUser;
  private readonly allAuthors = this.authorService.allAuthors;

  readonly versionInfo = toSignal(this.versionService.getVersion(), { initialValue: null });

  librarySortField = signal<'name' | 'id'>('name');
  librarySortOrder = signal<'asc' | 'desc'>('desc');
  shelfSortField = signal<'name' | 'id'>('name');
  shelfSortOrder = signal<'asc' | 'desc'>('asc');
  magicShelfSortField = signal<'name' | 'id'>('name');
  magicShelfSortOrder = signal<'asc' | 'desc'>('asc');
  sidebarWidth = this.localStorageService.get<number>('sidebarWidth') ?? 225;

  private readonly libraryBookCounts = computed(() => {
    const counts = new Map<number, number>();
    for (const book of this.bookService.books()) {
      if (book.libraryId != null) {
        counts.set(book.libraryId, (counts.get(book.libraryId) ?? 0) + 1);
      }
    }
    return counts;
  });

  private readonly shelfBookCounts = computed(() => {
    const currentUserId = this.currentUser()?.id;
    const counts = new Map<number, number>();
    let unshelvedCount = 0;

    for (const book of this.bookService.books()) {
      if (!book.shelves || book.shelves.length === 0) {
        unshelvedCount++;
      } else {
        for (const shelf of book.shelves) {
          if (shelf.id != null) {
            counts.set(shelf.id, (counts.get(shelf.id) ?? 0) + 1);
          }
        }
      }
    }

    // For shelves not owned by the current user, fall back to the shelf's bookCount field
    for (const shelf of this.shelfService.shelves()) {
      if (shelf.userId !== currentUserId && shelf.id != null) {
        counts.set(shelf.id, shelf.bookCount || 0);
      }
    }

    counts.set(-1, unshelvedCount); // sentinel key for unshelved count
    return counts;
  });

  private readonly magicShelfBookCounts = computed(() => {
    const counts = new Map<number, number>();
    const shelves = this.magicShelfService.shelves();
    if (shelves.length === 0) return counts;

    for (const shelf of shelves) {
      if (shelf.id != null) {
        counts.set(shelf.id, this.magicShelfService.getBookCountValue(shelf.id));
      }
    }
    return counts;
  });

  readonly homeMenu = computed<NavItem[]>(() => {
    this.activeLang();

    return [
      {
        label: this.t.translate('layout.menu.home'),
        items: [
          {
            label: this.t.translate('layout.menu.dashboard'),
            icon: 'pi pi-fw pi-home',
            routerLink: ['/dashboard'],
          },
          {
            label: this.t.translate('layout.menu.allBooks'),
            type: 'allBooks',
            icon: 'pi pi-fw pi-book',
            routerLink: ['/all-books'],
            bookCount: this.bookService.books().length,
          },
          {
            label: this.t.translate('layout.menu.series'),
            type: 'series',
            icon: 'pi pi-fw pi-objects-column',
            routerLink: ['/series'],
            bookCount: this.seriesDataService.allSeries().length,
          },
          {
            label: this.t.translate('layout.menu.authors'),
            type: 'authors',
            icon: 'pi pi-fw pi-users',
            routerLink: ['/authors'],
            bookCount: this.allAuthors()?.length ?? 0,
          },
          {
            label: this.t.translate('layout.menu.notebook'),
            icon: 'pi pi-fw pi-pencil',
            routerLink: ['/notebook'],
          }
        ],
      },
    ];
  });

  readonly libraryMenu = computed<NavItem[]>(() => {
    this.activeLang();
    const libCounts = this.libraryBookCounts();

    const sortedLibraries = this.sortArray(
      this.libraryService.libraries(),
      this.librarySortField(),
      this.librarySortOrder()
    );

    return [
      {
        label: this.t.translate('layout.menu.libraries'),
        type: 'library',
        group: true,
        hasDropDown: true,
        hasCreate: true,
        items: sortedLibraries.map((library) => ({
          contextMenuItems: this.libraryShelfMenuService.initializeLibraryMenuItems(library),
          label: library.name,
          type: 'library',
          icon: library.icon || undefined,
          iconType: this.toNavIconType(library.iconType),
          routerLink: [`/library/${library.id}/books`],
          bookCount: libCounts.get(library.id ?? 0) ?? 0,
          unhealthy: this.libraryHealthService.isUnhealthy(library.id ?? 0),
        })),
      },
    ];
  });

  readonly magicShelfMenu = computed<NavItem[]>(() => {
    this.activeLang();

    const sortedShelves = this.sortArray(
      this.magicShelfService.shelves(),
      this.magicShelfSortField(),
      this.magicShelfSortOrder()
    );

    return [
      {
        label: this.t.translate('layout.menu.magicShelves'),
        type: 'magicShelf',
        group: true,
        hasDropDown: true,
        hasCreate: true,
        items: sortedShelves.map((shelf) => ({
          label: shelf.name,
          type: 'magicShelf',
          icon: shelf.icon || undefined,
          iconType: this.toNavIconType(shelf.iconType),
          contextMenuItems: this.libraryShelfMenuService.initializeMagicShelfMenuItems(shelf),
          routerLink: [`/magic-shelf/${shelf.id}/books`],
          bookCount: this.magicShelfBookCounts().get(shelf.id ?? 0) ?? 0,
        })),
      },
    ];
  });

  readonly shelfMenu = computed<NavItem[]>(() => {
    this.activeLang();
    const shelfCounts = this.shelfBookCounts();

    const sortedShelves = this.sortArray(
      this.shelfService.shelves(),
      this.shelfSortField(),
      this.shelfSortOrder()
    );

    const shelves = [...sortedShelves];
    const koboShelfIndex = shelves.findIndex(shelf => shelf.name === 'Kobo');
    let koboShelf = null;
    if (koboShelfIndex !== -1) {
      koboShelf = shelves.splice(koboShelfIndex, 1)[0];
    }

    const shelfItems = shelves.map((shelf) => ({
      contextMenuItems: this.libraryShelfMenuService.initializeShelfMenuItems(shelf),
      label: shelf.name,
      type: 'shelf' as const,
      icon: shelf.icon || undefined,
      iconType: this.toNavIconType(shelf.iconType),
      routerLink: [`/shelf/${shelf.id}/books`],
      bookCount: shelfCounts.get(shelf.id ?? 0) ?? 0,
    }));

    const items: NavItem[] = [{
      label: this.t.translate('layout.menu.unshelved'),
      type: 'shelf',
      icon: 'pi pi-inbox',
      iconType: 'PRIME_NG',
      routerLink: ['/unshelved-books'],
      bookCount: shelfCounts.get(-1) ?? 0,
    }];

    if (koboShelf) {
      items.push({
        label: koboShelf.name,
        type: 'shelf',
        icon: koboShelf.icon || undefined,
        iconType: this.toNavIconType(koboShelf.iconType),
        routerLink: [`/shelf/${koboShelf.id}/books`],
        bookCount: shelfCounts.get(koboShelf.id ?? 0) ?? 0,
      });
    }

    items.push(...shelfItems);

    return [
      {
        type: 'shelf',
        group: true,
        label: this.t.translate('layout.menu.shelves'),
        hasDropDown: true,
        hasCreate: true,
        items,
      },
    ];
  });

  private readonly syncSortPreferencesEffect = effect(() => {
    const user = this.currentUser();
    if (!user) {
      return;
    }

    if (user.userSettings.sidebarLibrarySorting) {
      this.librarySortField.set(this.validateSortField(user.userSettings.sidebarLibrarySorting.field));
      this.librarySortOrder.set(this.validateSortOrder(user.userSettings.sidebarLibrarySorting.order));
    }
    if (user.userSettings.sidebarShelfSorting) {
      this.shelfSortField.set(this.validateSortField(user.userSettings.sidebarShelfSorting.field));
      this.shelfSortOrder.set(this.validateSortOrder(user.userSettings.sidebarShelfSorting.order));
    }
    if (user.userSettings.sidebarMagicShelfSorting) {
      this.magicShelfSortField.set(this.validateSortField(user.userSettings.sidebarMagicShelfSorting.field));
      this.magicShelfSortOrder.set(this.validateSortOrder(user.userSettings.sidebarMagicShelfSorting.order));
    }
  });

  onSidebarWidthChange(): void {
    document.documentElement.style.setProperty('--sidebar-width', this.sidebarWidth + 'px');
  }

  saveSidebarWidth(): void {
    this.localStorageService.set('sidebarWidth', this.sidebarWidth);
  }

  openChangelogDialog() {
    this.dialogLauncherService.openVersionChangelogDialog();
  }

  async copyVersion(version: string | undefined): Promise<void> {
    if (!version) {
      return;
    }

    const detailKey = 'layout.menu.versionCopyFailed';
    const clipboard = globalThis.navigator?.clipboard;

    if (!clipboard?.writeText) {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate(detailKey, { version }),
      });
      return;
    }

    try {
      await clipboard.writeText(version);
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('common.success'),
        detail: this.t.translate('layout.menu.versionCopied', { version }),
      });
    } catch {
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate(detailKey, { version }),
      });
    }
  }

  getVersionUrl(version: string | undefined): string {
    if (!version) return '#';
    return version.startsWith('v')
      ? `https://github.com/grimmory-tools/grimmory/releases/tag/${version}`
      : `https://github.com/grimmory-tools/grimmory/commit/${version}`;
  }

  isSemanticVersion(version: string | undefined): boolean {
    if (!version) return false;
    const semanticVersionPattern = /^v\d+\.\d+\.\d+$/;
    return semanticVersionPattern.test(version);
  }

  private sortArray<T extends { name?: string | null; id?: number | null }>(
    items: T[],
    field: 'name' | 'id',
    order: 'asc' | 'desc'
  ): T[] {
    const sorted = [...items].sort((a, b) => {
      if (field === 'id') {
        return (a.id ?? 0) - (b.id ?? 0);
      }
      return (a.name ?? '').localeCompare(b.name ?? '');
    });

    return order === 'desc' ? sorted.reverse() : sorted;
  }

  private validateSortField(field: string): 'name' | 'id' {
    return field === 'id' ? 'id' : 'name';
  }

  private validateSortOrder(order: string): 'asc' | 'desc' {
    return order === 'desc' ? 'desc' : 'asc';
  }

  private toNavIconType(iconType: string | null | undefined): NavIconType | undefined {
    return iconType === 'PRIME_NG' || iconType === 'CUSTOM_SVG' ? iconType : undefined;
  }
}
