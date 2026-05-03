import { Injectable } from '@angular/core';

export interface CacheBookMetadata {
  title: string;
  author?: string;
  fileType: string;
  fileName: string;
  cachedAt: string;
}

@Injectable({
  providedIn: 'root',
})
export class OfflineStorageService {
  private readonly OPFS_ROOT = 'books';

  async isAvailable(): Promise<boolean> {
    if (!navigator.storage?.getDirectory) return false;
    try {
      await navigator.storage.getDirectory();
      return true;
    } catch {
      return false;
    }
  }

  async getBookContent(bookId: number, fileType: string): Promise<File | null> {
    try {
      const root = await navigator.storage.getDirectory();
      const dirHandle = await this.ensureDir(root, [this.OPFS_ROOT, String(bookId)]);
      const fileHandle = await dirHandle.getFileHandle(`content.${fileType.toLowerCase()}`);
      const file = await fileHandle.getFile();
      return file;
    } catch {
      return null;
    }
  }

  async cacheBook(
    bookId: number,
    content: Blob,
    fileType: string,
    fileName: string,
    metadata: CacheBookMetadata
  ): Promise<void> {
    try {
      const root = await navigator.storage.getDirectory();
      const dirHandle = await this.ensureDir(root, [this.OPFS_ROOT, String(bookId)]);

      const contentHandle = await dirHandle.getFileHandle(`content.${fileType.toLowerCase()}`, { create: true });
      const contentWritable = await contentHandle.createWritable();
      await contentWritable.write(content);
      await contentWritable.close();

      const metaHandle = await dirHandle.getFileHandle('metadata.json', { create: true });
      const metaWritable = await metaHandle.createWritable();
      await metaWritable.write(JSON.stringify(metadata));
      await metaWritable.close();
    } catch (err) {
      console.warn('[OfflineStorage] cacheBook failed:', err);
    }
  }

  async cacheCover(bookId: number, coverBlob: Blob, extension: string): Promise<void> {
    try {
      const root = await navigator.storage.getDirectory();
      const dirHandle = await this.ensureDir(root, [this.OPFS_ROOT, String(bookId)]);
      const coverHandle = await dirHandle.getFileHandle(`cover.${extension}`, { create: true });
      const writable = await coverHandle.createWritable();
      await writable.write(coverBlob);
      await writable.close();
    } catch (err) {
      console.warn('[OfflineStorage] cacheCover failed:', err);
    }
  }

  async deleteBookFiles(bookId: number): Promise<void> {
    try {
      const root = await navigator.storage.getDirectory();
      const booksDir = await this.ensureDir(root, [this.OPFS_ROOT]);
      await booksDir.removeEntry(String(bookId), { recursive: true });
    } catch (err) {
      console.warn('[OfflineStorage] deleteBookFiles failed:', err);
    }
  }

  async clearAllBooks(): Promise<void> {
    try {
      const root = await navigator.storage.getDirectory();
      const booksDir = await this.ensureDir(root, [this.OPFS_ROOT]);
      const entries = booksDir.values();
      for await (const entry of entries) {
        await booksDir.removeEntry(entry.name, { recursive: true });
      }
    } catch (err) {
      console.warn('[OfflineStorage] clearAllBooks failed:', err);
    }
  }

  async getStorageInfo(): Promise<{ bookCount: number; opfsBytes: number }> {
    try {
      const root = await navigator.storage.getDirectory();
      const booksDir = await this.ensureDir(root, [this.OPFS_ROOT]);
      let bookCount = 0;
      let opfsBytes = 0;
      for await (const entry of booksDir.values()) {
        if (entry.kind === 'directory') {
          bookCount++;
          const bookDir = await this.ensureDir(booksDir, [entry.name]);
          for await (const fileEntry of bookDir.values()) {
            if (fileEntry.kind === 'file') {
              const file = await (fileEntry as FileSystemFileHandle).getFile();
              opfsBytes += file.size;
            }
          }
        }
      }
      return { bookCount, opfsBytes };
    } catch {
      return { bookCount: 0, opfsBytes: 0 };
    }
  }

  private async ensureDir(
    parent: FileSystemDirectoryHandle,
    path: string[]
  ): Promise<FileSystemDirectoryHandle> {
    let dir = parent;
    for (const segment of path) {
      dir = await dir.getDirectoryHandle(segment, { create: true });
    }
    return dir;
  }
}
