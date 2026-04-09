import {ChangeDetectionStrategy, Component, Input} from '@angular/core';
import {NgClass} from '@angular/common';
import {Book} from '../../../book/model/book.model';
import {ScrollerType} from '../../models/dashboard-config.model';
import {BookCardComponent} from '../../../book/components/book-browser/book-card/book-card.component';
import {TranslocoDirective, TranslocoPipe} from '@jsverse/transloco';

@Component({
  selector: 'app-dashboard-scroller',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-scroller.component.html',
  styleUrls: ['./dashboard-scroller.component.scss'],
  imports: [
    BookCardComponent,
    NgClass,
    TranslocoDirective,
    TranslocoPipe
  ],
  standalone: true
})
export class DashboardScrollerComponent {

  @Input() bookListType: ScrollerType | null = null;
  @Input() title!: string;
  @Input() books!: Book[] | null;
  @Input() isMagicShelf: boolean = false;
  @Input() useSquareCovers: boolean = false;

  get forceEbookMode(): boolean {
    return this.bookListType === ScrollerType.LAST_READ;
  }
}
