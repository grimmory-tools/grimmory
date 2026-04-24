import { computed, inject, Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, debounceTime, distinctUntilChanged, map, Observable, of, startWith, switchMap } from 'rxjs';

import { AppBookSummary } from '../book/model/app-book.model';
import { BookDialogHelperService } from '../book/components/book-browser/book-dialog-helper.service';
import { normalizeSearchTerm } from '../book/components/book-browser/filters/HeaderFilter';
import { AppBooksApiService } from '../book/service/app-books-api.service';
import { LibraryService } from '../book/service/library.service';
import { ShelfService } from '../book/service/shelf.service';
import { MagicShelfService } from '../magic-shelf/service/magic-shelf.service';
import { UserService } from '../settings/user-management/user.service';
import { NavItem } from '../../shared/layout/navigation/nav-item.model';
import { buildAllNavPages, buildQuickActionNavItems } from '../../shared/layout/navigation/nav-catalog';
import { IconSelection } from '../../shared/service/icon-picker.service';
import { UrlHelperService } from '../../shared/service/url-helper.service';
import { DialogLauncherService } from '../../shared/services/dialog-launcher.service';
import { IconService } from '../../shared/services/icon.service';

import { PaletteGroup, PaletteItem, PaletteItemKind } from './command-palette.model';

interface GroupDef {
  kind: PaletteItemKind;
  source: PaletteItem[];
  cap: number;
}

interface CommandPaletteOverlayController {
  open(): void;
  close(): void;
  focusInput(): void;
}

interface RemoteSearchState {
  query: string;
  items: PaletteItem[];
  isLoading: boolean;
}

const BOOK_RESULT_LIMIT = 25;
const MIN_REMOTE_SEARCH_LENGTH = 2;
const REMOTE_SEARCH_DEBOUNCE_MS = 200;

function emptyRemoteSearchState(query = ''): RemoteSearchState {
  return {
    query,
    items: [],
    isLoading: false,
  };
}

