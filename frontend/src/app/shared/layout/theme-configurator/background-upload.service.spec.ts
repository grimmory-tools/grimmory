import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {BackgroundUploadService} from './background-upload.service';

describe('BackgroundUploadService', () => {
  let service: BackgroundUploadService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BackgroundUploadService,
      ],
    });

    service = TestBed.inject(BackgroundUploadService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('uploads a file as multipart form data and maps the returned URL', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    const file = new File(['image-bytes'], 'background.png', {type: 'image/png'});
    let result: string | undefined;

    service.uploadFile(file).subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/background/upload'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    expect((request.request.body as FormData).get('file')).toBe(file);
    request.flush({url: 'https://example.test/background.png'});

    expect(result).toBe('https://example.test/background.png');
    expect(logSpy).toHaveBeenCalled();
  });

  it('uploads a background URL and maps the returned URL', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => undefined);
    let result: string | undefined;

    service.uploadUrl('https://example.test/source.jpg').subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/background/url'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({url: 'https://example.test/source.jpg'});
    request.flush({url: 'https://example.test/normalized.jpg'});

    expect(result).toBe('https://example.test/normalized.jpg');
    expect(logSpy).toHaveBeenCalled();
  });
});
