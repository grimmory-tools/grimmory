import {Injectable, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';

import {bookProgressPercentage} from '../data/book-actions';
import {type BookSummary} from '../data/book-response.models';
import {formatBookValue} from './book-browse-columns';
import {type BookColumnKind, type BookColumnValue, type BookDetailLineSortKey} from './book-browse-fields';

@Injectable({providedIn: 'root'})
export class BookBrowseDetailLineService {
  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  lineFor(key: BookDetailLineSortKey, book: BookSummary): string {
    this.activeLang();
    return this.valueFor(key, book) ?? '—';
  }

  private valueFor(key: BookDetailLineSortKey, book: BookSummary): string | null {
    const metadata = book.metadata;
    switch (key) {
      case 'addedOn':
        return this.format('date', book.addedOn);
      case 'lastReadTime':
        return this.format('date', book.lastReadTime);
      case 'dateFinished':
        return this.format('date', book.dateFinished);
      case 'publishedDate':
        return this.format('date', metadata?.publishedDate);
      case 'publisher':
        return this.format('text', metadata?.publisher);
      case 'seriesName':
        return this.format('text', metadata?.seriesName);
      case 'narrator':
        return this.format('text', metadata?.narrator);
      case 'language':
        return this.format('text', metadata?.language);
      case 'pageCount':
        return this.format('number', metadata?.pageCount);
      case 'personalRating':
        return this.format('rating', book.personalRating);
      case 'amazonRating':
      case 'goodreadsRating':
      case 'hardcoverRating':
      case 'ranobedbRating':
      case 'lubimyczytacRating':
      case 'audibleRating':
        return this.format('rating', metadata?.[key]);
      case 'amazonReviewCount':
      case 'goodreadsReviewCount':
      case 'hardcoverReviewCount':
      case 'audibleReviewCount':
        return this.format('number', metadata?.[key]);
      case 'readingProgress':
        return progressLine(book);
      case 'readStatus':
        return this.format('readStatus', book.readStatus);
    }
  }

  private format(kind: BookColumnKind, value: BookColumnValue): string | null {
    return formatBookValue(kind, value, key => this.transloco.translate(key));
  }
}

function progressLine(book: BookSummary): string | null {
  const percentage = bookProgressPercentage(book);
  return percentage == null ? null : `${Math.round(percentage)}%`;
}
