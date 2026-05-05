import {provideHttpClient} from '@angular/common/http';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {CacheStorageService} from './cache-storage.service';

interface MockCache {
  match: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
}

describe('CacheStorageService static asset helpers', () => {
  let service: CacheStorageService;
  let mockCache: MockCache;
  let openSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockCache = {
      match: vi.fn().mockResolvedValue(undefined),
      put: vi.fn().mockResolvedValue(undefined),
    };

    openSpy = vi.fn().mockResolvedValue(mockCache);

    Object.defineProperty(globalThis, 'caches', {
      configurable: true,
      value: {open: openSpy},
    });

    TestBed.configureTestingModule({
      providers: [provideHttpClient(), CacheStorageService],
    });

    service = TestBed.inject(CacheStorageService);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('prewarms static assets once per unique URL', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('asset-data', {status: 200}),
    );

    await service.prewarmStaticAssets([
      '/assets/pdfium/pdfium.wasm',
      '/assets/pdfium/pdfium.wasm',
      '/assets/pdfium/index.js',
    ]);

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(mockCache.put).toHaveBeenCalledTimes(2);
    expect(mockCache.put).toHaveBeenCalledWith(expect.stringContaining('/assets/pdfium/pdfium.wasm'), expect.any(Response));
    expect(mockCache.put).toHaveBeenCalledWith(expect.stringContaining('/assets/pdfium/index.js'), expect.any(Response));
  });

  it('returns an object URL from a cached static asset', async () => {
    mockCache.match.mockResolvedValueOnce(new Response('cached-wasm', {
      status: 200,
      headers: {'content-type': 'application/wasm'},
    }));
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:pdfium-cached');
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    const result = await service.getStaticAssetObjectUrl('/assets/pdfium/pdfium.wasm');

    expect(result).toBe('blob:pdfium-cached');
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(createObjectUrlSpy).toHaveBeenCalledTimes(1);
  });

  it('falls back to the original URI when static asset fetch fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network error'));

    const result = await service.getStaticAssetObjectUrl('/assets/pdfium/pdfium.wasm');

    expect(result).toBe('/assets/pdfium/pdfium.wasm');
  });

  it('falls back to the original URI when static asset response is not ok', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', {status: 404}));
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL');

    const result = await service.getStaticAssetObjectUrl('/assets/pdfium/pdfium.wasm');

    expect(result).toBe('/assets/pdfium/pdfium.wasm');
    expect(createObjectUrlSpy).not.toHaveBeenCalled();
  });

  it('falls back to the original URI when Cache API is unavailable and fetch fails', async () => {
    Object.defineProperty(globalThis, 'caches', {
      configurable: true,
      value: undefined,
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('network error'));
    const createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockImplementation(() => {
      throw new Error('should not create object URL without cache');
    });

    const result = await service.getStaticAssetObjectUrl('/assets/pdfium/pdfium.wasm');

    expect(result).toBe('/assets/pdfium/pdfium.wasm');
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(createObjectUrlSpy).not.toHaveBeenCalled();
  });

  it('skips prewarm fetch when asset is already cached', async () => {
    mockCache.match.mockResolvedValue(new Response('cached', {status: 200}));
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await service.prewarmStaticAssets(['/assets/pdfium/pdfium.wasm']);

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(mockCache.put).not.toHaveBeenCalled();
  });

  it('does not cache failed prewarm responses', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', {status: 503}));

    await service.prewarmStaticAssets(['/assets/pdfium/pdfium.wasm']);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(mockCache.put).not.toHaveBeenCalled();
  });

  it('handles prewarm race conditions by only firing one fetch for multiple rapid calls', async () => {
    let resolveFetch: (res: Response) => void = () => { /* initialized in promise */ };
    const fetchPromise = new Promise<Response>((resolve) => {
      resolveFetch = resolve;
    });

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockReturnValue(fetchPromise as unknown as Promise<Response>);

    // Fire two calls rapidly
    const p1 = service.prewarmStaticAssets(['/assets/pdfium/pdfium.wasm']);
    const p2 = service.prewarmStaticAssets(['/assets/pdfium/pdfium.wasm']);

    // Resolve the fetch
    resolveFetch(new Response('wasm-data', {status: 200}));

    await Promise.all([p1, p2]);

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(mockCache.put).toHaveBeenCalledTimes(1);
  });
});

