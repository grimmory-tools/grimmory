import {Injectable, inject} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {Observable, of, combineLatest} from 'rxjs';
import {map, switchMap, take, catchError} from 'rxjs/operators';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {Book} from '../../model/book.model';
import {BookState} from '../../model/state/book-state.model';
import {SortOption} from '../../model/sort.model';
import {LibraryService} from '../../service/library.service';
import {BookService} from '../../service/book.service';
import {ShelfService} from '../../service/shelf.service';
import {SortService} from '../../service/sort.service';
import {MagicShelf, MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {BookRuleEvaluatorService} from '../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../magic-shelf/component/magic-shelf-component';
import {EntityType} from './book-browser.component';

export interface EntityInfo {
  entityId: number;
  entityType: EntityType;
}

@Injectable({providedIn: 'root'})
export class BookBrowserEntityService {
  private libraryService = inject(LibraryService);
  private bookService = inject(BookService);
  private shelfService = inject(ShelfService);
  private sortService = inject(SortService);
  private magicShelfService = inject(MagicShelfService);
  private bookRuleEvaluatorService = inject(BookRuleEvaluatorService);

  getEntityInfoFromRoute(activatedRoute: ActivatedRoute): Observable<EntityInfo> {
    return activatedRoute.paramMap.pipe(
      map(params => {
        const libraryId = Number(params.get('libraryId') || NaN);
        const shelfId = Number(params.get('shelfId') || NaN);
        const magicShelfId = Number(params.get('magicShelfId') || NaN);

        if (!isNaN(libraryId)) {
          return {entityId: libraryId, entityType: EntityType.LIBRARY};
        } else if (!isNaN(shelfId)) {
          return {entityId: shelfId, entityType: EntityType.SHELF};
        } else if (!isNaN(magicShelfId)) {
          return {entityId: magicShelfId, entityType: EntityType.MAGIC_SHELF};
        } else {
          return {entityId: NaN, entityType: EntityType.ALL_BOOKS};
        }
      })
    );
  }

  fetchEntity(entityId: number, entityType: EntityType): Observable<Library | Shelf | MagicShelf | null> {
    switch (entityType) {
      case EntityType.LIBRARY:
        return this.fetchLibrary(entityId);
      case EntityType.SHELF:
        return this.fetchShelf(entityId);
      case EntityType.MAGIC_SHELF:
        return this.fetchMagicShelf(entityId);
      default:
        return of(null);
    }
  }

  fetchBooksByEntity(
    entityId: number,
    entityType: EntityType,
    sortOption: SortOption
  ): Observable<BookState> {
    switch (entityType) {
      case EntityType.LIBRARY:
        return this.fetchBooks(book => book.libraryId === entityId, sortOption);
      case EntityType.SHELF:
        return this.fetchShelfBooks(entityId, sortOption);
      case EntityType.MAGIC_SHELF:
        return this.fetchMagicShelfBooks(entityId, sortOption);
      case EntityType.ALL_BOOKS:
      default:
        return this.fetchAllBooks(sortOption);
    }
  }

  fetchAllBooks(sortOption: SortOption): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => this.processBookState(bookState, sortOption))
    );
  }

  fetchUnshelvedBooks(sortOption: SortOption): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => ({
        ...bookState,
        books: (bookState.books || []).filter(book => !book.shelves || book.shelves.length === 0)
      })),
      map(bookState => this.processBookState(bookState, sortOption))
    );
  }

  isLibrary(entity: Library | Shelf | MagicShelf): entity is Library {
    return (entity as Library).paths !== undefined;
  }

  isMagicShelf(entity: Library | Shelf | MagicShelf | null): entity is MagicShelf {
    return !!entity && 'filterJson' in entity;
  }

  private fetchLibrary(libraryId: number): Observable<Library | null> {
    return this.libraryService.libraryState$.pipe(
      map(libraryState => {
        if (libraryState.libraries) {
          return libraryState.libraries.find(lib => lib.id === libraryId) || null;
        }
        return null;
      })
    );
  }

  private fetchShelf(shelfId: number): Observable<Shelf | null> {
    return this.shelfService.shelfState$.pipe(
      map(shelfState => {
        if (shelfState.shelves) {
          return shelfState.shelves.find(shelf => shelf.id === shelfId) || null;
        }
        return null;
      })
    );
  }

  private fetchMagicShelf(magicShelfId: number): Observable<MagicShelf | null> {
    return this.magicShelfService.shelvesState$.pipe(
      take(1),
      switchMap((state) => {
        const cached = state.shelves?.find(s => s.id === magicShelfId) ?? null;
        if (cached) {
          return of(cached);
        }
        return this.magicShelfService.getShelf(magicShelfId).pipe(
          map(shelf => shelf ?? null),
          catchError(() => of(null))
        );
      })
    );
  }

  private fetchShelfBooks(shelfId: number, sortOption: SortOption): Observable<BookState> {
    return this.shelfService.getBooksOnShelf(shelfId).pipe(
      map(books => {
        const sortedBooks = this.sortService.applySort(books, sortOption);
        return {
          books: sortedBooks,
          loaded: true,
          error: null
        };
      })
    );
  }

  private fetchMagicShelfBooks(magicShelfId: number, sortOption: SortOption): Observable<BookState> {
    return combineLatest([
      this.bookService.bookState$,
      this.magicShelfService.getShelf(magicShelfId)
    ]).pipe(
      map(([bookState, magicShelf]) => {
        if (!bookState.loaded || bookState.error || !magicShelf?.filterJson) {
          return bookState;
        }
        const allBooks = bookState.books ?? [];
        const filteredBooks = allBooks.filter(book =>
          this.bookRuleEvaluatorService.evaluateGroup(book, JSON.parse(magicShelf.filterJson!) as GroupRule, allBooks)
        );
        const sortedBooks = this.sortService.applySort(filteredBooks ?? [], sortOption);
        return {...bookState, books: sortedBooks};
      })
    );
  }

  private fetchBooks(bookFilter: (book: Book) => boolean, sortOption: SortOption): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => {
        if (bookState.loaded && !bookState.error) {
          const filteredBooks = bookState.books?.filter(bookFilter) || [];
          const sortedBooks = this.sortService.applySort(filteredBooks, sortOption);
          return {...bookState, books: sortedBooks};
        }
        return bookState;
      })
    );
  }

  private processBookState(bookState: BookState, sortOption: SortOption): BookState {
    if (bookState.loaded && !bookState.error) {
      const sortedBooks = this.sortService.applySort(bookState.books || [], sortOption);
      return {...bookState, books: sortedBooks};
    }
    return bookState;
  }
}
