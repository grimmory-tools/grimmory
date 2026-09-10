import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {NgClass} from '@angular/common';
import {Book} from '../../../book/model/book.model';
import {ScrollerType} from '../../models/dashboard-config.model';
import {LegacyBookCardComponent} from '../../../book/components/legacy-book-card/legacy-book-card.component';
import {BookCardOverlayPreferenceService} from '../../../book/components/legacy-book-card/book-card-overlay-preference.service';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';

@Component({
  selector: 'app-dashboard-scroller',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-scroller.component.html',
  styleUrls: ['./dashboard-scroller.component.scss'],
  imports: [
    LegacyBookCardComponent,
    NgClass,
    TranslocoDirective,
    TranslocoPipe
  ],
  standalone: true
})
export class DashboardScrollerComponent {

  readonly bookListType = input<ScrollerType | null>(null);
  readonly title = input.required<string>();
  readonly books = input<Book[] | null>(null);
  readonly isMagicShelf = input<boolean>(false);
  readonly useSquareCovers = input<boolean>(false);
  readonly overlayPreferenceService = input.required<BookCardOverlayPreferenceService>();

  readonly forceEbookMode = computed(() => this.bookListType() === ScrollerType.LAST_READ);
}
