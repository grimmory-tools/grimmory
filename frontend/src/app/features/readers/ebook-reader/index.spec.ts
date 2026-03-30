import {describe, expect, it} from 'vitest';

import * as readerExports from './index';

describe('ebook-reader index barrel', () => {
  it('re-exports the primary reader entrypoints', () => {
    expect(readerExports.EbookReaderComponent).toBeDefined();
    expect(readerExports.ReaderViewManagerService).toBeDefined();
    expect(readerExports.ReaderEventService).toBeDefined();
    expect(readerExports.ReaderAnnotationHttpService).toBeDefined();
    expect(readerExports.ReaderIconComponent).toBeDefined();
  });
});
