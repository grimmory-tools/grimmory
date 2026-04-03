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

    // Cache stale or failed to get, fetch from uri

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
    
    this.put(uri, res.clone()).catch((error) => {
      console.error("Error caching response:", error);
    });

    return res;
  }

  /**
   * @throws no throws
   * @returns cached response, or null if cache is stale or failed to access/validate
   */
  private async attemptToGetAndValidateCache(uri: string, noValidate: boolean = false): Promise<Response | null> {
    let stale: boolean = true;
    let entry;
    try {
      entry = await this.match(uri);
      if (entry) {
        stale = false;
        if (!noValidate) {
          stale = !(await this.validateCacheFromUri(uri, entry));
        }
      }
    } catch (error) {
      console.error("Error accessing or validating cache:", error);
      stale = true;
    }

    if (!stale && entry)
      return entry;
    return null;
  }

  private async fetchFromUri(uri: string): Promise<HttpResponse<ArrayBuffer>> {
    return firstValueFrom(
      this.http.get<ArrayBuffer>(uri, {
        responseType: "arraybuffer" as "json",
        observe: "response",
        cache: "no-store",    // Avoid double caching
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
    } catch (error: unknown) {  // It throws if the status is not 2xx
      if (error instanceof HttpErrorResponse && error.status === 304) {
        return true; // Not modified, cache is valid
      }
      console.error("Error validating cache:", error);
      return false;
    }
  }

  match(uri: string): Promise<Response | undefined> {
    return this.cache().then((cache) => cache.match(uri));
  }

  has(uri: string): Promise<boolean> {
    return this.cache().then((cache) =>
      cache.match(uri).then((response) => !!response),
    );
  }

  put(uri: string, entry: Response): Promise<void> {
    return this.cache().then((cache) => cache.put(uri, entry));
  }

  delete(uri: string): Promise<boolean> {
    return this.cache().then((cache) => cache.delete(uri));
  }

  clear(): Promise<void> {
    return this.cache()
      .then((cache) => cache.keys())
      .then((keys) => Promise.all(keys.map((key) => this.delete(key.url))))
      .then(() => undefined);
  }

  getCacheSizeInBytes(): Promise<number> {
    return this.cache()
      .then((cache) => cache.keys())
      .then((keys) => keys.map((key) => this.match(key.url)))
      .then((promises) => Promise.all(promises))
      .then((responses) => responses.map(response => parseInt(response?.headers.get("content-length") || "0")))
      .then((sizes) => sizes.reduce((total, size) => total + size, 0));
  }


  private cache(): Promise<Cache> {
    return caches.open(CacheStorageService.CACHE_NAME);
  }
}
