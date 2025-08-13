import {Component, ElementRef, Input, OnChanges, OnInit, ViewChild} from '@angular/core';
import {BookCardComponent} from '../../../book/components/book-browser/book-card/book-card.component';
import {InfiniteScrollDirective} from 'ngx-infinite-scroll';

import {ProgressSpinnerModule} from 'primeng/progressspinner';
import {Book} from '../../../book/model/book.model';

@Component({
  selector: 'app-dashboard-scroller',
  templateUrl: './dashboard-scroller.component.html',
  styleUrls: ['./dashboard-scroller.component.scss'],
  imports: [
    InfiniteScrollDirective,
    BookCardComponent,
    ProgressSpinnerModule
  ],
  standalone: true
})
export class DashboardScrollerComponent {

  @Input() bookListType: 'lastRead' | null = null;
  @Input() title: string = 'Last Read Books';
  @Input() books!: Book[] | null;

  @ViewChild('scrollContainer') scrollContainer!: ElementRef;
}
