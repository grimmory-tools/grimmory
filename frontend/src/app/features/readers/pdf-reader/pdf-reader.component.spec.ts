import {afterEach, describe, expect, it, vi} from 'vitest';

import {PdfReaderComponent} from './pdf-reader.component';

interface PdfReaderHarness {
  embedPdfIframe: {contentWindow: {postMessage: ReturnType<typeof vi.fn>}} | null;
  embedPdfSaveResolve?: (buffer: ArrayBuffer | null) => void;
  cachedPdfBuffer: ArrayBuffer | null;
  pdfBlobUrl: string | null;
  authService: {getInternalAccessToken: () => string | null};
  bookId: number;
  saveEmbedPdfDocument: () => Promise<boolean>;
}

function bytes(buffer: ArrayBuffer): number[] {
  return Array.from(new Uint8Array(buffer));
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
  component.cachedPdfBuffer = new Uint8Array([9, 9, 9]).buffer;
  component.pdfBlobUrl = 'blob:old-pdf';
  component.authService = {getInternalAccessToken: () => null};
  component.bookId = 123;
  return component;
}

afterEach(() => {
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

    await expect(Promise.all([firstSave, secondSave])).resolves.toEqual([true, true]);
    expect(component.embedPdfIframe?.contentWindow.postMessage).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('refreshes the cached PDF bytes and blob URL after upload succeeds', async () => {
    const savedBuffer = new Uint8Array([4, 5, 6]).buffer;
    const component = makeComponent(savedBuffer);
    const fetchMock = vi.fn(() => Promise.resolve({ok: true} as Response));
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:saved-pdf');
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', fetchMock);

    await expect(component.saveEmbedPdfDocument()).resolves.toBe(true);

    expect(bytes(component.cachedPdfBuffer!)).toEqual([4, 5, 6]);
    expect(component.cachedPdfBuffer).not.toBe(savedBuffer);
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:old-pdf');
    expect(createObjectUrl).toHaveBeenCalledTimes(1);
    expect(component.pdfBlobUrl).toBe('blob:saved-pdf');
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/books/123/content'),
      expect.objectContaining({body: expect.any(ArrayBuffer)})
    );
  });
});
