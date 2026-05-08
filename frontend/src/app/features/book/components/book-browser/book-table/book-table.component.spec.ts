import {ComponentFixture, TestBed} from '@angular/core/testing';
import {By} from '@angular/platform-browser';
import {provideRouter} from '@angular/router';
import {of, Subject} from 'rxjs';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {MessageService} from 'primeng/api';

import {getTranslocoModule} from '../../../../../core/testing/transloco-testing';
import {UrlHelperService} from '../../../../../shared/service/url-helper.service';
import {Book, BookMetadata, ReadStatus} from '../../../model/book.model';
import {BookMetadataManageService} from '../../../service/book-metadata-manage.service';
import {ReadStatusHelper} from '../../../helpers/read-status.helper';
import {BookSelectionService} from '../book-selection.service';
import {BookTableComponent} from './book-table.component';
import {BookTableRowComponent} from './book-table-row.component';

function makeBook(id: number, title: string): Book & {metadata: BookMetadata} {
  return {
    id,
    libraryId: 1,
    libraryName: 'Library',
    metadata: {
      bookId: id,
      title,
      authors: ['Test Author'],
      categories: ['Fantasy'],
      amazonRating: 4.2,
      allMetadataLocked: false,
    },
    readStatus: ReadStatus.READING,
    primaryFile: {
      id: id * 10,
      bookId: id,
      bookType: 'EPUB',
      fileName: `${title}.epub`,
    },
  };
}

