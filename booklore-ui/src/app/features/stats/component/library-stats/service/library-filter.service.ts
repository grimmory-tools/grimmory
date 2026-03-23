import {computed, inject, Injectable, signal} from '@angular/core';
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
  private readonly selectedLibraryId = signal<number | null>(null);

  readonly selectedLibrary = computed(() => {
    const selectedLibraryId = this.selectedLibraryId();
    return this.libraryOptions().some(option => option.id === selectedLibraryId)
      ? selectedLibraryId
      : null;
  });

  setSelectedLibrary(libraryId: number | null): void {
    this.selectedLibraryId.set(libraryId);
  }

  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private t = inject(TranslocoService);
  readonly libraryOptions = computed(() => {
    const books = this.bookService.books();
    const libraries = this.libraryService.libraries();

    if (books.length === 0) {
      return [{id: null, name: this.t.translate('statsLibrary.libraryFilter.allLibraries')}];
    }

    const libraryMap = new Map<number, string>();
    books.forEach(book => {
      if (!libraryMap.has(book.libraryId)) {
        const library = libraries.find(lib => lib.id === book.libraryId);
        const libraryName = library?.name || this.t.translate('statsLibrary.libraryFilter.libraryFallback', {id: book.libraryId}) as string;
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
  });
}
