import {TestBed} from '@angular/core/testing';
import {Router} from '@angular/router';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {Book, BookType} from '../../features/book/model/book.model';
import {AuthService} from './auth.service';
import {UrlHelperService} from './url-helper.service';

describe('UrlHelperService', () => {
  const authServiceStub = {
    getInternalAccessToken: vi.fn(),
  };

  const routerStub = {
    createUrlTree: vi.fn((commands: unknown[], extras?: unknown) => ({commands, extras})),
  };

  let service: UrlHelperService;

  const supportedReadingRoutes: {bookType: BookType; readerSegment: string}[] = [
    {bookType: 'PDF', readerSegment: 'pdf-reader'},
    {bookType: 'EPUB', readerSegment: 'ebook-reader'},
    {bookType: 'FB2', readerSegment: 'ebook-reader'},
    {bookType: 'MOBI', readerSegment: 'ebook-reader'},
    {bookType: 'AZW3', readerSegment: 'ebook-reader'},
    {bookType: 'CBX', readerSegment: 'cbx-reader'},
    {bookType: 'AUDIOBOOK', readerSegment: 'audiobook-player'},
  ];

  beforeEach(() => {
    authServiceStub.getInternalAccessToken.mockReturnValue('token-123');
    routerStub.createUrlTree.mockClear();

    TestBed.configureTestingModule({
      providers: [
        UrlHelperService,
        {provide: AuthService, useValue: authServiceStub},
        {provide: Router, useValue: routerStub},
      ],
    });

    service = TestBed.inject(UrlHelperService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('returns null for thumbnail when no coverUpdatedOn is provided', () => {
    expect(service.getThumbnailUrl(42)).toBeNull();
  });

  it('appends the token to thumbnail urls that already have a cache key', () => {
    const url = service.getThumbnailUrl(42, 'updated-2024');

    expect(url).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/thumbnail?updated-2024&token=token-123`);
  });

  it('returns null for cover url when no coverUpdatedOn is provided', () => {
    expect(service.getCoverUrl(42)).toBeNull();
  });

  it('returns null for audiobook cover when no updated marker is provided', () => {
    expect(service.getAudiobookCoverUrl(42)).toBeNull();
  });

  it('returns null for audiobook thumbnail when no updated marker is provided', () => {
    expect(service.getAudiobookThumbnailUrl(42)).toBeNull();
  });

  it('uses the audiobook media url when an updated marker is present', () => {
    expect(service.getAudiobookThumbnailUrl(42, 'updated-2024')).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/audiobook-thumbnail?updated-2024&token=token-123`);
  });

  it('returns direct thumbnail and backup urls without a token when auth is unavailable', () => {
    authServiceStub.getInternalAccessToken.mockReturnValue(null);

    expect(service.getDirectThumbnailUrl(42)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/thumbnail`);
    expect(service.getBackupCoverUrl(42)).toBe(`${API_CONFIG.BASE_URL}/api/v1/media/book/42/backup-cover`);
  });

  it.each(supportedReadingRoutes)('routes %s books to the correct reader path', ({bookType, readerSegment}: {bookType: BookType; readerSegment: string}) => {
    const book = {
      id: 77,
      primaryFile: {bookType},
    } as Book;

    expect(service.getBookPrimaryReadingUrl(book)).toEqual({
      commands: [`/${readerSegment}/book/77`],
      extras: undefined,
    });
  });

  it('falls back to the book page for unsupported primary file types', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const book = {
      id: 88,
      primaryFile: {bookType: undefined},
    } as Book;

    expect(service.getBookPrimaryReadingUrl(book)).toEqual({
      commands: ['/book', 88],
      extras: {queryParams: {tab: 'view'}},
    });
    expect(consoleErrorSpy).toHaveBeenCalledWith('Unsupported book type:', undefined);
  });

  it('builds series and generic book filters with the expected router shape', () => {
    expect(service.filterBooksBy('series', 'The Wheel of Time')).toEqual({
      commands: ['/series', 'The Wheel of Time'],
      extras: undefined,
    });

    expect(service.filterBooksBy('author', 'Ursula K. Le Guin')).toEqual({
      commands: ['/all-books'],
      extras: {
        queryParams: {
          view: 'grid',
          sort: 'title',
          direction: 'asc',
          sidebar: true,
          filter: 'author:Ursula%20K.%20Le%20Guin',
        },
      },
    });
  });
});
