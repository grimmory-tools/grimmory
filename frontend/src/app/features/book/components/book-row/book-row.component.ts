import {
  afterRenderEffect,
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  ElementRef,
  inject,
  input,
  output,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideChevronLeft, LucideChevronRight} from '@lucide/angular';

import {type BookMenuComponent} from '../book-menu/book-menu.component';
import {BookCardComponent} from '../cards/book-card.component';
import {bookCardHeightForWidth} from '../cards/book-card.layout';
import {BookCardSkeletonComponent} from '../cards/book-card-skeleton.component';
import {type BookFileResponse, type BookSummary} from '../../data/book-response.models';
import {BookNavigationService} from '../../service/book-navigation.service';
import {UserService} from '../../../settings/user-management/user.service';
import {type BrowseStatus} from '../../../../shared/browse/results';
import {createBrowseSkeletonDelay} from '../../../../shared/browse/skeleton-delay';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {CoverScalePreferenceService} from '../../../../shared/service/cover-scale-preference.service';
import {AppButtonComponent} from '../../../../shared/ui/button/app-button.component';

export interface BookRowCard {
  readonly book: BookSummary;
  readonly file?: BookFileResponse;
}

const SKELETONS = Array.from({length: 24});
export const BOOK_ROW_CARD_GAP = 16;
export const BOOK_ROW_CARD_WIDTH = 150;

@Component({
  selector: 'app-book-row',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppButtonComponent,
    BookCardComponent,
    BookCardSkeletonComponent,
    LucideChevronLeft,
    LucideChevronRight,
    TranslocoPipe,
  ],
  host: {class: 'block'},
  templateUrl: './book-row.component.html',
})
export class BookRowComponent {
  readonly cards = input.required<readonly BookRowCard[]>();
  readonly status = input.required<BrowseStatus>();
  readonly bookMenu = input.required<BookMenuComponent>();
  /** Scroll cards out to the `.app-page` edge instead of the 12px inset that keeps end-card hover shadows unclipped. */
  readonly pageBleed = input(false, {transform: booleanAttribute});
  readonly retry = output<void>();
  readonly opened = output<BookSummary>();

  private readonly userService = inject(UserService);
  private readonly coverScale = inject(CoverScalePreferenceService);
  protected readonly desktop = inject(LayoutService).isDesktop;
  protected readonly bookNavigation = inject(BookNavigationService);
  protected readonly rowTitle = contentChild('rowTitle');
  private readonly strip = viewChild<ElementRef<HTMLElement>>('strip');
  private readonly cardElements = viewChildren<BookCardComponent, ElementRef<HTMLElement>>(BookCardComponent, {read: ElementRef});

  protected readonly squareCovers = computed(() => {
    const cards = this.cards();
    return cards.length > 0 && cards.every(({book, file}) => (file ?? book.primaryFile)?.bookType === 'AUDIOBOOK');
  });
  protected readonly showFormatPill = computed(() =>
    this.userService.currentUser()?.userSettings.entityViewPreferences?.global.overlayBookType ?? true);
  protected readonly skeletonVisible = createBrowseSkeletonDelay(this.status, computed(() => this.cards().length > 0));
  protected readonly cardWidth = computed(() =>
    this.desktop() ? Math.round(BOOK_ROW_CARD_WIDTH * this.coverScale.scaleFactor()) : this.coverScale.BASE_WIDTH);
  protected readonly cardHeight = computed(() =>
    bookCardHeightForWidth(this.cardWidth(), {square: this.squareCovers(), metaLines: 2}));
  protected readonly skeletons = SKELETONS;
  protected readonly arrowClass =
    'flex size-8 items-center justify-center rounded-full text-text-secondary outline-none transition-colors ' +
    'hover:text-text-strong focus-visible:ring-2 focus-visible:ring-primary disabled:pointer-events-none disabled:opacity-30';

  protected readonly atStart = signal(true);
  protected readonly atEnd = signal(true);

  constructor() {
    afterRenderEffect(onCleanup => {
      const strip = this.strip()?.nativeElement;
      const cards = this.cardElements();
      if (!strip || cards.length === 0) {
        return;
      }
      const first = cards[0].nativeElement;
      const last = cards[cards.length - 1].nativeElement;
      const observer = new IntersectionObserver(entries => {
        for (const entry of entries) {
          const inView = entry.intersectionRatio === 1;
          if (entry.target === first) {
            this.atStart.set(inView);
          }
          if (entry.target === last) {
            this.atEnd.set(inView);
          }
        }
      }, {root: strip, threshold: 1});
      observer.observe(first);
      observer.observe(last);
      onCleanup(() => observer.disconnect());
    });
  }

  protected scrollPage(direction: -1 | 1): void {
    const strip = this.strip()?.nativeElement;
    strip?.scrollBy({left: direction * strip.clientWidth});
  }
}
