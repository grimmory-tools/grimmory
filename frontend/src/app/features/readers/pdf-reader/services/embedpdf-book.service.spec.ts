import {TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {EmbedPdfBookService} from './embedpdf-book.service';

const {embedPdfInitSpy} = vi.hoisted(() => ({
  embedPdfInitSpy: vi.fn(),
}));

vi.mock('@embedpdf/snippet', () => ({
  default: {
    init: embedPdfInitSpy,
  },
}));

describe('EmbedPdfBookService', () => {
  let service: EmbedPdfBookService;

  const makeRegistry = () => ({
    getPlugin: vi.fn((name: string) => {
      if (name === 'scroll') {
        return {
          provides: () => ({
            onPageChange: vi.fn(() => () => undefined),
            getCurrentPage: vi.fn(() => 1),
            getTotalPages: vi.fn(() => 10),
          }),
        };
      }

      if (name === 'document-manager') {
        return {
          provides: () => ({
            onDocumentOpened: vi.fn(() => () => undefined),
          }),
        };
      }

      return undefined;
    }),
  });

  beforeEach(() => {
    embedPdfInitSpy.mockReset();

    TestBed.configureTestingModule({
      providers: [EmbedPdfBookService],
    });

    service = TestBed.inject(EmbedPdfBookService);

    (service as unknown as Record<string, unknown>)['applyWorkerShims'] = vi.fn();
    (service as unknown as Record<string, unknown>)['ensureHighDpiRendering'] = vi.fn();
    (service as unknown as Record<string, unknown>)['patchReleasePointerCapture'] = vi.fn();
    (service as unknown as Record<string, unknown>)['injectBookModeStyles'] = vi.fn();
    (service as unknown as Record<string, unknown>)['setupResizeObserver'] = vi.fn();
  });

  afterEach(() => {
    service.destroy();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('passes the provided wasmUrl override to EmbedPDF.init', async () => {
    const registry = makeRegistry();
    embedPdfInitSpy.mockReturnValue({
      registry: Promise.resolve(registry),
      remove: vi.fn(),
      setTheme: vi.fn(),
    });

    const target = document.createElement('div');
    await service.init(target, 'blob:test-pdf', 'dark', 'en', 'blob:test-wasm');

    expect(embedPdfInitSpy).toHaveBeenCalledTimes(1);
    expect(embedPdfInitSpy).toHaveBeenCalledWith(expect.objectContaining({
      wasmUrl: 'blob:test-wasm',
      src: 'blob:test-pdf',
    }));
  });

  it('uses the default pdfium wasm URL when no override is provided', async () => {
    const registry = makeRegistry();
    embedPdfInitSpy.mockReturnValue({
      registry: Promise.resolve(registry),
      remove: vi.fn(),
      setTheme: vi.fn(),
    });

    const target = document.createElement('div');
    await service.init(target, 'blob:test-pdf', 'light', 'en');

    expect(embedPdfInitSpy).toHaveBeenCalledTimes(1);
    expect(embedPdfInitSpy).toHaveBeenCalledWith(expect.objectContaining({
      wasmUrl: new URL('/assets/pdfium/pdfium.wasm', location.origin).href,
      src: 'blob:test-pdf',
    }));
  });
});
