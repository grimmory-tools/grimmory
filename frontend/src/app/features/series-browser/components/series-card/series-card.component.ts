import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, Output} from '@angular/core';
import {NgClass} from '@angular/common';
import {ProgressBar} from 'primeng/progressbar';
import {Tooltip} from 'primeng/tooltip';
import {SeriesSummary} from '../../model/series.model';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {CoverComponent} from '../../../../shared/components/cover/cover.component';

@Component({
  selector: 'app-series-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './series-card.component.html',
  styleUrls: ['./series-card.component.scss'],
  imports: [NgClass, ProgressBar, Tooltip, CoverComponent]
})
export class SeriesCardComponent {

  @Input({required: true}) series!: SeriesSummary;
  @Input() compact = false;
  @Output() cardClick = new EventEmitter<SeriesSummary>();

  protected urlHelper = inject(UrlHelperService);

  get progressPercent(): number {
    return Math.round(this.series.progress * 100);
  }

  get authorsDisplay(): string {
    if (!this.series.authors.length) return '';
    if (this.series.authors.length <= 2) return this.series.authors.join(', ');
    return this.series.authors.slice(0, 2).join(', ') + ' +' + (this.series.authors.length - 2);
  }

  getCoverUrl(index: number): string | null {
    const book = this.series.coverBooks[index];
    if (!book) return null;
    const isAudiobook = book.primaryFileType === 'AUDIOBOOK';
    return isAudiobook
      ? this.urlHelper.getAudiobookThumbnailUrl(book.bookId, book.coverUpdatedOn ?? undefined)
      : this.urlHelper.getThumbnailUrl(book.bookId, book.coverUpdatedOn ?? undefined);
  }

  onCardClick(event: Event): void {
    event.stopPropagation();
    this.cardClick.emit(this.series);
  }

}
