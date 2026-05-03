import { inject, Injectable } from '@angular/core';
import { OfflineIndexedDBService } from './offline-indexeddb.service';
import { BookPatchService } from '../../features/book/service/book-patch.service';

@Injectable({
  providedIn: 'root',
})
export class OfflineProgressQueueService {
  private indexedDB = inject(OfflineIndexedDBService);
  private bookPatchService = inject(BookPatchService);

  private syncDebounceTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    window.addEventListener('online', () => this.scheduleSync());
  }

  queue(
    bookId: number,
    progressType: 'epub' | 'pdf' | 'cbx',
    data: {
      cfi?: string;
      page?: number;
      percentage: number;
      href?: string;
      bookFileId?: number;
    }
  ): void {
    this.indexedDB
      .queueProgress({
        bookId,
        progressType,
        cfi: data.cfi,
        page: data.page,
        percentage: data.percentage,
        href: data.href,
        bookFileId: data.bookFileId,
        queuedAt: new Date().toISOString(),
        synced: 0,
      })
      .catch(() => undefined);
  }

  async syncAll(): Promise<{ synced: number; failed: number }> {
    const entries = await this.indexedDB.getUnsyncedProgress();
    let synced = 0;
    let failed = 0;

    for (const entry of entries) {
      try {
        if (entry.progressType === 'epub') {
          this.bookPatchService.saveEpubProgress(
            entry.bookId,
            entry.cfi ?? '',
            entry.href ?? '',
            entry.percentage,
            entry.bookFileId
          );
        } else if (entry.progressType === 'pdf') {
          await this.bookPatchService
            .savePdfProgress(entry.bookId, entry.page ?? 1, entry.percentage, entry.bookFileId)
            .toPromise();
        } else if (entry.progressType === 'cbx') {
          await this.bookPatchService
            .saveCbxProgress(entry.bookId, entry.page ?? 1, entry.percentage, entry.bookFileId)
            .toPromise();
        }

        if (entry.id !== undefined) {
          await this.indexedDB.markSynced(entry.id);
        }
        synced++;
      } catch {
        failed++;
      }
    }

    // Cleanup old synced entries
    try {
      await this.indexedDB.deleteSyncedOlderThan(7);
    } catch {
      // ignore
    }

    return { synced, failed };
  }

  private scheduleSync(): void {
    if (this.syncDebounceTimer) {
      clearTimeout(this.syncDebounceTimer);
    }
    this.syncDebounceTimer = setTimeout(() => {
      this.syncAll().catch(() => undefined);
    }, 5000);
  }

  isOnline(): boolean {
    return navigator.onLine;
  }
}
