import {inject, Injectable} from '@angular/core';
import {BehaviorSubject, combineLatest, map, Observable} from 'rxjs';
import {BookService} from '../../../../book/service/book.service';
import {LibraryService} from '../../../../book/service/library.service';
import {TranslocoService} from '@jsverse/transloco';

export interface LibraryOption {
  id: number | null;
  name: string;
}

@Injectable({
  providedIn: 'root'
})
export class LibraryFilterService {
  private selectedLibrarySubject = new BehaviorSubject<number | null>(null);

  selectedLibrary$ = this.selectedLibrarySubject.asObservable();

  getCurrentSelectedLibrary(): number | null {
    return this.selectedLibrarySubject.value;
  }

  setSelectedLibrary(libraryId: number | null): void {
    this.selectedLibrarySubject.next(libraryId);
  }

  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private t = inject(TranslocoService);

  getLibraryOptions(): Observable<LibraryOption[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.libraryService.libraryState$
    ]).pipe(
      map(([bookState, libraryState]) => {
        if (!bookState.loaded || !bookState.books || bookState.books.length === 0) {
          return [{id: null, name: this.t.translate('statsLibrary.libraryFilter.allLibraries')}];
        }

        if (!libraryState.loaded || !libraryState.libraries) {
          return [{id: null, name: this.t.translate('statsLibrary.libraryFilter.allLibraries')}];
        }

        const libraryMap = new Map<number, string>();
        bookState.books.forEach(book => {
          if (!libraryMap.has(book.libraryId)) {
            const library = libraryState.libraries?.find(lib => lib.id === book.libraryId);
            const libraryName: string = library?.name || this.t.translate('statsLibrary.libraryFilter.libraryFallback', {id: book.libraryId}) as string;
            libraryMap.set(book.libraryId, libraryName);
          }
        });

        const options: LibraryOption[] = [
          {id: null, name: this.t.translate('statsLibrary.libraryFilter.allLibraries')},
          ...Array.from(libraryMap.entries()).map(([id, name]) => ({id, name}))
        ];

        return options.sort((a, b) => {
          if (a.id === null) return -1;
          if (b.id === null) return 1;
          return a.name.localeCompare(b.name);
        });
      })
    );
  }
}
