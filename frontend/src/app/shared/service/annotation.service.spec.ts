import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {API_CONFIG} from '../../core/config/api-config';
import {AnnotationService} from './annotation.service';

describe('AnnotationService', () => {
  const baseUrl = `${API_CONFIG.BASE_URL}/api/v1/annotations`;

  let service: AnnotationService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AnnotationService,
      ]
    });

    service = TestBed.inject(AnnotationService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('loads annotations for a book', () => {
    service.getAnnotationsForBook(5).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/book/5`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates and updates annotations', () => {
    const createRequest = {
      bookId: 5,
      cfi: 'epubcfi(/6/8)',
      text: 'Important passage',
    };

    service.createAnnotation(createRequest).subscribe();
    const createHttpRequest = httpTestingController.expectOne(baseUrl);
    expect(createHttpRequest.request.method).toBe('POST');
    expect(createHttpRequest.request.body).toEqual(createRequest);
    createHttpRequest.flush({...createRequest, id: 8, color: '#ff0', style: 'highlight', createdAt: '2024-01-01'});

    const updateRequest = {
      note: 'Review later',
      style: 'underline' as const,
    };

    service.updateAnnotation(8, updateRequest).subscribe();
    const updateHttpRequest = httpTestingController.expectOne(`${baseUrl}/8`);
    expect(updateHttpRequest.request.method).toBe('PUT');
    expect(updateHttpRequest.request.body).toEqual(updateRequest);
    updateHttpRequest.flush({
      ...createRequest,
      ...updateRequest,
      id: 8,
      color: '#ff0',
      createdAt: '2024-01-01',
    });
  });

  it('deletes annotations by id', () => {
    service.deleteAnnotation(8).subscribe();

    const request = httpTestingController.expectOne(`${baseUrl}/8`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
