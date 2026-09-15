import {describe, expect, it} from 'vitest';

import {overlayPendingBookState, overlayShelfIds} from './book-command-pending-state';
import {type BookSummary} from './book-response.models';

describe('overlayPendingBookState', () => {
  const book: BookSummary = {
    id: 1,
    libraryId: 2,
    libraryName: 'Library',
    readStatus: 'UNREAD',
    shelves: [
      {id: 20, name: 'Remove', publicShelf: false, bookCount: 1},
      {id: 30, name: 'Keep', publicShelf: false, bookCount: 1},
    ],
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

  it('leaves shelf membership off the overlaid book', () => {
    expect(overlayPendingBookState(book, {
      ...emptyOverlay,
      readStatuses: new Map([[1, 'READ']]),
    }).shelves).toBe(book.shelves);
  });

  it('reports confirmed shelf IDs when nothing is in flight', () => {
    expect(overlayShelfIds(book, undefined)).toEqual(new Set([20, 30]));
  });

  it('applies in-flight assignments and removals to the shelf IDs', () => {
    expect(overlayShelfIds(book, {
      assignShelfIds: new Set([40]),
      unassignShelfIds: new Set([20]),
    })).toEqual(new Set([30, 40]));
  });
});
