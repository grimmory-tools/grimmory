import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {GroupRule} from '../component/magic-shelf-component';

describe('BookRuleEvaluatorService - metadataPresence', () => {
  let service: BookRuleEvaluatorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(BookRuleEvaluatorService);
  });

  const createBook = (overrides: Partial<Book> = {}): Book => ({
    id: 1,
    bookType: 'EPUB',
    libraryId: 1,
    libraryName: 'Test Library',
    fileName: 'test.epub',
    filePath: '/path/to/test.epub',
    readStatus: ReadStatus.UNREAD,
    shelves: [],
    metadata: {
      bookId: 1,
      title: 'Test Book',
      authors: ['Test Author'],
      categories: ['Fiction'],
      language: 'en'
    },
    ...overrides
  });

  const rule = (operator: string, value: string): GroupRule => ({
    name: 'test', type: 'group', join: 'and',
    rules: [{field: 'metadataPresence', operator, value} as never]
  });

  describe('string fields', () => {
    it('should detect present title', () => {
      const book = createBook({metadata: {bookId: 1, title: 'A Book'}});
      expect(service.evaluateGroup(book, rule('equals', 'title'))).toBe(true);
      expect(service.evaluateGroup(book, rule('not_equals', 'title'))).toBe(false);
    });

    it('should detect missing title', () => {
      const book = createBook({metadata: {bookId: 1, title: undefined}});
      expect(service.evaluateGroup(book, rule('equals', 'title'))).toBe(false);
      expect(service.evaluateGroup(book, rule('not_equals', 'title'))).toBe(true);
    });

    it('should treat empty string as absent', () => {
      const book = createBook({metadata: {bookId: 1, title: ''}});
      expect(service.evaluateGroup(book, rule('equals', 'title'))).toBe(false);
    });

    it('should treat whitespace-only string as absent', () => {
      const book = createBook({metadata: {bookId: 1, title: '   '}});
      expect(service.evaluateGroup(book, rule('equals', 'title'))).toBe(false);
    });

    it('should detect present subtitle', () => {
      const book = createBook({metadata: {bookId: 1, subtitle: 'A Novel'}});
      expect(service.evaluateGroup(book, rule('equals', 'subtitle'))).toBe(true);
    });

    it('should detect missing subtitle', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('equals', 'subtitle'))).toBe(false);
    });

    it('should detect present description', () => {
      const book = createBook({metadata: {bookId: 1, description: 'A tale of adventure'}});
      expect(service.evaluateGroup(book, rule('equals', 'description'))).toBe(true);
    });

    it('should detect missing description', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'description'))).toBe(true);
    });

    it('should detect present publisher', () => {
      const book = createBook({metadata: {bookId: 1, publisher: 'Tor Books'}});
      expect(service.evaluateGroup(book, rule('equals', 'publisher'))).toBe(true);
    });

    it('should detect present publishedDate', () => {
      const book = createBook({metadata: {bookId: 1, publishedDate: '2024-01-01'}});
      expect(service.evaluateGroup(book, rule('equals', 'publishedDate'))).toBe(true);
    });

    it('should detect missing publishedDate', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('equals', 'publishedDate'))).toBe(false);
    });

    it('should detect present language', () => {
      const book = createBook({metadata: {bookId: 1, language: 'en'}});
      expect(service.evaluateGroup(book, rule('equals', 'language'))).toBe(true);
    });

    it('should detect present thumbnailUrl', () => {
      const book = createBook({metadata: {bookId: 1, thumbnailUrl: 'https://example.com/cover.jpg'}});
      expect(service.evaluateGroup(book, rule('equals', 'thumbnailUrl'))).toBe(true);
    });

    it('should detect missing thumbnailUrl', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'thumbnailUrl'))).toBe(true);
    });

    it('should detect present narrator', () => {
      const book = createBook({metadata: {bookId: 1, narrator: 'Stephen Fry'}});
      expect(service.evaluateGroup(book, rule('equals', 'narrator'))).toBe(true);
    });

    it('should detect present contentRating', () => {
      const book = createBook({metadata: {bookId: 1, contentRating: 'MATURE'}});
      expect(service.evaluateGroup(book, rule('equals', 'contentRating'))).toBe(true);
    });

    it('should detect present seriesName', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: 'Dune'}});
      expect(service.evaluateGroup(book, rule('equals', 'seriesName'))).toBe(true);
    });

    it('should detect missing seriesName', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('equals', 'seriesName'))).toBe(false);
    });
  });

  describe('identifier fields', () => {
    it('should detect present isbn13', () => {
      const book = createBook({metadata: {bookId: 1, isbn13: '9780123456789'}});
      expect(service.evaluateGroup(book, rule('equals', 'isbn13'))).toBe(true);
    });

    it('should detect missing isbn13', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'isbn13'))).toBe(true);
    });

    it('should detect present isbn10', () => {
      const book = createBook({metadata: {bookId: 1, isbn10: '0123456789'}});
      expect(service.evaluateGroup(book, rule('equals', 'isbn10'))).toBe(true);
    });

    it('should detect present asin', () => {
      const book = createBook({metadata: {bookId: 1, asin: 'B08N5WRWNW'}});
      expect(service.evaluateGroup(book, rule('equals', 'asin'))).toBe(true);
    });

    it('should detect missing asin', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'asin'))).toBe(true);
    });
  });

  describe('numeric fields', () => {
    it('should detect present pageCount', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 350}});
      expect(service.evaluateGroup(book, rule('equals', 'pageCount'))).toBe(true);
    });

    it('should detect missing pageCount', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('equals', 'pageCount'))).toBe(false);
    });

    it('should treat zero pageCount as present', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 0}});
      expect(service.evaluateGroup(book, rule('equals', 'pageCount'))).toBe(true);
    });

    it('should detect present seriesNumber', () => {
      const book = createBook({metadata: {bookId: 1, seriesNumber: 3}});
      expect(service.evaluateGroup(book, rule('equals', 'seriesNumber'))).toBe(true);
    });

    it('should detect present seriesTotal', () => {
      const book = createBook({metadata: {bookId: 1, seriesTotal: 10}});
      expect(service.evaluateGroup(book, rule('equals', 'seriesTotal'))).toBe(true);
    });

    it('should detect present ageRating', () => {
      const book = createBook({metadata: {bookId: 1, ageRating: 16}});
      expect(service.evaluateGroup(book, rule('equals', 'ageRating'))).toBe(true);
    });
  });

  describe('array fields', () => {
    it('should detect present authors', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Author One']}});
      expect(service.evaluateGroup(book, rule('equals', 'authors'))).toBe(true);
    });

    it('should detect empty authors as absent', () => {
      const book = createBook({metadata: {bookId: 1, authors: []}});
      expect(service.evaluateGroup(book, rule('equals', 'authors'))).toBe(false);
      expect(service.evaluateGroup(book, rule('not_equals', 'authors'))).toBe(true);
    });

    it('should detect missing authors (undefined) as absent', () => {
      const book = createBook({metadata: {bookId: 1, authors: undefined}});
      expect(service.evaluateGroup(book, rule('equals', 'authors'))).toBe(false);
    });

    it('should detect present categories', () => {
      const book = createBook({metadata: {bookId: 1, categories: ['Fantasy']}});
      expect(service.evaluateGroup(book, rule('equals', 'categories'))).toBe(true);
    });

    it('should detect empty categories as absent', () => {
      const book = createBook({metadata: {bookId: 1, categories: []}});
      expect(service.evaluateGroup(book, rule('equals', 'categories'))).toBe(false);
    });

    it('should detect present moods', () => {
      const book = createBook({metadata: {bookId: 1, moods: ['Dark', 'Atmospheric']}});
      expect(service.evaluateGroup(book, rule('equals', 'moods'))).toBe(true);
    });

    it('should detect missing moods as absent', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'moods'))).toBe(true);
    });

    it('should detect present tags', () => {
      const book = createBook({metadata: {bookId: 1, tags: ['favorite']}});
      expect(service.evaluateGroup(book, rule('equals', 'tags'))).toBe(true);
    });

    it('should detect missing tags as absent', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'tags'))).toBe(true);
    });
  });

  describe('rating fields', () => {
    it('should detect present personalRating', () => {
      const book = createBook({personalRating: 8});
      expect(service.evaluateGroup(book, rule('equals', 'personalRating'))).toBe(true);
    });

    it('should detect missing personalRating', () => {
      const book = createBook({personalRating: undefined});
      expect(service.evaluateGroup(book, rule('equals', 'personalRating'))).toBe(false);
      expect(service.evaluateGroup(book, rule('not_equals', 'personalRating'))).toBe(true);
    });

    it('should detect present amazonRating', () => {
      const book = createBook({metadata: {bookId: 1, amazonRating: 4.5}});
      expect(service.evaluateGroup(book, rule('equals', 'amazonRating'))).toBe(true);
    });

    it('should detect missing amazonRating', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'amazonRating'))).toBe(true);
    });

    it('should detect present goodreadsRating', () => {
      const book = createBook({metadata: {bookId: 1, goodreadsRating: 4.1}});
      expect(service.evaluateGroup(book, rule('equals', 'goodreadsRating'))).toBe(true);
    });

    it('should detect present hardcoverRating', () => {
      const book = createBook({metadata: {bookId: 1, hardcoverRating: 3.9}});
      expect(service.evaluateGroup(book, rule('equals', 'hardcoverRating'))).toBe(true);
    });

    it('should detect present ranobedbRating', () => {
      const book = createBook({metadata: {bookId: 1, ranobedbRating: 4.0}});
      expect(service.evaluateGroup(book, rule('equals', 'ranobedbRating'))).toBe(true);
    });

    it('should detect present lubimyczytacRating', () => {
      const book = createBook({metadata: {bookId: 1, lubimyczytacRating: 4.2}});
      expect(service.evaluateGroup(book, rule('equals', 'lubimyczytacRating'))).toBe(true);
    });

    it('should detect present audibleRating', () => {
      const book = createBook({metadata: {bookId: 1, audibleRating: 4.7}});
      expect(service.evaluateGroup(book, rule('equals', 'audibleRating'))).toBe(true);
    });
  });

  describe('review count fields', () => {
    it('should detect present amazonReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, amazonReviewCount: 1200}});
      expect(service.evaluateGroup(book, rule('equals', 'amazonReviewCount'))).toBe(true);
    });

    it('should detect missing amazonReviewCount', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'amazonReviewCount'))).toBe(true);
    });

    it('should detect present goodreadsReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, goodreadsReviewCount: 5000}});
      expect(service.evaluateGroup(book, rule('equals', 'goodreadsReviewCount'))).toBe(true);
    });

    it('should detect present hardcoverReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, hardcoverReviewCount: 300}});
      expect(service.evaluateGroup(book, rule('equals', 'hardcoverReviewCount'))).toBe(true);
    });

    it('should detect present audibleReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, audibleReviewCount: 500}});
      expect(service.evaluateGroup(book, rule('equals', 'audibleReviewCount'))).toBe(true);
    });
  });

  describe('external ID fields', () => {
    it('should detect present goodreadsId', () => {
      const book = createBook({metadata: {bookId: 1, goodreadsId: '12345'}});
      expect(service.evaluateGroup(book, rule('equals', 'goodreadsId'))).toBe(true);
    });

    it('should detect missing goodreadsId', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'goodreadsId'))).toBe(true);
    });

    it('should detect present hardcoverId', () => {
      const book = createBook({metadata: {bookId: 1, hardcoverId: 'hc-123'}});
      expect(service.evaluateGroup(book, rule('equals', 'hardcoverId'))).toBe(true);
    });

    it('should detect present googleId', () => {
      const book = createBook({metadata: {bookId: 1, googleId: 'gid-456'}});
      expect(service.evaluateGroup(book, rule('equals', 'googleId'))).toBe(true);
    });

    it('should detect present audibleId', () => {
      const book = createBook({metadata: {bookId: 1, audibleId: 'aud-789'}});
      expect(service.evaluateGroup(book, rule('equals', 'audibleId'))).toBe(true);
    });

    it('should detect present lubimyczytacId', () => {
      const book = createBook({metadata: {bookId: 1, lubimyczytacId: 'lub-321'}});
      expect(service.evaluateGroup(book, rule('equals', 'lubimyczytacId'))).toBe(true);
    });

    it('should detect present ranobedbId', () => {
      const book = createBook({metadata: {bookId: 1, ranobedbId: 'ran-654'}});
      expect(service.evaluateGroup(book, rule('equals', 'ranobedbId'))).toBe(true);
    });

    it('should detect present comicvineId', () => {
      const book = createBook({metadata: {bookId: 1, comicvineId: 'cv-987'}});
      expect(service.evaluateGroup(book, rule('equals', 'comicvineId'))).toBe(true);
    });

    it('should detect missing comicvineId', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'comicvineId'))).toBe(true);
    });
  });

  describe('boolean fields', () => {
    it('should detect present abridged (true)', () => {
      const book = createBook({metadata: {bookId: 1, abridged: true}});
      expect(service.evaluateGroup(book, rule('equals', 'abridged'))).toBe(true);
    });

    it('should detect present abridged (false)', () => {
      const book = createBook({metadata: {bookId: 1, abridged: false}});
      expect(service.evaluateGroup(book, rule('equals', 'abridged'))).toBe(true);
    });

    it('should detect missing abridged', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('equals', 'abridged'))).toBe(false);
      expect(service.evaluateGroup(book, rule('not_equals', 'abridged'))).toBe(true);
    });
  });

  describe('audiobook fields', () => {
    it('should detect present audiobookDuration', () => {
      const book = createBook({metadata: {bookId: 1, audiobookMetadata: {durationSeconds: 36000}}});
      expect(service.evaluateGroup(book, rule('equals', 'audiobookDuration'))).toBe(true);
    });

    it('should detect missing audiobookDuration (no audiobookMetadata)', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'audiobookDuration'))).toBe(true);
    });

    it('should detect zero audiobookDuration as present', () => {
      const book = createBook({metadata: {bookId: 1, audiobookMetadata: {durationSeconds: 0}}});
      expect(service.evaluateGroup(book, rule('equals', 'audiobookDuration'))).toBe(true);
    });
  });

  describe('comic metadata fields', () => {
    it('should detect present comicCharacters', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {characters: ['Batman', 'Joker']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicCharacters'))).toBe(true);
    });

    it('should detect empty comicCharacters as absent', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {characters: []}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicCharacters'))).toBe(false);
    });

    it('should detect missing comicCharacters (no comicMetadata)', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('not_equals', 'comicCharacters'))).toBe(true);
    });

    it('should detect present comicTeams', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {teams: ['Justice League']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicTeams'))).toBe(true);
    });

    it('should detect present comicLocations', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {locations: ['Gotham City']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicLocations'))).toBe(true);
    });

    it('should detect present comicPencillers', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {pencillers: ['Jim Lee']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicPencillers'))).toBe(true);
    });

    it('should detect present comicInkers', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {inkers: ['Scott Williams']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicInkers'))).toBe(true);
    });

    it('should detect present comicColorists', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {colorists: ['Alex Sinclair']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicColorists'))).toBe(true);
    });

    it('should detect present comicLetterers', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {letterers: ['Todd Klein']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicLetterers'))).toBe(true);
    });

    it('should detect present comicCoverArtists', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {coverArtists: ['Alex Ross']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicCoverArtists'))).toBe(true);
    });

    it('should detect present comicEditors', () => {
      const book = createBook({metadata: {bookId: 1, comicMetadata: {editors: ['Mark Doyle']}}});
      expect(service.evaluateGroup(book, rule('equals', 'comicEditors'))).toBe(true);
    });
  });

  describe('edge cases', () => {
    it('should return false for unknown metadata field', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('equals', 'nonExistentField'))).toBe(false);
    });

    it('should handle book with no metadata', () => {
      const book = createBook({metadata: undefined});
      expect(service.evaluateGroup(book, rule('equals', 'title'))).toBe(false);
      expect(service.evaluateGroup(book, rule('not_equals', 'title'))).toBe(true);
    });

    it('should handle non-string rule value gracefully', () => {
      const book = createBook({metadata: {bookId: 1, title: 'Test'}});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'metadataPresence', operator: 'equals', value: null} as never]
      };
      expect(service.evaluateGroup(book, g)).toBe(false);
    });

    it('should not require allBooks parameter', () => {
      const book = createBook({metadata: {bookId: 1, title: 'Test'}});
      expect(service.evaluateGroup(book, rule('equals', 'title'))).toBe(true);
    });
  });

  describe('combined rules', () => {
    it('should match books missing cover OR description', () => {
      const bookNoCover = createBook({metadata: {bookId: 1, title: 'Test', description: 'Has desc'}});
      const bookNoDesc = createBook({metadata: {bookId: 2, title: 'Test', thumbnailUrl: 'http://cover.jpg'}});
      const bookHasBoth = createBook({metadata: {bookId: 3, title: 'Test', description: 'Has desc', thumbnailUrl: 'http://cover.jpg'}});

      const group: GroupRule = {
        name: 'test', type: 'group', join: 'or',
        rules: [
          {field: 'metadataPresence', operator: 'not_equals', value: 'thumbnailUrl'},
          {field: 'metadataPresence', operator: 'not_equals', value: 'description'}
        ]
      };

      expect(service.evaluateGroup(bookNoCover, group)).toBe(true);
      expect(service.evaluateGroup(bookNoDesc, group)).toBe(true);
      expect(service.evaluateGroup(bookHasBoth, group)).toBe(false);
    });

    it('should match books with complete identifiers', () => {
      const bookComplete = createBook({metadata: {bookId: 1, isbn13: '9780123456789', goodreadsId: '12345'}});
      const bookPartial = createBook({metadata: {bookId: 2, isbn13: '9780123456789'}});

      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'metadataPresence', operator: 'equals', value: 'isbn13'},
          {field: 'metadataPresence', operator: 'equals', value: 'goodreadsId'}
        ]
      };

      expect(service.evaluateGroup(bookComplete, group)).toBe(true);
      expect(service.evaluateGroup(bookPartial, group)).toBe(false);
    });

    it('should work with non-metadataPresence rules in same group', () => {
      const book = createBook({
        readStatus: ReadStatus.UNREAD,
        metadata: {bookId: 1, title: 'Test'}
      });

      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'readStatus', operator: 'equals', value: 'UNREAD'},
          {field: 'metadataPresence', operator: 'not_equals', value: 'thumbnailUrl'}
        ]
      };

      expect(service.evaluateGroup(book, group)).toBe(true);
    });
  });
});
