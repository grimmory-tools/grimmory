import {Injectable} from '@angular/core';
import {BehaviorSubject} from 'rxjs';
import {map} from 'rxjs/operators';

export type LibraryImportProgressStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ERROR';

export interface LibraryImportProgressState {
  active: boolean;
  libraryId?: number;
  libraryName: string;
  expectedCount: number;
  processedCount: number;
  currentBookTitle?: string;
  status: LibraryImportProgressStatus;
}

const EMPTY_STATE: LibraryImportProgressState = {
  active: false,
  libraryName: '',
  expectedCount: 0,
  processedCount: 0,
  status: 'COMPLETED',
};

@Injectable({providedIn: 'root'})
export class LibraryImportProgressService {
  private readonly stateSubject = new BehaviorSubject<LibraryImportProgressState>(EMPTY_STATE);

  readonly state$ = this.stateSubject.asObservable();
  readonly hasActiveImport$ = this.state$.pipe(map(state => state.active));

  start(libraryName: string, expectedCount: number): void {
    if (expectedCount <= 0) {
      this.clear();
      return;
    }

    this.stateSubject.next({
      active: false,
      libraryName,
      expectedCount,
      processedCount: 0,
      status: 'IN_PROGRESS',
    });
  }

  attachLibrary(libraryId: number): void {
    const state = this.stateSubject.value;
    if (state.expectedCount <= 0) return;
    this.stateSubject.next({...state, libraryId});
  }

  recordBookAdded(bookTitle: string): void {
    const state = this.stateSubject.value;
    if (state.expectedCount <= 0 || state.status !== 'IN_PROGRESS') return;

    const processedCount = Math.min(state.processedCount + 1, state.expectedCount);
    this.stateSubject.next({
      ...state,
      active: true,
      processedCount,
      currentBookTitle: bookTitle,
      status: processedCount >= state.expectedCount
        ? 'COMPLETED'
        : 'IN_PROGRESS',
    });
  }

  fail(): void {
    const state = this.stateSubject.value;
    if (state.expectedCount <= 0 || state.status !== 'IN_PROGRESS') return;
    if (!state.active) {
      this.clear();
      return;
    }
    this.stateSubject.next({...state, status: 'ERROR'});
  }

  clear(): void {
    this.stateSubject.next(EMPTY_STATE);
  }
}
