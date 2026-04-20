import {Component, DestroyRef, effect, inject, Input, OnChanges, OnInit, signal, SimpleChanges} from '@angular/core';

import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';

import {BookReview, BookReviewService} from './book-review-service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Rating} from 'primeng/rating';
import {Tag} from 'primeng/tag';
import {Button} from 'primeng/button';
import {ConfirmationService, MessageService} from 'primeng/api';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {UserService} from '../../../settings/user-management/user.service';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';
import {BookService} from '../../service/book.service';
import {BookMetadataManageService} from '../../service/book-metadata-manage.service';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';

@Component({
  selector: 'app-book-reviews',
  standalone: true,
  imports: [ProgressSpinner, Rating, Tag, Button, FormsModule, Tooltip, TranslocoDirective],
  templateUrl: './book-reviews.component.html',
  styleUrl: './book-reviews.component.scss'
})
export class BookReviewsComponent implements OnInit, OnChanges {
  @Input() bookId!: number;
  @Input() reviews: BookReview[] | undefined = [];
  @Input() active: boolean = false;

  private reviewService = inject(BookReviewService);
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private userService = inject(UserService);
  private appSettingsService = inject(AppSettingsService);
  private destroyRef = inject(DestroyRef);
  private readonly t = inject(TranslocoService);
  private bookIdState = signal<number | null>(null);
  private loadingBookId: number | null = null;


  loading = signal(false);
  hasPermission = false;
  revealedSpoilers = new Set<number>();
  sortAscending = false;
  reviewsLocked = false;
  allSpoilersRevealed = false;
  reviewDownloadEnabled = true;

  constructor() {
    effect(() => {
      const bookId = this.bookIdState();
      if (!bookId) {
        this.reviewsLocked = false;
        return;
      }

      const book = this.bookService.findBookById(bookId);
      this.reviewsLocked = book?.metadata?.reviewsLocked ?? false;
    });
  }

