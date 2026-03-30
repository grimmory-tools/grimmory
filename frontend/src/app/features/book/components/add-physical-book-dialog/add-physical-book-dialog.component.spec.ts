import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import type {AutoCompleteCompleteEvent} from 'primeng/autocomplete';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Observable, Subject, throwError} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';

import {Book, BookMetadata} from '../../model/book.model';
import {Library} from '../../model/library.model';
import {BookMetadataService} from '../../service/book-metadata.service';
import {BookService} from '../../service/book.service';
import {LibraryService} from '../../service/library.service';
import {AddPhysicalBookDialogComponent} from './add-physical-book-dialog.component';

describe('AddPhysicalBookDialogComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function createLibrary(overrides: Partial<Library> = {}): Library {
    return {
      id: 1,
      name: 'Main Library',
      watch: true,
      paths: [],
      ...overrides,
    };
  }

  function createBook(overrides: Partial<Book> = {}): Book {
    return {
      id: 99,
      libraryId: 2,
      libraryName: 'Branch Library',
      ...overrides,
    };
  }

  function createMetadata(overrides: Partial<BookMetadata> = {}): BookMetadata {
    return {
      bookId: 0,
      ...overrides,
    };
  }

  function createHarness(options: {
    dialogData?: {libraryId?: number};
    librariesData?: Library[];
    metadataValues?: {
      authors: string[];
      categories: string[];
      moods: string[];
      tags: string[];
      publishers: string[];
      series: string[];
    };
    lookupResult$?: Observable<BookMetadata>;
    createResult$?: Observable<Book>;
  } = {}) {
    const libraries = signal<Library[]>(options.librariesData ?? [
      createLibrary({id: 1, name: 'Main Library'}),
      createLibrary({id: 2, name: 'Branch Library'}),
    ]);
    const uniqueMetadata = signal(options.metadataValues ?? {
      authors: ['Ursula Le Guin', 'Octavia Butler', 'Robin Hobb'],
      categories: ['Science Fiction', 'Epic Fantasy', 'Mystery'],
      moods: [],
      tags: [],
      publishers: [],
      series: [],
    });
    const lookupResult$ = options.lookupResult$ ?? new Subject<BookMetadata>();
    const createResult$ = options.createResult$ ?? new Subject<Book>();
    const lookupByIsbn = vi.fn(() => lookupResult$);
    const createPhysicalBook = vi.fn(() => createResult$);
    const dialogRef = {close: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        {provide: DynamicDialogConfig, useValue: {data: options.dialogData ?? {}}},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: BookService, useValue: {uniqueMetadata, createPhysicalBook}},
        {provide: BookMetadataService, useValue: {lookupByIsbn}},
        {provide: LibraryService, useValue: {libraries}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new AddPhysicalBookDialogComponent());
    TestBed.flushEffects();

    return {
      component,
      libraries,
      uniqueMetadata,
      lookupResult$,
      createResult$,
      lookupByIsbn,
      createPhysicalBook,
      dialogRef,
    };
  }

  function triggerEnter(component: AddPhysicalBookDialogComponent, fieldName: 'authors' | 'categories', value: string): HTMLInputElement {
    const input = document.createElement('input');
    input.value = value;

    component.onAutoCompleteKeyUp(fieldName, {
      key: 'Enter',
      target: input,
    } as unknown as KeyboardEvent);

    return input;
  }

  function triggerSelect(component: AddPhysicalBookDialogComponent, fieldName: 'authors' | 'categories', value: string): HTMLInputElement {
    const input = document.createElement('input');
    input.value = value;

    component.onAutoCompleteSelect(fieldName, {
      value,
      originalEvent: {target: input} as unknown as Event,
    });

    return input;
  }

  it('prefers the dialog library selection when one is provided', () => {
    const {component} = createHarness({
      dialogData: {libraryId: 2},
    });

    expect(component.selectedLibraryId).toBe(2);
  });

  it('defaults to the first available library when the dialog does not provide one', () => {
    const {component} = createHarness({
      dialogData: {},
    });

    expect(component.selectedLibraryId).toBe(1);
  });

  it('filters authors and categories with case-insensitive substring matches', () => {
    const {component} = createHarness();

    component.filterAuthors({query: 'taV', originalEvent: new Event('input')} as AutoCompleteCompleteEvent);
    component.filterCategories({query: 'fic', originalEvent: new Event('input')} as AutoCompleteCompleteEvent);

    expect(component.filteredAuthors).toEqual(['Octavia Butler']);
    expect(component.filteredCategories).toEqual(['Science Fiction']);
  });

  it('adds entered autocomplete values once and clears the input after Enter', () => {
    const {component} = createHarness();

    const firstInput = triggerEnter(component, 'authors', '  N. K. Jemisin  ');
    const duplicateInput = triggerEnter(component, 'authors', 'N. K. Jemisin');

    expect(component.authors).toEqual(['N. K. Jemisin']);
    expect(firstInput.value).toBe('');
    expect(duplicateInput.value).toBe('');
  });

  it('adds selected autocomplete values once and clears the input after selection', () => {
    const {component} = createHarness();

    const firstInput = triggerSelect(component, 'categories', 'Mythic Fantasy');
    const duplicateInput = triggerSelect(component, 'categories', 'Mythic Fantasy');

    expect(component.categories).toEqual(['Mythic Fantasy']);
    expect(firstInput.value).toBe('');
    expect(duplicateInput.value).toBe('');
  });

  it('trims the isbn, hydrates metadata fields, and clears the fetching flag on success', () => {
    const lookupResult$ = new Subject<BookMetadata>();
    const {component, lookupByIsbn} = createHarness({lookupResult$});

    component.isbn = ' 9780441172719 ';
    component.fetchMetadataByIsbn();

    expect(lookupByIsbn).toHaveBeenCalledWith('9780441172719');
    expect(component.isFetchingMetadata).toBe(true);

    lookupResult$.next(createMetadata({
      title: 'Dune',
      authors: ['Frank Herbert'],
      description: 'Arrakis.',
      publisher: 'Ace',
      publishedDate: '1965',
      language: 'en',
      pageCount: 412,
      categories: ['Science Fiction'],
      thumbnailUrl: 'https://covers.example/dune.jpg',
    }));
    lookupResult$.complete();

    expect(component.title).toBe('Dune');
    expect(component.authors).toEqual(['Frank Herbert']);
    expect(component.description).toBe('Arrakis.');
    expect(component.publisher).toBe('Ace');
    expect(component.publishedDate).toBe('1965');
    expect(component.language).toBe('en');
    expect(component.pageCount).toBe(412);
    expect(component.categories).toEqual(['Science Fiction']);
    expect(component.coverUrl).toBe('https://covers.example/dune.jpg');
    expect(component.isFetchingMetadata).toBe(false);
  });

  it('does not start isbn lookup for blank or in-flight requests and clears the flag on error', () => {
    const {component, lookupByIsbn} = createHarness();

    component.isbn = '   ';
    component.fetchMetadataByIsbn();
    expect(lookupByIsbn).not.toHaveBeenCalled();

    component.isbn = '9780441172719';
    component.isFetchingMetadata = true;
    component.fetchMetadataByIsbn();
    expect(lookupByIsbn).not.toHaveBeenCalled();

    component.isFetchingMetadata = false;
    lookupByIsbn.mockReturnValueOnce(throwError(() => new Error('lookup failed')));

    component.fetchMetadataByIsbn();

    expect(lookupByIsbn).toHaveBeenCalledWith('9780441172719');
    expect(component.isFetchingMetadata).toBe(false);
  });

  it('gates creation on required fields and does not submit when already loading', () => {
    const {component, createPhysicalBook} = createHarness();

    expect(component.canCreate()).toBe(false);
    component.createBook();
    expect(createPhysicalBook).not.toHaveBeenCalled();

    component.selectedLibraryId = 2;
    component.title = 'Physical Copy';
    expect(component.canCreate()).toBe(true);

    component.isLoading = true;
    component.createBook();

    expect(createPhysicalBook).not.toHaveBeenCalled();
  });

  it('shapes the create payload, resets loading, and closes with the created book on success', () => {
    const createResult$ = new Subject<Book>();
    const createdBook = createBook({
      id: 321,
      libraryId: 2,
      libraryName: 'Branch Library',
      metadata: {bookId: 321, title: 'The Left Hand of Darkness'},
    });
    const {component, createPhysicalBook, dialogRef} = createHarness({createResult$});

    component.selectedLibraryId = 2;
    component.title = '  The Left Hand of Darkness ';
    component.isbn = '   ';
    component.authors = ['Ursula Le Guin'];
    component.description = '  ';
    component.publisher = '  Ace Books ';
    component.publishedDate = ' 1969 ';
    component.language = ' en ';
    component.pageCount = 304;
    component.categories = ['Science Fiction'];
    component.coverUrl = 'https://covers.example/left-hand.jpg';

    component.createBook();

    expect(component.isLoading).toBe(true);
    expect(createPhysicalBook).toHaveBeenCalledWith({
      libraryId: 2,
      title: 'The Left Hand of Darkness',
      isbn: undefined,
      authors: ['Ursula Le Guin'],
      description: undefined,
      publisher: 'Ace Books',
      publishedDate: '1969',
      language: 'en',
      pageCount: 304,
      categories: ['Science Fiction'],
      thumbnailUrl: 'https://covers.example/left-hand.jpg',
    });

    createResult$.next(createdBook);
    createResult$.complete();

    expect(component.isLoading).toBe(false);
    expect(dialogRef.close).toHaveBeenCalledWith(createdBook);
  });

  it('resets loading and keeps the dialog open when create fails', () => {
    const {component, createPhysicalBook, dialogRef} = createHarness();
    createPhysicalBook.mockReturnValueOnce(throwError(() => new Error('create failed')));

    component.selectedLibraryId = 2;
    component.isbn = '9780441172719';

    component.createBook();

    expect(createPhysicalBook).toHaveBeenCalledWith({
      libraryId: 2,
      title: undefined,
      isbn: '9780441172719',
      authors: undefined,
      description: undefined,
      publisher: undefined,
      publishedDate: undefined,
      language: undefined,
      pageCount: undefined,
      categories: undefined,
      thumbnailUrl: undefined,
    });
    expect(component.isLoading).toBe(false);
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
