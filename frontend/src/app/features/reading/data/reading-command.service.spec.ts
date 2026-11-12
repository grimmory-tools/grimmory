import {HttpTestingController} from '@angular/common/http/testing';
import {inject, Injectable} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {injectMutation, QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../../core/config/api-config';
import {
  createQueryClientHarness,
  flushSignalAndQueryEffects,
  observeActiveQuery,
} from '../../../core/testing/query-testing';
import {readingQueryKeys} from './reading-query-keys';
import {ReadingCommandService} from './reading-command.service';

@Injectable()
class ReadingCommandHost {
  private readonly commands = inject(ReadingCommandService);
  readonly createBookmark = injectMutation(() => this.commands.createBookmark());
  readonly savePdfAnnotations = injectMutation(() => this.commands.savePdfAnnotations());
}

describe('ReadingCommandService refresh', () => {
  let host: ReadingCommandHost;
  let queryClient: QueryClient;
  let http: HttpTestingController;

  beforeEach(() => {
    const harness = createQueryClientHarness();
    queryClient = harness.queryClient;
    TestBed.configureTestingModule({
      providers: [...harness.providers, ReadingCommandService, ReadingCommandHost],
    });
    host = TestBed.inject(ReadingCommandHost);
    http = TestBed.inject(HttpTestingController);
    flushSignalAndQueryEffects();
  });

  afterEach(() => {
    http.verify();
    queryClient.clear();
  });

  it('refreshes the book bookmarks and the notebook after a bookmark is created, leaving the rest of the book alone', async () => {
    const notebookKey = readingQueryKeys.notebookPage({types: [], bookId: null, search: '', sort: 'desc', page: 0, size: 20});
    queryClient.setQueryData(readingQueryKeys.bookmarks(7), []);
    queryClient.setQueryData(readingQueryKeys.pdfAnnotations(7), 'annotations');
    queryClient.setQueryData(notebookKey, {content: []});

    const result = host.createBookmark.mutateAsync({bookId: 7, pageNumber: 3});
    await Promise.resolve();
    flushSignalAndQueryEffects();
    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/bookmarks`).flush({id: 1});
    await result;

    expect(queryClient.getQueryState(readingQueryKeys.bookmarks(7))?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(notebookKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(readingQueryKeys.pdfAnnotations(7))?.isInvalidated).toBe(false);
  });

  it('marks saved PDF annotations stale without refetching them while they are on screen', async () => {
    const onScreen = observeActiveQuery(queryClient, readingQueryKeys.pdfAnnotations(7), 'annotations');

    const result = host.savePdfAnnotations.mutateAsync({bookId: 7, data: 'edited'});
    await Promise.resolve();
    flushSignalAndQueryEffects();
    http.expectOne(`${API_CONFIG.BASE_URL}/api/v1/pdf-annotations/book/7`).flush(null);
    await vi.waitFor(() => {
      expect(queryClient.getQueryState(readingQueryKeys.pdfAnnotations(7))?.isInvalidated).toBe(true);
    });

    expect(onScreen.fetchCount()).toBe(0);
    onScreen.finish();
    await result;
  });
});
