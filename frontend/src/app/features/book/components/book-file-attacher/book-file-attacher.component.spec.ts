import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {describe, expect, it, vi} from 'vitest';
import {Observable, of, throwError} from 'rxjs';
import {Book} from '../../model/book.model';
import {BookFileService} from '../../service/book-file.service';
import {BookService} from '../../service/book.service';
import {BookFileAttacherComponent} from './book-file-attacher.component';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';

interface DialogData {
  sourceBook?: Book;
  sourceBooks?: Book[];
}

type AppSettingsLike = {
  metadataPersistenceSettings?: {
    moveFilesToLibraryPattern?: boolean;
  };
} | null;

function buildBook(overrides: Partial<Book> = {}): Book {
  const id = overrides.id ?? 1;
  return {
    id,
    libraryId: overrides.libraryId ?? 10,
    libraryName: overrides.libraryName ?? 'Main Library',
    metadata: overrides.metadata ?? {
      bookId: id,
      title: `Book ${id}`,
      authors: [`Author ${id}`],
    },
    primaryFile: overrides.primaryFile,
    ...overrides,
  };
}

function setup(options: {
  dialogData?: DialogData;
  books?: Book[];
  appSettings?: AppSettingsLike;
  attachResult?: Observable<{updatedBook: Book; deletedSourceBookIds: number[]}>;
} = {}) {
  const dialogRef = {
    close: vi.fn(),
  };
  const config = {
    data: options.dialogData ?? {},
  };
  const booksSignal = signal<Book[]>(options.books ?? []);
  const bookFileService = {
    attachBookFiles: vi.fn().mockReturnValue(
      options.attachResult ?? of({updatedBook: buildBook({id: 999}), deletedSourceBookIds: []})
    ),
  };
  const appSettingsService = {
    appSettings: vi.fn(() => options.appSettings ?? null),
  };
  const translocoService = {
    translate: vi.fn((key: string) => key),
  };
  const messageService = {
    add: vi.fn(),
  };

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      {provide: DynamicDialogRef, useValue: dialogRef},
      {provide: DynamicDialogConfig, useValue: config},
      {provide: BookService, useValue: {books: booksSignal}},
      {provide: BookFileService, useValue: bookFileService},
      {provide: AppSettingsService, useValue: appSettingsService},
      {provide: TranslocoService, useValue: translocoService},
      {provide: MessageService, useValue: messageService},
    ],
  });

  const component = TestBed.runInInjectionContext(() => new BookFileAttacherComponent());

  return {
    component,
    dialogRef,
    bookFileService,
    appSettingsService,
    translocoService,
    booksSignal,
  };
}

