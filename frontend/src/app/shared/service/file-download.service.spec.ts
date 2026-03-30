import {HttpHeaders, provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {FileDownloadService} from './file-download.service';

describe('FileDownloadService', () => {
  let service: FileDownloadService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        FileDownloadService,
      ]
    });

    service = TestBed.inject(FileDownloadService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
  });

  it('downloads a blob using the filename from the response header', () => {
    const click = vi.fn();
    const anchor = {href: '', download: '', click} as unknown as HTMLAnchorElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(anchor);
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    service.downloadFile('/files/1', 'fallback.epub');

    const request = httpTestingController.expectOne('/files/1');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['content']), {
      headers: new HttpHeaders({
        'Content-Disposition': "attachment; filename*=UTF-8''server%20name.epub",
      }),
    });

    expect(createElementSpy).toHaveBeenCalledWith('a');
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(anchor.download).toBe('server name.epub');
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:download');
  });

  it('falls back to the provided filename when the response has no content-disposition header', () => {
    const click = vi.fn();
    const anchor = {href: '', download: '', click} as unknown as HTMLAnchorElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(anchor);
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:download');
    const revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    service.downloadFile('/files/2', 'fallback.epub');

    const request = httpTestingController.expectOne('/files/2');
    request.flush(new Blob(['content']));

    expect(createElementSpy).toHaveBeenCalledWith('a');
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(anchor.download).toBe('fallback.epub');
    expect(click).toHaveBeenCalledOnce();
    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:download');
  });
});
