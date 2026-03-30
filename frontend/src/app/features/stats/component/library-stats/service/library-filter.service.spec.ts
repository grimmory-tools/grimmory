import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {LibraryFilterService} from './library-filter.service';
import {BookService} from '../../../../book/service/book.service';
import {LibraryService} from '../../../../book/service/library.service';
import {Book} from '../../../../book/model/book.model';
import {Library} from '../../../../book/model/library.model';
import {TranslocoService} from '@jsverse/transloco';

describe('LibraryFilterService', () => {
  const books = signal<Book[]>([]);
  const libraries = signal<Library[]>([]);
  const translate = vi.fn((key: string, params?: Record<string, unknown>) =>
    params?.['id'] ? `${key}:${params['id']}` : key
  );

  beforeEach(() => {
    books.set([]);
    libraries.set([]);
    translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        LibraryFilterService,
        {provide: BookService, useValue: {books}},
        {provide: LibraryService, useValue: {libraries}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('shows the all libraries option when there are no books', () => {
    const service = TestBed.inject(LibraryFilterService);

    expect(service.libraryOptions()).toEqual([
      {id: null, name: 'statsLibrary.libraryFilter.allLibraries'}
    ]);
    expect(service.selectedLibrary()).toBeNull();
  });

  it('sorts library options and falls back when the library lookup is missing', () => {
    books.set([
      {id: 1, libraryId: 2, libraryName: 'Beta'} as Book,
      {id: 2, libraryId: 1, libraryName: 'Alpha'} as Book,
      {id: 3, libraryId: 2, libraryName: 'Beta duplicate'} as Book,
    ]);
    libraries.set([
      {id: 1, name: 'Alpha', watch: false, paths: []} as Library,
    ]);

    const service = TestBed.inject(LibraryFilterService);

    expect(service.libraryOptions()).toEqual([
      {id: null, name: 'statsLibrary.libraryFilter.allLibraries'},
      {id: 1, name: 'Alpha'},
      {id: 2, name: 'statsLibrary.libraryFilter.libraryFallback:2'},
    ]);

    service.setSelectedLibrary(2);
    expect(service.selectedLibrary()).toBe(2);

    service.setSelectedLibrary(99);
    expect(service.selectedLibrary()).toBeNull();
  });
});
