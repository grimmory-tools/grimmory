import { inject, Injectable } from '@angular/core';
import { LocalSettingsService } from './local-settings.service';
import { OfflineStorageService } from './offline-storage.service';
import { OfflineIndexedDBService } from './offline-indexeddb.service';

const STORAGE_KEY = 'recently_read_books';

interface RecentlyReadItem {
  bookId: number;
  lastReadAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class RecentlyReadService {
  private localSettingsService = inject(LocalSettingsService);
  private offlineStorage = inject(OfflineStorageService);
  private indexedDB = inject(OfflineIndexedDBService);

  getMaxBooks(): number {
    return this.localSettingsService.get().maxOfflineBooks;
  }

  getRecentlyReadIds(): number[] {
    const list = this.getList();
    return list.map((item) => item.bookId);
  }

  recordBookOpened(
    bookId: number,
    meta?: { title?: string; author?: string; fileType?: string }
  ): void {
    const settings = this.localSettingsService.get();
    const list = this.getList();

    // Remove existing entry for this bookId
    const filtered = list.filter((item) => item.bookId !== bookId);

    // Insert at front
    filtered.unshift({ bookId, lastReadAt: new Date().toISOString() });

    const maxBooks = settings.maxOfflineBooks;

    // Trim and evict
    if (filtered.length > maxBooks) {
      const evicted = filtered.splice(maxBooks);
      if (settings.offlineReadingEnabled) {
        for (const evict of evicted) {
          this.offlineStorage.deleteBookFiles(evict.bookId).catch(() => undefined);
          this.indexedDB.deleteRecentRead(evict.bookId).catch(() => undefined);
        }
      }
    }

    this.saveList(filtered);

    // Persist metadata to IndexedDB
    if (meta && settings.offlineReadingEnabled) {
      this.indexedDB
        .upsertRecentRead({
          bookId,
          title: meta.title ?? String(bookId),
          author: meta.author ?? '',
          fileType: meta.fileType ?? 'EPUB',
          lastReadAt: new Date().toISOString(),
        })
          .catch(() => undefined);
    }
  }

  private getList(): RecentlyReadItem[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) return JSON.parse(raw) as RecentlyReadItem[];
    } catch {
      // ignore
    }
    return [];
  }

  private saveList(list: RecentlyReadItem[]): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  }
}
