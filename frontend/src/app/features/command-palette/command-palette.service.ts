import { computed, inject, Injectable, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { BookDialogHelperService } from '../book/components/book-browser/book-dialog-helper.service';
import { filterBooksBySearchTerm, normalizeSearchTerm } from '../book/components/book-browser/filters/HeaderFilter';
import { Book } from '../book/model/book.model';
import { BookService } from '../book/service/book.service';
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

const BOOK_RESULT_LIMIT = 50;
const MIN_BOOK_SEARCH_LENGTH = 2;
const BOOK_SEARCH_DEBOUNCE_MS = 200;

@Injectable({ providedIn: 'root' })
export class CommandPaletteService {
  private readonly router = inject(Router);
  private readonly t = inject(TranslocoService);
  private readonly userService = inject(UserService);
  private readonly bookService = inject(BookService);
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

  private readonly activeLang = toSignal(this.t.langChanges$, { initialValue: this.t.getActiveLang() });
  private readonly translate = (key: string): string => this.t.translate(key);
  private readonly trimmedQuery = computed(() => this.query().trim());
  private readonly debouncedBookQuery = toSignal(
    toObservable(this.trimmedQuery).pipe(
      debounceTime(BOOK_SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
    ),
    { initialValue: this.trimmedQuery() },
  );
  private readonly localBookItems = computed<PaletteItem[]>(() => {
    const query = this.debouncedBookQuery();
    if (query.length < MIN_BOOK_SEARCH_LENGTH) {
      return [];
    }

    return filterBooksBySearchTerm(this.bookService.books(), query)
      .slice(0, BOOK_RESULT_LIMIT)
      .map((book) => this.toPaletteBookItem(book));
  });

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
    if (raw.length < MIN_BOOK_SEARCH_LENGTH) {
      return false;
    }

    return raw !== this.debouncedBookQuery();
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
    this.trimmedQuery().length >= MIN_BOOK_SEARCH_LENGTH ? this.localBookItems() : []
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

  private toPaletteBookItem(book: Book): PaletteItem {
    const metadata = book.metadata;
    const title = metadata?.title ?? book.primaryFile?.fileName ?? book.fileName ?? '';
    const authors = metadata?.authors ?? [];
    const publishedDate = metadata?.publishedDate ?? '';
    const year = publishedDate && /^\d{4}/.test(publishedDate) ? publishedDate.slice(0, 4) : null;
    const haystack = [title, metadata?.seriesName ?? '', ...authors].filter(Boolean).join(' ');

    return {
      id: `book:${book.id}`,
      kind: 'book',
      title,
      icon: { type: 'PRIME_NG', value: 'pi-book' },
      searchText: normalizeSearchTerm(haystack),
      route: ['/book', book.id],
      queryParams: { tab: 'view' },
      bookMeta: {
        thumbnailUrl: this.urlHelper.getThumbnailUrl(book.id, metadata?.coverUpdatedOn),
        authors,
        seriesName: metadata?.seriesName ?? null,
        seriesNumber: metadata?.seriesNumber ?? null,
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

}
