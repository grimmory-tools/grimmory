import {describe, expect, it} from 'vitest';

import {type BookFileResponse, type BookSummary} from '../book/data/book-response.models';
import {dashboardRowBooks} from './dashboard-row-query';
import {type ScrollerConfig, ScrollerType} from './models/dashboard-config.model';

function file(bookId: number, id: number, bookType: 'EPUB' | 'CBX' | 'AUDIOBOOK'): BookFileResponse {
  return {id, bookId, book: true, folderBased: false, bookType};
}

const READING: BookSummary = {
  id: 1,
  libraryId: 1,
  libraryName: 'Library',
  primaryFile: file(1, 10, 'EPUB'),
  epubProgress: {cfi: null, href: null, contentSourceProgressPercent: null, percentage: 40, ttsPositionCfi: null},
};
const LISTENING: BookSummary = {
  id: 2,
  libraryId: 1,
  libraryName: 'Library',
  primaryFile: file(2, 20, 'EPUB'),
  alternativeFormats: [file(2, 21, 'AUDIOBOOK')],
  audiobookProgress: {positionMs: 10, trackIndex: 0, trackPositionMs: 10, percentage: 55},
};
const UNSTARTED: BookSummary = {id: 3, libraryId: 1, libraryName: 'Library', primaryFile: file(3, 30, 'EPUB')};
const READING_ALTERNATIVE: BookSummary = {
  id: 4,
  libraryId: 1,
  libraryName: 'Library',
  primaryFile: file(4, 40, 'CBX'),
  alternativeFormats: [file(4, 41, 'EPUB')],
  epubProgress: {cfi: null, href: null, contentSourceProgressPercent: null, percentage: 55, ttsPositionCfi: null},
  koboProgress: {percentage: 3},
};

function row(type: ScrollerType): ScrollerConfig {
  return {id: 'row-1', type, title: 'Row', enabled: true, order: 1, maxItems: 20};
}

describe('dashboardRowBooks', () => {
  it('keeps the books started in the row format, and says which file that is', () => {
    const all = [READING, LISTENING, UNSTARTED, READING_ALTERNATIVE];

    expect(dashboardRowBooks(row(ScrollerType.LAST_READ), all)).toEqual([
      {book: READING, file: READING.primaryFile},
      {book: READING_ALTERNATIVE, file: file(4, 41, 'EPUB')},
    ]);
    expect(dashboardRowBooks(row(ScrollerType.LAST_LISTENED), all))
      .toEqual([{book: LISTENING, file: file(2, 21, 'AUDIOBOOK')}]);
  });
});
