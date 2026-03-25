import {Injectable, signal} from '@angular/core';
import {Subject} from 'rxjs';
import {Book} from '../../../../book/model/book.model';

export interface CbxFooterState {
  currentPage: number;
  totalPages: number;
  isTwoPageView: boolean;
  previousBookInSeries: Book | null;
  nextBookInSeries: Book | null;
  hasSeries: boolean;
}

@Injectable()
export class CbxFooterService {
  private readonly defaultState: CbxFooterState = {
    currentPage: 0,
    totalPages: 0,
    isTwoPageView: false,
    previousBookInSeries: null,
    nextBookInSeries: null,
    hasSeries: false
  };

  private readonly _state = signal<CbxFooterState>(this.defaultState);
  readonly state = this._state.asReadonly();

  private readonly _forceVisible = signal(false);
  readonly forceVisible = this._forceVisible.asReadonly();

  private _previousPage = new Subject<void>();
  previousPage$ = this._previousPage.asObservable();

  private _nextPage = new Subject<void>();
  nextPage$ = this._nextPage.asObservable();

  private _goToPage = new Subject<number>();
  goToPage$ = this._goToPage.asObservable();

  private _firstPage = new Subject<void>();
  firstPage$ = this._firstPage.asObservable();

  private _lastPage = new Subject<void>();
  lastPage$ = this._lastPage.asObservable();

  private _previousBook = new Subject<void>();
  previousBook$ = this._previousBook.asObservable();

  private _nextBook = new Subject<void>();
  nextBook$ = this._nextBook.asObservable();

  private _sliderChange = new Subject<number>();
  sliderChange$ = this._sliderChange.asObservable();

  setForceVisible(visible: boolean): void {
    this._forceVisible.set(visible);
  }

  updateState(partial: Partial<CbxFooterState>): void {
    this._state.update(current => ({...current, ...partial}));
  }

  setCurrentPage(page: number): void {
    this.updateState({currentPage: page});
  }

  setTotalPages(total: number): void {
    this.updateState({totalPages: total});
  }

  setTwoPageView(isTwoPage: boolean): void {
    this.updateState({isTwoPageView: isTwoPage});
  }

  setSeriesBooks(previous: Book | null, next: Book | null): void {
    this.updateState({
      previousBookInSeries: previous,
      nextBookInSeries: next
    });
  }

  setHasSeries(hasSeries: boolean): void {
    this.updateState({hasSeries});
  }

  // Navigation actions (called from footer component)
  emitPreviousPage(): void {
    this._previousPage.next();
  }

  emitNextPage(): void {
    this._nextPage.next();
  }

  emitGoToPage(page: number): void {
    this._goToPage.next(page);
  }

  emitFirstPage(): void {
    this._firstPage.next();
  }

  emitLastPage(): void {
    this._lastPage.next();
  }

  emitPreviousBook(): void {
    this._previousBook.next();
  }

  emitNextBook(): void {
    this._nextBook.next();
  }

  emitSliderChange(page: number): void {
    this._sliderChange.next(page);
  }

  reset(): void {
    this._state.set(this.defaultState);
    this._forceVisible.set(false);
  }
}
