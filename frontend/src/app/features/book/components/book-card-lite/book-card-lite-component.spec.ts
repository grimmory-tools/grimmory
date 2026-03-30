import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BookMetadataHostService} from '../../../../shared/service/book-metadata-host.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {Book} from '../../model/book.model';
import {BookCardLiteComponent} from './book-card-lite-component';
import {UserService} from '../../../settings/user-management/user.service';

describe('BookCardLiteComponent', () => {
  let fixture: ComponentFixture<BookCardLiteComponent>;
  let component: BookCardLiteComponent;
  let router: {navigate: ReturnType<typeof vi.fn>};
  let urlHelper: {
    getThumbnailUrl: ReturnType<typeof vi.fn>;
    getAudiobookThumbnailUrl: ReturnType<typeof vi.fn>;
  };
  let userService: {currentUser: ReturnType<typeof vi.fn>};
  let hostService: {requestBookSwitch: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    router = {navigate: vi.fn()};
    urlHelper = {
      getThumbnailUrl: vi.fn(() => '/covers/1'),
      getAudiobookThumbnailUrl: vi.fn(() => '/covers/audio/1'),
    };
    userService = {
      currentUser: vi.fn(() => ({userSettings: {metadataCenterViewMode: 'route'}})),
    };
    hostService = {
      requestBookSwitch: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [BookCardLiteComponent],
      providers: [
        {provide: Router, useValue: router},
        {provide: UrlHelperService, useValue: urlHelper},
        {provide: UserService, useValue: userService},
        {provide: BookMetadataHostService, useValue: hostService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BookCardLiteComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('treats a book as audiobook-only when no non-audiobook alternative format exists', () => {
    component.book = buildBook({
      primaryFile: {id: 10, bookId: 1, bookType: 'AUDIOBOOK'},
      alternativeFormats: [{id: 11, bookId: 1, bookType: 'AUDIOBOOK'}],
    });

    expect(component.isAudiobookOnly()).toBe(true);
    expect(component.getThumbnailUrl()).toBe('/covers/audio/1');
    expect(urlHelper.getAudiobookThumbnailUrl).toHaveBeenCalledWith(1, '2026-01-02');
  });

  it('uses the standard thumbnail when the book has an ebook format available', () => {
    component.book = buildBook({
      primaryFile: {id: 10, bookId: 1, bookType: 'AUDIOBOOK'},
      alternativeFormats: [{id: 11, bookId: 1, bookType: 'EPUB'}],
    });

    expect(component.isAudiobookOnly()).toBe(false);
    expect(component.getThumbnailUrl()).toBe('/covers/1');
    expect(urlHelper.getThumbnailUrl).toHaveBeenCalledWith(1, '2026-01-01');
  });

  it('navigates to the route-based metadata center when that mode is active', () => {
    const book = buildBook();

    component.openBookInfo(book);

    expect(router.navigate).toHaveBeenCalledWith(['/book', 1], {
      queryParams: {tab: 'view'}
    });
    expect(hostService.requestBookSwitch).not.toHaveBeenCalled();
  });

  it('delegates to the metadata host when the user prefers dialog mode', () => {
    userService.currentUser.mockReturnValue({userSettings: {metadataCenterViewMode: 'dialog'}});
    const book = buildBook();

    component.openBookInfo(book);

    expect(hostService.requestBookSwitch).toHaveBeenCalledWith(1);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});

function buildBook(overrides: Partial<Book> = {}): Book {
  return {
    id: 1,
    fileName: 'Example Book',
    metadata: {
      title: 'Example Book',
      coverUpdatedOn: '2026-01-01',
      audiobookCoverUpdatedOn: '2026-01-02',
      ...overrides.metadata,
    },
    primaryFile: {
      id: 10,
      bookType: 'EPUB',
      ...overrides.primaryFile,
    },
    alternativeFormats: overrides.alternativeFormats ?? [],
    ...overrides,
  } as Book;
}
