import {HttpRequest} from '@angular/common/http';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {mitigateWebkitUploadBug} from './mitigate-webkit-upload-bug';

describe('mitigateWebkitUploadBug', () => {
  const hadServiceWorker = Object.prototype.hasOwnProperty.call(navigator, 'serviceWorker');
  const originalServiceWorker = Object.getOwnPropertyDescriptor(navigator, 'serviceWorker');

  function setServiceWorkerController(controller: ServiceWorker | null): void {
    Object.defineProperty(navigator, 'serviceWorker', {
      configurable: true,
      value: {controller},
    });
  }

  function removeServiceWorkerSupport(): void {
    delete (navigator as {serviceWorker?: unknown}).serviceWorker;
  }

  function uploadRequest(): HttpRequest<FormData> {
    const body = new FormData();
    body.append('file', new File(['book contents'], 'book.epub', {type: 'application/epub+zip'}));
    body.append('libraryId', '1');
    return new HttpRequest('POST', '/api/v1/files/upload', body);
  }

  beforeEach(() => {
    vi.spyOn(navigator, 'platform', 'get').mockReturnValue('Linux x86_64');
    vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (X11; Linux x86_64) Firefox/159.0');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    if (hadServiceWorker && originalServiceWorker) {
      Object.defineProperty(navigator, 'serviceWorker', originalServiceWorker);
    } else {
      removeServiceWorkerSupport();
    }
  });

  it('bypasses the service worker when one controls the page', () => {
    setServiceWorkerController({} as ServiceWorker);
    const request = uploadRequest();

    const result = mitigateWebkitUploadBug(request);

    expect(result.headers.get('ngsw-bypass')).toBe('1');
    expect(result.body).toBe(request.body);
    expect(result.method).toBe('POST');
    expect(result.url).toBe('/api/v1/files/upload');
  });

  it('returns the request unchanged when no service worker controls the page', () => {
    setServiceWorkerController(null);
    const request = uploadRequest();

    expect(mitigateWebkitUploadBug(request)).toBe(request);
  });

  it('returns the request unchanged when the browser has no service worker support', () => {
    removeServiceWorkerSupport();
    const request = uploadRequest();

    expect(mitigateWebkitUploadBug(request)).toBe(request);
  });

  it('bypasses the service worker and copies the files on iOS WebKit', () => {
    setServiceWorkerController(null);
    vi.spyOn(navigator, 'platform', 'get').mockReturnValue('iPhone');
    const request = uploadRequest();

    const result = mitigateWebkitUploadBug(request);

    expect(result.headers.get('ngsw-bypass')).toBe('1');
    expect(result.body).not.toBe(request.body);
    const file = result.body?.get('file') as File;
    expect(file).toBeInstanceOf(File);
    expect(file.name).toBe('book.epub');
    expect(file.size).toBe('book contents'.length);
    expect(result.body?.get('libraryId')).toBe('1');
  });
});
