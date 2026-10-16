import {inject, Service} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoService} from '@jsverse/transloco';

import {type BookSummary} from '../data/book-response.models';
import {formatBookValue} from './book-browse-columns';
import {BOOK_FIELDS, type BookDetailLineSortKey} from './book-browse-fields';

@Service()
export class BookBrowseDetailLineService {
  private readonly transloco = inject(TranslocoService);
  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  lineFor(key: BookDetailLineSortKey, book: BookSummary): string {
    this.activeLang();
    const display = BOOK_FIELDS.fieldForSort(key).display;
    const line = display && formatBookValue(display.kind, display.value(book), labelKey => this.transloco.translate(labelKey));
    return line ?? '—';
  }
}
