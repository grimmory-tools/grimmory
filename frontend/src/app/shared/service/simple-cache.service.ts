import Dexie from "dexie";
import { inject, Injectable, DestroyRef } from "@angular/core";
import {
  HttpClient,
  HttpErrorResponse,
  HttpResponse,
} from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { LocalSettingsService } from "./local-settings.service";

export interface SimpleCacheEntry {
  uri: string;
  lastModified: Date;
  data: Blob;
  lastAccessed: Date;
  ttl: number; // Time to live in milliseconds
}

@Injectable({
  providedIn: "root",
})
export class SimpleCacheService {
  static readonly MAX_DATA_SIZE_IN_BYTES = 256 * 1024 * 1024; // 256 MB
  private static readonly TABLE_NAME = "simpleCache";

  private http = inject(HttpClient);
  private localSettingsService = inject(LocalSettingsService);
  private destroyRef = inject(DestroyRef);

  private db: Dexie;
  private cleanupTimeoutId: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.db = new Dexie("SimpleCacheDB");
    this.db.version(1).stores({
      simpleCache: "uri, lastAccessed",
    });

    // Run cleanup immediately on startup and continue using configured interval.
    void this.runCleanup();
    this.scheduleCleanup();

    this.destroyRef.onDestroy(() => {
      this.clearCleanupTimer();
    });
  }

  async getCache(uri: string, noValidate: boolean = false): Promise<Blob> {
    let stale: boolean = true;
    try {
      const entry = await this.table().get({ uri: uri });
      if (entry) {
        stale = false;
        this.updateLastAccessed(entry);
        if (!noValidate) stale = !(await this.validateCacheFromUri(entry));
      }
    } catch (error) {
      console.error("Error accessing cache:", error);
    }

    if (!stale)
      // @ts-ignore
      return entry.data;

    // Cache stale, fetch from uri

    const response = await this.fetchFromUri(uri);
    const body = response.body;
    const lastModified = response.headers.get("last-modified")
      ? //@ts-ignore
        new Date(response.headers.get("last-modified"))
      : new Date(0); // Fallback: Jan 1, 1970

    if (!body) return new Blob(); // How did we get here?

    // WARNING: async addCache must not throw exceptions
    this.addCache({
      uri: uri,
      data: body,
      lastModified: lastModified,
      lastAccessed: new Date(),
      ttl: this.getEntryTtlMs(),
    });

    return body;
  }

  async existCache(uri: string): Promise<boolean> {
    try {
      return !!(await this.table().get({ uri: uri }));
    } catch (error) {
      console.error("Error checking cache existence:", error);
      return false;
    }
  }

  async addCache(entry: SimpleCacheEntry): Promise<void> {
    try {
      if (await this.existCache(entry.uri)) {
        await this.table().update(entry.uri, entry);
      } else {
        await this.table().put(entry);
      }
    } catch (error) {
      console.error("Error caching:", error);
    }
  }

  async deleteCache(uri: string): Promise<void> {
    try {
      await this.table().delete(uri);
    } catch (error) {
      console.error("Error deleting cache:", error);
    }
  }

  async clearCache(): Promise<void> {
    try {
      await this.table().clear();
    } catch (error) {
      console.error("Error clearing cache:", error);
    }
  }

  async cleanUpTimedOutEntries(): Promise<void> {
    try {
      const entries = await this.table().toArray();
      for (const entry of entries) {
        await this.deleteIfTimedOut(entry);
      }
    } catch (error) {
      console.error("Error cleaning up timed out cache entries:", error);
    }
  }

  async cleanUpExcessEntries(): Promise<void> {
    try {
      // Max entries cleanup
      const maxEntries = this.localSettingsService.get().simpleCacheMaxEntries;
      const count = await this.table().count();
      if (count > maxEntries) {
        await this.table()
          .orderBy("lastAccessed") // Ascending order, least recently accessed first
          .limit(count - maxEntries)
          .delete();
      }
    } catch (error) {
      console.error("Error cleaning up excess cache entries:", error);
    }

    try {
      // Max data size cleanup
      const maxDataSize = this.localSettingsService.get().simpleCacheMaxSizeMB * 1024 * 1024;
      const dataSizes = await this.getDataSizes();
      const totalSize = dataSizes.reduce((acc, entry) => acc + entry.size, 0);

      if (totalSize > maxDataSize) {
        dataSizes.sort((a, b) => a.lastAccessed.getTime() - b.lastAccessed.getTime());
        const excessSize = totalSize - maxDataSize;
        let deletedSize = 0;
        for (const { uri, size } of dataSizes) {
          if (deletedSize >= excessSize) break;
          await this.deleteCache(uri);
          deletedSize += size;
        }
      }
    } catch (error) {
      console.error("Error cleaning up excess cache entries:", error);
    }
  }

  async runCleanup(): Promise<void> {
    await this.cleanUpTimedOutEntries();
    await this.cleanUpExcessEntries();
  }

  async getCacheSizeInBytes(): Promise<number> {
    const dataSizes = await this.getDataSizes();
    return dataSizes.reduce((acc, entry) => acc + entry.size, 0);
  }

  private async getDataSizes(): Promise<
    Array<{ uri: string; size: number; lastAccessed: Date }>
  > {
    try {
      const entries = await this.table().toArray();
      const sizes = entries.map((entry) => ({
        uri: entry.uri,
        size: entry.data.size,
        lastAccessed: entry.lastAccessed,
      }));
      return sizes;
    } catch (error) {
      console.error("Error calculating cache data sizes:", error);
      return [];
    }
  }

  private table(): Dexie.Table<SimpleCacheEntry, string> {
    return this.db.table(SimpleCacheService.TABLE_NAME);
  }

  private async fetchFromUri(uri: string): Promise<HttpResponse<Blob>> {
    return firstValueFrom(
      this.http.get<Blob>(uri, {
        responseType: "blob" as "json",
        observe: "response",
      }),
    );
  }

  private async validateCacheFromUri(
    entry: SimpleCacheEntry,
  ): Promise<boolean> {
    try {
      const response = await firstValueFrom(
        this.http.head<Blob>(entry.uri, {
          observe: "response",
          responseType: "blob" as "json",
          headers: {
            "if-modified-since": entry.lastModified.toUTCString(),
          },
        }),
      );

      return response.status === 304;
    } catch (error: HttpErrorResponse | any) {
      if (error instanceof HttpErrorResponse && error.status === 304) {
        return true; // Not modified, cache is valid
      }
      console.error("Error validating cache:", error);
      return false;
    }
  }

  private async updateLastAccessed(entry: SimpleCacheEntry): Promise<void> {
    try {
      entry.lastAccessed = new Date();
      await this.table().update(entry.uri, entry);
    } catch (error) {
      console.error("Error updating last accessed time:", error);
    }
  }

  private async deleteIfTimedOut(
    entry: SimpleCacheEntry,
  ): Promise<SimpleCacheEntry | null> {
    if (this.isTimedOut(entry)) {
      await this.deleteCache(entry.uri);
      return null;
    }
    return entry;
  }

  private isTimedOut(entry: SimpleCacheEntry): boolean {
    return Date.now() > entry.lastAccessed.getTime() + entry.ttl;
  }

  private getEntryTtlMs(): number {
    const ttlHours = this.localSettingsService.get().simpleCacheTtlHours;
    return ttlHours * 60 * 60 * 1000;
  }

  private getCleanupIntervalMs(): number {
    const cleanupIntervalMinutes = this.localSettingsService.get().simpleCacheCleanupIntervalMinutes;
    return cleanupIntervalMinutes * 60 * 1000;
  }

  private scheduleCleanup(): void {
    this.clearCleanupTimer();

    this.cleanupTimeoutId = setTimeout(async () => {
      try {
        await this.runCleanup();
      } catch (error) {
        console.error("Error running scheduled cache cleanup:", error);
      } finally {
        this.scheduleCleanup();
      }
    }, this.getCleanupIntervalMs());
  }

  private clearCleanupTimer(): void {
    if (this.cleanupTimeoutId == null) return;

    clearTimeout(this.cleanupTimeoutId);
    this.cleanupTimeoutId = null;
  }
}
