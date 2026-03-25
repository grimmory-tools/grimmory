import {Component, computed, effect, inject, OnInit, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {AppMenuitemComponent} from './app.menuitem.component';
import {MenuModule} from 'primeng/menu';
import {LibraryService} from '../../../../features/book/service/library.service';
import {LibraryHealthService} from '../../../../features/book/service/library-health.service';
import {ShelfService} from '../../../../features/book/service/shelf.service';
import {BookService} from '../../../../features/book/service/book.service';
import {LibraryShelfMenuService} from '../../../../features/book/service/library-shelf-menu.service';
import {AppVersion, VersionService} from '../../../service/version.service';
import {DynamicDialogRef} from 'primeng/dynamicdialog';
import {UserService} from '../../../../features/settings/user-management/user.service';
import {MagicShelfService} from '../../../../features/magic-shelf/service/magic-shelf.service';
import {SeriesDataService} from '../../../../features/series-browser/service/series-data.service';
import {AuthorService} from '../../../../features/author-browser/service/author.service';
import {MenuItem} from 'primeng/api';
import {DialogLauncherService} from '../../../services/dialog-launcher.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Slider} from 'primeng/slider';
import {FormsModule} from '@angular/forms';
import {Popover} from 'primeng/popover';
import {LocalStorageService} from '../../../service/local-storage.service';

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [AppMenuitemComponent, MenuModule, TranslocoDirective, Slider, FormsModule, Popover],
  templateUrl: './app.menu.component.html',
  styleUrl: './app.menu.component.scss',
})
export class AppMenuComponent implements OnInit {
  versionInfo: AppVersion | null = null;
  dynamicDialogRef: DynamicDialogRef | undefined | null;

  private libraryService = inject(LibraryService);
  private libraryHealthService = inject(LibraryHealthService);
  private shelfService = inject(ShelfService);
  private bookService = inject(BookService);
  private versionService = inject(VersionService);
  private libraryShelfMenuService = inject(LibraryShelfMenuService);
  private dialogLauncherService = inject(DialogLauncherService);
  private userService = inject(UserService);
  private magicShelfService = inject(MagicShelfService);
  private seriesDataService = inject(SeriesDataService);
  private authorService = inject(AuthorService);
  private t = inject(TranslocoService);
  private localStorageService = inject(LocalStorageService);

  private activeLang = toSignal(this.t.langChanges$, {initialValue: this.t.getActiveLang()});
  private readonly currentUser = this.userService.currentUser;
  private allAuthors = this.authorService.allAuthors;

  librarySortField = signal<'name' | 'id'>('name');
  librarySortOrder = signal<'asc' | 'desc'>('desc');
  shelfSortField = signal<'name' | 'id'>('name');
  shelfSortOrder = signal<'asc' | 'desc'>('asc');
  magicShelfSortField = signal<'name' | 'id'>('name');
  magicShelfSortOrder = signal<'asc' | 'desc'>('asc');
  sidebarWidth = 225;

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

  readonly homeMenu = computed<MenuItem[]>(() => {
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
            type: 'All Books',
            icon: 'pi pi-fw pi-book',
            routerLink: ['/all-books'],
            bookCount: this.bookService.books().length,
          },
          {
            label: this.t.translate('layout.menu.series'),
            type: 'Series',
            icon: 'pi pi-fw pi-objects-column',
            routerLink: ['/series'],
            bookCount: this.seriesDataService.allSeries().length,
          },
          {
            label: this.t.translate('layout.menu.authors'),
            type: 'Authors',
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

  readonly libraryMenu = computed<MenuItem[]>(() => {
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
        hasDropDown: true,
        hasCreate: true,
        items: sortedLibraries.map((library) => ({
          menu: this.libraryShelfMenuService.initializeLibraryMenuItems(library),
          label: library.name,
          type: 'Library',
          icon: library.icon || undefined,
          iconType: (library.iconType || undefined) as 'PRIME_NG' | 'CUSTOM_SVG' | undefined,
          routerLink: [`/library/${library.id}/books`],
          bookCount: libCounts.get(library.id ?? 0) ?? 0,
          unhealthy: this.libraryHealthService.isUnhealthy(library.id ?? 0),
        })),
      },
    ];
  });

  readonly magicShelfMenu = computed<MenuItem[]>(() => {
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
        hasDropDown: true,
        hasCreate: true,
        items: sortedShelves.map((shelf) => ({
          label: shelf.name,
          type: 'magicShelfItem',
          icon: shelf.icon || undefined,
          iconType: (shelf.iconType || undefined) as 'PRIME_NG' | 'CUSTOM_SVG' | undefined,
          menu: this.libraryShelfMenuService.initializeMagicShelfMenuItems(shelf),
          routerLink: [`/magic-shelf/${shelf.id}/books`],
          bookCount: this.magicShelfBookCounts().get(shelf.id ?? 0) ?? 0,
        })),
      },
    ];
  });

  readonly shelfMenu = computed<MenuItem[]>(() => {
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
      menu: this.libraryShelfMenuService.initializeShelfMenuItems(shelf),
      label: shelf.name,
      type: 'Shelf',
      icon: shelf.icon || undefined,
      iconType: (shelf.iconType || undefined) as 'PRIME_NG' | 'CUSTOM_SVG' | undefined,
      routerLink: [`/shelf/${shelf.id}/books`],
      bookCount: shelfCounts.get(shelf.id ?? 0) ?? 0,
    }));

    const items: MenuItem[] = [{
      label: this.t.translate('layout.menu.unshelved'),
      type: 'Shelf',
      icon: 'pi pi-inbox',
      iconType: 'PRIME_NG' as 'PRIME_NG' | 'CUSTOM_SVG',
      routerLink: ['/unshelved-books'],
      bookCount: shelfCounts.get(-1) ?? 0,
    }];

    if (koboShelf) {
      items.push({
        label: koboShelf.name,
        type: 'Shelf',
        icon: koboShelf.icon || undefined,
        iconType: (koboShelf.iconType || undefined) as 'PRIME_NG' | 'CUSTOM_SVG' | undefined,
        routerLink: [`/shelf/${koboShelf.id}/books`],
        bookCount: shelfCounts.get(koboShelf.id ?? 0) ?? 0,
      });
    }

    items.push(...shelfItems);

    return [
      {
        type: 'shelf',
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

  ngOnInit(): void {
    this.sidebarWidth = this.localStorageService.get<number>('sidebarWidth') ?? 225;

    this.versionService.getVersion().subscribe((data) => {
      this.versionInfo = data;
    });

  }

  onSidebarWidthChange(): void {
    document.documentElement.style.setProperty('--sidebar-width', this.sidebarWidth + 'px');
  }

  saveSidebarWidth(): void {
    this.localStorageService.set('sidebarWidth', this.sidebarWidth);
  }

  openChangelogDialog() {
    this.dialogLauncherService.openVersionChangelogDialog();
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
}
