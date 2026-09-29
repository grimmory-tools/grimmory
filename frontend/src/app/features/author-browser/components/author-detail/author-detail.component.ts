import {
  AfterViewChecked,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgClass } from '@angular/common';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from '@openng/optimus-ui/tabs';
import { ProgressSpinner } from '@openng/optimus-ui/progressspinner';
import { Button } from '@openng/optimus-ui/button';
import { Tag } from '@openng/optimus-ui/tag';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { MessageService } from '@openng/optimus-ui/api';
import { Tooltip } from '@openng/optimus-ui/tooltip';
import { AuthorService } from '../../service/author.service';
import { AuthorDetails } from '../../model/author.model';
import { BookService } from '../../../book/service/book.service';
import { CoverScalePreferenceService } from '../../../../shared/service/cover-scale-preference.service';
import { BookCardOverlayPreferenceService } from '../../../book/components/legacy-book-card/book-card-overlay-preference.service';
import { UserService } from '../../../settings/user-management/user.service';
import { AuthorMatchComponent } from '../author-match/author-match.component';
import { AuthorEditorComponent } from '../author-editor/author-editor.component';
import { PageTitleService } from '../../../../shared/service/page-title.service';
import { createVirtualGrid } from '../../../../shared/util/virtual-grid.util';
import {BookQueryService} from '../../../book/data/book-query.service';
import {injectInfiniteQuery} from '@tanstack/angular-query-experimental';
import {BookCardComponent} from '../../../book/components/cards/book-card.component';
import {bookCardHeightForWidth} from '../../../book/components/cards/book-card.layout';
import {UrlHelperService} from '../../../../shared/service/url-helper.service';
import {BookNavigationService} from '../../../book/service/book-navigation.service';
import {BookMenuComponent} from '../../../book/components/book-menu/book-menu.component';

@Component({
  selector: 'app-author-detail',
  standalone: true,
  templateUrl: './author-detail.component.html',
  styleUrls: ['./author-detail.component.scss'],
  imports: [
    NgClass,
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

  private static readonly GRID_GAP = 21;

  private route = inject(ActivatedRoute);
  private authorService = inject(AuthorService);
  protected readonly bookNavigation = inject(BookNavigationService);
  private readonly bookQuery = inject(BookQueryService);
  private messageService = inject(MessageService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected bookCardOverlayPreferenceService = inject(BookCardOverlayPreferenceService);
  protected userService = inject(UserService);
  private pageTitle = inject(PageTitleService);
  private t = inject(TranslocoService);

  readonly descriptionContentRef = viewChild<ElementRef<HTMLElement>>('descriptionContent');
  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');
  protected readonly bookMenu = viewChild(BookMenuComponent);

  loading = signal(true);
  tab = 'books';
  isExpanded = false;
  isOverflowing = false;
  hasPhoto = true;
  photoTimestamp = Date.now();
  quickMatching = false;
  private authorState = signal<AuthorDetails | null>(null);
  author = this.authorState.asReadonly();

  readonly menuOpenBookId = computed(() => this.bookMenu()?.openBookId() ?? null);

  readonly books = injectInfiniteQuery(() => this.bookQuery.infinitePage({
    enabled: this.author() != null,
    size: 30,
    sort: [],
    facets: {
      author: [ this.author()?.name ?? '' ]
    },
    facetLogic: 'and'
  }));

  authorBooks = computed(
    () => this.books.data()?.pages.flatMap(p => p.content) ?? []
  );

  private readonly fetchNextPageEffect = effect(() => {
    const virtualItems = this.virtualGrid.virtualizer.getVirtualItems();
    const lastItem = virtualItems[virtualItems.length - 1];

    if (!lastItem) {
      return
    }

    if (
      lastItem.index >= this.authorBooks().length - 1 &&
      this.books.hasNextPage() &&
      !this.books.isFetchingNextPage()
    ) {
      this.books.fetchNextPage()
    }
  })

  get currentCardSize() {
    const { width } = this.coverScalePreferenceService.currentCardSize();
    const height = bookCardHeightForWidth(width, {square: false, metaLines: 2})
    return { width, height };
  }

  readonly virtualGrid = createVirtualGrid({
    items: this.authorBooks,
    scrollElement: this.scrollElement,
    minItemWidth: computed(() => this.currentCardSize.width),
    estimateItemHeight: () => this.currentCardSize.height,
    gap: AuthorDetailComponent.GRID_GAP,
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
    if (!author || this.quickMatching) return;
    this.quickMatching = true;
    this.authorService.quickMatchAuthor(author.id).subscribe({
      next: (matched) => {
        this.onAuthorUpdated(matched);
        this.quickMatching = false;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.quickMatchSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.quickMatchSuccessDetail')
        });
      },
      error: () => {
        this.quickMatching = false;
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.quickMatchFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.quickMatchFailedDetail')
        });
      }
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
