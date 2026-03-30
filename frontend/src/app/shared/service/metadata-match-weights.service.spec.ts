import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {MetadataMatchWeightsService} from './metadata-match-weights.service';

describe('MetadataMatchWeightsService', () => {
  let service: MetadataMatchWeightsService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        MetadataMatchWeightsService,
      ]
    });

    service = TestBed.inject(MetadataMatchWeightsService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
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
});
