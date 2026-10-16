import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {bookQueryKeys} from '../../features/book/data/book-query-keys';
import {BOOKS_QUERY_KEY} from '../../features/book/service/book-query-keys';
import {MetadataMatchWeightsService} from './metadata-match-weights.service';

describe('MetadataMatchWeightsService', () => {
  let service: MetadataMatchWeightsService;
  let httpTestingController: HttpTestingController;
  let queryClient: QueryClient;

  beforeEach(() => {
    TestBed.resetTestingModule();
    queryClient = new QueryClient();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MetadataMatchWeightsService,
        {provide: QueryClient, useValue: queryClient},
      ]
    });

    service = TestBed.inject(MetadataMatchWeightsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    queryClient.clear();
    TestBed.resetTestingModule();
  });

  it('posts to the metadata score recalculation endpoint', () => {
    service.recalculateAll().subscribe();

    const request = httpTestingController.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/books/metadata/recalculate-match-scores`
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});

    request.flush(null);
  });

  it('refreshes book caches once recalculated scores are saved', () => {
    const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries').mockResolvedValue(undefined);

    service.recalculateAll().subscribe();

    httpTestingController.expectOne(
      `${API_CONFIG.BASE_URL}/api/v1/books/metadata/recalculate-match-scores`
    ).flush(null);

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: BOOKS_QUERY_KEY, exact: true});
    expect(invalidateQueriesSpy).toHaveBeenCalledWith({queryKey: bookQueryKeys.all()});
  });
});
