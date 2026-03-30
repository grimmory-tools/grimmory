import {TestBed} from '@angular/core/testing';
import {Renderer2} from '@angular/core';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {firstValueFrom} from 'rxjs';

import {CustomFontService} from '../../../../../shared/service/custom-font.service';
import {FontFormat} from '../../../../../shared/model/custom-font.model';
import {EpubCustomFontService} from './custom-font.service';

describe('EpubCustomFontService', () => {
  const fonts = [
    {
      id: 7,
      fontName: 'Fancy Serif (Display).otf',
      originalFileName: 'Fancy Serif (Display).otf',
      format: FontFormat.OTF,
      fileSize: 1024,
      uploadedAt: '2026-03-26T00:00:00Z',
    },
  ];

  const customFontService = {
    ensureFonts: vi.fn(),
    loadAllFonts: vi.fn(),
    getFontUrl: vi.fn(),
    appendToken: vi.fn(),
  };

  let service: EpubCustomFontService;
  let fetchMock: ReturnType<typeof vi.fn>;
  let createObjectUrlSpy: ReturnType<typeof vi.spyOn>;
  let revokeObjectUrlSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    customFontService.ensureFonts.mockReset();
    customFontService.loadAllFonts.mockReset();
    customFontService.getFontUrl.mockReset();
    customFontService.appendToken.mockReset();

    customFontService.ensureFonts.mockResolvedValue(fonts);
    customFontService.loadAllFonts.mockResolvedValue(undefined);
    customFontService.getFontUrl.mockImplementation((fontId: number) => `https://fonts.test/${fontId}`);
    customFontService.appendToken.mockImplementation((url: string) => `${url}?token=abc`);

    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:font-7');
    revokeObjectUrlSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    TestBed.configureTestingModule({
      providers: [
        EpubCustomFontService,
        {provide: CustomFontService, useValue: customFontService},
      ]
    });

    service = TestBed.inject(EpubCustomFontService);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('loads fonts, caches blob urls, and exposes preview metadata', async () => {
    fetchMock.mockResolvedValue({
      blob: () => Promise.resolve(new Blob(['font-bytes'], {type: 'font/otf'})),
    });

    await expect(firstValueFrom(service.loadAndCacheFonts())).resolves.toEqual(fonts);

    expect(customFontService.ensureFonts).toHaveBeenCalledOnce();
    expect(customFontService.loadAllFonts).toHaveBeenCalledWith(fonts);
    expect(fetchMock).toHaveBeenCalledWith('https://fonts.test/7?token=abc');
    expect(service.getBlobUrl(7)).toBe('blob:font-7');
    expect(service.getCustomFonts()).toEqual(fonts);
    expect(service.getCustomFontById(7)).toEqual(fonts[0]);
    expect(service.getCustomFontById(999)).toBeUndefined();
    expect(service.sanitizeFontName('Fancy Serif (Display).otf')).toBe('Fancy-Serif-Display');
    expect(service.getFontFamilyForPreview('custom:7')).toBe('"Fancy-Serif-Display", sans-serif');
    expect(service.getFontFamilyForPreview('sans-serif')).toBe('sans-serif');
  });

  it('returns an empty list when the underlying font load fails', async () => {
    customFontService.ensureFonts.mockRejectedValue(new Error('offline'));

    await expect(firstValueFrom(service.loadAndCacheFonts())).resolves.toEqual([]);

    expect(customFontService.loadAllFonts).not.toHaveBeenCalled();
  });

  it('skips blob caching when there are no fonts', async () => {
    customFontService.ensureFonts.mockResolvedValue([]);
    customFontService.loadAllFonts.mockResolvedValue(undefined);

    await expect(firstValueFrom(service.loadAndCacheFonts())).resolves.toEqual([]);

    expect(fetchMock).not.toHaveBeenCalled();
    expect(createObjectUrlSpy).not.toHaveBeenCalled();
  });

  it('continues when one font blob fetch fails and injects stylesheet output for cached fonts', async () => {
    const secondFont = {
      id: 9,
      fontName: 'Mono Sans.woff2',
      originalFileName: 'Mono Sans.woff2',
      format: FontFormat.WOFF2,
      fileSize: 2048,
      uploadedAt: '2026-03-26T00:00:00Z',
    };

    customFontService.ensureFonts.mockResolvedValue([fonts[0], secondFont]);
    customFontService.loadAllFonts.mockResolvedValue(undefined);
    customFontService.getFontUrl.mockImplementation((fontId: number) => `https://fonts.test/${fontId}`);
    customFontService.appendToken.mockImplementation((url: string) => `${url}?token=abc`);

    fetchMock
      .mockResolvedValueOnce({
        blob: () => Promise.resolve(new Blob(['font-one'], {type: 'font/otf'})),
      })
      .mockRejectedValueOnce(new Error('missing blob'));

    createObjectUrlSpy
      .mockReturnValueOnce('blob:font-7')
      .mockReturnValueOnce('blob:font-9');

    await expect(firstValueFrom(service.loadAndCacheFonts())).resolves.toEqual([fonts[0], secondFont]);

    const stylesheet = service.generateCustomFontsStylesheet();
    expect(stylesheet).toContain('font-family: "Fancy-Serif-Display"');
    expect(stylesheet).toContain('src: url("blob:font-7")');
    expect(stylesheet).not.toContain('Mono-Sans');

    const renderer = {
      createElement: vi.fn(() => document.createElement('style')),
      appendChild: vi.fn(),
    } as unknown as Renderer2;

    service.injectCustomFontsStylesheet(renderer, document);

    expect(renderer.createElement).toHaveBeenCalledWith('style');
    expect(renderer.appendChild).toHaveBeenCalledOnce();
  });

  it('cleans up blob urls and returns empty css when nothing is cached', async () => {
    fetchMock.mockResolvedValue({
      blob: () => Promise.resolve(new Blob(['font-bytes'], {type: 'font/otf'})),
    });

    await firstValueFrom(service.loadAndCacheFonts());

    service.cleanup();

    expect(revokeObjectUrlSpy).toHaveBeenCalledWith('blob:font-7');
    expect(service.getBlobUrl(7)).toBeUndefined();
    expect(service.generateCustomFontsStylesheet()).toBe('');
  });
});
