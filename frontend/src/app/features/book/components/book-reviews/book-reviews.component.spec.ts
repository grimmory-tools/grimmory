import {SimpleChange} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';

import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {UserService} from '../../../settings/user-management/user.service';
import {BookReview, BookReviewService} from './book-review-service';
import {BookReviewsComponent} from './book-reviews.component';

interface ConfirmationLike {
  message?: string;
  header?: string;
  accept?: () => void;
}

function createReview(id: number, overrides: Partial<BookReview> = {}): BookReview {
  return {
    id,
    metadataProvider: 'goodreads',
    reviewerName: `Reviewer ${id}`,
    title: `Review ${id}`,
    rating: 4,
    date: `2026-03-${String(id).padStart(2, '0')}T12:00:00.000Z`,
    body: `Review body ${id}`,
    country: 'US',
    spoiler: false,
    followersCount: 10,
    textReviewsCount: 20,
    ...overrides,
  };
}

describe('BookReviewsComponent', () => {
  let component: BookReviewsComponent;
  let reviewService: {
    getByBookId: ReturnType<typeof vi.fn>;
    refreshReviews: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    deleteAllByBookId: ReturnType<typeof vi.fn>;
  };
  let bookService: {
    findBookById: ReturnType<typeof vi.fn>;
  };
  let bookMetadataManageService: {
    toggleFieldLocks: ReturnType<typeof vi.fn>;
  };
  let confirmationService: {
    confirm: ReturnType<typeof vi.fn>;
  };
  let messageService: {
    add: ReturnType<typeof vi.fn>;
  };
  let userService: {
    currentUser: ReturnType<typeof vi.fn>;
  };
  let appSettingsService: {
    appSettings: ReturnType<typeof vi.fn>;
  };
  let translate: ReturnType<typeof vi.fn>;
  let lastConfirmation: ConfirmationLike | null;

  function createComponent(): BookReviewsComponent {
    return TestBed.runInInjectionContext(() => new BookReviewsComponent());
  }

  beforeEach(() => {
    lastConfirmation = null;
    reviewService = {
      getByBookId: vi.fn(),
      refreshReviews: vi.fn(),
      delete: vi.fn(),
      deleteAllByBookId: vi.fn(),
    };
    bookService = {
      findBookById: vi.fn(),
    };
    bookMetadataManageService = {
      toggleFieldLocks: vi.fn(),
    };
    confirmationService = {
      confirm: vi.fn((confirmation: ConfirmationLike) => {
        lastConfirmation = confirmation;
      }),
    };
    messageService = {
      add: vi.fn(),
    };
    userService = {
      currentUser: vi.fn(() => ({
        permissions: {
          admin: false,
          canEditMetadata: true,
        },
      })),
    };
    appSettingsService = {
      appSettings: vi.fn(() => ({
        metadataPublicReviewsSettings: {
          downloadEnabled: false,
        },
      })),
    };
    translate = vi.fn((key: string, params?: Record<string, unknown>) => (
      params ? `${key}:${JSON.stringify(params)}` : key
    ));

    TestBed.configureTestingModule({
      providers: [
        {provide: BookReviewService, useValue: reviewService},
        {provide: BookService, useValue: bookService},
        {provide: BookMetadataManageService, useValue: bookMetadataManageService},
        {provide: ConfirmationService, useValue: confirmationService},
        {provide: MessageService, useValue: messageService},
        {provide: UserService, useValue: userService},
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: TranslocoService, useValue: {translate}},
      ],
    });

    component = createComponent();
    component.bookId = 42;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads reviews on book changes, sorts them by date, and mirrors the current lock state', () => {
    const undated = createReview(1, {date: undefined});
    const oldest = createReview(2, {date: '2026-03-02T12:00:00.000Z'});
    const newest = createReview(3, {date: '2026-03-03T12:00:00.000Z'});
    reviewService.getByBookId.mockReturnValue(of([oldest, undated, newest]));
    bookService.findBookById.mockReturnValue({
      metadata: {
        reviewsLocked: true,
      },
    });

    component.ngOnChanges({
      bookId: new SimpleChange(undefined, 42, true),
    });
    TestBed.flushEffects();

    expect(bookService.findBookById).toHaveBeenCalledWith(42);
    expect(reviewService.getByBookId).toHaveBeenCalledWith(42);
    expect(component.reviews?.map(review => review.id)).toEqual([3, 2, 1]);
    expect(component.reviewsLocked).toBe(true);
    expect(component.loading).toBe(false);
  });

  it('refreshes reviews, clears revealed spoilers, and shows a success toast', () => {
    const spoilerReview = createReview(5, {spoiler: true, date: '2026-03-05T12:00:00.000Z'});
    const plainReview = createReview(4, {date: '2026-03-04T12:00:00.000Z'});
    reviewService.refreshReviews.mockReturnValue(of([plainReview, spoilerReview]));
    component.revealedSpoilers.add(spoilerReview.id!);
    component.allSpoilersRevealed = true;

    component.fetchNewReviews();

    expect(reviewService.refreshReviews).toHaveBeenCalledWith(42);
    expect(component.reviews?.map(review => review.id)).toEqual([5, 4]);
    expect(component.revealedSpoilers.size).toBe(0);
    expect(component.allSpoilersRevealed).toBe(false);
    expect(component.loading).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.reviews.toast.reviewsUpdatedSummary',
      detail: 'book.reviews.toast.reviewsUpdatedDetail',
      life: 3000,
    });
  });

  it('shows an error toast when refreshing reviews fails', () => {
    const error = new Error('refresh failed');
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    reviewService.refreshReviews.mockReturnValue(throwError(() => error));

    component.fetchNewReviews();

    expect(errorSpy).toHaveBeenCalledWith('Failed to fetch new reviews:', error);
    expect(component.loading).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'book.reviews.toast.fetchFailedSummary',
      detail: 'book.reviews.toast.fetchFailedDetail',
      life: 3000,
    });
  });

  it('confirms single-review deletion, removes the review, and recomputes spoiler state', () => {
    const keepReview = createReview(1, {spoiler: false});
    const deleteReview = createReview(2, {spoiler: true});
    reviewService.delete.mockReturnValue(of(void 0));
    component.reviews = [keepReview, deleteReview];
    component.revealedSpoilers.add(deleteReview.id!);
    component.allSpoilersRevealed = true;

    component.deleteReview(deleteReview);
    lastConfirmation?.accept?.();

    expect(confirmationService.confirm).toHaveBeenCalledOnce();
    expect(lastConfirmation).toMatchObject({
      message: 'book.reviews.confirm.deleteMessage:{"reviewer":"Reviewer 2"}',
      header: 'book.reviews.confirm.deleteHeader',
    });
    expect(reviewService.delete).toHaveBeenCalledWith(2);
    expect(component.reviews).toEqual([keepReview]);
    expect(component.allSpoilersRevealed).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.reviews.toast.deleteSuccessSummary',
      detail: 'book.reviews.toast.deleteSuccessDetail',
      life: 2000,
    });
  });

  it('confirms deleting all reviews and clears state after the accept callback succeeds', () => {
    const firstReview = createReview(7, {spoiler: true});
    const secondReview = createReview(8, {spoiler: false});
    reviewService.deleteAllByBookId.mockReturnValue(of(void 0));
    component.reviews = [firstReview, secondReview];
    component.revealedSpoilers.add(firstReview.id!);
    component.allSpoilersRevealed = true;

    component.deleteAllReviews();
    lastConfirmation?.accept?.();

    expect(confirmationService.confirm).toHaveBeenCalledOnce();
    expect(lastConfirmation).toMatchObject({
      message: 'book.reviews.confirm.deleteAllMessage:{"count":2}',
      header: 'book.reviews.confirm.deleteAllHeader',
    });
    expect(reviewService.deleteAllByBookId).toHaveBeenCalledWith(42);
    expect(component.reviews).toEqual([]);
    expect(component.revealedSpoilers.size).toBe(0);
    expect(component.allSpoilersRevealed).toBe(false);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'book.reviews.toast.allDeletedSummary',
      detail: 'book.reviews.toast.allDeletedDetail',
      life: 3000,
    });
  });

  it('sends lock and unlock actions through the metadata service and reports the result', () => {
    bookMetadataManageService.toggleFieldLocks.mockReturnValue(of(void 0));

    component.reviewsLocked = false;
    component.toggleReviewsLock();

    component.reviewsLocked = true;
    component.toggleReviewsLock();

    expect(bookMetadataManageService.toggleFieldLocks).toHaveBeenNthCalledWith(1, [42], {
      reviewsLocked: 'LOCK',
    });
    expect(bookMetadataManageService.toggleFieldLocks).toHaveBeenNthCalledWith(2, [42], {
      reviewsLocked: 'UNLOCK',
    });
    expect(messageService.add).toHaveBeenNthCalledWith(1, {
      severity: 'info',
      summary: 'book.reviews.toast.lockedSummary',
      detail: 'book.reviews.toast.lockedDetail',
      life: 3000,
    });
    expect(messageService.add).toHaveBeenNthCalledWith(2, {
      severity: 'info',
      summary: 'book.reviews.toast.unlockedSummary',
      detail: 'book.reviews.toast.unlockedDetail',
      life: 3000,
    });
  });

  it('shows an error toast when lock toggling fails', () => {
    const error = new Error('toggle failed');
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    bookMetadataManageService.toggleFieldLocks.mockReturnValue(throwError(() => error));

    component.toggleReviewsLock();

    expect(bookMetadataManageService.toggleFieldLocks).toHaveBeenCalledWith([42], {
      reviewsLocked: 'LOCK',
    });
    expect(errorSpy).toHaveBeenCalledWith('Failed to toggle lock status:', error);
    expect(messageService.add).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'book.reviews.toast.lockFailedSummary',
      detail: 'book.reviews.toast.lockFailedDetail',
      life: 3000,
    });
  });

  it('tracks spoiler visibility for individual and bulk reveal flows', () => {
    const firstSpoiler = createReview(9, {spoiler: true});
    const secondSpoiler = createReview(10, {spoiler: true});
    const nonSpoiler = createReview(11, {spoiler: false});
    component.reviews = [firstSpoiler, secondSpoiler, nonSpoiler];

    component.revealSpoiler(firstSpoiler.id!);
    expect(component.isSpoilerRevealed(firstSpoiler.id!)).toBe(true);
    expect(component.allSpoilersRevealed).toBe(false);

    component.toggleSpoilerVisibility();
    expect(component.isSpoilerRevealed(secondSpoiler.id!)).toBe(true);
    expect(component.allSpoilersRevealed).toBe(true);

    component.toggleSpoilerVisibility();
    expect(component.revealedSpoilers.size).toBe(0);
    expect(component.allSpoilersRevealed).toBe(false);
  });

  it('toggles sort order and reorders dated and undated reviews accordingly', () => {
    const newest = createReview(12, {date: '2026-03-12T12:00:00.000Z'});
    const oldest = createReview(13, {date: '2026-03-01T12:00:00.000Z'});
    const undated = createReview(14, {date: undefined});
    component.reviews = [newest, undated, oldest];

    component.toggleSortOrder();
    expect(component.sortAscending).toBe(true);
    expect(component.reviews?.map(review => review.id)).toEqual([14, 13, 12]);

    component.toggleSortOrder();
    expect(component.sortAscending).toBe(false);
    expect(component.reviews?.map(review => review.id)).toEqual([12, 13, 14]);
  });

  it('derives permission and app-setting state and exposes helper formatting behavior', () => {
    component.ngOnInit();

    expect(component.hasPermission).toBe(true);
    expect(component.reviewDownloadEnabled).toBe(false);
    expect(component.getProviderSeverity('amazon')).toBe('warn');
    expect(component.getProviderSeverity('goodreads')).toBe('success');
    expect(component.formatDate('2026-03-28T12:00:00.000Z')).toBe('March 28, 2026');
  });
});
