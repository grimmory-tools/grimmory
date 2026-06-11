import {TestBed} from '@angular/core/testing';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {FileDownloadService} from './file-download.service';
import {AuthService} from './auth.service';

describe('FileDownloadService', () => {
  let service: FileDownloadService;
  let ensureAccessToken: ReturnType<typeof vi.fn>;
  let getInternalAccessToken: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    ensureAccessToken = vi.fn().mockReturnValue(of('tok'));
    getInternalAccessToken = vi.fn().mockReturnValue('stale-tok');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        FileDownloadService,
        {provide: AuthService, useValue: {ensureAccessToken, getInternalAccessToken}},
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

    service.downloadFile('/files/1', 'book.epub');

    expect(ensureAccessToken).toHaveBeenCalledOnce();
    expect(anchor.href).toBe('/files/1?token=tok');
    expect(anchor.download).toBe('book.epub');
    expect(click).toHaveBeenCalledOnce();
  });

  it('uses & as the separator when the url already has a query string', () => {
    const {anchor} = stubAnchor();

    service.downloadFile('/files/1?foo=bar', 'book.epub');

    expect(anchor.href).toBe('/files/1?foo=bar&token=tok');
  });

  it('url-encodes the token', () => {
    ensureAccessToken.mockReturnValue(of('a b/c+d'));
    const {anchor} = stubAnchor();

    service.downloadFile('/files/1', 'book.epub');

    expect(anchor.href).toBe('/files/1?token=a%20b%2Fc%2Bd');
  });

  it('falls back to the stored access token when refresh fails', () => {
    ensureAccessToken.mockReturnValue(throwError(() => new Error('refresh failed')));
    const {anchor, click} = stubAnchor();

    service.downloadFile('/files/1', 'book.epub');

    expect(anchor.href).toBe('/files/1?token=stale-tok');
    expect(click).toHaveBeenCalledOnce();
  });

  it('downloads without a token when none is available', () => {
    ensureAccessToken.mockReturnValue(throwError(() => new Error('refresh failed')));
    getInternalAccessToken.mockReturnValue(null);
    const {anchor, click} = stubAnchor();

    service.downloadFile('/files/1', 'book.epub');

    expect(anchor.href).toBe('/files/1');
    expect(click).toHaveBeenCalledOnce();
  });
});
