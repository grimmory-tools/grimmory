import {HttpErrorResponse} from '@angular/common/http';
import {HttpTestingController} from '@angular/common/http/testing';
import {inject, Injectable} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {injectMutation, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  createQueryClientHarness,
  flushSignalAndQueryEffects,
} from '../../../core/testing/query-testing';
import {readingQueryKeys} from '../../reading/data/reading-query-keys';
import {bookQueryKeys} from './book-query-keys';
import {BookCommandService} from './book-command.service';

@Injectable()
class BookCommandHost {
  private readonly commands = inject(BookCommandService);
  readonly setReadStatus = injectMutation(() => this.commands.setReadStatus());
  readonly deleteAdditionalFile = injectMutation(() => this.commands.deleteAdditionalFile());
}

describe('BookCommandService reconciliation', () => {
  let host: BookCommandHost;
  let queryClient: QueryClient;
  let http: HttpTestingController;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    TestBed.configureTestingModule({
      providers: [...harness.providers, BookCommandService, BookCommandHost],
    });
    host = TestBed.inject(BookCommandHost);
    http = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    http.verify();
    queryClient.clear();
  });

  it('invalidates the requested books even when the command fails', async () => {
    const detailKey = bookQueryKeys.detail(4, false);
    queryClient.setQueryData(detailKey, {id: 4});

    const result = host.setReadStatus.mutateAsync({bookIds: [4], status: 'READ'});
    void result.catch(() => undefined);
    await Promise.resolve();
    flushSignalAndQueryEffects();

    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/status`).flush(
      'Unavailable',
      {status: 503, statusText: 'Service Unavailable'},
    );

    await expect(result).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(queryClient.getQueryState(detailKey)?.isInvalidated).toBe(true);
  });

  it('refreshes the reader file info when a file is removed from a book', async () => {
    const fileInfoKey = readingQueryKeys.epubInfo(4, 'EPUB');
    queryClient.setQueryData(fileInfoKey, {});

    const result = host.deleteAdditionalFile.mutateAsync({bookId: 4, fileId: 9});
    await Promise.resolve();
    flushSignalAndQueryEffects();
    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/books/4/files/9`).flush(null);
    await result;

    expect(queryClient.getQueryState(fileInfoKey)?.isInvalidated).toBe(true);
  });
});
