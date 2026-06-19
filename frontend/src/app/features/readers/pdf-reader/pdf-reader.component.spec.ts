import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {PdfReaderComponent} from './pdf-reader.component';

interface PdfReaderHarness {
  embedPdfIframe: {contentWindow: {postMessage: ReturnType<typeof vi.fn>}} | null;
  embedPdfSaveResolve?: (buffer: ArrayBuffer | null) => void;
  pdfBlobUrl: string | null;
  authService: {getInternalAccessToken: () => string | null};
  cacheStorageService: {delete: ReturnType<typeof vi.fn>};
  bookId: number;
  saveEmbedPdfDocument: () => Promise<boolean>;
}

function makeComponent(savedBuffer: ArrayBuffer): PdfReaderHarness {
  const component = Object.create(PdfReaderComponent.prototype) as PdfReaderHarness;
  component.embedPdfIframe = {
    contentWindow: {
      postMessage: vi.fn(() => {
        setTimeout(() => component.embedPdfSaveResolve?.(savedBuffer.slice(0)));
      })
    }
  };
  component.pdfBlobUrl = 'blob:old-pdf';
  component.authService = {getInternalAccessToken: () => null};
  component.cacheStorageService = {delete: vi.fn(() => Promise.resolve(true))};
  component.bookId = 123;
  return component;
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('PdfReaderComponent save handling', () => {
  it('shares one iframe export and upload across concurrent saves', async () => {
    const savedBuffer = new Uint8Array([1, 2, 3]).buffer;
    const component = makeComponent(savedBuffer);
    const fetchMock = vi.fn(() => Promise.resolve({ok: true} as Response));
    vi.stubGlobal('fetch', fetchMock);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:saved-pdf');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);

    const firstSave = component.saveEmbedPdfDocument();
    const secondSave = component.saveEmbedPdfDocument();
    vi.advanceTimersByTime(0);

    await expect(Promise.all([firstSave, secondSave])).resolves.toEqual([true, true]);
    expect(component.embedPdfIframe?.contentWindow.postMessage).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(component.cacheStorageService.delete).toHaveBeenCalledTimes(1);
  });

  it('refreshes local PDF bytes and evicts the shared cache after upload succeeds', async () => {
    const savedBuffer = new Uint8Array([4, 5, 6]).buffer;
    const component = makeComponent(savedBuffer);
    const fetchMock = vi.fn(() => Promise.resolve({ok: true} as Response));
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:saved-pdf');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', fetchMock);

    const save = component.saveEmbedPdfDocument();
    vi.advanceTimersByTime(0);

    await expect(save).resolves.toBe(true);

    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:old-pdf');
    expect(createObjectUrl).toHaveBeenCalledTimes(1);
    expect(component.pdfBlobUrl).toBe('blob:saved-pdf');
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/books/123/content'),
      expect.objectContaining({body: expect.any(ArrayBuffer)})
    );
    expect(component.cacheStorageService.delete).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/books/123/content')
    );
  });
});
