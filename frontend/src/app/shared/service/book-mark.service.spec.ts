import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {BookMarkService} from './book-mark.service';

describe('BookMarkService', () => {
  const baseUrl = `${API_CONFIG.BASE_URL}/api/v1/bookmarks`;

  let service: BookMarkService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BookMarkService,
      ]
    });

    service = TestBed.inject(BookMarkService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads bookmarks for a book', () => {
    service.getBookmarksForBook(9).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/book/9`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates and updates bookmarks', () => {
    const createRequest = {
      bookId: 9,
      cfi: 'epubcfi(/6/4)',
      title: 'Great quote',
    };

    service.createBookmark(createRequest).subscribe();
    const createHttpRequest = httpTestingController.expectOne(baseUrl);
    expect(createHttpRequest.request.method).toBe('POST');
    expect(createHttpRequest.request.body).toEqual(createRequest);
    createHttpRequest.flush({...createRequest, id: 12, createdAt: '2024-01-01'});

    const updateRequest = {
      title: 'Updated quote',
      notes: 'Look here again',
    };

    service.updateBookmark(12, updateRequest).subscribe();
    const updateHttpRequest = httpTestingController.expectOne(`${baseUrl}/12`);
    expect(updateHttpRequest.request.method).toBe('PUT');
    expect(updateHttpRequest.request.body).toEqual(updateRequest);
    updateHttpRequest.flush({...createRequest, ...updateRequest, id: 12, createdAt: '2024-01-01'});
  });

  it('deletes bookmarks by id', () => {
    service.deleteBookmark(12).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/12`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
