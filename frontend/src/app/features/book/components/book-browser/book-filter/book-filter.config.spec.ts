import {describe, expect, it} from 'vitest';

import {Book, ReadStatus} from '../../../model/book.model';
import {
  CONTENT_RATING_LABELS,
  FILTER_CONFIGS,
  FILTER_EXTRACTORS,
  FILTER_LABEL_KEYS,
  MATCH_SCORE_RANGES,
  NUMERIC_ID_FILTER_TYPES,
  RATING_OPTIONS_10,
  READ_STATUS_LABELS
} from './book-filter.config';

const makeBook = (overrides: Partial<Book> = {}): Book => ({
  id: 1,
  libraryId: 9,
  libraryName: 'Main',
  ...overrides
});

describe('book-filter.config', () => {
  it('covers every read status with a label', () => {
    expect(READ_STATUS_LABELS[ReadStatus.UNREAD]).toBe('Unread');
    expect(READ_STATUS_LABELS[ReadStatus.UNSET]).toBe('Unset');
    expect(Object.keys(READ_STATUS_LABELS)).toHaveLength(Object.keys(ReadStatus).length);
  });

  it('defines 10 one-step rating buckets', () => {
    expect(RATING_OPTIONS_10).toHaveLength(10);
    expect(RATING_OPTIONS_10[0]).toMatchObject({id: 1, label: '1', min: 1, max: 2, sortIndex: 0});
    expect(RATING_OPTIONS_10.at(-1)).toMatchObject({id: 10, label: '10', min: 10, max: 11, sortIndex: 9});
  });

  it('keeps filter labels and configs aligned', () => {
    const configKeys = Object.keys(FILTER_CONFIGS).sort();
    const labelKeysWithoutLibrary = Object.keys(FILTER_LABEL_KEYS).filter((key) => key !== 'library').sort();

    expect(labelKeysWithoutLibrary).toEqual(configKeys);
    expect(FILTER_LABEL_KEYS.library).toBe('book.filter.labels.library');
    expect(NUMERIC_ID_FILTER_TYPES.has('pageCount')).toBe(true);
    expect(NUMERIC_ID_FILTER_TYPES.has('author')).toBe(false);
  });

  it('normalizes read status, metadata match score, and content ratings', () => {
    expect(FILTER_EXTRACTORS.readStatus(makeBook({readStatus: ReadStatus.READING}))).toEqual([
      {id: ReadStatus.READING, name: 'Reading'}
    ]);

    expect(FILTER_EXTRACTORS.readStatus(makeBook({readStatus: 'INVALID' as ReadStatus}))).toEqual([
      {id: ReadStatus.UNSET, name: 'Unset'}
    ]);

    expect(FILTER_EXTRACTORS.matchScore(makeBook({metadataMatchScore: 92}))).toEqual([
      {id: MATCH_SCORE_RANGES[1].id, name: MATCH_SCORE_RANGES[1].label, sortIndex: MATCH_SCORE_RANGES[1].sortIndex}
    ]);

    expect(FILTER_EXTRACTORS.contentRating(makeBook({
      metadata: {bookId: 1, contentRating: 'EVERYONE'}
    }))).toEqual([{id: 'EVERYONE', name: CONTENT_RATING_LABELS['EVERYONE']}]);
  });

  it('builds comic creator filters with role-aware labels', () => {
    const filters = FILTER_EXTRACTORS.comicCreator(makeBook({
      metadata: {
        bookId: 1,
        comicMetadata: {
          pencillers: ['Jane Artist'],
          editors: ['Max Editor']
        }
      }
    }));

    expect(filters).toEqual([
      {id: 'Jane Artist:penciller', name: 'Jane Artist (Penciller)'},
      {id: 'Max Editor:editor', name: 'Max Editor (Editor)'}
    ]);
  });
});
