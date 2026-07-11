import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {CustomSvgCacheService} from './custom-svg-cache.service';
import {CustomSvgService} from './custom-svg.service';

describe('CustomSvgService', () => {
  let service: CustomSvgService;
  let httpTestingController: HttpTestingController;
  let customSvgCache: {
    getCachedSanitized: ReturnType<typeof vi.fn>;
    cacheIcon: ReturnType<typeof vi.fn>;
    removeIcon: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    customSvgCache = {
      getCachedSanitized: vi.fn(() => null),
      cacheIcon: vi.fn(),
      removeIcon: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CustomSvgService,
        {
          provide: CustomSvgCacheService,
          useValue: customSvgCache,
        },
      ],
    });

    service = TestBed.inject(CustomSvgService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('maps icon names from the paged response shape', () => {
    let result: string[] | undefined;
    service.getIconNames(2, 25).subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.includes('/api/v1/icons?page=2&size=25'));
    expect(request.request.method).toBe('GET');
    request.flush({content: ['sun', 'moon']});

    expect(result).toEqual(['sun', 'moon']);
  });

  it('short-circuits svg content requests when the sanitized icon is already cached', () => {
    customSvgCache.getCachedSanitized.mockReturnValue('cached-svg');

    let result: string | undefined;
    service.getSvgIconContent('sun').subscribe(value => {
      result = value;
    });

    httpTestingController.expectNone(req => req.url.includes('/api/v1/icons/sun/content'));
    expect(result).toBe('');
  });

  it('fetches svg content, sanitizes it, and caches the result', () => {
    let result: string | undefined;
    service.getSvgIconContent('sun').subscribe(value => {
      result = value;
    });

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/icons/sun/content'));
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('text');
    request.flush('<svg><rect /></svg>');

    expect(result).toBe('<svg><rect /></svg>');
    expect(customSvgCache.cacheIcon).toHaveBeenCalledWith(
      'sun',
      '<svg><rect /></svg>',
      expect.stringContaining('<svg>')
    );
  });

  it('returns cached sanitized svg content without issuing a request', () => {
    customSvgCache.getCachedSanitized.mockReturnValue('cached-svg');

    let result: string | undefined;
    service.getSanitizedSvgContent('moon').subscribe(value => {
      result = value;
    });

    httpTestingController.expectNone(req => req.url.includes('/api/v1/icons/moon/content'));
    expect(result).toBe('cached-svg');
  });

  it('removes icons from the cache after deletion', () => {
    service.deleteSvgIcon('sun rise').subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/icons/sun%20rise'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(customSvgCache.removeIcon).toHaveBeenCalledWith('sun rise');
  });

  it('caches only successfully saved icons from batch saves', () => {
    const icons = [
      {svgName: 'sun', svgData: '<svg>sun</svg>'},
      {svgName: 'moon', svgData: '<svg>moon</svg>'},
    ];

    service.saveBatchSvgIcons(icons).subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/icons/batch'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({icons});
    request.flush({
      totalRequested: 2,
      successCount: 1,
      failureCount: 1,
      results: [
        {iconName: 'sun', success: true, errorMessage: ''},
        {iconName: 'moon', success: false, errorMessage: 'duplicate'},
      ],
    });

    expect(customSvgCache.cacheIcon).toHaveBeenCalledTimes(1);
    expect(customSvgCache.cacheIcon).toHaveBeenCalledWith('sun', '<svg>sun</svg>', '<svg>sun</svg>');
  });
});
