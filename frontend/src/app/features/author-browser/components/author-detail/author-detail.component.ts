import { AfterViewChecked, Component, computed, effect, ElementRef, inject, OnInit, signal, untracked, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from '@openng/optimus-ui/tabs';
import { ProgressSpinner } from '@openng/optimus-ui/progressspinner';
import { Button } from '@openng/optimus-ui/button';
import { Tag } from '@openng/optimus-ui/tag';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from '@openng/optimus-ui/api';
import { Tooltip } from '@openng/optimus-ui/tooltip';
import { AuthorService } from '../../service/author.service';
import { AuthorDetails, AuthorSearchResult } from '../../model/author.model';
import { CoverScalePreferenceService } from '../../../../shared/service/cover-scale-preference.service';
import { UserService } from '../../../settings/user-management/user.service';
import { AuthorMatchComponent } from '../author-match/author-match.component';
import { AuthorEditorComponent } from '../author-editor/author-editor.component';
import { PageTitleService } from '../../../../shared/service/page-title.service';
import { createVirtualGrid } from '../../../../shared/util/virtual-grid.util';
import { createBrowseResults } from '../../../../shared/browse/results';
import { BookQueryService } from '../../../book/data/book-query.service';
import { DEFAULT_BOOK_SORT_TERMS } from '../../../book/data/book-query-params';
import { injectInfiniteQuery } from '@tanstack/angular-query-experimental';
import { BookCardComponent } from '../../../book/components/cards/book-card.component';
import { bookCardHeightForWidth } from '../../../book/components/cards/book-card.layout';
import { BookNavigationService } from '../../../book/service/book-navigation.service';
import { BookMenuComponent } from '../../../book/components/book-menu/book-menu.component';
import { AuthorMatchValidationDialogComponent } from '../author-match-validation-dialog/author-match-validation-dialog.component';

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
    BookMenuComponent,
    AuthorMatchValidationDialogComponent
  ]
})
export class AuthorDetailComponent implements OnInit, AfterViewChecked {

  private static readonly GRID_GAP = 16;

  private readonly route = inject(ActivatedRoute);
  private readonly authorService = inject(AuthorService);
  protected readonly bookNavigation = inject(BookNavigationService);
  private readonly bookQueryService = inject(BookQueryService);
  private readonly messageService = inject(MessageService);
  private readonly coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected userService = inject(UserService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  readonly descriptionContentRef = viewChild<ElementRef<HTMLElement>>('descriptionContent');
  private readonly tabPanels = viewChild<TabPanels, ElementRef<HTMLElement>>(TabPanels, {read: ElementRef});
  private readonly authorHeader = viewChild<ElementRef<HTMLElement>>('authorHeader');
  private readonly gridContent = viewChild<ElementRef<HTMLElement>>('gridContent');
  private readonly gridOffset = signal(0);
  protected readonly bookMenu = viewChild(BookMenuComponent);

  readonly loading = signal(true);
  tab = 'books';
  isExpanded = false;
  isOverflowing = false;
  hasPhoto = true;
  photoTimestamp = Date.now();
  quickMatching = false;
  showMatchValidation = false;
  matchCandidates: AuthorSearchResult[] = [];
  private readonly authorState = signal<AuthorDetails | null>(null);
  author = this.authorState.asReadonly();

  readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);

  readonly booksQuery = injectInfiniteQuery(() => ({
    ...this.bookQueryService.infinitePage({
      size: 30,
      sort: DEFAULT_BOOK_SORT_TERMS,
      facets: {
        author: {include: [this.author()?.name ?? ''], exclude: []}
      },
    }),
    enabled: this.author() != null,
  }));

  readonly results = createBrowseResults({
    query: this.booksQuery,
    listKey: computed(() => this.author()?.name ?? ''),
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
    const author = this.author();
    if (!author) return '';
    return this.authorService.getAuthorPhotoUrl(author.id) + '&t=' + this.photoTimestamp;
  }

  get canEditMetadata(): boolean {
    const user = this.userService.getCurrentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canEditMetadata;
  }

  ngOnInit(): void {
    const authorId = Number(this.route.snapshot.paramMap.get('authorId'));
    const tabParam = this.route.snapshot.queryParamMap.get('tab');
    if (tabParam) {
      this.tab = tabParam;
    }
    this.loadAuthor(authorId);
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
    this.hasPhoto = false;
  }

  onAuthorUpdated(updatedAuthor: AuthorDetails): void {
    this.authorState.set(updatedAuthor);
    this.hasPhoto = true;
    this.photoTimestamp = Date.now();
    this.authorService.patchAuthorInCache(updatedAuthor.id, {
      name: updatedAuthor.name,
      asin: updatedAuthor.asin,
      hasPhoto: true,
    });
  }

  quickMatch(): void {
    const author = this.author();
    if (!author || this.quickMatching || this.showMatchValidation) return;
    this.quickMatching = true;
    this.authorService.resolveAuthorMatch(author.id, author.name).subscribe({
      next: resolution => {
        this.quickMatching = false;
        if (resolution.status === 'matched') {
          this.finishQuickMatch(resolution.author);
        } else if (resolution.status === 'ambiguous') {
          this.matchCandidates = resolution.candidates;
          this.showMatchValidation = true;
        } else {
          this.showQuickMatchError();
        }
      },
      error: () => {
        this.quickMatching = false;
        this.showQuickMatchError();
      }
    });
  }

  selectMatchCandidate(candidate: AuthorSearchResult): void {
    const author = this.author();
    if (!author || this.quickMatching) return;
    this.quickMatching = true;
    this.showMatchValidation = false;
    this.authorService.matchAuthor(author.id, {
      source: candidate.source,
      asin: candidate.asin,
    }).subscribe({
      next: updated => {
        this.quickMatching = false;
        this.finishQuickMatch(updated);
      },
      error: () => {
        this.quickMatching = false;
        this.showQuickMatchError();
      }
    });
  }

  dismissMatchValidation(): void {
    this.showMatchValidation = false;
    this.matchCandidates = [];
  }

  private finishQuickMatch(updated: AuthorDetails): void {
    this.onAuthorUpdated(updated);
    this.matchCandidates = [];
    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('authorBrowser.toast.quickMatchSuccessSummary'),
      detail: this.t.translate('authorBrowser.toast.quickMatchSuccessDetail')
    });
  }

  private showQuickMatchError(): void {
    this.messageService.add({
      severity: 'error',
      summary: this.t.translate('authorBrowser.toast.quickMatchFailedSummary'),
      detail: this.t.translate('authorBrowser.toast.quickMatchFailedDetail')
    });
  }

  private loadAuthor(authorId: number): void {
    this.authorService.getAuthorDetails(authorId).subscribe({
      next: (author) => {
        this.authorState.set(author);
        this.loading.set(false);
        this.pageTitle.setPageTitle(author.name);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }
}
