import {ComponentFixture, TestBed} from '@angular/core/testing';
import {SafeHtml} from '@angular/platform-browser';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {IconDisplayComponent} from './icon-display.component';
import {IconCacheService} from '../../services/icon-cache.service';
import {IconService} from '../../services/icon.service';

describe('IconDisplayComponent', () => {
  let fixture: ComponentFixture<IconDisplayComponent>;
  let component: IconDisplayComponent;
  let iconCache: {
    getCachedSanitized: ReturnType<typeof vi.fn>;
    cacheIcon: ReturnType<typeof vi.fn>;
  };
  let iconService: {getSanitizedSvgContent: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    iconCache = {
      getCachedSanitized: vi.fn(() => null),
      cacheIcon: vi.fn(),
    };
    iconService = {
      getSanitizedSvgContent: vi.fn(() => of('<svg>ok</svg>' as SafeHtml)),
    };

    await TestBed.configureTestingModule({
      imports: [IconDisplayComponent],
      providers: [
        {provide: IconCacheService, useValue: iconCache},
        {provide: IconService, useValue: iconService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(IconDisplayComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('normalizes PrimeNG icon class names', () => {
    expect(component.getPrimeNgIconClass('pi pi-book')).toBe('pi pi-book');
    expect(component.getPrimeNgIconClass('pi-book')).toBe('pi pi-book');
    expect(component.getPrimeNgIconClass('book')).toBe('pi pi-book');
  });

  it('builds PrimeNG styles from the configured size and extra icon style', () => {
    component.size = '20px';
    component.iconStyle = {color: 'tomato'};

    expect(component.getPrimeNgStyle()).toEqual({
      fontSize: '17px',
      width: '20px',
      height: '20px',
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: 'tomato',
    });
  });

  it('loads uncached custom SVG icons on initialization', () => {
    component.icon = {type: 'CUSTOM_SVG', value: 'dragon'};

    fixture.detectChanges();

    expect(iconCache.getCachedSanitized).toHaveBeenCalledWith('dragon');
    expect(iconService.getSanitizedSvgContent).toHaveBeenCalledWith('dragon');
  });

  it('does not reload the same custom SVG when the value has not changed', () => {
    const loadSpy = vi.spyOn(component as never, 'loadIconIfNeeded');
    component.ngOnChanges({
      icon: {
        currentValue: {type: 'CUSTOM_SVG', value: 'dragon'},
        previousValue: {type: 'CUSTOM_SVG', value: 'dragon'},
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect(loadSpy).not.toHaveBeenCalled();
  });

  it('reloads when the custom SVG icon value changes', () => {
    const loadSpy = vi.spyOn(component as never, 'loadIconIfNeeded');

    component.ngOnChanges({
      icon: {
        currentValue: {type: 'CUSTOM_SVG', value: 'dragon'},
        previousValue: {type: 'CUSTOM_SVG', value: 'phoenix'},
        firstChange: false,
        isFirstChange: () => false,
      },
    });

    expect(loadSpy).toHaveBeenCalled();
  });

  it('exposes cached SVG content and empty-icon dimensions', () => {
    iconCache.getCachedSanitized.mockReturnValueOnce('<svg>cached</svg>' as SafeHtml);
    component.size = '2rem';

    expect(component.getSvgContent('cached')).toBe('<svg>cached</svg>' as SafeHtml);
    expect(component.getEmptyIconStyle()).toEqual({
      width: '2rem',
      height: '2rem',
    });
  });

  it('caches an error SVG when loading a custom icon fails', () => {
    iconService.getSanitizedSvgContent.mockReturnValueOnce(
      throwError(() => new Error('nope'))
    );
    component.icon = {type: 'CUSTOM_SVG', value: 'broken'};

    fixture.detectChanges();

    expect(iconCache.cacheIcon).toHaveBeenCalledOnce();
    expect(iconCache.cacheIcon.mock.calls[0]?.[0]).toBe('broken');
    expect(iconCache.cacheIcon.mock.calls[0]?.[1]).toContain('stroke="red"');
  });
});
