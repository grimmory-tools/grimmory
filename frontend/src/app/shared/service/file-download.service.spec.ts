import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {FileDownloadService} from './file-download.service';
import {AuthService} from './auth.service';
import {API_CONFIG} from '../../core/config/api-config';

describe('FileDownloadService', () => {
  const apiUrl = `${API_CONFIG.BASE_URL}/api/v1/books/1/download`;
  let service: FileDownloadService;
  let ensureAccessToken: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    ensureAccessToken = vi.fn().mockReturnValue(of('tok'));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        FileDownloadService,
        {provide: AuthService, useValue: {ensureAccessToken}},
      ]
    });

    service = TestBed.inject(FileDownloadService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  function stubAnchor() {
    const click = vi.fn();
    const anchor = {href: '', download: '', click} as unknown as HTMLAnchorElement;
    vi.spyOn(document, 'createElement').mockReturnValue(anchor);
    return {anchor, click};
  }

  it('navigates to the download url with a fresh access token appended', () => {
    const {anchor, click} = stubAnchor();

    service.downloadFile(apiUrl, 'book.epub');

    expect(ensureAccessToken).toHaveBeenCalledOnce();
    expect(anchor.href).toBe(`${apiUrl}?token=tok`);
    expect(anchor.download).toBe('book.epub');
    expect(click).toHaveBeenCalledOnce();
  });

  it('uses & as the separator when the url already has a query string', () => {
    const {anchor} = stubAnchor();

    service.downloadFile(`${apiUrl}?foo=bar`, 'book.epub');

    expect(anchor.href).toBe(`${apiUrl}?foo=bar&token=tok`);
  });

  it('url-encodes the token', () => {
    ensureAccessToken.mockReturnValue(of('a b/c+d'));
    const {anchor} = stubAnchor();

    service.downloadFile(apiUrl, 'book.epub');

    expect(anchor.href).toBe(`${apiUrl}?token=a%20b%2Fc%2Bd`);
  });

  it('does not append the token to a non-API (cross-origin) url', () => {
    const {anchor, click} = stubAnchor();

    service.downloadFile('https://example.com/evil', 'book.epub');

    expect(anchor.href).toBe('https://example.com/evil');
    expect(click).toHaveBeenCalledOnce();
  });

  it('does not start a download when the token refresh fails', () => {
    ensureAccessToken.mockReturnValue(throwError(() => new Error('refresh failed')));
    const createElement = vi.spyOn(document, 'createElement');

    service.downloadFile(apiUrl, 'book.epub');

    expect(createElement).not.toHaveBeenCalled();
  });
});
