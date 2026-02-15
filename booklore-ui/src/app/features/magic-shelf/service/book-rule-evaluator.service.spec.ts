import {beforeEach, describe, expect, it} from 'vitest';
import {TestBed} from '@angular/core/testing';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {Book, ReadStatus} from '../../book/model/book.model';
import {GroupRule} from '../component/magic-shelf-component';

describe('BookRuleEvaluatorService', () => {
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

  describe('fileType filtering', () => {
    it('should filter EPUB books correctly when rule uses "epub"', () => {
      const book = createBook({bookType: 'EPUB'});
      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'epub'
          }
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });

    it('should filter PDF books correctly when rule uses "pdf"', () => {
      const book = createBook({bookType: 'PDF'});
      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'pdf'
          }
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });

    it('should handle CBX books correctly for cbr, cbz, and cb7 rules', () => {
      const book = createBook({bookType: 'CBX'});

      const cbrGroup: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'cbr'
          }
        ]
      };
      expect(service.evaluateGroup(book, cbrGroup)).toBe(true);

      const cbzGroup: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'cbz'
          }
        ]
      };
      expect(service.evaluateGroup(book, cbzGroup)).toBe(true);

      const cb7Group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'equals',
            value: 'cb7'
          }
        ]
      };
      expect(service.evaluateGroup(book, cb7Group)).toBe(true);
    });

    it('should handle not_equals operator correctly', () => {
      const epubBook = createBook({bookType: 'EPUB'});
      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'not_equals',
            value: 'pdf'
          }
        ]
      };

      const result = service.evaluateGroup(epubBook, group);
      expect(result).toBe(true);

      const pdfBook = createBook({bookType: 'PDF'});
      const result2 = service.evaluateGroup(pdfBook, group);
      expect(result2).toBe(false);
    });

    it('should filter EPUB books correctly when rule uses "not_equals" with "epub"', () => {
      const epubBook = createBook({bookType: 'EPUB'});
      const pdfBook = createBook({bookType: 'PDF'});

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {
            field: 'fileType',
            operator: 'not_equals',
            value: 'epub'
          }
        ]
      };

      expect(service.evaluateGroup(epubBook, group)).toBe(false);

      expect(service.evaluateGroup(pdfBook, group)).toBe(true);
    });
  });

  describe('evaluateGroup', () => {
    it('should evaluate group rules with AND logic', () => {
      const book = createBook({
        bookType: 'EPUB'
      });

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'and',
        rules: [
          {field: 'fileType', operator: 'equals', value: 'epub'},
          {field: 'language', operator: 'equals', value: 'en'}
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });

    it('should evaluate group rules with OR logic', () => {
      const book = createBook({bookType: 'PDF'});

      const group: GroupRule = {
        name: 'test',
        type: 'group',
        join: 'or',
        rules: [
          {field: 'fileType', operator: 'equals', value: 'epub'},
          {field: 'fileType', operator: 'equals', value: 'pdf'}
        ]
      };

      const result = service.evaluateGroup(book, group);
      expect(result).toBe(true);
    });
  });

  const rule = (field: string, operator: string, value: unknown, valueEnd?: unknown): GroupRule => ({
    name: 'test', type: 'group', join: 'and',
    rules: [{field, operator, value, valueEnd} as never]
  });

  const seriesBook = (num: number, status: ReadStatus, extra: Partial<Book> = {}): Book => createBook({
    id: num * 100,
    readStatus: status,
    metadata: {
      bookId: num * 100,
      title: `Book ${num}`,
      seriesName: 'Dune',
      seriesNumber: num,
      authors: [],
      categories: [],
      ...extra.metadata
    },
    ...extra
  });

  describe('within_last operator', () => {
    it('should match addedOn within last 30 days', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'within_last', 30, 'days'))).toBe(true);
    });

    it('should not match addedOn older than 30 days for within_last 30 days', () => {
      const old = new Date();
      old.setDate(old.getDate() - 60);
      const book = createBook({addedOn: old.toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'within_last', 30, 'days'))).toBe(false);
    });

    it('should work with weeks unit', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'within_last', 2, 'weeks'))).toBe(true);
    });

    it('should work with months unit', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'within_last', 3, 'months'))).toBe(true);
    });

    it('should work with years unit', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'within_last', 1, 'years'))).toBe(true);
    });

    it('should work with dateFinished', () => {
      const book = createBook({dateFinished: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('dateFinished', 'within_last', 7, 'days'))).toBe(true);
    });

    it('should work with lastReadTime', () => {
      const book = createBook({lastReadTime: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('lastReadTime', 'within_last', 7, 'days'))).toBe(true);
    });

    it('should work with publishedDate', () => {
      const book = createBook({metadata: {bookId: 1, publishedDate: new Date().toISOString().split('T')[0]}});
      expect(service.evaluateGroup(book, rule('publishedDate', 'within_last', 30, 'days'))).toBe(true);
    });

    it('should return false for null date', () => {
      const book = createBook({addedOn: undefined});
      expect(service.evaluateGroup(book, rule('addedOn', 'within_last', 30, 'days'))).toBe(false);
    });

    it('should return false for non-date field', () => {
      const book = createBook({metadata: {bookId: 1, title: 'Test', pageCount: 300}});
      expect(service.evaluateGroup(book, rule('pageCount', 'within_last', 30, 'days'))).toBe(false);
    });
  });

  describe('older_than operator', () => {
    it('should match addedOn older than 30 days', () => {
      const old = new Date();
      old.setDate(old.getDate() - 60);
      const book = createBook({addedOn: old.toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'older_than', 30, 'days'))).toBe(true);
    });

    it('should not match recent addedOn for older_than 30 days', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'older_than', 30, 'days'))).toBe(false);
    });

    it('should work with months unit', () => {
      const old = new Date();
      old.setFullYear(old.getFullYear() - 1);
      const book = createBook({addedOn: old.toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'older_than', 6, 'months'))).toBe(true);
    });

    it('should work with lastReadTime', () => {
      const old = new Date();
      old.setMonth(old.getMonth() - 4);
      const book = createBook({lastReadTime: old.toISOString()});
      expect(service.evaluateGroup(book, rule('lastReadTime', 'older_than', 3, 'months'))).toBe(true);
    });

    it('should return false for null date', () => {
      const book = createBook({lastReadTime: undefined});
      expect(service.evaluateGroup(book, rule('lastReadTime', 'older_than', 3, 'months'))).toBe(false);
    });
  });

  describe('this_period operator', () => {
    it('should match dateFinished this year', () => {
      const book = createBook({dateFinished: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('dateFinished', 'this_period', 'year'))).toBe(true);
    });

    it('should not match dateFinished from last year for this_period year', () => {
      const old = new Date();
      old.setFullYear(old.getFullYear() - 2);
      const book = createBook({dateFinished: old.toISOString()});
      expect(service.evaluateGroup(book, rule('dateFinished', 'this_period', 'year'))).toBe(false);
    });

    it('should match addedOn this month', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'this_period', 'month'))).toBe(true);
    });

    it('should match addedOn this week', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'this_period', 'week'))).toBe(true);
    });

    it('should work with publishedDate', () => {
      const book = createBook({metadata: {bookId: 1, publishedDate: new Date().toISOString().split('T')[0]}});
      expect(service.evaluateGroup(book, rule('publishedDate', 'this_period', 'year'))).toBe(true);
    });

    it('should return false for null date', () => {
      const book = createBook({dateFinished: undefined});
      expect(service.evaluateGroup(book, rule('dateFinished', 'this_period', 'year'))).toBe(false);
    });
  });

  describe('seriesStatus', () => {
    it('should match "reading" when a series book is READING', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READING);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'reading'), allBooks)).toBe(true);
    });

    it('should match "reading" when a series book is RE_READING', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.RE_READING);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'reading'), allBooks)).toBe(true);
    });

    it('should not match "reading" when no book is READING or RE_READING', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'reading'), allBooks)).toBe(false);
    });

    it('should match "not_started" when no book has been read', () => {
      const b1 = seriesBook(1, ReadStatus.UNREAD);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'not_started'), allBooks)).toBe(true);
    });

    it('should not match "not_started" when a book is READING', () => {
      const b1 = seriesBook(1, ReadStatus.UNREAD);
      const b2 = seriesBook(2, ReadStatus.READING);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'not_started'), allBooks)).toBe(false);
    });

    it('should match "fully_read" when all books are READ', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READ);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'fully_read'), allBooks)).toBe(true);
    });

    it('should not match "fully_read" when some books are not READ', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'fully_read'), allBooks)).toBe(false);
    });

    it('should match "completed" when own last book in series', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 3}});
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'completed'), allBooks)).toBe(true);
    });

    it('should not match "completed" when missing last book', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 5}});
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'completed'), allBooks)).toBe(false);
    });

    it('should match "ongoing" when missing last book', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 5}});
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'ongoing'), allBooks)).toBe(true);
    });

    it('should not match "ongoing" when own last book', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 3}});
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b3];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'ongoing'), allBooks)).toBe(false);
    });

    it('should negate with not_equals', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READING);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'not_equals', 'reading'), allBooks)).toBe(false);
    });

    it('should return false for book without series', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('seriesStatus', 'equals', 'reading'), [book])).toBe(false);
    });
  });

  describe('seriesGaps', () => {
    it('should match "any_gap" when series has a missing number', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b3];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'any_gap'), allBooks)).toBe(true);
    });

    it('should not match "any_gap" when series is contiguous', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'any_gap'), allBooks)).toBe(false);
    });

    it('should match "missing_first" when book 1 is absent', () => {
      const b2 = seriesBook(2, ReadStatus.READ);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b2, b3];
      expect(service.evaluateGroup(b2, rule('seriesGaps', 'equals', 'missing_first'), allBooks)).toBe(true);
    });

    it('should not match "missing_first" when book 1 exists', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'missing_first'), allBooks)).toBe(false);
    });

    it('should match "missing_latest" when final book is absent', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 5}});
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'missing_latest'), allBooks)).toBe(true);
    });

    it('should not match "missing_latest" when final book is present', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 3}});
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b3];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'missing_latest'), allBooks)).toBe(false);
    });

    it('should not match "missing_latest" when no seriesTotal is set', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'missing_latest'), allBooks)).toBe(false);
    });

    it('should match "duplicate_number" when two books share a number', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b1dup = seriesBook(1, ReadStatus.UNREAD);
      b1dup.id = 999;
      const allBooks = [b1, b1dup];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'duplicate_number'), allBooks)).toBe(true);
    });

    it('should not match "duplicate_number" when all numbers are unique', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'duplicate_number'), allBooks)).toBe(false);
    });

    it('should return false for book without series', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('seriesGaps', 'equals', 'any_gap'), [book])).toBe(false);
    });
  });

  describe('seriesPosition', () => {
    it('should match "first_in_series" for lowest numbered book', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'first_in_series'), allBooks)).toBe(true);
      expect(service.evaluateGroup(b2, rule('seriesPosition', 'equals', 'first_in_series'), allBooks)).toBe(false);
    });

    it('should match "last_in_series" for highest numbered book', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b3, rule('seriesPosition', 'equals', 'last_in_series'), allBooks)).toBe(true);
      expect(service.evaluateGroup(b2, rule('seriesPosition', 'equals', 'last_in_series'), allBooks)).toBe(false);
    });

    it('should match "next_unread" for the first unread after a read book', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b2, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(true);
    });

    it('should not match "next_unread" for a later unread book', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b3, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(false);
    });

    it('should not match "next_unread" for a READ book', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(false);
    });

    it('should not match "next_unread" when no prior book is read', () => {
      const b1 = seriesBook(1, ReadStatus.UNREAD);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(false);
    });

    it('should negate with not_equals', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'not_equals', 'first_in_series'), allBooks)).toBe(false);
    });

    it('should return false for book without seriesNumber', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: 'Dune'}});
      expect(service.evaluateGroup(book, rule('seriesPosition', 'equals', 'first_in_series'), [book])).toBe(false);
    });

    it('should return false for book without series', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('seriesPosition', 'equals', 'first_in_series'), [book])).toBe(false);
    });
  });

  describe('readingProgress', () => {
    it('should return max of all progress sources', () => {
      const book = createBook({
        epubProgress: {cfi: '', percentage: 45},
        pdfProgress: {page: 10, percentage: 20},
        koreaderProgress: {percentage: 80}
      });
      expect(service.evaluateGroup(book, rule('readingProgress', 'greater_than', 70))).toBe(true);
      expect(service.evaluateGroup(book, rule('readingProgress', 'greater_than', 85))).toBe(false);
    });

    it('should return 0 when no progress exists', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('readingProgress', 'equals', 0))).toBe(true);
    });

    it('should work with less_than', () => {
      const book = createBook({epubProgress: {cfi: '', percentage: 5}});
      expect(service.evaluateGroup(book, rule('readingProgress', 'less_than', 10))).toBe(true);
    });

    it('should work with in_between', () => {
      const book = createBook({pdfProgress: {page: 50, percentage: 50}});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'readingProgress', operator: 'in_between', value: null, valueStart: 40, valueEnd: 60}]
      };
      expect(service.evaluateGroup(book, g)).toBe(true);
    });

    it('should pick kobo progress when it is the highest', () => {
      const book = createBook({koboProgress: {percentage: 95}});
      expect(service.evaluateGroup(book, rule('readingProgress', 'greater_than', 90))).toBe(true);
    });

    it('should pick cbx progress when it is the highest', () => {
      const book = createBook({cbxProgress: {page: 5, percentage: 60}});
      expect(service.evaluateGroup(book, rule('readingProgress', 'greater_than_equal_to', 60))).toBe(true);
    });

    it('should pick audiobook progress when it is the highest', () => {
      const book = createBook({audiobookProgress: {positionMs: 1000, percentage: 75}});
      expect(service.evaluateGroup(book, rule('readingProgress', 'greater_than', 70))).toBe(true);
    });
  });

  describe('new fields', () => {
    it('should filter by addedOn with equals', () => {
      const date = '2025-06-15T10:00:00Z';
      const book = createBook({addedOn: date});
      expect(service.evaluateGroup(book, rule('addedOn', 'equals', date))).toBe(true);
    });

    it('should filter by description with contains', () => {
      const book = createBook({metadata: {bookId: 1, description: 'A tale of dragons and fire'}});
      expect(service.evaluateGroup(book, rule('description', 'contains', 'dragon'))).toBe(true);
    });

    it('should filter by narrator with contains', () => {
      const book = createBook({metadata: {bookId: 1, narrator: 'Stephen Fry'}});
      expect(service.evaluateGroup(book, rule('narrator', 'contains', 'fry'))).toBe(true);
    });

    it('should filter by ageRating with greater_than', () => {
      const book = createBook({metadata: {bookId: 1, ageRating: 16}});
      expect(service.evaluateGroup(book, rule('ageRating', 'greater_than', 13))).toBe(true);
    });

    it('should filter by contentRating with equals', () => {
      const book = createBook({metadata: {bookId: 1, contentRating: 'MATURE'}});
      expect(service.evaluateGroup(book, rule('contentRating', 'equals', 'MATURE'))).toBe(true);
    });

    it('should filter by audibleRating with greater_than_equal_to', () => {
      const book = createBook({metadata: {bookId: 1, audibleRating: 4.5}});
      expect(service.evaluateGroup(book, rule('audibleRating', 'greater_than_equal_to', 4))).toBe(true);
    });

    it('should filter by audibleReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, audibleReviewCount: 500}});
      expect(service.evaluateGroup(book, rule('audibleReviewCount', 'greater_than', 100))).toBe(true);
    });

    it('should filter by abridged with equals true', () => {
      const book = createBook({metadata: {bookId: 1, abridged: true}});
      expect(service.evaluateGroup(book, rule('abridged', 'equals', true))).toBe(true);
    });

    it('should filter by abridged with equals false', () => {
      const book = createBook({metadata: {bookId: 1, abridged: false}});
      expect(service.evaluateGroup(book, rule('abridged', 'equals', false))).toBe(true);
    });

    it('should filter by audiobookDuration', () => {
      const book = createBook({
        metadata: {bookId: 1, audiobookMetadata: {durationSeconds: 36000}}
      });
      expect(service.evaluateGroup(book, rule('audiobookDuration', 'greater_than', 30000))).toBe(true);
    });

    it('should return null for audiobookDuration when no metadata', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('audiobookDuration', 'is_empty', null))).toBe(true);
    });

    it('should filter by isPhysical', () => {
      const book = createBook({isPhysical: true});
      expect(service.evaluateGroup(book, rule('isPhysical', 'equals', true))).toBe(true);
    });

    it('should filter by lubimyczytacRating', () => {
      const book = createBook({metadata: {bookId: 1, lubimyczytacRating: 4.2}});
      expect(service.evaluateGroup(book, rule('lubimyczytacRating', 'greater_than', 4))).toBe(true);
    });

    it('should filter by categories with includes_any', () => {
      const book = createBook({metadata: {bookId: 1, categories: ['Science Fiction', 'Fantasy']}});
      expect(service.evaluateGroup(book, rule('categories', 'includes_any', ['fantasy']))).toBe(true);
    });

    it('should handle categories with includes_all', () => {
      const book = createBook({metadata: {bookId: 1, categories: ['Science Fiction', 'Fantasy']}});
      expect(service.evaluateGroup(book, rule('categories', 'includes_all', ['science fiction', 'fantasy']))).toBe(true);
    });

    it('should handle categories with excludes_all', () => {
      const book = createBook({metadata: {bookId: 1, categories: ['Science Fiction']}});
      expect(service.evaluateGroup(book, rule('categories', 'excludes_all', ['horror', 'romance']))).toBe(true);
    });

    it('should filter by description is_empty', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('description', 'is_empty', null))).toBe(true);
    });

    it('should filter by narrator is_not_empty', () => {
      const book = createBook({metadata: {bookId: 1, narrator: 'John Smith'}});
      expect(service.evaluateGroup(book, rule('narrator', 'is_not_empty', null))).toBe(true);
    });
  });

  describe('combined Part 3 rules', () => {
    it('should evaluate "Gathering Dust": UNREAD AND addedOn older_than 1 year', () => {
      const old = new Date();
      old.setFullYear(old.getFullYear() - 2);
      const book = createBook({readStatus: ReadStatus.UNREAD, addedOn: old.toISOString()});
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'readStatus', operator: 'equals', value: 'UNREAD'},
          {field: 'addedOn', operator: 'older_than', value: 1, valueEnd: 'years'}
        ]
      };
      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should evaluate "Barely Started": readingProgress < 10 AND READING/RE_READING', () => {
      const book = createBook({
        readStatus: ReadStatus.READING,
        epubProgress: {cfi: '', percentage: 5}
      });
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'readingProgress', operator: 'less_than', value: 10},
          {field: 'readStatus', operator: 'includes_any', value: ['READING', 'RE_READING']}
        ]
      };
      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should evaluate "Completed Series Not Started": completed AND not_started', () => {
      const b1 = seriesBook(1, ReadStatus.UNREAD, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 3}});
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'seriesStatus', operator: 'equals', value: 'completed'},
          {field: 'seriesStatus', operator: 'equals', value: 'not_started'}
        ]
      };
      expect(service.evaluateGroup(b1, group, allBooks)).toBe(true);
    });

    it('should evaluate "Ongoing Series Im Behind On": ongoing AND NOT reading', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 5}});
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b1, b2];
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'seriesStatus', operator: 'equals', value: 'ongoing'},
          {field: 'seriesStatus', operator: 'not_equals', value: 'reading'}
        ]
      };
      expect(service.evaluateGroup(b1, group, allBooks)).toBe(true);
    });
  });

  describe('evaluateGroup edge cases', () => {
    it('should handle nested groups', () => {
      const book = createBook({readStatus: ReadStatus.READING, metadata: {bookId: 1, title: 'Epic', language: 'en', categories: ['Fantasy']}});
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'language', operator: 'equals', value: 'en'},
          {
            type: 'group', join: 'or', rules: [
              {field: 'readStatus', operator: 'equals', value: 'READ'},
              {field: 'readStatus', operator: 'equals', value: 'READING'}
            ]
          } as never
        ]
      };
      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should pass allBooks through to nested groups', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READING);
      const allBooks = [b1, b2];
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {
            type: 'group', join: 'and', rules: [
              {field: 'seriesStatus', operator: 'equals', value: 'reading'}
            ]
          } as never
        ]
      };
      expect(service.evaluateGroup(b1, group, allBooks)).toBe(true);
    });

    it('should return true for AND with empty rules', () => {
      const book = createBook();
      const group: GroupRule = {name: 'test', type: 'group', join: 'and', rules: []};
      expect(service.evaluateGroup(book, group)).toBe(true);
    });

    it('should return false for OR with empty rules', () => {
      const book = createBook();
      const group: GroupRule = {name: 'test', type: 'group', join: 'or', rules: []};
      expect(service.evaluateGroup(book, group)).toBe(false);
    });

    it('should fail AND when one rule fails', () => {
      const book = createBook({readStatus: ReadStatus.READING});
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [
          {field: 'readStatus', operator: 'equals', value: 'READING'},
          {field: 'readStatus', operator: 'equals', value: 'READ'}
        ]
      };
      expect(service.evaluateGroup(book, group)).toBe(false);
    });

    it('should pass OR when one rule passes', () => {
      const book = createBook({readStatus: ReadStatus.READING});
      const group: GroupRule = {
        name: 'test', type: 'group', join: 'or',
        rules: [
          {field: 'readStatus', operator: 'equals', value: 'READ'},
          {field: 'readStatus', operator: 'equals', value: 'READING'}
        ]
      };
      expect(service.evaluateGroup(book, group)).toBe(true);
    });
  });

  describe('equals and not_equals edge cases', () => {
    it('should match equals on array field (authors)', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson', 'Robert Jordan']}});
      expect(service.evaluateGroup(book, rule('authors', 'equals', 'brandon sanderson'))).toBe(true);
    });

    it('should not match equals on array when value absent', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'equals', 'patrick rothfuss'))).toBe(false);
    });

    it('should match not_equals on array field (all must differ)', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'not_equals', 'patrick rothfuss'))).toBe(true);
    });

    it('should not match not_equals on array when value present', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'not_equals', 'brandon sanderson'))).toBe(false);
    });

    it('should compare dates with equals', () => {
      const date = '2024-06-15T00:00:00Z';
      const book = createBook({dateFinished: date});
      expect(service.evaluateGroup(book, rule('dateFinished', 'equals', date))).toBe(true);
    });

    it('should compare dates with not_equals', () => {
      const book = createBook({dateFinished: '2024-06-15T00:00:00Z'});
      expect(service.evaluateGroup(book, rule('dateFinished', 'not_equals', '2024-01-01T00:00:00Z'))).toBe(true);
    });

    it('should compare strings case-insensitively', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'equals', 'THE GREAT GATSBY'))).toBe(true);
    });

    it('should compare numbers with equals', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 350}});
      expect(service.evaluateGroup(book, rule('pageCount', 'equals', 350))).toBe(true);
    });

    it('should handle library id (numeric id field) with equals', () => {
      const book = createBook({libraryId: 42});
      expect(service.evaluateGroup(book, rule('library', 'equals', 42))).toBe(true);
    });

    it('should handle shelf id (numeric id field) with includes_any', () => {
      const book = createBook({shelves: [{id: 5, name: 'Fav', icon: ''}]});
      expect(service.evaluateGroup(book, rule('shelf', 'includes_any', [5]))).toBe(true);
    });

    it('should handle shelf with excludes_all', () => {
      const book = createBook({shelves: [{id: 5, name: 'Fav', icon: ''}]});
      expect(service.evaluateGroup(book, rule('shelf', 'excludes_all', [99]))).toBe(true);
      expect(service.evaluateGroup(book, rule('shelf', 'excludes_all', [5]))).toBe(false);
    });

    it('should handle readStatus UNSET when book has no readStatus', () => {
      const book = createBook({readStatus: undefined});
      expect(service.evaluateGroup(book, rule('readStatus', 'equals', 'UNSET'))).toBe(true);
    });
  });

  describe('text operators', () => {
    it('should match contains on array field (authors)', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'contains', 'sanderson'))).toBe(true);
    });

    it('should not match contains on array when no element matches', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'contains', 'rothfuss'))).toBe(false);
    });

    it('should match does_not_contain on string', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'does_not_contain', 'hobbit'))).toBe(true);
    });

    it('should not match does_not_contain when string matches', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'does_not_contain', 'gatsby'))).toBe(false);
    });

    it('should match does_not_contain on array field', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'does_not_contain', 'rothfuss'))).toBe(true);
    });

    it('should not match does_not_contain on array when any element matches', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson', 'Robert Jordan']}});
      expect(service.evaluateGroup(book, rule('authors', 'does_not_contain', 'jordan'))).toBe(false);
    });

    it('should match starts_with on string', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'starts_with', 'the great'))).toBe(true);
    });

    it('should not match starts_with when prefix differs', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'starts_with', 'gatsby'))).toBe(false);
    });

    it('should match starts_with on array (authors)', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'starts_with', 'brandon'))).toBe(true);
    });

    it('should match ends_with on string', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'ends_with', 'gatsby'))).toBe(true);
    });

    it('should not match ends_with when suffix differs', () => {
      const book = createBook({metadata: {bookId: 1, title: 'The Great Gatsby'}});
      expect(service.evaluateGroup(book, rule('title', 'ends_with', 'great'))).toBe(false);
    });

    it('should match ends_with on array (authors)', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Brandon Sanderson']}});
      expect(service.evaluateGroup(book, rule('authors', 'ends_with', 'sanderson'))).toBe(true);
    });

    it('should return false for contains on non-string value', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 350}});
      expect(service.evaluateGroup(book, rule('pageCount', 'contains', '35'))).toBe(false);
    });

    it('should return true for does_not_contain on non-string value', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 350}});
      expect(service.evaluateGroup(book, rule('pageCount', 'does_not_contain', '35'))).toBe(true);
    });

    it('should return false for starts_with on non-string value', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 350}});
      expect(service.evaluateGroup(book, rule('pageCount', 'starts_with', '3'))).toBe(false);
    });

    it('should return false for ends_with on non-string value', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 350}});
      expect(service.evaluateGroup(book, rule('pageCount', 'ends_with', '0'))).toBe(false);
    });
  });

  describe('date comparison operators', () => {
    it('should match greater_than on dates', () => {
      const book = createBook({dateFinished: '2024-06-15T00:00:00Z'});
      expect(service.evaluateGroup(book, rule('dateFinished', 'greater_than', '2024-01-01T00:00:00Z'))).toBe(true);
    });

    it('should not match greater_than when date is earlier', () => {
      const book = createBook({dateFinished: '2024-01-01T00:00:00Z'});
      expect(service.evaluateGroup(book, rule('dateFinished', 'greater_than', '2024-06-15T00:00:00Z'))).toBe(false);
    });

    it('should match greater_than_equal_to on exact date', () => {
      const date = '2024-06-15T00:00:00Z';
      const book = createBook({dateFinished: date});
      expect(service.evaluateGroup(book, rule('dateFinished', 'greater_than_equal_to', date))).toBe(true);
    });

    it('should match less_than on dates', () => {
      const book = createBook({dateFinished: '2024-01-01T00:00:00Z'});
      expect(service.evaluateGroup(book, rule('dateFinished', 'less_than', '2024-06-15T00:00:00Z'))).toBe(true);
    });

    it('should match less_than_equal_to on exact date', () => {
      const date = '2024-06-15T00:00:00Z';
      const book = createBook({dateFinished: date});
      expect(service.evaluateGroup(book, rule('dateFinished', 'less_than_equal_to', date))).toBe(true);
    });

    it('should match in_between on dates', () => {
      const book = createBook({dateFinished: '2024-06-15T00:00:00Z'});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'dateFinished', operator: 'in_between', value: null, valueStart: '2024-01-01T00:00:00Z', valueEnd: '2024-12-31T00:00:00Z'}]
      };
      expect(service.evaluateGroup(book, g)).toBe(true);
    });

    it('should not match in_between when date outside range', () => {
      const book = createBook({dateFinished: '2023-06-15T00:00:00Z'});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'dateFinished', operator: 'in_between', value: null, valueStart: '2024-01-01T00:00:00Z', valueEnd: '2024-12-31T00:00:00Z'}]
      };
      expect(service.evaluateGroup(book, g)).toBe(false);
    });

    it('should return false for in_between when value is null', () => {
      const book = createBook({dateFinished: undefined});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'dateFinished', operator: 'in_between', value: null, valueStart: '2024-01-01T00:00:00Z', valueEnd: '2024-12-31T00:00:00Z'}]
      };
      expect(service.evaluateGroup(book, g)).toBe(false);
    });
  });

  describe('numeric comparison edge cases', () => {
    it('should match less_than_equal_to on number', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 200}});
      expect(service.evaluateGroup(book, rule('pageCount', 'less_than_equal_to', 200))).toBe(true);
      expect(service.evaluateGroup(book, rule('pageCount', 'less_than_equal_to', 199))).toBe(false);
    });

    it('should match in_between on number', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 300}});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'pageCount', operator: 'in_between', value: null, valueStart: 100, valueEnd: 500}]
      };
      expect(service.evaluateGroup(book, g)).toBe(true);
    });

    it('should match fileSize with greater_than', () => {
      const book = createBook({fileSizeKb: 5000});
      expect(service.evaluateGroup(book, rule('fileSize', 'greater_than', 1000))).toBe(true);
    });

    it('should match metadataScore with less_than', () => {
      const book = createBook({metadataMatchScore: 45});
      expect(service.evaluateGroup(book, rule('metadataScore', 'less_than', 50))).toBe(true);
    });

    it('should match personalRating with equals', () => {
      const book = createBook({personalRating: 8});
      expect(service.evaluateGroup(book, rule('personalRating', 'equals', 8))).toBe(true);
    });

    it('should match seriesNumber with greater_than', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: 'Dune', seriesNumber: 5}});
      expect(service.evaluateGroup(book, rule('seriesNumber', 'greater_than', 3))).toBe(true);
    });

    it('should match seriesTotal with equals', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: 'Dune', seriesTotal: 10}});
      expect(service.evaluateGroup(book, rule('seriesTotal', 'equals', 10))).toBe(true);
    });
  });

  describe('is_empty and is_not_empty edge cases', () => {
    it('should match is_empty on empty string', () => {
      const book = createBook({metadata: {bookId: 1, title: '  '}});
      expect(service.evaluateGroup(book, rule('title', 'is_empty', null))).toBe(true);
    });

    it('should not match is_empty on non-empty string', () => {
      const book = createBook({metadata: {bookId: 1, title: 'Test'}});
      expect(service.evaluateGroup(book, rule('title', 'is_empty', null))).toBe(false);
    });

    it('should match is_empty on empty array', () => {
      const book = createBook({metadata: {bookId: 1, authors: []}});
      expect(service.evaluateGroup(book, rule('authors', 'is_empty', null))).toBe(true);
    });

    it('should not match is_empty on non-empty array', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Author']}});
      expect(service.evaluateGroup(book, rule('authors', 'is_empty', null))).toBe(false);
    });

    it('should match is_empty on null value', () => {
      const book = createBook({metadata: {bookId: 1, publisher: undefined}});
      expect(service.evaluateGroup(book, rule('publisher', 'is_empty', null))).toBe(true);
    });

    it('should return false for is_empty on a number', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 0}});
      expect(service.evaluateGroup(book, rule('pageCount', 'is_empty', null))).toBe(false);
    });

    it('should match is_not_empty on non-empty array', () => {
      const book = createBook({metadata: {bookId: 1, tags: ['sci-fi']}});
      expect(service.evaluateGroup(book, rule('tags', 'is_not_empty', null))).toBe(true);
    });

    it('should not match is_not_empty on null', () => {
      const book = createBook({metadata: {bookId: 1, isbn13: undefined}});
      expect(service.evaluateGroup(book, rule('isbn13', 'is_not_empty', null))).toBe(false);
    });

    it('should return true for is_not_empty on a number', () => {
      const book = createBook({metadata: {bookId: 1, pageCount: 100}});
      expect(service.evaluateGroup(book, rule('pageCount', 'is_not_empty', null))).toBe(true);
    });
  });

  describe('multi-value operator edge cases', () => {
    it('should not match includes_any when no values match', () => {
      const book = createBook({metadata: {bookId: 1, categories: ['Fiction']}});
      expect(service.evaluateGroup(book, rule('categories', 'includes_any', ['horror', 'romance']))).toBe(false);
    });

    it('should match includes_all when all present', () => {
      const book = createBook({metadata: {bookId: 1, moods: ['dark', 'atmospheric', 'tense']}});
      expect(service.evaluateGroup(book, rule('moods', 'includes_all', ['dark', 'tense']))).toBe(true);
    });

    it('should not match includes_all when one is missing', () => {
      const book = createBook({metadata: {bookId: 1, moods: ['dark', 'atmospheric']}});
      expect(service.evaluateGroup(book, rule('moods', 'includes_all', ['dark', 'tense']))).toBe(false);
    });

    it('should not match excludes_all when one value matches', () => {
      const book = createBook({metadata: {bookId: 1, tags: ['sci-fi', 'space']}});
      expect(service.evaluateGroup(book, rule('tags', 'excludes_all', ['space', 'western']))).toBe(false);
    });

    it('should match excludes_all when no values match', () => {
      const book = createBook({metadata: {bookId: 1, tags: ['sci-fi', 'space']}});
      expect(service.evaluateGroup(book, rule('tags', 'excludes_all', ['western', 'noir']))).toBe(true);
    });

    it('should handle includes_any on readStatus', () => {
      const book = createBook({readStatus: ReadStatus.PAUSED});
      expect(service.evaluateGroup(book, rule('readStatus', 'includes_any', ['PAUSED', 'ABANDONED']))).toBe(true);
    });

    it('should handle includes_any on language', () => {
      const book = createBook({metadata: {bookId: 1, language: 'en'}});
      expect(service.evaluateGroup(book, rule('language', 'includes_any', ['en', 'fr']))).toBe(true);
    });

    it('should handle includes_any with fileType mapping', () => {
      const book = createBook({bookType: 'CBX'});
      expect(service.evaluateGroup(book, rule('fileType', 'includes_any', ['cbr', 'pdf']))).toBe(true);
    });

    it('should handle null rule value as empty list', () => {
      const book = createBook({metadata: {bookId: 1, authors: ['Author']}});
      expect(service.evaluateGroup(book, rule('authors', 'includes_any', null))).toBe(false);
    });
  });

  describe('fileType mapping edge cases', () => {
    it('should map azw to azw3', () => {
      const book = createBook({bookType: 'AZW3'});
      expect(service.evaluateGroup(book, rule('fileType', 'equals', 'azw'))).toBe(true);
    });

    it('should handle MOBI bookType', () => {
      const book = createBook({bookType: 'MOBI'});
      expect(service.evaluateGroup(book, rule('fileType', 'equals', 'mobi'))).toBe(true);
    });

    it('should handle not_equals with fileType cbr mapping', () => {
      const book = createBook({bookType: 'PDF'});
      expect(service.evaluateGroup(book, rule('fileType', 'not_equals', 'cbr'))).toBe(true);
    });
  });

  describe('extractBookValue coverage', () => {
    it('should extract subtitle', () => {
      const book = createBook({metadata: {bookId: 1, subtitle: 'A Novel'}});
      expect(service.evaluateGroup(book, rule('subtitle', 'equals', 'a novel'))).toBe(true);
    });

    it('should extract isbn13', () => {
      const book = createBook({metadata: {bookId: 1, isbn13: '9780123456789'}});
      expect(service.evaluateGroup(book, rule('isbn13', 'equals', '9780123456789'))).toBe(true);
    });

    it('should extract isbn10', () => {
      const book = createBook({metadata: {bookId: 1, isbn10: '0123456789'}});
      expect(service.evaluateGroup(book, rule('isbn10', 'equals', '0123456789'))).toBe(true);
    });

    it('should extract seriesName', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: 'Wheel of Time'}});
      expect(service.evaluateGroup(book, rule('seriesName', 'contains', 'wheel'))).toBe(true);
    });

    it('should extract amazonRating', () => {
      const book = createBook({metadata: {bookId: 1, amazonRating: 4.3}});
      expect(service.evaluateGroup(book, rule('amazonRating', 'greater_than', 4))).toBe(true);
    });

    it('should extract goodreadsRating', () => {
      const book = createBook({metadata: {bookId: 1, goodreadsRating: 4.1}});
      expect(service.evaluateGroup(book, rule('goodreadsRating', 'greater_than', 4))).toBe(true);
    });

    it('should extract hardcoverRating', () => {
      const book = createBook({metadata: {bookId: 1, hardcoverRating: 3.8}});
      expect(service.evaluateGroup(book, rule('hardcoverRating', 'less_than', 4))).toBe(true);
    });

    it('should extract ranobedbRating', () => {
      const book = createBook({metadata: {bookId: 1, ranobedbRating: 4.5}});
      expect(service.evaluateGroup(book, rule('ranobedbRating', 'equals', 4.5))).toBe(true);
    });

    it('should extract amazonReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, amazonReviewCount: 1200}});
      expect(service.evaluateGroup(book, rule('amazonReviewCount', 'greater_than', 1000))).toBe(true);
    });

    it('should extract goodreadsReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, goodreadsReviewCount: 5000}});
      expect(service.evaluateGroup(book, rule('goodreadsReviewCount', 'greater_than', 1000))).toBe(true);
    });

    it('should extract hardcoverReviewCount', () => {
      const book = createBook({metadata: {bookId: 1, hardcoverReviewCount: 300}});
      expect(service.evaluateGroup(book, rule('hardcoverReviewCount', 'less_than', 500))).toBe(true);
    });

    it('should return null for publishedDate when missing', () => {
      const book = createBook({metadata: {bookId: 1}});
      expect(service.evaluateGroup(book, rule('publishedDate', 'is_empty', null))).toBe(true);
    });

    it('should return null for dateFinished when missing', () => {
      const book = createBook({dateFinished: undefined});
      expect(service.evaluateGroup(book, rule('dateFinished', 'is_empty', null))).toBe(true);
    });

    it('should return null for lastReadTime when missing', () => {
      const book = createBook({lastReadTime: undefined});
      expect(service.evaluateGroup(book, rule('lastReadTime', 'is_empty', null))).toBe(true);
    });

    it('should handle default field via dynamic property', () => {
      const book = createBook();
      (book as Record<string, unknown>)['customField'] = 'hello';
      expect(service.evaluateGroup(book, rule('customField' as never, 'equals', 'hello'))).toBe(true);
    });
  });

  describe('seriesStatus edge cases', () => {
    it('should not match "not_started" when a book is PARTIALLY_READ', () => {
      const b1 = seriesBook(1, ReadStatus.UNREAD);
      const b2 = seriesBook(2, ReadStatus.PARTIALLY_READ);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'not_started'), allBooks)).toBe(false);
    });

    it('should not match "completed" when no seriesTotal exists', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READ);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'completed'), allBooks)).toBe(false);
    });

    it('should not match "ongoing" when no seriesTotal exists', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READ);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'ongoing'), allBooks)).toBe(false);
    });

    it('should return false for unknown status value', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const allBooks = [b1];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'bogus'), allBooks)).toBe(false);
    });

    it('should handle completed with fractional seriesNumber matching floor(total)', () => {
      const b1 = seriesBook(1, ReadStatus.READ, {metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1, seriesTotal: 3}});
      const b3 = createBook({
        id: 300, readStatus: ReadStatus.UNREAD,
        metadata: {bookId: 300, seriesName: 'Dune', seriesNumber: 3.5}
      });
      const allBooks = [b1, b3];
      expect(service.evaluateGroup(b1, rule('seriesStatus', 'equals', 'completed'), allBooks)).toBe(true);
    });

    it('should only match series books from same series name', () => {
      const dune1 = seriesBook(1, ReadStatus.READING);
      const other = createBook({
        id: 200, readStatus: ReadStatus.UNREAD,
        metadata: {bookId: 200, seriesName: 'Foundation', seriesNumber: 1}
      });
      const allBooks = [dune1, other];
      expect(service.evaluateGroup(other, rule('seriesStatus', 'equals', 'not_started'), allBooks)).toBe(true);
      expect(service.evaluateGroup(dune1, rule('seriesStatus', 'equals', 'reading'), allBooks)).toBe(true);
    });
  });

  describe('seriesGaps edge cases', () => {
    it('should handle fractional series numbers with floor for any_gap', () => {
      const b1 = createBook({
        id: 100, readStatus: ReadStatus.READ,
        metadata: {bookId: 100, seriesName: 'Dune', seriesNumber: 1.5}
      });
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b3];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'any_gap'), allBooks)).toBe(true);
    });

    it('should return false for unknown gap value', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const allBooks = [b1];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'equals', 'bogus'), allBooks)).toBe(false);
    });

    it('should return false when series has no numbered books', () => {
      const book = createBook({metadata: {bookId: 1, seriesName: 'Dune'}});
      expect(service.evaluateGroup(book, rule('seriesGaps', 'equals', 'any_gap'), [book])).toBe(false);
    });

    it('should negate gaps with not_equals', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3];
      expect(service.evaluateGroup(b1, rule('seriesGaps', 'not_equals', 'any_gap'), allBooks)).toBe(true);
    });
  });

  describe('seriesPosition edge cases', () => {
    it('should handle next_unread when all books are read', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READ);
      const allBooks = [b1, b2];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(false);
      expect(service.evaluateGroup(b2, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(false);
    });

    it('should handle next_unread with multiple read then first unread', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.READ);
      const b3 = seriesBook(3, ReadStatus.UNREAD);
      const b4 = seriesBook(4, ReadStatus.UNREAD);
      const allBooks = [b1, b2, b3, b4];
      expect(service.evaluateGroup(b3, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(true);
      expect(service.evaluateGroup(b4, rule('seriesPosition', 'equals', 'next_unread'), allBooks)).toBe(false);
    });

    it('should return false for unknown position value', () => {
      const b1 = seriesBook(1, ReadStatus.READ);
      const allBooks = [b1];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'bogus'), allBooks)).toBe(false);
    });

    it('should handle single-book series for first and last', () => {
      const b1 = seriesBook(1, ReadStatus.UNREAD);
      const allBooks = [b1];
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'first_in_series'), allBooks)).toBe(true);
      expect(service.evaluateGroup(b1, rule('seriesPosition', 'equals', 'last_in_series'), allBooks)).toBe(true);
    });

    it('should handle fractional series numbers for first/last', () => {
      const b05 = createBook({
        id: 50, readStatus: ReadStatus.UNREAD,
        metadata: {bookId: 50, seriesName: 'Dune', seriesNumber: 0.5}
      });
      const b1 = seriesBook(1, ReadStatus.READ);
      const b2 = seriesBook(2, ReadStatus.UNREAD);
      const allBooks = [b05, b1, b2];
      expect(service.evaluateGroup(b05, rule('seriesPosition', 'equals', 'first_in_series'), allBooks)).toBe(true);
      expect(service.evaluateGroup(b2, rule('seriesPosition', 'equals', 'last_in_series'), allBooks)).toBe(true);
    });
  });

  describe('relative date default/edge cases', () => {
    it('should default to days when valueEnd is missing for within_last', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'addedOn', operator: 'within_last', value: 30}]
      };
      expect(service.evaluateGroup(book, g)).toBe(true);
    });

    it('should default to days when valueEnd is missing for older_than', () => {
      const old = new Date();
      old.setDate(old.getDate() - 60);
      const book = createBook({addedOn: old.toISOString()});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'addedOn', operator: 'older_than', value: 30}]
      };
      expect(service.evaluateGroup(book, g)).toBe(true);
    });

    it('should default to year when value is missing for this_period', () => {
      const book = createBook({addedOn: new Date().toISOString()});
      const g: GroupRule = {
        name: 'test', type: 'group', join: 'and',
        rules: [{field: 'addedOn', operator: 'this_period', value: null}]
      };
      expect(service.evaluateGroup(book, g)).toBe(true);
    });

    it('should handle older_than with weeks unit', () => {
      const old = new Date();
      old.setDate(old.getDate() - 30);
      const book = createBook({addedOn: old.toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'older_than', 2, 'weeks'))).toBe(true);
    });

    it('should handle older_than with years unit', () => {
      const old = new Date();
      old.setFullYear(old.getFullYear() - 3);
      const book = createBook({addedOn: old.toISOString()});
      expect(service.evaluateGroup(book, rule('addedOn', 'older_than', 2, 'years'))).toBe(true);
    });
  });

  describe('unknown operator', () => {
    it('should return false for an unrecognized operator', () => {
      const book = createBook();
      expect(service.evaluateGroup(book, rule('title', 'regex_match' as never, '.*'))).toBe(false);
    });
  });
});


