import {describe, expect, it} from 'vitest';

import {overlayPendingBookState} from './book-command-pending-state';
import {type BookSummary} from './book-response.models';

describe('overlayPendingBookState', () => {
  const book: BookSummary = {
    id: 1,
    libraryId: 2,
    libraryName: 'Library',
    readStatus: 'UNREAD',
    epubProgress: {
      cfi: 'epubcfi(/6/2)',
      href: 'chapter-1.xhtml',
      contentSourceProgressPercent: 40,
      percentage: 42,
      ttsPositionCfi: null,
    },
    koreaderProgress: {percentage: 17},
  };
  const emptyOverlay = {
    readStatuses: new Map(),
    progressResets: new Map(),
  };

  it('preserves identity when no pending state touches the book', () => {
    expect(overlayPendingBookState(book, emptyOverlay)).toBe(book);
  });

  it('replaces the read status verbatim', () => {
    const result = overlayPendingBookState(book, {
      ...emptyOverlay,
      readStatuses: new Map([[1, 'UNSET']]),
    });

    expect(result.readStatus).toBe('UNSET');
  });

  it('clears grimmory-side progress and read status while a reset is in flight', () => {
    const result = overlayPendingBookState(book, {
      ...emptyOverlay,
      progressResets: new Map([[1, 'GRIMMORY' as const]]),
    });

    expect(result.epubProgress).toBeUndefined();
    expect(result.readStatus).toBeUndefined();
    expect(result.koreaderProgress).toEqual(book.koreaderProgress);
  });

  it('clears only the named source for a device reset', () => {
    const result = overlayPendingBookState(book, {
      ...emptyOverlay,
      progressResets: new Map([[1, 'KOREADER' as const]]),
    });

    expect(result.koreaderProgress).toBeUndefined();
    expect(result.epubProgress).toEqual(book.epubProgress);
  });
});
