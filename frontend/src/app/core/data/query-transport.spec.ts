import {HttpErrorResponse} from '@angular/common/http';
import {describe, expect, it} from 'vitest';

import {retryTransientQueryError} from './query-transport';

describe('query transport', () => {
  it('retries only transient failures and only twice', () => {
    const networkError = new HttpErrorResponse({status: 0});
    const badRequest = new HttpErrorResponse({status: 400});
    const serverError = new HttpErrorResponse({status: 503});

    expect(retryTransientQueryError(0, networkError)).toBe(true);
    expect(retryTransientQueryError(0, badRequest)).toBe(false);
    expect(retryTransientQueryError(0, serverError)).toBe(true);
    expect(retryTransientQueryError(0, new Error('Unexpected failure'))).toBe(false);
    expect(retryTransientQueryError(2, serverError)).toBe(false);
  });
});
