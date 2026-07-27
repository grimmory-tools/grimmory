import {HttpErrorResponse} from '@angular/common/http';
import {Observable} from 'rxjs';

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