describe('BookFileAttacherComponent', () => {
  it('bootstraps a single source book, filters candidates by library/source ids, and applies the moveFiles setting', () => {
    const sourceBook = buildBook({id: 1, libraryId: 7, metadata: {bookId: 1, title: 'Source', authors: ['Origin']}});
    const siblingCandidate = buildBook({id: 2, libraryId: 7, metadata: {bookId: 2, title: 'Sibling', authors: ['Match']}});
    const foreignLibraryBook = buildBook({id: 3, libraryId: 99, metadata: {bookId: 3, title: 'Foreign', authors: ['Other']}});
    const {component} = setup({
      dialogData: {sourceBook},
      books: [sourceBook, siblingCandidate, foreignLibraryBook],
      appSettings: {
        metadataPersistenceSettings: {
          moveFilesToLibraryPattern: true,
        },
      },
    });

    component.ngOnInit();

    expect(component.sourceBooks).toEqual([sourceBook]);
    expect(component.isBulkMode).toBe(false);
    expect(component.moveFiles).toBe(true);
    expect(component.filteredBooks).toEqual([siblingCandidate]);
  });

  it('bootstraps bulk mode from sourceBooks and defaults moveFiles to false when settings are unavailable', () => {
    const sourceA = buildBook({id: 10, libraryId: 4});
    const sourceB = buildBook({id: 11, libraryId: 4});
    const target = buildBook({id: 12, libraryId: 4});
    const {component} = setup({
      dialogData: {sourceBooks: [sourceA, sourceB]},
      books: [sourceA, sourceB, target],
      appSettings: null,
    });

    component.ngOnInit();

    expect(component.sourceBooks).toEqual([sourceA, sourceB]);
    expect(component.isBulkMode).toBe(true);
    expect(component.moveFiles).toBe(false);
    expect(component.filteredBooks).toEqual([target]);
  });

  it('closes immediately when no source payload is provided', () => {
    const {component, dialogRef, appSettingsService} = setup({
      dialogData: {},
      books: [buildBook({id: 20})],
    });

    component.ngOnInit();

    expect(dialogRef.close).toHaveBeenCalledOnce();
    expect(dialogRef.close).toHaveBeenCalledWith();
    expect(appSettingsService.appSettings).not.toHaveBeenCalled();
  });

  it('filters candidate books by title and author and resets blank queries to the first twenty matches', () => {
    const sourceBook = buildBook({id: 1, libraryId: 5, metadata: {bookId: 1, title: 'Source', authors: ['Keeper']}});
    const matchingByTitle = buildBook({id: 2, libraryId: 5, metadata: {bookId: 2, title: 'Dune Messiah', authors: ['Frank Herbert']}});
    const matchingByAuthor = buildBook({id: 3, libraryId: 5, metadata: {bookId: 3, title: 'Fahrenheit 451', authors: ['Ray Bradbury']}});
    const fillerBooks = Array.from({length: 25}, (_, index) =>
      buildBook({
        id: index + 10,
        libraryId: 5,
        metadata: {bookId: index + 10, title: `Candidate ${index}`, authors: [`Writer ${index}`]},
      })
    );
    const {component} = setup({
      dialogData: {sourceBook},
      books: [sourceBook, matchingByTitle, matchingByAuthor, ...fillerBooks],
    });

    component.ngOnInit();

    expect(component.filteredBooks).toHaveLength(20);

    component.filterBooks({query: ' dune '});
    expect(component.filteredBooks).toEqual([matchingByTitle]);

    component.filterBooks({query: 'bradbury'});
    expect(component.filteredBooks).toEqual([matchingByAuthor]);

    component.filterBooks({query: '   '});
    expect(component.filteredBooks).toHaveLength(20);
    expect(component.filteredBooks[0]).toEqual(matchingByTitle);
  });

  it('handles lightweight selection events and formats display helpers with fallbacks', () => {
    const selectedBook = buildBook({
      id: 30,
      metadata: {bookId: 30, title: 'The Left Hand of Darkness', authors: ['Ursula K. Le Guin', 'Guest Author']},
      primaryFile: {id: 301, bookId: 30, extension: 'epub', fileName: 'left-hand.epub'},
    });
    const untitledBook = buildBook({id: 31, metadata: undefined, primaryFile: undefined});
    const {component, translocoService} = setup();

    component.onBookSelect({value: selectedBook} as never);
    expect(component.targetBook).toEqual(selectedBook);

    component.onBookClear();
    expect(component.targetBook).toBeNull();

    expect(component.getBookDisplayName(selectedBook)).toBe('The Left Hand of Darkness - Ursula K. Le Guin, Guest Author');
    expect(component.getBookDisplayName(untitledBook)).toBe('Book #31');
    expect(component.getSourceFileInfo(selectedBook)).toBe('EPUB - left-hand.epub');
    expect(component.getSourceFileInfo(untitledBook)).toBe('book.fileAttacher.unknownFile');
    expect(translocoService.translate).toHaveBeenCalledWith('book.fileAttacher.unknownFile');
  });

  it('uses the target selection and defaulted moveFiles value when attach succeeds', () => {
    const sourceBook = buildBook({id: 40, libraryId: 8});
    const targetBook = buildBook({id: 41, libraryId: 8});
    const {component, bookFileService, dialogRef} = setup({
      dialogData: {sourceBook},
      books: [sourceBook, targetBook],
      appSettings: null,
    });

    component.ngOnInit();
    component.targetBook = targetBook;

    component.attach();

    expect(bookFileService.attachBookFiles).toHaveBeenCalledWith(41, [40], false);
    expect(dialogRef.close).toHaveBeenCalledWith({success: true});
  });

  it('resets attaching state when attach fails and preserves the dialog', () => {
    const sourceBook = buildBook({id: 50, libraryId: 9});
    const targetBook = buildBook({id: 51, libraryId: 9});
    const {component, bookFileService, dialogRef} = setup({
      dialogData: {sourceBook},
      books: [sourceBook, targetBook],
      appSettings: {
        metadataPersistenceSettings: {
          moveFilesToLibraryPattern: true,
        },
      },
      attachResult: throwError(() => new Error('attach failed')),
    });

    component.ngOnInit();
    component.targetBook = targetBook;

    component.attach();

    expect(bookFileService.attachBookFiles).toHaveBeenCalledWith(51, [50], true);
    expect(component.isAttaching).toBe(false);
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('does not attach without a target book and exposes direct close behavior', () => {
    const sourceBook = buildBook({id: 60});
    const {component, bookFileService, dialogRef} = setup({
      dialogData: {sourceBook},
      books: [sourceBook],
    });

    component.ngOnInit();
    component.attach();
    component.closeDialog();

    expect(component.canAttach()).toBe(false);
    expect(bookFileService.attachBookFiles).not.toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledOnce();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
