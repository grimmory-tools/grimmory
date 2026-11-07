import {computed, inject, Service, signal} from '@angular/core';
import {type Params, Router} from '@angular/router';

import {UserService} from '../../settings/user-management/user.service';
import {type BookFileType} from '../data/book-response.models';
import {BookDialogHelperService} from './book-dialog-helper.service';

export interface ReadableBook {
  readonly id: number;
  readonly primaryFile?: {readonly bookType?: BookFileType};
}

export interface ReadOptions {
  readonly bookType?: BookFileType;
  readonly streaming?: boolean;
}

export interface BookNavigationState {
  bookIds: number[];
  currentIndex: number;
}

@Service()
export class BookNavigationService {
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly dialogHelper = inject(BookDialogHelperService);

  private readonly _navigationState = signal<BookNavigationState | null>(null);
  readonly navigationState = this._navigationState.asReadonly();

  private readonly _availableBookIds = signal<number[]>([]);
  readonly availableBookIds = this._availableBookIds.asReadonly();

  readonly canNavigatePrevious = computed(() => {
    const state = this.navigationState();
    return state !== null && state.currentIndex > 0;
  });

  readonly canNavigateNext = computed(() => {
    const state = this.navigationState();
    return state !== null && state.currentIndex < state.bookIds.length - 1;
  });

  readonly previousBookId = computed<number | null>(() => {
    const state = this.navigationState();
    return state && state.currentIndex > 0
      ? state.bookIds[state.currentIndex - 1]
      : null;
  });

  readonly nextBookId = computed<number | null>(() => {
    const state = this.navigationState();
    return state && state.currentIndex < state.bookIds.length - 1
      ? state.bookIds[state.currentIndex + 1]
      : null;
  });

  readonly currentPosition = computed<{ current: number; total: number } | null>(() => {
    const state = this.navigationState();
    return state
      ? {
          current: state.currentIndex + 1,
          total: state.bookIds.length
        }
      : null;
  });

  setAvailableBookIds(bookIds: number[]): void {
    this._availableBookIds.set(bookIds);
  }

  readBook(book: ReadableBook, {bookType = book.primaryFile?.bookType, streaming = false}: ReadOptions = {}): void {
    const baseUrl = this.readerRoute(bookType);
    if (!baseUrl) {
      return;
    }

    const queryParams: Params = {};
    if (bookType !== book.primaryFile?.bookType) {
      queryParams['bookType'] = bookType;
    }
    if (streaming) {
      queryParams['streaming'] = true;
    }

    void this.router.navigate([`/${baseUrl}/book/${book.id}`], {queryParams});
  }

  openBook(bookId: number, contextIds: number[]): void {
    if (contextIds.length > 0) {
      this.setNavigationContext(contextIds, bookId);
    }

    const mode = this.userService.currentUser()?.userSettings.metadataCenterViewMode ?? 'route';
    if (mode === 'route') {
      void this.router.navigate(['/book', bookId]);
      return;
    }
    void this.dialogHelper.openBookDetailsDialog(bookId);
  }

  setNavigationContext(bookIds: number[], currentBookId: number): void {
    const currentIndex = bookIds.indexOf(currentBookId);
    if (currentIndex !== -1) {
      this._navigationState.set({bookIds, currentIndex});
    } else {
      this._navigationState.set(null);
    }
  }

  updateCurrentBook(bookId: number): void {
    const state = this.navigationState();
    if (state) {
      const newIndex = state.bookIds.indexOf(bookId);
      if (newIndex !== -1) {
        this._navigationState.set({
          ...state,
          currentIndex: newIndex
        });
      }
    }
  }

  private readerRoute(bookType: BookFileType | undefined): string | null {
    switch (bookType) {
      case 'PDF':
        return 'pdf-reader';
      case 'EPUB':
      case 'FB2':
      case 'MOBI':
      case 'AZW3':
        return 'ebook-reader';
      case 'CBX':
        return 'cbx-reader';
      case 'AUDIOBOOK':
        return 'audiobook-player';
      default:
        return null;
    }
  }
}
