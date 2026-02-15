import {Injectable} from '@angular/core';
import {Book} from '../../book/model/book.model';
import {GroupRule, Rule, RuleField} from '../component/magic-shelf-component';

@Injectable({providedIn: 'root'})
export class BookRuleEvaluatorService {

  evaluateGroup(book: Book, group: GroupRule, allBooks: Book[] = []): boolean {
    const results = group.rules.map(rule => {
      if ('type' in rule && rule.type === 'group') {
        return this.evaluateGroup(book, rule as GroupRule, allBooks);
      } else {
        return this.evaluateRule(book, rule as Rule, allBooks);
      }
    });
    return group.join === 'and' ? results.every(Boolean) : results.some(Boolean);
  }

  private evaluateRule(book: Book, rule: Rule, allBooks: Book[]): boolean {
    if (rule.field === 'metadataPresence') {
      return this.evaluateMetadataPresence(book, rule);
    }

    if (rule.field === 'seriesStatus' || rule.field === 'seriesGaps' || rule.field === 'seriesPosition') {
      return this.evaluateCompositeField(book, rule, allBooks);
    }

    const rawValue = this.extractBookValue(book, rule.field);

    const normalize = (val: unknown): unknown => {
      if (val === null || val === undefined) return val;
      if (val instanceof Date) return val;
      if (typeof val === 'string') {
        const date = new Date(val);
        if (!isNaN(date.getTime())) return date;
        return val.toLowerCase();
      }
      return val;
    };

    const value = normalize(rawValue);
    const ruleVal = normalize(rule.value);
    const ruleStart = normalize(rule.valueStart);
    const ruleEnd = normalize(rule.valueEnd);

    const mapFileTypeValue = (uiValue: string): string => {
      const lowerValue = uiValue.toLowerCase();
      switch (lowerValue) {
        case 'cbr':
        case 'cbz':
        case 'cb7':
          return 'cbx';
        case 'azw':
          return 'azw3';
        default:
          return lowerValue;
      }
    };

    const getArrayField = (field: RuleField): string[] => {
      switch (field) {
        case 'authors':
          return (book.metadata?.authors ?? []).map(a => a.toLowerCase());
        case 'categories':
          return (book.metadata?.categories ?? []).map(c => c.toLowerCase());
        case 'moods':
          return (book.metadata?.moods ?? []).map(m => m.toLowerCase());
        case 'tags':
          return (book.metadata?.tags ?? []).map(t => t.toLowerCase());
        case 'readStatus':
          return [String(book.readStatus ?? 'UNSET').toLowerCase()];
        case 'fileType':
          return [String(book['bookType'] ?? '').toLowerCase()];
        case 'library':
          return [String(book.libraryId)];
        case 'shelf':
          return (book.shelves ?? []).map(s => String(s.id));
        case 'language':
          return [String(book.metadata?.language ?? '').toLowerCase()];
        case 'title':
          return [String(book.metadata?.title ?? '').toLowerCase()];
        case 'subtitle':
          return [String(book.metadata?.subtitle ?? '').toLowerCase()];
        case 'publisher':
          return [String(book.metadata?.publisher ?? '').toLowerCase()];
        case 'seriesName':
          return [String(book.metadata?.seriesName ?? '').toLowerCase()];
        case 'isbn13':
          return [String(book.metadata?.isbn13 ?? '').toLowerCase()];
        case 'isbn10':
          return [String(book.metadata?.isbn10 ?? '').toLowerCase()];
        case 'narrator':
          return [String(book.metadata?.narrator ?? '').toLowerCase()];
        case 'description':
          return [String(book.metadata?.description ?? '').toLowerCase()];
        case 'contentRating':
          return [String(book.metadata?.contentRating ?? '').toLowerCase()];
        default:
          return [];
      }
    };

    const isNumericIdField = rule.field === 'library' || rule.field === 'shelf';
    const isFileTypeField = rule.field === 'fileType';

    const ruleList = Array.isArray(rule.value)
      ? rule.value.map(v => {
        if (isNumericIdField) return String(v);
        const lowerValue = String(v).toLowerCase();
        return isFileTypeField ? mapFileTypeValue(lowerValue) : lowerValue;
      })
      : (rule.value ? [
        isNumericIdField
          ? String(rule.value)
          : isFileTypeField
            ? mapFileTypeValue(String(rule.value).toLowerCase())
            : String(rule.value).toLowerCase()
      ] : []);

    switch (rule.operator) {
      case 'equals':
        if (Array.isArray(value)) {
          return value.some(v => ruleList.includes(isNumericIdField ? String(v) : String(v).toLowerCase()));
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() === ruleVal.getTime();
        }
        if (isFileTypeField && typeof ruleVal === 'string') {
          const mappedRuleVal = mapFileTypeValue(ruleVal.toLowerCase());
          return value === mappedRuleVal;
        }
        return value === ruleVal;

      case 'not_equals':
        if (Array.isArray(value)) {
          return value.every(v => !ruleList.includes(isNumericIdField ? String(v) : String(v).toLowerCase()));
        }
        if (value instanceof Date && ruleVal instanceof Date) {
          return value.getTime() !== ruleVal.getTime();
        }
        if (isFileTypeField && typeof ruleVal === 'string') {
          const mappedRuleVal = mapFileTypeValue(ruleVal.toLowerCase());
          return value !== mappedRuleVal;
        }
        return value !== ruleVal;

      case 'contains':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return false;
          return value.some(v => String(v).includes(ruleVal));
        }
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.includes(ruleVal);

      case 'does_not_contain':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return true;
          return value.every(v => !String(v).includes(ruleVal));
        }
        if (typeof value !== 'string') return true;
        if (typeof ruleVal !== 'string') return true;
        return !value.includes(ruleVal);

