import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {firstValueFrom, lastValueFrom, Observable, takeUntil} from 'rxjs';

import type {AuthService} from '../../shared/service/auth.service';
import {readSseJson} from './sse';

export function retryTransientQueryError(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) {
    return false;
  }
  return error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500);
}

export const QUERY_DEFAULTS = {
  staleTime: 30_000,
  retry: retryTransientQueryError,
} as const;

export function abortSignal(signal: AbortSignal): Observable<void> {
  return new Observable(subscriber => {
    if (signal.aborted) {
      subscriber.next();
      subscriber.complete();
      return;
    }
    const onAbort = () => {
      subscriber.next();
      subscriber.complete();
    };
    signal.addEventListener('abort', onAbort, {once: true});
    return () => signal.removeEventListener('abort', onAbort);
  });
}

export function toAbortablePromise<T>(source: Observable<T>, signal: AbortSignal): Promise<T> {
  return lastValueFrom(source.pipe(takeUntil(abortSignal(signal))));
}

export function httpGet<T>(http: HttpClient, url: string, signal: AbortSignal): Promise<T> {
  return toAbortablePromise(http.get<T>(url), signal);
}

export function postSseJson<T>(
  auth: AuthService,
  url: string,
  body: string,
  signal?: AbortSignal,
): Promise<AsyncIterable<T>> {
  return fetchSseJson<T>(auth, url, {method: 'POST', body, headers: {'Content-Type': 'application/json'}}, signal);
}

export function getSseJson<T>(
  auth: AuthService,
  url: string,
  signal: AbortSignal,
): Promise<AsyncIterable<T>> {
  return fetchSseJson<T>(auth, url, {method: 'GET'}, signal);
}

async function fetchSseJson<T>(
  auth: AuthService,
  url: string,
  {method, body, headers}: {method: 'GET' | 'POST'; body?: string; headers?: Record<string, string>},
  signal?: AbortSignal,
): Promise<AsyncIterable<T>> {
  const send = async (options: {forceRefresh?: boolean} = {}) => {
    const token = await firstValueFrom(auth.ensureAccessToken(options));
    return fetch(url, {
      method,
      headers: {
        ...headers,
        'Authorization': `Bearer ${token}`,
      },
      body,
      signal,
    });
  };

  let response = await send();
  if (response.status === 401) {
    await response.body?.cancel();
    response = await send({forceRefresh: true});
  }

  if (!response.ok) {
    await response.body?.cancel();
    // oxlint-disable-next-line typescript/only-throw-error -- SAFETY: HttpClient rejects with HttpErrorResponse, and retryTransientQueryError only retries that type, so SSE failures must match it.
    throw new HttpErrorResponse({status: response.status, statusText: response.statusText, url});
  }

  return readSseJson<T>(response);
}
