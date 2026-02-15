import {Injectable} from '@angular/core';
import {openDB, IDBPDatabase} from 'idb';
import {Book} from '../model/book.model';

const DB_NAME = 'booklore-books';
const DB_VERSION = 1;
const BOOKS_STORE = 'books';
const META_STORE = 'meta';

@Injectable({
  providedIn: 'root',
})
export class BookCacheService {

  private dbPromise: Promise<IDBPDatabase> = openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      if (!db.objectStoreNames.contains(BOOKS_STORE)) {
        db.createObjectStore(BOOKS_STORE, {keyPath: 'id'});
      }
      if (!db.objectStoreNames.contains(META_STORE)) {
        db.createObjectStore(META_STORE);
      }
    },
  });

  async getAll(): Promise<Book[]> {
    const db = await this.dbPromise;
    return db.getAll(BOOKS_STORE);
  }

  async putAll(books: Book[]): Promise<void> {
    const db = await this.dbPromise;
    const tx = db.transaction(BOOKS_STORE, 'readwrite');
    for (const book of books) {
      tx.store.put(book);
    }
    await tx.done;
  }

  async put(book: Book): Promise<void> {
    const db = await this.dbPromise;
    await db.put(BOOKS_STORE, book);
  }

  async delete(id: number): Promise<void> {
    const db = await this.dbPromise;
    await db.delete(BOOKS_STORE, id);
  }

  async deleteMany(ids: number[]): Promise<void> {
    const db = await this.dbPromise;
    const tx = db.transaction(BOOKS_STORE, 'readwrite');
    for (const id of ids) {
      tx.store.delete(id);
    }
    await tx.done;
  }

  async getSyncTimestamp(): Promise<string | null> {
    const db = await this.dbPromise;
    return (await db.get(META_STORE, 'syncTimestamp')) ?? null;
  }

  async setSyncTimestamp(ts: string): Promise<void> {
    const db = await this.dbPromise;
    await db.put(META_STORE, ts, 'syncTimestamp');
  }

  async clear(): Promise<void> {
    const db = await this.dbPromise;
    const tx = db.transaction([BOOKS_STORE, META_STORE], 'readwrite');
    tx.objectStore(BOOKS_STORE).clear();
    tx.objectStore(META_STORE).clear();
    await tx.done;
  }
}
