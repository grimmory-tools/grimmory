import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {beforeEach, describe, expect, it, vi} from 'vitest';

import {UserService} from '../../settings/user-management/user.service';
import {BookDialogHelperService} from './book-dialog-helper.service';
import {BookNavigationService} from './book-navigation.service';

const navigate = vi.fn(() => Promise.resolve(true));

function createService(): BookNavigationService {
  TestBed.configureTestingModule({
    providers: [
      {provide: Router, useValue: {navigate}},
      {provide: UserService, useValue: {currentUser: () => null}},
      {provide: BookDialogHelperService, useValue: {openBookDetailsDialog: () => Promise.resolve()}},
    ],
  });
  return TestBed.inject(BookNavigationService);
}

describe('BookNavigationService', () => {
  beforeEach(() => navigate.mockClear());

  it('tracks navigation context and derived positions', () => {
    const service = createService();

    expect(service.navigationState()).toBeNull();
    expect(service.canNavigatePrevious()).toBe(false);
    expect(service.canNavigateNext()).toBe(false);
    expect(service.previousBookId()).toBeNull();
    expect(service.nextBookId()).toBeNull();
    expect(service.currentPosition()).toBeNull();

    service.setAvailableBookIds([1, 2, 3]);
    expect(service.availableBookIds()).toEqual([1, 2, 3]);

    service.setNavigationContext([1, 2, 3], 2);

    expect(service.navigationState()).toEqual({bookIds: [1, 2, 3], currentIndex: 1});
    expect(service.canNavigatePrevious()).toBe(true);
    expect(service.canNavigateNext()).toBe(true);
    expect(service.previousBookId()).toBe(1);
    expect(service.nextBookId()).toBe(3);
    expect(service.currentPosition()).toEqual({current: 2, total: 3});

    service.updateCurrentBook(3);

    expect(service.navigationState()).toEqual({bookIds: [1, 2, 3], currentIndex: 2});
    expect(service.canNavigatePrevious()).toBe(true);
    expect(service.canNavigateNext()).toBe(false);
    expect(service.previousBookId()).toBe(2);
    expect(service.nextBookId()).toBeNull();
    expect(service.currentPosition()).toEqual({current: 3, total: 3});
  });

  it('clears invalid navigation context and ignores unknown book ids', () => {
    const service = createService();

    service.setNavigationContext([1, 2, 3], 99);
    expect(service.navigationState()).toBeNull();

    service.updateCurrentBook(2);
    expect(service.navigationState()).toBeNull();

    service.setNavigationContext([1, 2, 3], 1);
    service.updateCurrentBook(99);

    expect(service.navigationState()).toEqual({bookIds: [1, 2, 3], currentIndex: 0});
  });

  it.each([
    ['PDF', '/pdf-reader/book/7'],
    ['EPUB', '/ebook-reader/book/7'],
    ['FB2', '/ebook-reader/book/7'],
    ['MOBI', '/ebook-reader/book/7'],
    ['AZW3', '/ebook-reader/book/7'],
    ['CBX', '/cbx-reader/book/7'],
    ['AUDIOBOOK', '/audiobook-player/book/7'],
  ] as const)('opens %s books in the matching reader', (bookType, route) => {
    const service = createService();

    service.readBook({
      id: 7,
      libraryId: 1,
      libraryName: 'Library',
      primaryFile: {id: 70, bookId: 7, book: true, folderBased: false, bookType},
    });

    expect(navigate).toHaveBeenCalledWith([route]);
  });
});
