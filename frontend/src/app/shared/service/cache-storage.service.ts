import {inject, Injectable} from "@angular/core";
import {HttpClient, HttpErrorResponse, HttpResponse} from "@angular/common/http";
import {firstValueFrom} from "rxjs";

@Injectable({
  providedIn: "root",
})
export class CacheStorageService {
  private static readonly CACHE_NAME = "storage";

  private http = inject(HttpClient);

  async getCache(uri: string, noValidate: boolean = false): Promise<Response> {
    const cachedResponse = await this.attemptToGetAndValidateCache(uri, noValidate);
    if (cachedResponse) {
      return cachedResponse;
    }

    // Cache unavailable, stale, or failed — fetch from server
    const httpResponse = await this.fetchFromUri(uri);
    const headers = new Headers();
    httpResponse.headers.keys().forEach((key) => {
      headers.set(key, httpResponse.headers.get(key)!);
    });

    const res = new Response(httpResponse.body, {
      headers: headers,
      status: httpResponse.status,
      statusText: httpResponse.statusText,
    });

    this.put(uri, res.clone());

    return res;
  }

  /**
   * @returns cached response, or null if cache is unavailable, stale, or failed to access/validate
   */
  private async attemptToGetAndValidateCache(uri: string, noValidate: boolean = false): Promise<Response | null> {
    try {
      const entry = await this.match(uri);
      if (!entry) return null;
      if (noValidate) return entry;
      const valid = await this.validateCacheFromUri(uri, entry);
      return valid ? entry : null;
    } catch {
      return null;
    }
  }

  private async fetchFromUri(uri: string): Promise<HttpResponse<ArrayBuffer>> {
    return firstValueFrom(
      this.http.get<ArrayBuffer>(uri, {
        responseType: "arraybuffer" as "json",
        observe: "response",
        cache: "no-store",
      }),
    );
  }

  private async validateCacheFromUri(uri: string, entry: Response): Promise<boolean> {
    const h = entry.headers.get("last-modified");
    if (h == null) return false;
    try {
      const response = await firstValueFrom(
        this.http.head<ArrayBuffer>(uri, {
          observe: "response",
          responseType: "arraybuffer" as "json",
          headers: { "if-modified-since": h },
        }),
      );

      return response.status === 304;
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 304) {
        return true;
      }
      return false;
    }
  }

  async match(uri: string): Promise<Response | undefined> {
    try {
      const cache = await this.openCache();
      return cache ? await cache.match(uri) : undefined;
    } catch {
      return undefined;
    }
  }

  async has(uri: string): Promise<boolean> {
    const response = await this.match(uri);
    return !!response;
  }

  async put(uri: string, entry: Response): Promise<void> {
    try {
      const cache = await this.openCache();
      if (cache) await cache.put(uri, entry);
    } catch {
      // Silently fail — caching is best-effort
    }
  }

  async delete(uri: string): Promise<boolean> {
    try {
      const cache = await this.openCache();
      return cache ? await cache.delete(uri) : false;
    } catch {
      return false;
    }
  }

  async clear(): Promise<void> {
    try {
      const cache = await this.openCache();
      if (!cache) return;
      const keys = await cache.keys();
      await Promise.all(keys.map((key) => cache.delete(key.url)));
    } catch {
      // Silently fail
    }
  }

  async getCacheSizeInBytes(): Promise<number> {
    try {
      const cache = await this.openCache();
      if (!cache) return 0;
      const keys = await cache.keys();
      const responses = await Promise.all(keys.map((key) => cache.match(key.url)));
      return responses.reduce((total, response) =>
        total + parseInt(response?.headers.get("content-length") || "0"), 0);
    } catch {
      return 0;
    }
  }

  private async openCache(): Promise<Cache | null> {
    try {
      if (typeof caches === 'undefined') return null;
      return await caches.open(CacheStorageService.CACHE_NAME);
    } catch {
      return null;
    }
  }
}