      case 'starts_with':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return false;
          return value.some(v => String(v).startsWith(ruleVal));
        }
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.startsWith(ruleVal);

      case 'ends_with':
        if (Array.isArray(value)) {
          if (typeof ruleVal !== 'string') return false;
          return value.some(v => String(v).endsWith(ruleVal));
        }
        if (typeof value !== 'string') return false;
        if (typeof ruleVal !== 'string') return false;
        return value.endsWith(ruleVal);

      case 'greater_than':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value > ruleVal;
        }
        return Number(value) > Number(ruleVal);

      case 'greater_than_equal_to':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value >= ruleVal;
        }
        return Number(value) >= Number(ruleVal);

      case 'less_than':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value < ruleVal;
        }
        return Number(value) < Number(ruleVal);

      case 'less_than_equal_to':
        if (value instanceof Date && ruleVal instanceof Date) {
          return value <= ruleVal;
        }
        return Number(value) <= Number(ruleVal);

      case 'in_between':
        if (value == null || ruleStart == null || ruleEnd == null) return false;
        if (value instanceof Date && ruleStart instanceof Date && ruleEnd instanceof Date) {
          return value >= ruleStart && value <= ruleEnd;
        }
        return Number(value) >= Number(ruleStart) && Number(value) <= Number(ruleEnd);

      case 'is_empty':
        if (value == null) return true;
        if (typeof value === 'string') return value.trim() === '';
        if (Array.isArray(value)) return value.length === 0;
        return false;

      case 'is_not_empty':
        if (value == null) return false;
        if (typeof value === 'string') return value.trim() !== '';
        if (Array.isArray(value)) return value.length > 0;
        return true;

      case 'includes_all': {
        const bookList = getArrayField(rule.field);
        return ruleList.every(v => bookList.includes(v));
      }

      case 'excludes_all': {
        const bookList = getArrayField(rule.field);
        return ruleList.every(v => !bookList.includes(v));
      }

      case 'includes_any': {
        const bookList = getArrayField(rule.field);
        return ruleList.some(v => bookList.includes(v));
      }

      case 'within_last': {
        if (!(value instanceof Date)) return false;
        const threshold = this.computeDateThreshold(Number(rule.value), String(rule.valueEnd ?? 'days'));
        return value >= threshold;
      }

      case 'older_than': {
        if (!(value instanceof Date)) return false;
        const threshold = this.computeDateThreshold(Number(rule.value), String(rule.valueEnd ?? 'days'));
        return value < threshold;
      }

      case 'this_period': {
        if (!(value instanceof Date)) return false;
        const start = this.getStartOfPeriod(String(rule.value ?? 'year'));
        return value >= start;
      }

      default:
        return false;
    }
  }

  private extractBookValue(book: Book, field: RuleField): unknown {
    switch (field) {
      case 'library':
        return book.libraryId;
      case 'shelf':
        return (book.shelves ?? []).map(s => s.id);
      case 'readStatus':
        return book.readStatus ?? 'UNSET';
      case 'fileType':
        return (book['bookType'] as string)?.toLowerCase() ?? null;
      case 'fileSize':
        return book.fileSizeKb;
      case 'metadataScore':
        return book.metadataMatchScore;
      case 'personalRating':
        return book.personalRating;
      case 'title':
        return book.metadata?.title?.toLowerCase() ?? null;
      case 'subtitle':
        return book.metadata?.subtitle?.toLowerCase() ?? null;
      case 'authors':
        return (book.metadata?.authors ?? []).map(a => a.toLowerCase());
      case 'categories':
        return (book.metadata?.categories ?? []).map(c => c.toLowerCase());
      case 'moods':
        return (book.metadata?.moods ?? []).map(m => m.toLowerCase());
      case 'tags':
        return (book.metadata?.tags ?? []).map(t => t.toLowerCase());
      case 'publisher':
        return book.metadata?.publisher?.toLowerCase() ?? null;
      case 'publishedDate':
        return book.metadata?.publishedDate ? new Date(book.metadata.publishedDate) : null;
      case 'dateFinished':
        return book.dateFinished ? new Date(book.dateFinished) : null;
      case 'lastReadTime':
        return book.lastReadTime ? new Date(book.lastReadTime) : null;
      case 'seriesName':
        return book.metadata?.seriesName?.toLowerCase() ?? null;
      case 'seriesNumber':
        return book.metadata?.seriesNumber;
      case 'seriesTotal':
        return book.metadata?.seriesTotal;
      case 'pageCount':
        return book.metadata?.pageCount;
      case 'language':
        return book.metadata?.language?.toLowerCase() ?? null;
      case 'isbn13':
        return book.metadata?.isbn13?.toLowerCase() ?? null;
      case 'isbn10':
        return book.metadata?.isbn10?.toLowerCase() ?? null;
      case 'amazonRating':
        return book.metadata?.amazonRating;
      case 'amazonReviewCount':
        return book.metadata?.amazonReviewCount;
      case 'goodreadsRating':
        return book.metadata?.goodreadsRating;
      case 'goodreadsReviewCount':
        return book.metadata?.goodreadsReviewCount;
      case 'hardcoverRating':
        return book.metadata?.hardcoverRating;
      case 'hardcoverReviewCount':
        return book.metadata?.hardcoverReviewCount;
      case 'ranobedbRating':
        return book.metadata?.ranobedbRating;
      case 'addedOn':
        return book.addedOn ? new Date(book.addedOn) : null;
      case 'description':
        return book.metadata?.description?.toLowerCase() ?? null;
      case 'narrator':
        return book.metadata?.narrator?.toLowerCase() ?? null;
      case 'ageRating':
        return book.metadata?.ageRating;
      case 'contentRating':
        return book.metadata?.contentRating?.toLowerCase() ?? null;
      case 'audibleRating':
        return book.metadata?.audibleRating;
      case 'audibleReviewCount':
        return book.metadata?.audibleReviewCount;
      case 'abridged':
        return book.metadata?.abridged;
      case 'audiobookDuration':
        return book.metadata?.audiobookMetadata?.durationSeconds ?? null;
      case 'isPhysical':
        return book.isPhysical;
      case 'lubimyczytacRating':
        return book.metadata?.lubimyczytacRating;
      case 'readingProgress': {
        const prg = [
          book.koreaderProgress?.percentage ?? 0,
          book.koboProgress?.percentage ?? 0,
          book.pdfProgress?.percentage ?? 0,
          book.epubProgress?.percentage ?? 0,
          book.cbxProgress?.percentage ?? 0,
          book.audiobookProgress?.percentage ?? 0,
        ];
        return Math.max(...prg);
      }
      default:
        return (book as Record<string, unknown>)[field];
    }
  }

  private evaluateCompositeField(book: Book, rule: Rule, allBooks: Book[]): boolean {
    const seriesName = book.metadata?.seriesName;
    if (!seriesName) return false;

    const seriesBooks = allBooks.filter(b => b.metadata?.seriesName === seriesName);
    const value = typeof rule.value === 'string' ? rule.value.toLowerCase() : '';
    const negate = rule.operator === 'not_equals';

    let result: boolean;
    switch (rule.field) {
      case 'seriesStatus':
        result = this.evaluateSeriesStatus(seriesBooks, value);
        break;
      case 'seriesGaps':
        result = this.evaluateSeriesGaps(seriesBooks, value);
        break;
      case 'seriesPosition':
        result = this.evaluateSeriesPosition(book, seriesBooks, value);
        break;
      default:
        result = false;
    }

    return negate ? !result : result;
  }

  private evaluateSeriesStatus(seriesBooks: Book[], value: string): boolean {
    switch (value) {
      case 'reading':
        return seriesBooks.some(b => b.readStatus === 'READING' || b.readStatus === 'RE_READING');
      case 'not_started':
        return !seriesBooks.some(b =>
          b.readStatus === 'READ' || b.readStatus === 'READING' ||
          b.readStatus === 'RE_READING' || b.readStatus === 'PARTIALLY_READ'
        );
      case 'fully_read':
        return seriesBooks.length > 0 && seriesBooks.every(b => b.readStatus === 'READ');
      case 'completed': {
        const totals = seriesBooks.map(b => b.metadata?.seriesTotal).filter((t): t is number => t != null);
        if (totals.length === 0) return false;
        const maxTotal = Math.max(...totals);
        return seriesBooks.some(b => b.metadata?.seriesNumber != null && Math.floor(b.metadata.seriesNumber) === maxTotal);
      }
      case 'ongoing': {
        const totals = seriesBooks.map(b => b.metadata?.seriesTotal).filter((t): t is number => t != null);
        if (totals.length === 0) return false;
        const maxTotal = Math.max(...totals);
        return !seriesBooks.some(b => b.metadata?.seriesNumber != null && Math.floor(b.metadata.seriesNumber) === maxTotal);
      }
      default:
        return false;
    }
  }

  private evaluateSeriesGaps(seriesBooks: Book[], value: string): boolean {
    const numberedBooks = seriesBooks
      .filter(b => b.metadata?.seriesNumber != null)
      .map(b => b.metadata!.seriesNumber!);

    if (numberedBooks.length === 0) return false;

    switch (value) {
      case 'any_gap': {
        const uniqueFloors = new Set(numberedBooks.map(n => Math.floor(n)));
        const maxFloor = Math.max(...numberedBooks.map(n => Math.floor(n)));
        return uniqueFloors.size < maxFloor;
      }
      case 'missing_first':
        return !numberedBooks.some(n => Math.floor(n) === 1);
      case 'missing_latest': {
        const totals = seriesBooks.map(b => b.metadata?.seriesTotal).filter((t): t is number => t != null);
        if (totals.length === 0) return false;
        const maxTotal = Math.max(...totals);
        return !numberedBooks.some(n => Math.floor(n) === maxTotal);
      }
      case 'duplicate_number':
        return numberedBooks.length > new Set(numberedBooks).size;
      default:
        return false;
    }
  }

  private evaluateSeriesPosition(book: Book, seriesBooks: Book[], value: string): boolean {
    if (book.metadata?.seriesNumber == null) return false;

    const numberedBooks = seriesBooks.filter(b => b.metadata?.seriesNumber != null);

    switch (value) {
      case 'first_in_series': {
        const minNumber = Math.min(...numberedBooks.map(b => b.metadata!.seriesNumber!));
        return book.metadata.seriesNumber === minNumber;
      }
      case 'last_in_series': {
        const maxNumber = Math.max(...numberedBooks.map(b => b.metadata!.seriesNumber!));
        return book.metadata.seriesNumber === maxNumber;
      }
      case 'next_unread': {
        if (book.readStatus === 'READ') return false;
        const hasLowerUnread = numberedBooks.some(b =>
          b.metadata!.seriesNumber! < book.metadata!.seriesNumber! &&
          b.readStatus !== 'READ'
        );
        if (hasLowerUnread) return false;
        return numberedBooks.some(b =>
          b.metadata!.seriesNumber! < book.metadata!.seriesNumber! &&
          b.readStatus === 'READ'
        );
      }
      default:
        return false;
    }
  }

  private evaluateMetadataPresence(book: Book, rule: Rule): boolean {
    const metadataField = typeof rule.value === 'string' ? rule.value : '';
    const isPresent = this.isMetadataFieldPresent(book, metadataField);
    return rule.operator === 'equals' ? isPresent : !isPresent;
  }

  private isMetadataFieldPresent(book: Book, field: string): boolean {
    const val = this.getMetadataFieldValue(book, field);
    if (val == null) return false;
    if (typeof val === 'string') return val.trim() !== '';
    if (Array.isArray(val)) return val.length > 0;
    return true;
  }

  private getMetadataFieldValue(book: Book, field: string): unknown {
    switch (field) {
      case 'title': return book.metadata?.title;
      case 'subtitle': return book.metadata?.subtitle;
      case 'description': return book.metadata?.description;
      case 'publisher': return book.metadata?.publisher;
      case 'publishedDate': return book.metadata?.publishedDate;
      case 'language': return book.metadata?.language;
      case 'thumbnailUrl': return book.metadata?.thumbnailUrl;
      case 'narrator': return book.metadata?.narrator;
      case 'contentRating': return book.metadata?.contentRating;
      case 'pageCount': return book.metadata?.pageCount;
      case 'seriesNumber': return book.metadata?.seriesNumber;
      case 'seriesTotal': return book.metadata?.seriesTotal;
      case 'ageRating': return book.metadata?.ageRating;
      case 'seriesName': return book.metadata?.seriesName;
      case 'isbn13': return book.metadata?.isbn13;
      case 'isbn10': return book.metadata?.isbn10;
      case 'asin': return book.metadata?.asin;
      case 'authors': return book.metadata?.authors;
      case 'categories': return book.metadata?.categories;
      case 'moods': return book.metadata?.moods;
      case 'tags': return book.metadata?.tags;
      case 'personalRating': return book.personalRating;
      case 'amazonRating': return book.metadata?.amazonRating;
      case 'goodreadsRating': return book.metadata?.goodreadsRating;
      case 'hardcoverRating': return book.metadata?.hardcoverRating;
      case 'ranobedbRating': return book.metadata?.ranobedbRating;
      case 'lubimyczytacRating': return book.metadata?.lubimyczytacRating;
      case 'audibleRating': return book.metadata?.audibleRating;
      case 'amazonReviewCount': return book.metadata?.amazonReviewCount;
      case 'goodreadsReviewCount': return book.metadata?.goodreadsReviewCount;
      case 'hardcoverReviewCount': return book.metadata?.hardcoverReviewCount;
      case 'audibleReviewCount': return book.metadata?.audibleReviewCount;
      case 'goodreadsId': return book.metadata?.goodreadsId;
      case 'hardcoverId': return book.metadata?.hardcoverId;
      case 'googleId': return book.metadata?.googleId;
      case 'audibleId': return book.metadata?.audibleId;
      case 'lubimyczytacId': return book.metadata?.lubimyczytacId;
      case 'ranobedbId': return book.metadata?.ranobedbId;
      case 'comicvineId': return book.metadata?.comicvineId;
      case 'abridged': return book.metadata?.abridged;
      case 'audiobookDuration': return book.metadata?.audiobookMetadata?.durationSeconds;
      case 'comicCharacters': return book.metadata?.comicMetadata?.characters;
      case 'comicTeams': return book.metadata?.comicMetadata?.teams;
      case 'comicLocations': return book.metadata?.comicMetadata?.locations;
      case 'comicPencillers': return book.metadata?.comicMetadata?.pencillers;
      case 'comicInkers': return book.metadata?.comicMetadata?.inkers;
      case 'comicColorists': return book.metadata?.comicMetadata?.colorists;
      case 'comicLetterers': return book.metadata?.comicMetadata?.letterers;
      case 'comicCoverArtists': return book.metadata?.comicMetadata?.coverArtists;
      case 'comicEditors': return book.metadata?.comicMetadata?.editors;
      default: return null;
    }
  }

  private computeDateThreshold(amount: number, unit: string): Date {
    const now = new Date();
    switch (unit.toLowerCase()) {
      case 'weeks':
        return new Date(now.getTime() - amount * 7 * 24 * 60 * 60 * 1000);
      case 'months':
        return new Date(now.getFullYear(), now.getMonth() - amount, now.getDate());
      case 'years':
        return new Date(now.getFullYear() - amount, now.getMonth(), now.getDate());
      default:
        return new Date(now.getTime() - amount * 24 * 60 * 60 * 1000);
    }
  }

  private getStartOfPeriod(period: string): Date {
    const now = new Date();
    switch (period.toLowerCase()) {
      case 'week': {
        const day = now.getDay();
        const diff = day === 0 ? 6 : day - 1;
        return new Date(now.getFullYear(), now.getMonth(), now.getDate() - diff);
      }
      case 'month':
        return new Date(now.getFullYear(), now.getMonth(), 1);
      default:
        return new Date(now.getFullYear(), 0, 1);
    }
  }
}