@Injectable({ providedIn: 'root' })
export class CommandPaletteService {
  private readonly router = inject(Router);
  private readonly t = inject(TranslocoService);
  private readonly userService = inject(UserService);
  private readonly appBooksApi = inject(AppBooksApiService);
  private readonly shelfService = inject(ShelfService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly libraryService = inject(LibraryService);
  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly bookDialogHelperService = inject(BookDialogHelperService);
  private readonly iconService = inject(IconService);
  private readonly urlHelper = inject(UrlHelperService);

  private readonly _isOpen = signal(false);
  readonly isOpen = this._isOpen.asReadonly();
  readonly query = signal('');
  private overlayController?: CommandPaletteOverlayController;

  private readonly activeLang = signal(this.t.getActiveLang());
  private readonly translate = (key: string): string => this.t.translate(key);
  private readonly trimmedQuery = computed(() => this.query().trim());
  private readonly debouncedRemoteQuery = toSignal(
    toObservable(this.trimmedQuery).pipe(
      debounceTime(REMOTE_SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
    ),
    { initialValue: this.trimmedQuery() },
  );
  private readonly bookSearchState = this.createRemoteSearchState((query) =>
    this.appBooksApi.searchBooks(query, BOOK_RESULT_LIMIT).pipe(
      map((response) => response.content.map((book) => this.toPaletteBookItem(book))),
    )
  );

  constructor() {
    this.t.langChanges$.subscribe((lang) => this.activeLang.set(lang));
  }

  registerOverlayController(controller: CommandPaletteOverlayController): () => void {
    this.overlayController = controller;

    return () => {
      if (this.overlayController === controller) {
        this.overlayController = undefined;
      }
    };
  }

  open(): void {
    if (!this._isOpen()) {
      this.overlayController?.open();
      this._isOpen.set(true);
    } else {
      this.overlayController?.focusInput();
    }

    this.prefetchCustomIcons();
  }

  private prefetchCustomIcons(): void {
    const seen = new Set<string>();
    const collect = (items: PaletteItem[]) => {
      for (const item of items) {
        if (item.icon?.type === 'CUSTOM_SVG' && !seen.has(item.icon.value)) {
          seen.add(item.icon.value);
          this.iconService.getSvgIconContent(item.icon.value).subscribe({ error: () => undefined });
        }
      }
    };
    collect(this.indexedLibraries());
    collect(this.indexedShelves());
    collect(this.indexedMagicShelves());
  }

  hide(): void {
    this.overlayController?.close();
    this._isOpen.set(false);
    this.query.set('');
  }

  toggle(): void {
    if (this._isOpen()) {
      this.hide();
    } else {
      this.open();
    }
  }

  select(item: PaletteItem): void {
    this.hide();
    queueMicrotask(() => {
      if (item.command) {
        item.command();
        return;
      }
      if (item.route) {
        void this.router.navigate(item.route, item.queryParams ? { queryParams: item.queryParams } : {});
      }
    });
  }

  readonly groups = computed<PaletteGroup[]>(() => {
    const raw = this.trimmedQuery();
    const normalized = normalizeSearchTerm(raw);
    const tokens = normalized ? normalized.split(/\s+/).filter(Boolean) : [];
    if (tokens.length === 0) {
      return [];
    }

    const groups: PaletteGroup[] = [];

    const bookItems = this.visibleBookItems();
    if (bookItems.length > 0) {
      groups.push({ kind: 'book', items: bookItems });
    }

    const defs: GroupDef[] = [
      { kind: 'shelf', source: this.indexedShelves(), cap: 10 },
      { kind: 'magicShelf', source: this.indexedMagicShelves(), cap: 10 },
      { kind: 'library', source: this.indexedLibraries(), cap: 10 },
      { kind: 'page', source: this.indexedPages(), cap: 10 },
      { kind: 'action', source: this.indexedActions(), cap: 10 },
    ];

    groups.push(
      ...defs
        .map((def) => ({
          kind: def.kind,
          items: this.filterItems(def.source, tokens, def.cap),
        }))
        .filter((group) => group.items.length > 0)
    );

    return groups;
  });

  readonly visibleItems = computed<PaletteItem[]>(() =>
    this.groups().flatMap((group) => group.items)
  );

  readonly isSearching = computed(() => {
    const raw = this.trimmedQuery();
    if (raw.length < MIN_REMOTE_SEARCH_LENGTH) {
      return false;
    }

    return this.isRemoteSearchPending(raw, this.bookSearchState());
  });

  private filterItems(source: PaletteItem[], tokens: string[], cap: number): PaletteItem[] {
    const matched: PaletteItem[] = [];
    for (const item of source) {
      if (matched.length >= cap) break;
      let hit = true;
      for (const tok of tokens) {
        if (!item.searchText.includes(tok)) {
          hit = false;
          break;
        }
      }
      if (hit) {
        matched.push(item);
      }
    }
    return matched;
  }

  private readonly indexedActions = computed<PaletteItem[]>(() => {
    this.activeLang();
    const user = this.userService.currentUser();
    if (!user) return [];
    return buildQuickActionNavItems(this.translate, user.permissions, {
      createLibrary: () => this.dialogLauncherService.openLibraryCreateDialog(),
      createShelf: () => this.bookDialogHelperService.openShelfCreatorDialog(),
      createMagicShelf: () => this.dialogLauncherService.openMagicShelfCreateDialog(),
      uploadBook: () => this.dialogLauncherService.openFileUploadDialog(),
    }).map((item) => this.toPaletteNavItem(item, 'action'));
  });

  private readonly indexedPages = computed<PaletteItem[]>(() => {
    this.activeLang();
    const user = this.userService.currentUser();
    if (!user) return [];
    return buildAllNavPages(this.translate, user.permissions)
      .map((item) => this.toPaletteNavItem(item, 'page'));
  });

  private readonly indexedShelves = computed<PaletteItem[]>(() =>
    this.shelfService.shelves()
      .filter((shelf) => shelf.id != null)
      .map((shelf) => ({
        id: `shelf:${shelf.id}`,
        kind: 'shelf' as const,
        title: shelf.name,
        icon: this.iconSelectionFor(shelf.icon, shelf.iconType, 'pi-bookmark'),
        searchText: normalizeSearchTerm(shelf.name),
        route: [`/shelf/${shelf.id}/books`],
      }))
  );

  private readonly indexedMagicShelves = computed<PaletteItem[]>(() =>
    this.magicShelfService.shelves()
      .filter((shelf) => shelf.id != null)
      .map((shelf) => ({
        id: `magic-shelf:${shelf.id}`,
        kind: 'magicShelf' as const,
        title: shelf.name,
        icon: this.iconSelectionFor(shelf.icon, shelf.iconType, 'pi-sparkles'),
        searchText: normalizeSearchTerm(shelf.name),
        route: [`/magic-shelf/${shelf.id}/books`],
      }))
  );

  private readonly indexedLibraries = computed<PaletteItem[]>(() =>
    this.libraryService.libraries()
      .filter((library) => library.id != null)
      .map((library) => ({
        id: `library:${library.id}`,
        kind: 'library' as const,
        title: library.name,
        icon: this.iconSelectionFor(library.icon, library.iconType, 'pi-folder'),
        searchText: normalizeSearchTerm(library.name),
        route: [`/library/${library.id}/books`],
      }))
  );

  private readonly visibleBookItems = computed<PaletteItem[]>(() =>
    this.visibleRemoteItems(this.bookSearchState())
  );

  private iconSelectionFor(
    value: string | null | undefined,
    iconType: string | null | undefined,
    fallback: string,
  ): IconSelection {
    if (!value) return { type: 'PRIME_NG', value: fallback };
    if (iconType === 'CUSTOM_SVG') return { type: 'CUSTOM_SVG', value };
    return { type: 'PRIME_NG', value };
  }

  private toPaletteBookItem(book: AppBookSummary): PaletteItem {
    const publishedDate = book.publishedDate ?? '';
    const year = publishedDate && /^\d{4}/.test(publishedDate) ? publishedDate.slice(0, 4) : null;
    const haystack = [book.title, book.seriesName ?? '', ...book.authors].filter(Boolean).join(' ');

    return {
      id: `book:${book.id}`,
      kind: 'book',
      title: book.title,
      icon: { type: 'PRIME_NG', value: 'pi-book' },
      searchText: normalizeSearchTerm(haystack),
      route: ['/book', book.id],
      queryParams: { tab: 'view' },
      bookMeta: {
        thumbnailUrl: this.urlHelper.getThumbnailUrl(book.id, book.coverUpdatedOn ?? undefined),
        authors: book.authors,
        seriesName: book.seriesName,
        seriesNumber: book.seriesNumber,
        year,
      },
    };
  }

  private toPaletteNavItem(item: NavItem, kind: Extract<PaletteItemKind, 'action' | 'page'>): PaletteItem {
    return {
      id: `${kind}:${item.id}`,
      kind,
      title: item.label,
      icon: item.icon ? { type: 'PRIME_NG', value: item.icon } : undefined,
      searchText: normalizeSearchTerm(item.label),
      route: item.routerLink,
      command: item.action,
    };
  }

  private createRemoteSearchState(searchFn: (query: string) => Observable<PaletteItem[]>) {
    /* Keep the last resolved items in closure so in-flight searches can show
       stale results instead of collapsing the palette to empty. */
    let lastItems: PaletteItem[] = [];
    return toSignal(
      toObservable(this.debouncedRemoteQuery).pipe(
        switchMap((query) => {
          if (query.length < MIN_REMOTE_SEARCH_LENGTH) {
            lastItems = [];
            return of<RemoteSearchState>({ query, items: [], isLoading: false });
          }

          const previousItems = lastItems;
          return searchFn(query).pipe(
            map((items) => {
              lastItems = items;
              return { query, items, isLoading: false } satisfies RemoteSearchState;
            }),
            startWith<RemoteSearchState>({
              query,
              items: previousItems,
              isLoading: true,
            }),
            catchError(() => {
              lastItems = [];
              return of<RemoteSearchState>({ query, items: [], isLoading: false });
            }),
          );
        }),
      ),
      { initialValue: emptyRemoteSearchState(this.trimmedQuery()) },
    );
  }

  private visibleRemoteItems(state: RemoteSearchState): PaletteItem[] {
    const raw = this.trimmedQuery();
    if (raw.length < MIN_REMOTE_SEARCH_LENGTH) {
      return [];
    }

    /* Keep previous items visible while a new search is in flight so the
       palette doesn't jump; isSearching() drives the dimmed state. */
    return state.items;
  }

  private isRemoteSearchPending(raw: string, state: RemoteSearchState): boolean {
    return raw !== state.query || state.isLoading;
  }
}
