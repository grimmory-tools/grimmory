import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {NgClass} from '@angular/common';
import {ProgressBar} from 'primeng/progressbar';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoPipe} from '@jsverse/transloco';
import {SeriesSummary} from '../../model/series.model';
import {BookService} from '../../../book/service/book.service';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';

@Component({
  selector: 'app-series-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './series-card.component.html',
  styleUrls: ['./series-card.component.scss'],
  imports: [NgClass, ProgressBar, Button, Tooltip, TranslocoPipe]
})
export class SeriesCardComponent {

  @Input({required: true}) series!: SeriesSummary;
  @Output() cardClick = new EventEmitter<SeriesSummary>();

  protected urlHelper = inject(UrlHelperService);
  private bookService = inject(BookService);

  get progressPercent(): number {
    return Math.round(this.series.progress * 100);
  }

  get authorsDisplay(): string {
    if (!this.series.authors.length) return '';
    if (this.series.authors.length <= 2) return this.series.authors.join(', ');
    return this.series.authors.slice(0, 2).join(', ') + ' +' + (this.series.authors.length - 2);
  }

  getCoverUrl(index: number): string {
    const book = this.series.coverBooks[index];
    if (!book) return '';
    const isAudiobook = book.primaryFile?.bookType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(book.id, book.metadata?.audiobookCoverUpdatedOn)
      : this.urlHelper.getThumbnailUrl(book.id, book.metadata?.coverUpdatedOn);
  }

  onCardClick(event: MouseEvent): void {
    event.stopPropagation();
    this.cardClick.emit(this.series);
  }

  readNext(event: MouseEvent): void {
    event.stopPropagation();
    if (this.series.nextUnread) {
      this.bookService.readBook(this.series.nextUnread.id);
    }
  }
}
