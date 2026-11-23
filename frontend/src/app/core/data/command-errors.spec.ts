import {HttpErrorResponse} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {MessageService, type ToastMessageOptions} from '@openng/optimus-ui/api';
import {MutationObserver, QueryClient} from '@tanstack/angular-query';
import {lastValueFrom, throwError} from 'rxjs';
import {beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../testing/transloco-testing';
import {injectCommandErrorToasts} from './command-errors';

describe('injectCommandErrorToasts', () => {
  let queryClient: QueryClient;
  let toasts: ToastMessageOptions[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [getTranslocoModule({preloadLangs: true})],
      providers: [MessageService],
    });
    toasts = [];
    TestBed.inject(MessageService).messageObserver.subscribe(message => {
      toasts.push(...(Array.isArray(message) ? message : [message]));
    });
    queryClient = new QueryClient({
      mutationCache: TestBed.runInInjectionContext(() => injectCommandErrorToasts()),
    });
  });

  function run(mutationFn: () => Promise<unknown>, meta?: {errorToast: false}): Promise<unknown> {
    return new MutationObserver(queryClient, {mutationFn, meta}).mutate().catch(() => undefined);
  }

  function serverError(status: number, body: unknown): Promise<never> {
    return lastValueFrom(throwError(() => new HttpErrorResponse({status, error: body})));
  }

  it('shows one error toast with the server message when a command fails', async () => {
    await run(() => serverError(404, {status: 404, message: 'Book not found with ID: 12'}));

    expect(toasts).toEqual([
      {severity: 'error', summary: 'Something went wrong', detail: 'Book not found with ID: 12'},
    ]);
  });

  it('explains the server could not be reached when there is no server message', async () => {
    await run(() => serverError(0, new ProgressEvent('error')));

    expect(toasts).toEqual([
      {severity: 'error', summary: 'Something went wrong', detail: "Couldn't reach the server."},
    ]);
  });

  it('shows nothing when a command succeeds', async () => {
    await run(() => Promise.resolve('done'));

    expect(toasts).toEqual([]);
  });

  it('shows nothing for an expired login or a command that reports its own errors', async () => {
    await run(() => serverError(401, {status: 401, message: 'Invalid JWT token'}));
    await run(() => serverError(500, {status: 500, message: 'An unexpected error occurred.'}), {errorToast: false});

    expect(toasts).toEqual([]);
  });
});
