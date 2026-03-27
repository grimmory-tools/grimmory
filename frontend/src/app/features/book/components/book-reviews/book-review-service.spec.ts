import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {BookReviewService} from './book-review-service';

describe('BookReviewService', () => {
  let service: BookReviewService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookReviewService,
      ],
    });

    service = TestBed.inject(BookReviewService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads reviews for a specific book', () => {
    let responseBody: unknown;

    service.getByBookId(7).subscribe(response => {
      responseBody = response;
    });

    const request = httpTestingController.expectOne(req =>
      req.method === 'GET' && req.url.endsWith('/api/v1/reviews/book/7')
    );
    request.flush([{id: 1, title: 'Great review'}]);

    expect(responseBody).toEqual([{id: 1, title: 'Great review'}]);
  });

  it('refreshes reviews through the refresh endpoint', () => {
    service.refreshReviews(9).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'POST' && req.url.endsWith('/api/v1/reviews/book/9/refresh')
    );
    expect(request.request.body).toEqual({});
    request.flush([{id: 3, title: 'Fresh review'}]);
  });

  it('deletes a single review by id', () => {
    service.delete(11).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'DELETE' && req.url.endsWith('/api/v1/reviews/11')
    );
    request.flush(null);
  });

  it('deletes all reviews for a book', () => {
    service.deleteAllByBookId(12).subscribe();

    const request = httpTestingController.expectOne(req =>
      req.method === 'DELETE' && req.url.endsWith('/api/v1/reviews/book/12')
    );
    request.flush(null);
  });
});
