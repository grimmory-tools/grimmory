import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {signal} from '@angular/core';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import type {AutoCompleteCompleteEvent} from '@openng/optimus-ui/autocomplete';
import {MessageService} from '@openng/optimus-ui/api';
import {DynamicDialogConfig, DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {TranslocoService} from '@jsverse/transloco';
import {provideTanStackQuery, QueryClient} from '@tanstack/angular-query-experimental';
import {Observable, Subject, throwError} from 'rxjs';
import {afterEach, describe, expect, it, vi} from 'vitest';

import {createAuthServiceStub} from '../../../../core/testing/query-testing';
import {AuthService} from '../../../../shared/service/auth.service';
import {Book} from '../../model/book.model';
import {Library} from '../../model/library.model';
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
    const createResult$ = options.createResult$ ?? new Subject<Book>();
    const createPhysicalBook = vi.fn(() => createResult$);
    const dialogRef = {close: vi.fn()};

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTanStackQuery(new QueryClient()),
        {provide: DynamicDialogConfig, useValue: {data: options.dialogData ?? {}}},
        {provide: DynamicDialogRef, useValue: dialogRef},
        {provide: AppSettingsService, useValue: {appSettings: signal(null)}},
        {provide: AuthService, useValue: createAuthServiceStub()},
        {provide: BookService, useValue: {uniqueMetadata, createPhysicalBook}},
        {provide: LibraryService, useValue: {libraries}},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {provide: TranslocoService, useValue: {translate: (key: string) => key}},
      ],
    });

    const component = TestBed.runInInjectionContext(() => new AddPhysicalBookDialogComponent());
    TestBed.flushEffects();

    return {
      component,
      libraries,
      uniqueMetadata,
      createResult$,
      createPhysicalBook,
      dialogRef,
      http: TestBed.inject(HttpTestingController),
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

  it('gates creation on required fields and does not submit when already loading', () => {
    const {component, createPhysicalBook} = createHarness();

    expect(component.canCreate()).toBe(false);
    component.createBook();
    expect(createPhysicalBook).not.toHaveBeenCalled();

    component.selectedLibraryId = 2;
    component.title = 'Physical Copy';
    expect(component.canCreate()).toBe(true);

    component.isLoading.set(true);
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

    expect(component.isLoading()).toBe(true);
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

    expect(component.isLoading()).toBe(false);
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
    expect(component.isLoading()).toBe(false);
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
