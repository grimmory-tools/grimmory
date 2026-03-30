import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {BookCoverService, CoverFetchRequest, CoverImage} from './book-cover.service';

describe('BookCoverService', () => {
  let service: BookCoverService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookCoverService,
      ],
    });

    service = TestBed.inject(BookCoverService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('posts the cover fetch request and returns the backend response', () => {
    const requestBody: CoverFetchRequest = {
      title: 'Dune',
      author: 'Frank Herbert',
      coverType: 'ebook',
    };
    const response: CoverImage[] = [
      {url: 'https://example.test/cover-1.jpg', source: 'google', width: 600, height: 900, index: 0},
      {url: 'https://example.test/cover-2.jpg', source: 'openlibrary', index: 1},
    ];

    let result: CoverImage[] | undefined;
    service.fetchBookCovers(requestBody).subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/books/1/metadata/covers'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush(response);

    expect(result).toEqual(response);
  });
});
