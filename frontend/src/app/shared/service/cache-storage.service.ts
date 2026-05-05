import {inject, Injectable} from "@angular/core";
import {HttpClient, HttpErrorResponse, HttpResponse} from "@angular/common/http";
import {firstValueFrom} from "rxjs";

@Injectable({
  providedIn: "root",
})
export class CacheStorageService {
  private static readonly CACHE_NAME = "storage";

  private http = inject(HttpClient);
  private inFlightPrewarms = new Map<string, Promise<void>>();


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

  async prewarmStaticAssets(uris: string[]): Promise<void> {
    const uniqueUris = [...new Set(uris)];
    await Promise.allSettled(uniqueUris.map((uri) => this.prewarmStaticAsset(uri)));
  }

  /**
   * Returns a URL for the requested static asset.
   * If the asset is cached, it returns a blob URL created via URL.createObjectURL().
   *
   * @IMPORTANT The consumer is responsible for calling URL.revokeObjectURL() on the returned string
   * if it is a blob URL (starts with "blob:").
   */
  async getStaticAssetObjectUrl(uri: string): Promise<string> {

    const absoluteUri = this.toAbsoluteUrl(uri);

    try {
      const cached = await this.match(absoluteUri);
      if (cached) {
        const blob = await cached.blob();
        return URL.createObjectURL(blob);
      }

      const response = await fetch(absoluteUri, {
        credentials: "same-origin",
      });


      if (!response.ok) {
        return uri;
      }

      void this.put(absoluteUri, response.clone());
      return URL.createObjectURL(await response.blob());

    } catch {
      return uri;
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

  private async prewarmStaticAsset(uri: string): Promise<void> {
    const absoluteUri = this.toAbsoluteUrl(uri);

    const existing = this.inFlightPrewarms.get(absoluteUri);
    if (existing) return existing;

    const promise = (async () => {
      try {
        if (await this.has(absoluteUri)) {
          return;
        }

        const response = await fetch(absoluteUri, {
          credentials: "same-origin",
        });

        if (!response.ok) {
          return;
        }

        await this.put(absoluteUri, response.clone());
      } catch {
        // Silently fail — caching is best-effort
      } finally {
        this.inFlightPrewarms.delete(absoluteUri);
      }
    })();

    this.inFlightPrewarms.set(absoluteUri, promise);
    return promise;
  }


  private toAbsoluteUrl(uri: string): string {
    try {
      return new URL(uri, window.location.origin).href;
    } catch {
      return uri;
    }
  }
}
