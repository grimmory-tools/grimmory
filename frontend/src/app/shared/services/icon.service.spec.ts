import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {DomSanitizer, SafeHtml} from '@angular/platform-browser';
import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {IconCacheService} from './icon-cache.service';
import {IconService} from './icon.service';

describe('IconService', () => {
  let service: IconService;
  let httpTestingController: HttpTestingController;
  let iconCache: {
    getCachedSanitized: ReturnType<typeof vi.fn>;
    cacheIcon: ReturnType<typeof vi.fn>;
    removeIcon: ReturnType<typeof vi.fn>;
  };
  let bypassSecurityTrustHtml: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    iconCache = {
      getCachedSanitized: vi.fn(() => null),
      cacheIcon: vi.fn(),
      removeIcon: vi.fn(),
    };
    bypassSecurityTrustHtml = vi.fn(value => value as SafeHtml);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        IconService,
        {
          provide: IconCacheService,
          useValue: iconCache,
        },
        {
          provide: DomSanitizer,
          useValue: {bypassSecurityTrustHtml},
        },
      ],
    });

    service = TestBed.inject(IconService);
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
    iconCache.getCachedSanitized.mockReturnValue('cached-safe-html' as SafeHtml);

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
    expect(bypassSecurityTrustHtml).toHaveBeenCalled();
    expect(iconCache.cacheIcon).toHaveBeenCalledWith(
      'sun',
      '<svg><rect /></svg>',
      expect.stringContaining('<svg>')
    );
  });

  it('returns cached sanitized svg content without issuing a request', () => {
    iconCache.getCachedSanitized.mockReturnValue('cached-safe-html' as SafeHtml);

    let result: SafeHtml | undefined;
    service.getSanitizedSvgContent('moon').subscribe(value => {
      result = value;
    });

    httpTestingController.expectNone(req => req.url.includes('/api/v1/icons/moon/content'));
    expect(result).toBe('cached-safe-html');
  });

  it('removes icons from the cache after deletion', () => {
    service.deleteSvgIcon('sun rise').subscribe();

    const request = httpTestingController.expectOne(req => req.url.endsWith('/api/v1/icons/sun%20rise'));
    expect(request.request.method).toBe('DELETE');
    request.flush(null);

    expect(iconCache.removeIcon).toHaveBeenCalledWith('sun rise');
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

    expect(iconCache.cacheIcon).toHaveBeenCalledTimes(1);
    expect(iconCache.cacheIcon).toHaveBeenCalledWith('sun', '<svg>sun</svg>', '<svg>sun</svg>');
  });
});
