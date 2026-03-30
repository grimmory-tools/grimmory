import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {PdfAnnotationService} from './pdf-annotation.service';

describe('PdfAnnotationService', () => {
  let service: PdfAnnotationService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        PdfAnnotationService,
      ],
    });

    service = TestBed.inject(PdfAnnotationService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('fetches annotations for a book', () => {
    let result: {data: string} | undefined;
    service.getAnnotations(42).subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/pdf-annotations/book/42'));
    expect(request.request.method).toBe('GET');
    request.flush({data: '{"annotations":[]}'});

    expect(result).toEqual({data: '{"annotations":[]}'});
  });

  it('saves annotations for a book', () => {
    let completed = false;
    service.saveAnnotations(7, '{"page":1}').subscribe(() => {
      completed = true;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/pdf-annotations/book/7'));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({data: '{"page":1}'});
    request.flush(null);

    expect(completed).toBe(true);
  });

  it('deletes annotations for a book', () => {
    let completed = false;
    service.deleteAnnotations(9).subscribe(() => {
      completed = true;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/pdf-annotations/book/9'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(completed).toBe(true);
  });
});
