import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Subject, of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {IconDisplayComponent} from './icon-display.component';
import {CustomSvgCacheService} from '../../services/custom-svg-cache.service';
import {CustomSvgService} from '../../services/custom-svg.service';

describe('IconDisplayComponent', () => {
  let fixture: ComponentFixture<IconDisplayComponent>;
  let component: IconDisplayComponent;
  let customSvgCache: {
    getCachedSanitized: ReturnType<typeof vi.fn>;
    cacheIcon: ReturnType<typeof vi.fn>;
  };
  let customSvgService: {getSanitizedSvgContent: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    customSvgCache = {
      getCachedSanitized: vi.fn(() => null),
      cacheIcon: vi.fn(),
    };
    customSvgService = {
      getSanitizedSvgContent: vi.fn(() => of('<svg>ok</svg>')),
    };

    await TestBed.configureTestingModule({
      imports: [IconDisplayComponent],
      providers: [
        {provide: CustomSvgCacheService, useValue: customSvgCache},
        {provide: CustomSvgService, useValue: customSvgService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(IconDisplayComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads uncached custom SVG icons on initialization', () => {
    component.icon = {type: 'CUSTOM_SVG', value: 'dragon'};

    fixture.detectChanges();

    expect(customSvgCache.getCachedSanitized).toHaveBeenCalledWith('dragon');
    expect(customSvgService.getSanitizedSvgContent).toHaveBeenCalledWith('dragon');
  });

  it('does not reload the same custom SVG when the value has not changed', () => {
    component.icon = {type: 'CUSTOM_SVG', value: 'dragon'};
    fixture.detectChanges();
    customSvgService.getSanitizedSvgContent.mockClear();

    component.ngOnChanges({
      icon: {
        currentValue: {type: 'CUSTOM_SVG', value: 'dragon'},
        previousValue: {type: 'CUSTOM_SVG', value: 'dragon'},
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect(customSvgService.getSanitizedSvgContent).not.toHaveBeenCalled();
  });

  it('reloads when the custom SVG icon value changes', () => {
    component.icon = {type: 'CUSTOM_SVG', value: 'phoenix'};
    fixture.detectChanges();
    customSvgService.getSanitizedSvgContent.mockClear();

    component.icon = {type: 'CUSTOM_SVG', value: 'dragon'};
    component.ngOnChanges({
      icon: {
        currentValue: {type: 'CUSTOM_SVG', value: 'dragon'},
        previousValue: {type: 'CUSTOM_SVG', value: 'phoenix'},
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect(customSvgService.getSanitizedSvgContent).toHaveBeenCalledWith('dragon');
  });

  it('exposes cached SVG content and empty-icon dimensions', () => {
    customSvgCache.getCachedSanitized.mockReturnValueOnce('<svg>cached</svg>');
    component.size = '2rem';

    expect(component.getSvgContent('cached')).toBe('<svg>cached</svg>');
    expect(component.getEmptyIconStyle()).toEqual({
      width: '2rem',
      height: '2rem',
    });
  });

  it('caches an error SVG when loading a custom icon fails', () => {
    customSvgService.getSanitizedSvgContent.mockReturnValueOnce(
      throwError(() => new Error('nope'))
    );
    component.icon = {type: 'CUSTOM_SVG', value: 'broken'};

    fixture.detectChanges();

    expect(customSvgCache.cacheIcon).toHaveBeenCalledOnce();
    expect(customSvgCache.cacheIcon.mock.calls[0]?.[0]).toBe('broken');
    expect(customSvgCache.cacheIcon.mock.calls[0]?.[1]).toContain('stroke="red"');
  });

  it('caches a load failure under the originally requested custom icon name', () => {
    const loadResult = new Subject<string>();
    customSvgService.getSanitizedSvgContent.mockReturnValueOnce(loadResult.asObservable());
    component.icon = {type: 'CUSTOM_SVG', value: 'dragon'};

    fixture.detectChanges();
    component.icon = {type: 'CUSTOM_SVG', value: 'phoenix'};
    loadResult.error(new Error('nope'));

    expect(customSvgCache.cacheIcon.mock.calls[0]?.[0]).toBe('dragon');
  });
});
