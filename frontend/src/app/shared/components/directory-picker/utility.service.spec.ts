import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it} from 'vitest';

import {UtilityService} from './utility.service';

describe('UtilityService', () => {
  let service: UtilityService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        UtilityService,
      ],
    });

    service = TestBed.inject(UtilityService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('requests folders for the provided path', () => {
    const response = ['/books', '/books/scifi'];

    let result: string[] | undefined;
    service.getFolders('/books').subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/path'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('path')).toBe('/books');
    request.flush(response);

    expect(result).toEqual(response);
  });
});
