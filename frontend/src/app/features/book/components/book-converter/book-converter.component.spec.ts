import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {signal} from '@angular/core';

import {AdditionalFile, AdditionalFileType, Book, BookFile, BookType} from '../../model/book.model';
import {Library} from '../../model/library.model';
import {BookConversionCapability, BookConversionResponse, BookConversionService} from '../../service/book-conversion.service';
import {LibraryService} from '../../service/library.service';
import {BookConverterComponent} from './book-converter.component';


describe('BookConverterComponent', () => {
  const capability = signal<BookConversionCapability>({
    available: true,
    supportedTargetFormats: ['EPUB', 'PDF', 'MOBI', 'AZW3', 'FB2'],
  });
  const libraries = new Map<number, Library>();

  const bookConversionService = {
    capability,
    convertBooks: vi.fn((bookIds: number[], targetFormat: BookType) => {
      const response: BookConversionResponse = {
        acceptedCount: bookIds.length,
        targetFormat,
      };
      return of(response);
    }),
  };
  const libraryService = {
    findLibraryById: vi.fn((libraryId: number) => libraries.get(libraryId)),
  };
  const messageService = {
    add: vi.fn(),
  };
  const dialogRef = {
    close: vi.fn(),
  };
  const translocoService = {
    translate: vi.fn((key: string, params?: Record<string, unknown>) => params ? `${key}:${JSON.stringify(params)}` : key),
  };
  const dialogConfig: {data: {books: Book[]}} = {
    data: {books: []},
  };

  beforeEach(() => {
    capability.set({
      available: true,
      supportedTargetFormats: ['EPUB', 'PDF', 'MOBI', 'AZW3', 'FB2'],
    });
    libraries.clear();
    libraries.set(7, createLibrary({id: 7, allowedFormats: []}));
    libraries.set(8, createLibrary({id: 8, allowedFormats: []}));

    bookConversionService.convertBooks.mockClear();
    libraryService.findLibraryById.mockClear();
    messageService.add.mockClear();
    dialogRef.close.mockClear();
    translocoService.translate.mockClear();
    dialogConfig.data = {books: [createBook()]};

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        {provide: BookConversionService, useValue: bookConversionService},
        {provide: LibraryService, useValue: libraryService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: DynamicDialogConfig, useValue: dialogConfig},
      ],
    });
  });

  it('filters already present formats for a single book', () => {
    const component = instantiateComponent([createBook({
      primaryFile: createBookFile({bookType: 'EPUB'}),
      alternativeFormats: [createAdditionalFile({bookType: 'PDF'})],
    })]);

    component.ngOnInit();

    expect(component.eligibleBooks().map(book => book.id)).toEqual([1]);
    expect(component.targetFormats()).toEqual(['MOBI', 'AZW3', 'FB2']);
    expect(component.selectedFormat()).toBe('MOBI');
  });

  it('keeps bulk targets allowed by every library and useful for at least one book', () => {
    capability.set({available: true, supportedTargetFormats: ['EPUB', 'PDF', 'MOBI']});
    libraries.set(7, createLibrary({id: 7, allowedFormats: ['EPUB', 'MOBI']}));
    libraries.set(8, createLibrary({id: 8, allowedFormats: ['MOBI']}));

    const component = instantiateComponent([
      createBook({id: 1, libraryId: 7, primaryFile: createBookFile({bookId: 1, bookType: 'EPUB'})}),
      createBook({id: 2, libraryId: 8, primaryFile: createBookFile({bookId: 2, bookType: 'MOBI'})}),
    ]);

    component.ngOnInit();

    expect(component.targetFormats()).toEqual(['MOBI']);
    expect(component.selectedFormat()).toBe('MOBI');
  });

  it('submits eligible books and closes after the backend accepts conversion', () => {
    const component = instantiateComponent([
      createBook({id: 1, primaryFile: createBookFile({bookId: 1, bookType: 'EPUB'})}),
      createBook({id: 2, primaryFile: createBookFile({bookId: 2, bookType: 'AUDIOBOOK'})}),
    ]);
    component.ngOnInit();
    component.selectedFormat.set('MOBI');

    component.submit();

    expect(bookConversionService.convertBooks).toHaveBeenCalledWith([1], 'MOBI');
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.converter.toast.startedSummary',
      detail: 'book.converter.toast.startedDetail:{"count":1,"format":"MOBI"}',
    });
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  function instantiateComponent(books: Book[]): BookConverterComponent {
    dialogConfig.data = {books};
    return TestBed.runInInjectionContext(() => new BookConverterComponent());
  }
});

function createBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    libraryId: 7,
    libraryName: 'Main Library',
    primaryFile: createBookFile(),
    alternativeFormats: [],
    supplementaryFiles: [],
    ...overrides,
  };
}

function createBookFile(overrides: Partial<BookFile> = {}): BookFile {
  return {
    id: 11,
    bookId: 1,
    fileName: 'book.epub',
    bookType: 'EPUB',
    fileSizeKb: 512,
    ...overrides,
  };
}

function createAdditionalFile(overrides: Partial<AdditionalFile> = {}): AdditionalFile {
  return {
    ...createBookFile(),
    additionalFileType: AdditionalFileType.ALTERNATIVE_FORMAT,
    ...overrides,
  };
}

function createLibrary(overrides: Partial<Library> = {}): Library {
  return {
    id: 7,
    name: 'Main Library',
    watch: false,
    paths: [],
    allowedFormats: [],
    ...overrides,
  };
}
