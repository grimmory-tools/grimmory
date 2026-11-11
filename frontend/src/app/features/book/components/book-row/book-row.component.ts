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
import {NgTemplateOutlet} from '@angular/common';
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
    NgTemplateOutlet,
    TranslocoPipe,
  ],
  host: {class: 'block'},
  templateUrl: './book-row.component.html',
})
export class BookRowComponent {
  private readonly userService = inject(UserService);
  private readonly coverScale = inject(CoverScalePreferenceService);
  protected readonly desktop = inject(LayoutService).isDesktop;
  private readonly bookNavigation = inject(BookNavigationService);

  readonly cards = input.required<readonly BookRowCard[]>();
  readonly status = input.required<BrowseStatus>();
  readonly bookMenu = input.required<BookMenuComponent>();
  readonly pageBleed = input(false, {transform: booleanAttribute});
  readonly retry = output<void>();
  readonly opened = output<BookSummary>();
  readonly endReached = output<void>();

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
  protected readonly hiddenWhileLoading = computed(() =>
    this.status() === 'pending' && this.cards().length === 0 && !this.skeletonVisible());
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
  protected readonly arrowsVisible = computed(() => this.desktop() && !(this.atStart() && this.atEnd()));

  constructor() {
    afterRenderEffect(onCleanup => {
      const strip = this.strip()?.nativeElement;
      const last = this.cardElements().at(-1)?.nativeElement;
      if (!strip || !last) {
        return;
      }
      const updateEnds = (): void => {
        this.atStart.set(strip.scrollLeft <= 1);
        this.atEnd.set(strip.scrollWidth - strip.clientWidth - strip.scrollLeft <= 1);
      };
      updateEnds();
      strip.addEventListener('scroll', updateEnds, {passive: true});
      const resizeObserver = new ResizeObserver(updateEnds);
      resizeObserver.observe(strip);
      const lastObserver = new IntersectionObserver(([entry]) => {
        if (entry.isIntersecting) {
          this.endReached.emit();
        }
      }, {root: strip});
      lastObserver.observe(last);
      onCleanup(() => {
        strip.removeEventListener('scroll', updateEnds);
        resizeObserver.disconnect();
        lastObserver.disconnect();
      });
    });
  }

  protected readBook(card: BookRowCard): void {
    this.bookNavigation.readBook(card.book, {bookType: card.file?.bookType});
  }

  protected scrollPage(direction: -1 | 1): void {
    const strip = this.strip()?.nativeElement;
    strip?.scrollBy({left: direction * strip.clientWidth});
  }
}
