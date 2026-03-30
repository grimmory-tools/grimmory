import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {themes} from '../state/themes.constant';
import {EpubCustomFontService} from '../features/fonts/custom-font.service';
import {ReaderStyleService} from './style.service';

describe('ReaderStyleService', () => {
  const epubCustomFontService = {
    getCustomFontById: vi.fn(),
    getBlobUrl: vi.fn(),
    sanitizeFontName: vi.fn(),
  };

  let service: ReaderStyleService;

  beforeEach(() => {
    epubCustomFontService.getCustomFontById.mockReset();
    epubCustomFontService.getBlobUrl.mockReset();
    epubCustomFontService.sanitizeFontName.mockReset();
    epubCustomFontService.sanitizeFontName.mockImplementation((fontName: string) => fontName.replace(/\W+/g, '-'));

    TestBed.configureTestingModule({
      providers: [
        ReaderStyleService,
        {provide: EpubCustomFontService, useValue: epubCustomFontService},
      ]
    });

    service = TestBed.inject(ReaderStyleService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('generates custom font css when a cached custom font is selected', () => {
    epubCustomFontService.getCustomFontById.mockReturnValue({fontName: 'Fancy Serif.otf'});
    epubCustomFontService.getBlobUrl.mockReturnValue('blob:fancy-serif');

    const css = service.generateCSS({
      lineHeight: 1.6,
      justify: false,
      hyphenate: true,
      maxColumnCount: 2,
      gap: 0.05,
      fontSize: 18,
      theme: {...themes[0], fg: themes[0].dark.fg, bg: themes[0].dark.bg, link: themes[0].dark.link},
      maxInlineSize: 720,
      maxBlockSize: 1440,
      fontFamily: 'custom:7',
      isDark: true,
      flow: 'paginated',
    });

    expect(epubCustomFontService.getCustomFontById).toHaveBeenCalledWith(7);
    expect(epubCustomFontService.getBlobUrl).toHaveBeenCalledWith(7);
    expect(css).toContain('@font-face');
    expect(css).toContain('font-family: "Fancy-Serif-otf", sans-serif !important;');
    expect(css).toContain('font-size: 18px;');
    expect(css).toContain('text-align: start !important;');
    expect(css).toContain('hyphens: auto;');
  });

  it('falls back to plain font families when the font value is not a cached custom font', () => {
    const css = service.generateCSS({
      lineHeight: 1.4,
      justify: true,
      hyphenate: false,
      maxColumnCount: 2,
      gap: 0.05,
      fontSize: 16,
      theme: {...themes[1], fg: themes[1].light.fg, bg: themes[1].light.bg, link: themes[1].light.link},
      maxInlineSize: 720,
      maxBlockSize: 1440,
      fontFamily: 'serif',
      isDark: false,
      flow: 'scrolled',
    });

    expect(epubCustomFontService.getCustomFontById).not.toHaveBeenCalled();
    expect(css).not.toContain('@font-face');
    expect(css).toContain('font-family: serif !important;');
    expect(css).toContain('text-align: justify !important;');
    expect(css).toContain('hyphens: none;');
  });

  it('applies renderer attributes and paginated margins only when a renderer exists', () => {
    const renderer = {
      setAttribute: vi.fn(),
      removeAttribute: vi.fn(),
      setStyles: vi.fn(),
    };

    service.applyStylesToRenderer(renderer, {
      lineHeight: 1.5,
      justify: true,
      hyphenate: true,
      maxColumnCount: 3,
      gap: 0.2,
      fontSize: 20,
      theme: {...themes[0], fg: themes[0].dark.fg, bg: themes[0].dark.bg, link: themes[0].dark.link},
      maxInlineSize: 800,
      maxBlockSize: 1500,
      fontFamily: null,
      isDark: true,
      flow: 'paginated',
    });

    expect(renderer.setAttribute).toHaveBeenCalledWith('max-column-count', 3);
    expect(renderer.setAttribute).toHaveBeenCalledWith('gap', '20%');
    expect(renderer.setAttribute).toHaveBeenCalledWith('max-inline-size', '800px');
    expect(renderer.setAttribute).toHaveBeenCalledWith('max-block-size', '1500px');
    expect(renderer.setAttribute).toHaveBeenCalledWith('margin', '40px');
    expect(renderer.setStyles).toHaveBeenCalledOnce();

    service.applyStylesToRenderer(renderer, {
      lineHeight: 1.5,
      justify: true,
      hyphenate: true,
      maxColumnCount: 3,
      gap: 0.2,
      fontSize: 20,
      theme: {...themes[0], fg: themes[0].dark.fg, bg: themes[0].dark.bg, link: themes[0].dark.link},
      maxInlineSize: 800,
      maxBlockSize: 1500,
      fontFamily: null,
      isDark: true,
      flow: 'scrolled',
    });

    expect(renderer.removeAttribute).toHaveBeenCalledWith('margin');
    expect(() => service.applyStylesToRenderer(null, {
      lineHeight: 1.5,
      justify: true,
      hyphenate: true,
      maxColumnCount: 3,
      gap: 0.2,
      fontSize: 20,
      theme: {...themes[0], fg: themes[0].dark.fg, bg: themes[0].dark.bg, link: themes[0].dark.link},
      maxInlineSize: 800,
      maxBlockSize: 1500,
      fontFamily: null,
      isDark: true,
      flow: 'scrolled',
    })).not.toThrow();
  });
});
