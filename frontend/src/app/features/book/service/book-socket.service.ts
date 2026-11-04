import {DestroyRef, inject, Service} from '@angular/core';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {invalidateBookRecommendations} from '../data/book-query-cache';
import {TaskProgressPayload, TaskStatus} from '../../settings/task-management/task.service';
import {Book} from '../model/book.model';
import {
  BookCoverPatch,
  invalidateAllBookCaches,
  invalidateBooksById,
  invalidateLegacyBookRecommendations,
  patchBookCoversInCache,
  patchListOnlyBooks,
  reconcileBookCacheChangeSet,
  upsertListOnlyBooks,
} from './legacy-book-cache';

function isBookIdArray(payload: Book | readonly number[]): payload is readonly number[] {
  return Array.isArray(payload);
}
@Service()
export class BookSocketService {
  private readonly queryClient = inject(QueryClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reconciliationDelayMs = 1000;
  private readonly pendingPatchedBookIds = new Set<number>();
  private readonly pendingChangedBookIds = new Set<number>();
  private readonly pendingDeletedBookIds = new Set<number>();
  private reconciliationTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearPendingReconciliation());
  }

  handleNewlyCreatedBook(book: Book): void {
    upsertListOnlyBooks(this.queryClient, [book]);
    this.queuePatchedBooks([book.id]);
  }

  handleRemovedBookIds(bookIds: readonly number[]): void {
    this.queueDeletedBooks(bookIds);
  }

  handleBookUpdate(payload: Book | readonly number[]): void {
    if (isBookIdArray(payload)) {
      this.queueKnownChanges(payload);
      return;
    }
    patchListOnlyBooks(this.queryClient, [payload]);
    this.queuePatchedBooks([payload.id]);
  }

  handleBookMetadataUpdate(bookId: number): void {
    invalidateBooksById(this.queryClient, [bookId]);
  }

  handleMultipleBookCoverPatches(patches: readonly BookCoverPatch[]): void {
    patchBookCoversInCache(this.queryClient, patches);
    this.queuePatchedBooks(patches.map(patch => patch.id));
  }

  handleTaskProgress(payload: TaskProgressPayload): void {
    if (payload.taskType !== 'UPDATE_BOOK_RECOMMENDATIONS' || payload.taskStatus !== TaskStatus.COMPLETED) {
      return;
    }
    void Promise.all([
      invalidateBookRecommendations(this.queryClient),
      invalidateLegacyBookRecommendations(this.queryClient),
    ]);
  }

  handleReconnect(): void {
    this.clearPendingReconciliation();
    invalidateAllBookCaches(this.queryClient);
  }

  private queuePatchedBooks(bookIds: readonly number[]): void {
    for (const bookId of bookIds) {
      if (!this.pendingDeletedBookIds.has(bookId)) {
        this.pendingPatchedBookIds.add(bookId);
      }
    }
    this.scheduleReconciliation();
  }

  private queueKnownChanges(bookIds: readonly number[]): void {
    for (const bookId of bookIds) {
      if (!this.pendingDeletedBookIds.has(bookId)) {
        this.pendingChangedBookIds.add(bookId);
      }
    }
    this.scheduleReconciliation();
  }

  private queueDeletedBooks(bookIds: readonly number[]): void {
    for (const bookId of bookIds) {
      this.pendingPatchedBookIds.delete(bookId);
      this.pendingChangedBookIds.delete(bookId);
      this.pendingDeletedBookIds.add(bookId);
    }
    this.scheduleReconciliation();
  }

  private scheduleReconciliation(): void {
    if (this.reconciliationTimer !== null) {
      return;
    }

    this.reconciliationTimer = setTimeout(() => this.flushPendingReconciliation(), this.reconciliationDelayMs);
  }

  private flushPendingReconciliation(): void {
    this.reconciliationTimer = null;
    const legacyListIsCurrent = this.pendingChangedBookIds.size === 0 && this.pendingDeletedBookIds.size === 0;
    const changedBookIds = [...this.pendingPatchedBookIds, ...this.pendingChangedBookIds];
    const deletedBookIds = [...this.pendingDeletedBookIds];
    this.pendingPatchedBookIds.clear();
    this.pendingChangedBookIds.clear();
    this.pendingDeletedBookIds.clear();

    void reconcileBookCacheChangeSet(
      this.queryClient,
      {changedBookIds, deletedBookIds},
      {legacyList: legacyListIsCurrent ? 'already-updated' : 'needs-refetch'},
    );
  }

  private clearPendingReconciliation(): void {
    if (this.reconciliationTimer !== null) {
      clearTimeout(this.reconciliationTimer);
      this.reconciliationTimer = null;
    }
    this.pendingPatchedBookIds.clear();
    this.pendingChangedBookIds.clear();
    this.pendingDeletedBookIds.clear();
  }
}
