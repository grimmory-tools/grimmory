import { AfterViewChecked, Component, computed, effect, ElementRef, inject, linkedSignal, OnInit, signal, untracked, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from '@openng/optimus-ui/tabs';
import { ProgressSpinner } from '@openng/optimus-ui/progressspinner';
import { Button } from '@openng/optimus-ui/button';
import { Tag } from '@openng/optimus-ui/tag';
import { TranslocoDirective } from '@jsverse/transloco';
import { Tooltip } from '@openng/optimus-ui/tooltip';
import { AuthorQueryService } from '../../data/author-query.service';
import { CoverScalePreferenceService } from '../../../../shared/service/cover-scale-preference.service';
import { UrlHelperService } from '../../../../shared/service/url-helper.service';
import { UserService } from '../../../settings/user-management/user.service';
import { AuthorMatchComponent } from '../author-match/author-match.component';
import { AuthorEditorComponent } from '../author-editor/author-editor.component';
import { injectAuthorQuickMatch } from '../../components/author-quick-match';
import { PageTitleService } from '../../../../shared/service/page-title.service';
import { createVirtualGrid } from '../../../../shared/util/virtual-grid.util';
import { createBrowseResults } from '../../../../shared/browse/results';
import { BookQueryService } from '../../../book/data/book-query.service';
import { DEFAULT_BOOK_SORT_TERMS } from '../../../book/data/book-query-params';
import { injectInfiniteQuery, injectQuery } from '@tanstack/angular-query-experimental';
import { BookCardComponent } from '../../../book/components/cards/book-card.component';
import { bookCardHeightForWidth } from '../../../book/components/cards/book-card.layout';
import { BookNavigationService } from '../../../book/service/book-navigation.service';
import { BookMenuComponent } from '../../../book/components/book-menu/book-menu.component';

@Component({
  selector: 'app-author-detail',
  standalone: true,
  templateUrl: './author-detail.component.html',
  styleUrls: ['./author-detail.component.scss'],
  imports: [
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    ProgressSpinner,
    Button,
    Tag,
    TranslocoDirective,
    Tooltip,
    AuthorMatchComponent,
    AuthorEditorComponent,
    BookCardComponent,
    BookMenuComponent
  ]
})
export class AuthorDetailComponent implements OnInit, AfterViewChecked {
  private readonly urlHelper = inject(UrlHelperService);
  private readonly authorQueries = inject(AuthorQueryService);

  private static readonly GRID_GAP = 16;

  private readonly route = inject(ActivatedRoute);
  protected readonly bookNavigation = inject(BookNavigationService);
  private readonly bookQueryService = inject(BookQueryService);
  private readonly coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected userService = inject(UserService);
  private readonly pageTitle = inject(PageTitleService);

  readonly descriptionContentRef = viewChild<ElementRef<HTMLElement>>('descriptionContent');
  private readonly tabPanels = viewChild<TabPanels, ElementRef<HTMLElement>>(TabPanels, {read: ElementRef});
  private readonly authorHeader = viewChild<ElementRef<HTMLElement>>('authorHeader');
  private readonly gridContent = viewChild<ElementRef<HTMLElement>>('gridContent');
  private readonly gridOffset = signal(0);
  protected readonly bookMenu = viewChild(BookMenuComponent);

  private readonly authorId = Number(this.route.snapshot.paramMap.get('authorId'));
  protected readonly authorQuery = injectQuery(() => this.authorQueries.detail(this.authorId));
  tab = 'books';
  isExpanded = false;
  isOverflowing = false;
  readonly photoTimestamp = signal(Date.now());
  readonly hasPhoto = linkedSignal({source: this.photoTimestamp, computation: () => true});
  protected readonly authorQuickMatch = injectAuthorQuickMatch();

  readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);

  readonly booksQuery = injectInfiniteQuery(() => ({
    ...this.bookQueryService.infinitePage({
      size: 30,
      sort: DEFAULT_BOOK_SORT_TERMS,
      facets: {
        author: {include: [this.authorQuery.data()?.name ?? ''], exclude: []}
      },
    }),
    enabled: this.authorQuery.data() != null,
  }));

  readonly results = createBrowseResults({
    query: this.booksQuery,
    listKey: computed(() => this.authorQuery.data()?.name ?? ''),
    artworkUrls: () => [],
    scrollToTop: () => undefined,
  });

  readonly virtualGrid = createVirtualGrid({
    items: this.results.items,
    scrollElement: this.tabPanels,
    scrollMargin: this.gridOffset,
    minItemWidth: computed(() => this.coverScalePreferenceService.currentCardSize().width),
    estimateItemHeight: width => bookCardHeightForWidth(width, {square: false, metaLines: 2}),
    gap: AuthorDetailComponent.GRID_GAP,
    fillItemWidth: true,
  });

  private readonly trackGridOffset = effect(onCleanup => {
    const scroller = this.tabPanels()?.nativeElement;
    const header = this.authorHeader()?.nativeElement;
    const grid = this.gridContent()?.nativeElement;
    if (!scroller || !header || !grid) {
      return;
    }
    const observer = new ResizeObserver(() => this.gridOffset.set(Math.round(
      grid.getBoundingClientRect().top - scroller.getBoundingClientRect().top + scroller.scrollTop
    )));
    observer.observe(header);
    onCleanup(() => observer.disconnect());
  });

  private readonly loadPagesInView = effect(() => {
    const virtualItems = this.virtualGrid.virtualizer.getVirtualItems();
    const first = virtualItems.at(0);
    const last = virtualItems.at(-1);
    if (first && last) {
      untracked(() => this.results.onRenderedRange({start: first.index, end: last.index}));
    }
  });

  get photoUrl(): string {
    const author = this.authorQuery.data();
    if (!author) return '';
    return this.urlHelper.getAuthorPhotoUrl(author.id, this.photoTimestamp());
  }

  get canEditMetadata(): boolean {
    const user = this.userService.getCurrentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canEditMetadata;
  }

  constructor() {
    effect(() => {
      const author = this.authorQuery.data();
      if (author) {
        this.pageTitle.setPageTitle(author.name);
      }
    });
  }

  ngOnInit(): void {
    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam) {
      this.tab = tabParam;
    }
  }

  ngAfterViewChecked(): void {
    const descriptionContent = this.descriptionContentRef();
    this.updateDescriptionOverflow(descriptionContent?.nativeElement);
  }

  updateDescriptionOverflow(element?: Pick<HTMLElement, 'scrollHeight' | 'clientHeight'>): void {
    if (!this.isExpanded && element) {
      this.isOverflowing = element.scrollHeight > element.clientHeight;
    }
  }

  toggleExpand(): void {
    this.isExpanded = !this.isExpanded;
  }

  onPhotoError(): void {
    this.hasPhoto.set(false);
  }

  onAuthorUpdated(): void {
    this.photoTimestamp.set(Date.now());
  }

  quickMatch(): void {
    const author = this.authorQuery.data();
    if (!author || this.authorQuickMatch.isPending()) return;
    this.authorQuickMatch.run(author.id, () => this.onAuthorUpdated());
  }
}
