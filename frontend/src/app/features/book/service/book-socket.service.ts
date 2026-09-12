import {DestroyRef, inject, Injectable} from '@angular/core';
import {QueryClient} from '@tanstack/angular-query-experimental';

import {invalidateBookRecommendations} from '../data/book-query-cache';
import {TaskProgressPayload} from '../../settings/task-management/task.service';
import {Book} from '../model/book.model';
import {
  BookCoverPatch,
  invalidateAllBookCaches,
  invalidateLegacyBookRecommendations,
  patchBooksInCache,
  patchBookCoversInCache,
  reconcileBookCacheChangeSet,
  upsertBooksInCache,
} from './legacy-book-cache';

function isBookIdArray(payload: Book | readonly number[]): payload is readonly number[] {
  return Array.isArray(payload);
}
@Injectable({providedIn: 'root'})
export class BookSocketService {
  private readonly queryClient = inject(QueryClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reconciliationDelayMs = 50;
  private readonly pendingChangedBookIds = new Set<number>();
  private readonly pendingDeletedBookIds = new Set<number>();
  private reconciliationTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearPendingReconciliation());
  }

  handleNewlyCreatedBook(book: Book): void {
    upsertBooksInCache(this.queryClient, [book]);
  }

  handleRemovedBookIds(bookIds: readonly number[]): void {
    this.queueDeletedBooks(bookIds);
  }

  handleBookUpdate(payload: Book | readonly number[]): void {
    if (isBookIdArray(payload)) {
      this.queueKnownChanges(payload);
      return;
    }
    patchBooksInCache(this.queryClient, [payload]);
  }

  handleBookMetadataUpdate(bookId: number): void {
    this.queueKnownChanges([bookId]);
  }

  handleMultipleBookCoverPatches(patches: readonly BookCoverPatch[]): void {
    patchBookCoversInCache(this.queryClient, patches);
  }

  handleTaskProgress(payload: TaskProgressPayload): void {
    if (payload.taskType !== 'UPDATE_BOOK_RECOMMENDATIONS' || payload.taskStatus !== 'COMPLETED') {
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
    const changedBookIds = [...this.pendingChangedBookIds];
    const deletedBookIds = [...this.pendingDeletedBookIds];
    this.pendingChangedBookIds.clear();
    this.pendingDeletedBookIds.clear();

    void reconcileBookCacheChangeSet(
      this.queryClient,
      {changedBookIds, deletedBookIds},
      {legacyList: 'needs-refetch'},
    );
  }

  private clearPendingReconciliation(): void {
    if (this.reconciliationTimer !== null) {
      clearTimeout(this.reconciliationTimer);
      this.reconciliationTimer = null;
    }
    this.pendingChangedBookIds.clear();
    this.pendingDeletedBookIds.clear();
  }
}
