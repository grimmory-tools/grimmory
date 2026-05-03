import { Injectable } from '@angular/core';

const DB_NAME = 'grimmory-offline';
const DB_VERSION = 1;

export interface RecentlyReadEntry {
  bookId: number;
  title: string;
  author: string;
  fileType: string;
  lastReadAt: string;
}

export interface QueuedProgress {
  id?: number;
  bookId: number;
  progressType: 'epub' | 'pdf' | 'cbx';
  cfi?: string;
  page?: number;
  percentage: number;
  href?: string;
  bookFileId?: number;
  queuedAt: string;
  synced: 0 | 1;
}

@Injectable({
  providedIn: 'root',
})
export class OfflineIndexedDBService {
  private db: IDBDatabase | null = null;
  private readyPromise: Promise<void>;

  constructor() {
    this.readyPromise = new Promise<void>((resolve, reject) => {
      if (typeof window === 'undefined' || !window.indexedDB) {
        resolve();
        return;
      }
      const request = window.indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = () => {
        const db = request.result;

        if (!db.objectStoreNames.contains('recently_read')) {
          db.createObjectStore('recently_read', { keyPath: 'bookId' });
        }

        if (!db.objectStoreNames.contains('progress_queue')) {
          const store = db.createObjectStore('progress_queue', { autoIncrement: true });
          store.createIndex('synced', 'synced', { unique: false });
          store.createIndex('bookId_type', ['bookId', 'progressType'], { unique: false });
        }

        if (!db.objectStoreNames.contains('settings')) {
          db.createObjectStore('settings', { keyPath: 'key' });
        }
      };

      request.onsuccess = () => {
        this.db = request.result;
        resolve();
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  ready(): Promise<void> {
    return this.readyPromise;
  }

  // -- recently_read store --

  getRecentlyRead(): Promise<RecentlyReadEntry[]> {
    return this.requestPromise('recently_read', 'readonly', (store) => store.getAll());
  }

  upsertRecentRead(entry: RecentlyReadEntry): Promise<void> {
    return this.requestPromise('recently_read', 'readwrite', (store) => store.put(entry)).then(() => undefined);
  }

  deleteRecentRead(bookId: number): Promise<void> {
    return this.requestPromise('recently_read', 'readwrite', (store) => store.delete(bookId)).then(() => undefined);
  }

  // -- progress_queue store --

  async queueProgress(entry: Omit<QueuedProgress, 'id'>): Promise<void> {
    await this.readyPromise;
    return new Promise<void>((resolve, reject) => {
      const tx = this.db!.transaction('progress_queue', 'readwrite');
      const store = tx.objectStore('progress_queue');

      const index = store.index('bookId_type');
      const getRequest = index.getAllKeys(IDBKeyRange.only([entry.bookId, entry.progressType]));

      getRequest.onsuccess = () => {
        const keys = getRequest.result;
        for (const key of keys) {
          store.delete(key);
        }
        store.add(entry);
      };

      getRequest.onerror = () => {
        store.add(entry);
      };

      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  getUnsyncedProgress(): Promise<QueuedProgress[]> {
    return this.readyPromise.then(() => {
      return new Promise<QueuedProgress[]>((resolve, reject) => {
        const tx = this.db!.transaction('progress_queue', 'readonly');
        const store = tx.objectStore('progress_queue');
        const index = store.index('synced');
        const range = IDBKeyRange.only(0);
        const request = index.getAll(range);

        request.onsuccess = () => resolve(request.result as QueuedProgress[]);
        request.onerror = () => reject(request.error);
      });
    });
  }

  async markSynced(id: number): Promise<void> {
    await this.readyPromise;
    return new Promise<void>((resolve, reject) => {
      const tx = this.db!.transaction('progress_queue', 'readwrite');
      const store = tx.objectStore('progress_queue');
      const request = store.get(id);

      request.onsuccess = () => {
        const entry = request.result as QueuedProgress | undefined;
        if (entry) {
          entry.synced = 1;
          store.put(entry);
        }
      };

      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async deleteSyncedOlderThan(days: number): Promise<void> {
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - days);
    const cutoffStr = cutoff.toISOString();

    await this.readyPromise;
    const all = await new Promise<QueuedProgress[]>((resolve, reject) => {
      const tx = this.db!.transaction('progress_queue', 'readonly');
      const store = tx.objectStore('progress_queue');
      const request = store.getAll();

      request.onsuccess = () => resolve(request.result as QueuedProgress[]);
      request.onerror = () => reject(request.error);
    });

    const toDelete = all.filter((e) => e.synced === 1 && e.queuedAt < cutoffStr);
    if (toDelete.length === 0) return;

    return new Promise<void>((resolve, reject) => {
      const tx = this.db!.transaction('progress_queue', 'readwrite');
      const store = tx.objectStore('progress_queue');
      for (const entry of toDelete) {
        if (entry.id !== undefined) {
          store.delete(entry.id);
        }
      }
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  // -- settings store --

  getSetting(key: string): Promise<string | null> {
    return this.requestPromise<{ key: string; value: string } | undefined>('settings', 'readonly', (store) => store.get(key))
      .then((result) => result?.value ?? null);
  }

  setSetting(key: string, value: string): Promise<void> {
    return this.requestPromise('settings', 'readwrite', (store) => store.put({ key, value })).then(() => undefined);
  }

  private async requestPromise<T>(storeName: string, mode: IDBTransactionMode, operation: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
    await this.readyPromise;
    if (!this.db) {
      return undefined as T;
    }
    return new Promise<T>((resolve, reject) => {
      const tx = this.db!.transaction(storeName, mode);
      const store = tx.objectStore(storeName);
      const request = operation(store);

      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }
}
