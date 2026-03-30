import {describe, expect, expectTypeOf, it} from 'vitest';

import {
  AdditionalFileType,
  Book,
  BookMetadata,
  BookStatusUpdateResponse,
  BulkMetadataUpdateRequest,
  computeSeriesReadStatus,
  CreatePhysicalBookRequest,
  ReadStatus
} from './book.model';

describe('book.model', () => {
  it('exposes stable additional file and read-status enums', () => {
    expect(AdditionalFileType).toEqual({
      ALTERNATIVE_FORMAT: 'ALTERNATIVE_FORMAT',
      SUPPLEMENTARY: 'SUPPLEMENTARY'
    });
    expect(ReadStatus.UNREAD).toBe('UNREAD');
    expect(ReadStatus.RE_READING).toBe('RE_READING');
  });

  it('computes aggregate series status from book states', () => {
    expect(computeSeriesReadStatus([])).toBe(ReadStatus.UNREAD);
    expect(computeSeriesReadStatus([{id: 1, libraryId: 1, libraryName: 'Main', readStatus: ReadStatus.READ} as Book])).toBe(ReadStatus.READ);
    expect(computeSeriesReadStatus([
      {id: 1, libraryId: 1, libraryName: 'Main', readStatus: ReadStatus.READ} as Book,
      {id: 2, libraryId: 1, libraryName: 'Main', readStatus: ReadStatus.UNREAD} as Book
    ])).toBe(ReadStatus.PARTIALLY_READ);
    expect(computeSeriesReadStatus([
      {id: 1, libraryId: 1, libraryName: 'Main', readStatus: ReadStatus.READING} as Book,
      {id: 2, libraryId: 1, libraryName: 'Main', readStatus: ReadStatus.UNREAD} as Book
    ])).toBe(ReadStatus.READING);
    expect(computeSeriesReadStatus([
      {id: 1, libraryId: 1, libraryName: 'Main', readStatus: ReadStatus.WONT_READ} as Book
    ])).toBe(ReadStatus.WONT_READ);
  });

  it('keeps core payload and metadata contracts typed', () => {
    const metadata: BookMetadata = {
      bookId: 4,
      title: 'A Wizard of Earthsea',
      authors: ['Ursula K. Le Guin'],
      pageCount: 205
    };
    const createRequest: CreatePhysicalBookRequest = {
      libraryId: 2,
      title: 'The Tombs of Atuan',
      authors: ['Ursula K. Le Guin']
    };
    const bulkUpdate: BulkMetadataUpdateRequest = {
      bookIds: [1, 2],
      genres: ['Fantasy'],
      mergeCategories: true
    };
    const statusUpdate: BookStatusUpdateResponse = {
      bookId: 1,
      readStatus: ReadStatus.READ,
      readStatusModifiedTime: '2026-03-26T10:00:00Z'
    };

    expect(metadata.authors).toEqual(['Ursula K. Le Guin']);
    expect(createRequest.libraryId).toBe(2);
    expect(bulkUpdate.mergeCategories).toBe(true);
    expect(statusUpdate.readStatus).toBe(ReadStatus.READ);
    expectTypeOf(metadata.pageCount).toEqualTypeOf<number | null | undefined>();
  });
});
