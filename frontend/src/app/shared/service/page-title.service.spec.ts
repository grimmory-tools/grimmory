import {TestBed} from '@angular/core/testing';
import {Title} from '@angular/platform-browser';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {Book} from '../../features/book/model/book.model';
import {PageTitleService} from './page-title.service';

describe('PageTitleService', () => {
  const titleService = {
    setTitle: vi.fn(),
  };

  let service: PageTitleService;

  beforeEach(() => {
    vi.restoreAllMocks();
    titleService.setTitle.mockClear();

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        PageTitleService,
        {provide: Title, useValue: titleService},
      ]
    });

    service = TestBed.inject(PageTitleService);
  });

  it('appends the application name to a page title', () => {
    service.setPageTitle('Dashboard');

    expect(titleService.setTitle).toHaveBeenCalledWith('Dashboard - Grimmory');
  });

  it('builds a book page title from the available book metadata', () => {
    const book = {
      libraryName: 'Central',
      fileName: 'fallback.epub',
      metadata: {
        title: 'Dune',
        seriesName: 'Chronicles',
        authors: ['Frank Herbert', 'Brian Herbert'],
      },
      primaryFile: {
        bookType: 'EPUB',
      },
    } as Book;

    service.setBookPageTitle(book);

    expect(titleService.setTitle).toHaveBeenCalledWith(
      'Central/Dune (Chronicles series) - by Frank Herbert and Brian Herbert (EPUB) - Grimmory'
    );
  });

  it('falls back to the filename when the metadata title is missing', () => {
    const book = {
      libraryName: 'Central',
      fileName: 'fallback.epub',
      metadata: {},
      primaryFile: {
        bookType: 'PDF',
      },
    } as Book;

    service.setBookPageTitle(book);

    expect(titleService.setTitle).toHaveBeenCalledWith('Central/fallback.epub (PDF) - Grimmory');
  });
});
