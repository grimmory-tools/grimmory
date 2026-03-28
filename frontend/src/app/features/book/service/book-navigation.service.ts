import {computed, Injectable, signal} from '@angular/core';

export interface BookNavigationState {
  bookIds: number[];
  currentIndex: number;
}

@Injectable({
  providedIn: 'root'
})
export class BookNavigationService {
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
}