describe('BookTableComponent', () => {
  let fixture: ComponentFixture<BookTableComponent>;
  let component: BookTableComponent;
  let bookSelectionService: BookSelectionService;
  let bookMetadataManageService: { toggleAllLock: ReturnType<typeof vi.fn> };
  let queryClient: { setQueriesData: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    queryClient = {setQueriesData: vi.fn()};
    bookMetadataManageService = {
      toggleAllLock: vi.fn(() => of(null)),
    };

    TestBed.configureTestingModule({
      imports: [BookTableComponent, getTranslocoModule()],
      providers: [
        provideRouter([]),
        {provide: QueryClient, useValue: queryClient},
        {provide: MessageService, useValue: {add: vi.fn()}},
        {
          provide: UrlHelperService,
          useValue: {
            getBookUrl: (book: Book) => `/book/${book.id}`,
            filterBooksBy: (filterKey: string, filterValue: string) => `/all-books?filter=${filterKey}:${filterValue}`,
            getThumbnailUrl: () => null,
            getAudiobookThumbnailUrl: () => null,
          },
        },
        {
          provide: BookMetadataManageService,
          useValue: bookMetadataManageService,
        },
        {
          provide: ReadStatusHelper,
          useValue: {
            getReadStatusIcon: () => 'pi pi-book',
            getReadStatusClass: () => 'status-reading',
            getReadStatusTooltip: () => 'Reading',
            shouldShowStatusIcon: () => true,
          },
        },
      ],
    });

    fixture = TestBed.createComponent(BookTableComponent);
    component = fixture.componentInstance;
    bookSelectionService = TestBed.inject(BookSelectionService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('renders grid semantics and keeps resize handles out of the tab order', () => {
    fixture.componentRef.setInput('books', [makeBook(1, 'Alpha')]);
    fixture.componentRef.setInput('visibleColumns', [
      {field: 'title', header: 'Title'},
      {field: 'amazonRating', header: 'Amazon Rating'},
    ]);

    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const grid = host.querySelector<HTMLElement>('.book-table-scroll');
    const headerCells = host.querySelectorAll<HTMLElement>('.book-table-header-cell');
    const resizer = host.querySelector<HTMLElement>('.column-resizer');

    expect(grid?.getAttribute('role')).toBe('grid');
    expect(grid?.getAttribute('aria-rowcount')).toBe('2');
    expect(grid?.getAttribute('aria-colcount')).toBe('5');
    expect(headerCells[0]?.getAttribute('role')).toBe('columnheader');
    expect(headerCells[0]?.getAttribute('aria-colindex')).toBe('1');
    expect(resizer?.tagName).toBe('SPAN');
    expect(resizer?.getAttribute('aria-hidden')).toBe('true');
    expect(resizer?.hasAttribute('tabindex')).toBe(false);
  });

  it('does not recreate a rendered row when selection changes', () => {
    const book = makeBook(1, 'Alpha');
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 0, key: 1, start: 0, size: 46, end: 46, lane: 0}
    ]);
    fixture.componentRef.setInput('books', [book]);
    fixture.componentRef.setInput('visibleColumns', [
      {field: 'title', header: 'Title'},
      {field: 'amazonRating', header: 'Amazon Rating'},
    ]);
    fixture.detectChanges();

    const initialRowDebugElement = fixture.debugElement.query(By.directive(BookTableRowComponent));
    const initialRowInstance = initialRowDebugElement.componentInstance as BookTableRowComponent;
    const selectionSpy = vi.spyOn(bookSelectionService, 'handleBookSelection');
    const initialRowElement = initialRowDebugElement.nativeElement as HTMLElement;
    const checkboxInput = initialRowElement.querySelector<HTMLInputElement>('input[type="checkbox"]');

    expect(checkboxInput).toBeTruthy();
    checkboxInput!.click();
    fixture.detectChanges();

    const updatedRowDebugElement = fixture.debugElement.query(By.directive(BookTableRowComponent));
    const updatedRowInstance = updatedRowDebugElement.componentInstance as BookTableRowComponent;
    expect(updatedRowDebugElement.nativeElement).toBe(initialRowDebugElement.nativeElement);
    expect(updatedRowInstance).toBe(initialRowInstance);
    expect(selectionSpy).toHaveBeenCalledWith(book, true);
    expect(updatedRowInstance.isSelected()).toBe(true);
    expect(bookSelectionService.isBookSelected(book)).toBe(true);
    expect(component.isRowSelected(book)).toBe(true);
  });

  it('does not request the next page again after a same-size book refresh', () => {
    const loadNextPageSpy = vi.fn();
    const books = Array.from({length: 50}, (_, index) => makeBook(index + 1, `Book ${index + 1}`));
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 49, key: 50, start: 2254, size: 46, end: 2300, lane: 0}
    ]);
    component.loadNextPage.subscribe(loadNextPageSpy);

    fixture.componentRef.setInput('books', books);
    fixture.componentRef.setInput('virtualRowCount', 100);
    fixture.componentRef.setInput('isFetchingNextPage', false);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('books', books.map(book => ({
      ...book,
      metadata: {...book.metadata},
    })));
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(1);
  });

  it('can request the next page again after the parent signals a query swap', () => {
    const loadNextPageSpy = vi.fn();
    const books = Array.from({length: 50}, (_, index) => makeBook(index + 1, `Book ${index + 1}`));
    const nextBooks = Array.from({length: 50}, (_, index) => makeBook(index + 101, `Next Book ${index + 1}`));
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 49, key: 50, start: 2254, size: 46, end: 2300, lane: 0}
    ]);
    component.loadNextPage.subscribe(loadNextPageSpy);

    const initialQueryToken = {query: 'first'};
    fixture.componentRef.setInput('books', books);
    fixture.componentRef.setInput('virtualRowCount', 51);
    fixture.componentRef.setInput('isFetchingNextPage', false);
    fixture.componentRef.setInput('bookQueryToken', initialQueryToken);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('books', nextBooks);
    fixture.componentRef.setInput('bookQueryToken', {query: 'second'});
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(2);
  });

  it('re-arms pagination after a next-page request completes without new rows', () => {
    const loadNextPageSpy = vi.fn();
    const books = Array.from({length: 50}, (_, index) => makeBook(index + 1, `Book ${index + 1}`));
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 49, key: 50, start: 2254, size: 46, end: 2300, lane: 0}
    ]);
    component.loadNextPage.subscribe(loadNextPageSpy);

    fixture.componentRef.setInput('books', books);
    fixture.componentRef.setInput('virtualRowCount', 51);
    fixture.componentRef.setInput('isFetchingNextPage', false);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('isFetchingNextPage', true);
    fixture.detectChanges();
    fixture.componentRef.setInput('isFetchingNextPage', false);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('virtualRowCount', 52);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(2);
  });

  it('can request the next page again when loaded books grow but rendered rows do not', () => {
    const loadNextPageSpy = vi.fn();
    const books = Array.from({length: 50}, (_, index) => makeBook(index + 1, `Book ${index + 1}`));
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 49, key: 50, start: 2254, size: 46, end: 2300, lane: 0}
    ]);
    component.loadNextPage.subscribe(loadNextPageSpy);

    fixture.componentRef.setInput('books', books);
    fixture.componentRef.setInput('virtualRowCount', 51);
    fixture.componentRef.setInput('loadedBookCount', 50);
    fixture.componentRef.setInput('isFetchingNextPage', false);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('isFetchingNextPage', true);
    fixture.detectChanges();
    fixture.componentRef.setInput('loadedBookCount', 60);
    fixture.componentRef.setInput('isFetchingNextPage', false);
    fixture.detectChanges();

    expect(loadNextPageSpy).toHaveBeenCalledTimes(2);
  });

  it('requests metadata lock without mutating the input book', () => {
    const book = makeBook(1, 'Alpha');
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 0, key: 1, start: 0, size: 46, end: 46, lane: 0}
    ]);
    fixture.componentRef.setInput('books', [book]);
    fixture.componentRef.setInput('visibleColumns', [{field: 'title', header: 'Title'}]);
    fixture.detectChanges();

    component.toggleMetadataLock(book.metadata);
    fixture.detectChanges();

    expect(book.metadata?.allMetadataLocked).toBe(false);
    expect(bookMetadataManageService.toggleAllLock).toHaveBeenCalledWith(new Set([1]), 'LOCK');
    expect(queryClient.setQueriesData).toHaveBeenCalledWith({queryKey: ['app-books']}, expect.any(Function));
  });

  it('disables a row lock button while that book lock request is in flight', () => {
    const lockRequest = new Subject<void>();
    bookMetadataManageService.toggleAllLock.mockReturnValue(lockRequest.asObservable());
    const book = makeBook(1, 'Alpha');
    vi.spyOn(component.rowVirtualizer, 'getVirtualItems').mockReturnValue([
      {index: 0, key: 1, start: 0, size: 46, end: 46, lane: 0}
    ]);
    fixture.componentRef.setInput('books', [book]);
    fixture.componentRef.setInput('visibleColumns', [{field: 'title', header: 'Title'}]);
    fixture.detectChanges();

    component.toggleMetadataLock(book.metadata);
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    const button = host.querySelector<HTMLButtonElement>('.metadata-lock-button');
    expect(button?.disabled).toBe(true);

    component.toggleMetadataLock(book.metadata);
    expect(bookMetadataManageService.toggleAllLock).toHaveBeenCalledTimes(1);

    lockRequest.next();
    lockRequest.complete();
    fixture.detectChanges();

    expect(button?.disabled).toBe(false);
  });
});