  ngOnInit(): void {
    this.checkUserPermissions();
    this.subscribeToAppSettings();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['bookId'] && changes['bookId'].currentValue) {
      this.bookIdState.set(changes['bookId'].currentValue);
      this.loadReviews();
    }
  }

  private loadReviews(): void {
    const requestedBookId = this.bookId;
    if (!requestedBookId || this.loadingBookId === requestedBookId) {
      return;
    }

    this.loadingBookId = requestedBookId;
    this.loading.set(true);
    this.reviewService.getByBookId(requestedBookId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.loadingBookId === requestedBookId) {
            this.loadingBookId = null;
            this.loading.set(false);
          }
        })
      )
      .subscribe({
        next: (reviews) => {
          if (this.bookId !== requestedBookId) return;
          this.reviews = this.sortReviewsByDate(reviews || []);
        },
        error: (error) => {
          if (this.bookId !== requestedBookId) return;
          console.error('Failed to load reviews for bookId', requestedBookId, ':', error);
          this.reviews = [];

          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('book.reviews.toast.loadFailedSummary'),
            detail: this.t.translate('book.reviews.toast.loadFailedDetail'),
            life: 3000
          });
        }
      });
  }


  fetchNewReviews(): void {
    const requestedBookId = this.bookId;
    if (!requestedBookId || this.loadingBookId === requestedBookId || this.loading() || this.reviewsLocked) return;

    this.loadingBookId = requestedBookId;
    this.loading.set(true);
    this.revealedSpoilers.clear();

    this.reviewService.refreshReviews(requestedBookId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.loadingBookId === requestedBookId) {
            this.loadingBookId = null;
            this.loading.set(false);
          }
        })
      )
      .subscribe({
        next: (reviews) => {
          if (this.bookId !== requestedBookId) return;
          this.reviews = this.sortReviewsByDate(reviews || []);
          this.updateSpoilerState();
          this.messageService.add({
            severity: 'success',
            summary: this.t.translate('book.reviews.toast.reviewsUpdatedSummary'),
            detail: this.t.translate('book.reviews.toast.reviewsUpdatedDetail'),
            life: 3000
          });
        },
        error: (error) => {
          if (this.bookId !== requestedBookId) return;
          console.error('Failed to fetch new reviews:', error);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('book.reviews.toast.fetchFailedSummary'),
            detail: this.t.translate('book.reviews.toast.fetchFailedDetail'),
            life: 3000
          });
        }
      });
  }



  deleteAllReviews(): void {
    if (!this.reviews || this.reviews.length === 0 || this.reviewsLocked) return;

    this.confirmationService.confirm({
      message: this.t.translate('book.reviews.confirm.deleteAllMessage', {count: this.reviews.length}),
      header: this.t.translate('book.reviews.confirm.deleteAllHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        if (!this.bookId) return;

        this.reviewService.deleteAllByBookId(this.bookId).subscribe({
          next: () => {
            this.reviews = [];
            this.revealedSpoilers.clear();
            this.allSpoilersRevealed = false;
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.reviews.toast.allDeletedSummary'),
              detail: this.t.translate('book.reviews.toast.allDeletedDetail'),
              life: 3000
            });
          },
          error: (error) => {
            console.error('Failed to delete all reviews:', error);
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.reviews.toast.deleteAllFailedSummary'),
              detail: this.t.translate('book.reviews.toast.deleteAllFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  toggleSpoilerVisibility(): void {
    if (this.allSpoilersRevealed) {
      this.hideAllSpoilers();
    } else {
      this.revealAllSpoilers();
    }
  }

  hideAllSpoilers(): void {
    this.revealedSpoilers.clear();
    this.allSpoilersRevealed = false;
  }

  revealAllSpoilers(): void {
    if (this.reviews) {
      this.reviews.forEach(review => {
        if (review.spoiler && review.id) {
          this.revealedSpoilers.add(review.id);
        }
      });
      this.allSpoilersRevealed = true;
    }
  }

  toggleReviewsLock(): void {
    if (!this.bookId) return;

    const newLockState = !this.reviewsLocked;
    const fieldActions: Record<string, 'LOCK' | 'UNLOCK'> = {
      'reviewsLocked': newLockState ? 'LOCK' : 'UNLOCK'
    };

    this.bookMetadataManageService.toggleFieldLocks([this.bookId], fieldActions)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'info',
            summary: this.t.translate(newLockState ? 'book.reviews.toast.lockedSummary' : 'book.reviews.toast.unlockedSummary'),
            detail: this.t.translate(newLockState ? 'book.reviews.toast.lockedDetail' : 'book.reviews.toast.unlockedDetail'),
            life: 3000
          });
        },
        error: (error) => {
          console.error('Failed to toggle lock status:', error);
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('book.reviews.toast.lockFailedSummary'),
            detail: this.t.translate('book.reviews.toast.lockFailedDetail'),
            life: 3000
          });
        }
      });
  }

  toggleSortOrder(): void {
    this.sortAscending = !this.sortAscending;
    if (this.reviews) {
      this.reviews = this.sortReviewsByDate([...this.reviews]);
    }
  }

  private sortReviewsByDate(reviews: BookReview[]): BookReview[] {
    return reviews.sort((a, b) => {
      if (!a.date && !b.date) return 0;
      if (!a.date) return this.sortAscending ? -1 : 1;
      if (!b.date) return this.sortAscending ? 1 : -1;

      const dateA = new Date(a.date).getTime();
      const dateB = new Date(b.date).getTime();

      return this.sortAscending ? dateA - dateB : dateB - dateA;
    });
  }

  private checkUserPermissions(): void {
    const user = this.userService.currentUser();
    this.hasPermission = user?.permissions?.admin ||
      user?.permissions?.canEditMetadata || false;
  }

  deleteReview(review: BookReview): void {
    if (!review.id || this.reviewsLocked) return;

    this.confirmationService.confirm({
      message: this.t.translate('book.reviews.confirm.deleteMessage', {reviewer: review.reviewerName || 'Anonymous'}),
      header: this.t.translate('book.reviews.confirm.deleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptIcon: 'pi pi-trash',
      rejectIcon: 'pi pi-times',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.reviewService.delete(review.id!).subscribe({
          next: () => {
            this.reviews = this.reviews?.filter(r => r.id !== review.id);
            this.updateSpoilerState();
            this.messageService.add({
              severity: 'success',
              summary: this.t.translate('book.reviews.toast.deleteSuccessSummary'),
              detail: this.t.translate('book.reviews.toast.deleteSuccessDetail'),
              life: 2000
            });
          },
          error: (error) => {
            console.error('Failed to delete review:', error);
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.reviews.toast.deleteFailedSummary'),
              detail: this.t.translate('book.reviews.toast.deleteFailedDetail'),
              life: 3000
            });
          }
        });
      }
    });
  }

  revealSpoiler(reviewId: number): void {
    if (reviewId) {
      this.revealedSpoilers.add(reviewId);
      this.updateSpoilerState();
    }
  }

  private updateSpoilerState(): void {
    const spoilerReviews = this.reviews?.filter(r => r.spoiler) || [];
    const revealedSpoilerReviews = spoilerReviews.filter(r => r.id && this.revealedSpoilers.has(r.id));
    this.allSpoilersRevealed = spoilerReviews.length > 0 && spoilerReviews.length === revealedSpoilerReviews.length;
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }

  isSpoilerRevealed(reviewId: number): boolean {
    return this.revealedSpoilers.has(reviewId);
  }

  private subscribeToAppSettings(): void {
    const settings = this.appSettingsService.appSettings();
    this.reviewDownloadEnabled = settings?.metadataPublicReviewsSettings?.downloadEnabled ?? true;
  }

  getProviderSeverity(provider: string): 'success' | 'warn' {
    switch (provider?.toLowerCase()) {
      case 'amazon':
        return 'warn';
      default:
        return 'success';
    }
  }
}
