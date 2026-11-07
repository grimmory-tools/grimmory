import {TestBed} from '@angular/core/testing';
import {TranslocoService} from '@jsverse/transloco';
import {firstValueFrom} from 'rxjs';
import {beforeEach, describe, expect, it} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {type BookSummary} from '../data/book-response.models';
import {BookBrowseDetailLineService} from './book-browse-detail-line.service';

function book(overrides: Partial<BookSummary> = {}): BookSummary {
  return {
    id: 1,
    libraryId: 1,
    libraryName: 'Library',
    primaryFile: {id: 10, bookId: 1, book: true, folderBased: false, bookType: 'EPUB'},
    ...overrides,
  };
}

describe('BookBrowseDetailLineService', () => {
  let service: BookBrowseDetailLineService;

  beforeEach(async () => {
    TestBed.configureTestingModule({imports: [getTranslocoModule()]});
    service = TestBed.inject(BookBrowseDetailLineService);
    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));
  });

  it('renders the reading progress the card bar shows', () => {
    const reading = book({
      epubProgress: {cfi: null, href: null, contentSourceProgressPercent: null, percentage: 33.6, ttsPositionCfi: null},
      koboProgress: {percentage: 90},
    });
    expect(service.lineFor('readingProgress', reading)).toBe('34%');
    expect(service.lineFor('readingProgress', book({koboProgress: {percentage: 90}}))).toBe('90%');
  });

  it('translates read status and renders every absent value as the muted dash', () => {
    expect(service.lineFor('readStatus', book({readStatus: 'RE_READING'}))).toBe('Re-reading');
    const bare = book();
    for (const key of ['addedOn', 'publisher', 'pageCount', 'goodreadsRating', 'readingProgress', 'readStatus'] as const) {
      expect(service.lineFor(key, bare)).toBe('—');
    }
  });
});
