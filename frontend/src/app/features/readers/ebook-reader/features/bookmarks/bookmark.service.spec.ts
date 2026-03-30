import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {firstValueFrom, of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookMarkService} from '../../../../../shared/service/book-mark.service';
import {ReaderBookmarkService} from './bookmark.service';

describe('ReaderBookmarkService', () => {
  let service: ReaderBookmarkService;
  const bookMarkService = {
    createBookmark: vi.fn(),
  };
  const messageService = {
    add: vi.fn(),
  };
  const translocoService = {
    translate: vi.fn((key: string) => key),
  };

  beforeEach(() => {
    bookMarkService.createBookmark.mockReset();
    messageService.add.mockReset();
    translocoService.translate.mockClear();

    TestBed.configureTestingModule({
      providers: [
        ReaderBookmarkService,
        {provide: BookMarkService, useValue: bookMarkService},
        {provide: MessageService, useValue: messageService},
        {provide: TranslocoService, useValue: translocoService},
      ]
    });

    service = TestBed.inject(ReaderBookmarkService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('returns false when no current position is set', async () => {
    await expect(firstValueFrom(service.createBookmarkAtCurrentPosition(10))).resolves.toBe(false);
    expect(bookMarkService.createBookmark).not.toHaveBeenCalled();
    expect(messageService.add).not.toHaveBeenCalled();
  });

  it('creates a bookmark at the current position and shows a success toast', async () => {
    bookMarkService.createBookmark.mockReturnValue(of({id: 1}));
    service.updateCurrentPosition('epubcfi(/6/2)', 'Chapter One');

    await expect(firstValueFrom(service.createBookmarkAtCurrentPosition(10))).resolves.toBe(true);

    expect(bookMarkService.createBookmark).toHaveBeenCalledWith({
      bookId: 10,
      cfi: 'epubcfi(/6/2)',
      title: 'Chapter One'
    });
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'readerEbook.toast.bookmarkAddedSummary',
      detail: 'readerEbook.toast.bookmarkAddedDetail'
    });
  });

  it('falls back to a default bookmark title when the chapter name is missing', async () => {
    bookMarkService.createBookmark.mockReturnValue(of({id: 1}));
    service.updateCurrentPosition('epubcfi(/6/4)');

    await firstValueFrom(service.createBookmarkAtCurrentPosition(22));

    expect(bookMarkService.createBookmark).toHaveBeenCalledWith({
      bookId: 22,
      cfi: 'epubcfi(/6/4)',
      title: 'Bookmark'
    });
  });

  it('shows a warning toast when the bookmark already exists', async () => {
    bookMarkService.createBookmark.mockReturnValue(throwError(() => ({status: 409})));
    service.updateCurrentPosition('epubcfi(/6/6)', 'Chapter Two');

    await expect(firstValueFrom(service.createBookmarkAtCurrentPosition(33))).resolves.toBe(false);

    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'warn',
      summary: 'readerEbook.toast.bookmarkExistsSummary',
      detail: 'readerEbook.toast.bookmarkExistsDetail'
    });
  });

  it('shows an error toast for non-duplicate bookmark failures and reset clears the saved position', async () => {
    bookMarkService.createBookmark.mockReturnValue(throwError(() => ({status: 500})));
    service.updateCurrentPosition('epubcfi(/6/8)', 'Chapter Three');

    await expect(firstValueFrom(service.createBookmarkAtCurrentPosition(44))).resolves.toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'readerEbook.toast.bookmarkFailedSummary',
      detail: 'readerEbook.toast.bookmarkFailedDetail'
    });

    service.reset();

    await expect(firstValueFrom(service.createBookmarkAtCurrentPosition(44))).resolves.toBe(false);
    expect(bookMarkService.createBookmark).toHaveBeenCalledTimes(1);
  });
});
