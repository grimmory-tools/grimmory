import { AfterViewChecked, Component, computed, effect, ElementRef, inject, OnInit, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgClass } from '@angular/common';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from '@openng/optimus-ui/tabs';
import { ProgressSpinner } from '@openng/optimus-ui/progressspinner';
import { Button } from '@openng/optimus-ui/button';
import { Tag } from '@openng/optimus-ui/tag';
import { TranslocoDirective } from '@jsverse/transloco';
import { Tooltip } from '@openng/optimus-ui/tooltip';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { AuthorQueryService } from '../../data/author-query.service';
import { BookService } from '../../../book/service/book.service';
import { LegacyBookCardComponent } from '../../../book/components/legacy-book-card/legacy-book-card.component';
import { CoverScalePreferenceService } from '../../../../shared/service/cover-scale-preference.service';
import { UrlHelperService } from '../../../../shared/service/url-helper.service';
import { BookCardOverlayPreferenceService } from '../../../book/components/legacy-book-card/book-card-overlay-preference.service';
import { UserService } from '../../../settings/user-management/user.service';
import { AuthorMatchComponent } from '../author-match/author-match.component';
import { AuthorEditorComponent } from '../author-editor/author-editor.component';
import { injectAuthorQuickMatch } from '../../components/author-quick-match';
import { PageTitleService } from '../../../../shared/service/page-title.service';
import { createVirtualGrid } from '../../../../shared/util/virtual-grid.util';

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
    LegacyBookCardComponent,
    AuthorMatchComponent,
    AuthorEditorComponent
  ]
})
export class AuthorDetailComponent implements OnInit, AfterViewChecked {
  private readonly urlHelper = inject(UrlHelperService);
  private readonly authorQueries = inject(AuthorQueryService);

  private static readonly GRID_GAP = 21;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private bookService = inject(BookService);
  protected coverScalePreferenceService = inject(CoverScalePreferenceService);
  protected bookCardOverlayPreferenceService = inject(BookCardOverlayPreferenceService);
  protected userService = inject(UserService);
  private pageTitle = inject(PageTitleService);

  readonly descriptionContentRef = viewChild<ElementRef<HTMLElement>>('descriptionContent');
  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');

  private readonly authorId = Number(this.route.snapshot.paramMap.get('authorId'));
  private readonly authorQuery = injectQuery(() => this.authorQueries.detail(this.authorId));
  readonly author = computed(() => this.authorQuery.data() ?? null);
  readonly loading = computed(() => this.authorQuery.isPending());
  tab = 'books';
  isExpanded = false;
  isOverflowing = false;
  hasPhoto = true;
  photoTimestamp = Date.now();
  protected readonly authorQuickMatch = injectAuthorQuickMatch();
  authorBooks = computed(() => {
    const authorName = this.author()?.name?.toLowerCase();
    if (!authorName) {
      return [];
    }

    return this.bookService.books().filter(book =>
      book.metadata?.authors?.some(author => author.toLowerCase() === authorName)
    );
  });

  get currentCardSize() {
    return this.coverScalePreferenceService.currentCardSize();
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
    return this.urlHelper.getAuthorPhotoUrl(author.id, this.photoTimestamp);
  }

  get canEditMetadata(): boolean {
    const user = this.userService.getCurrentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canEditMetadata;
  }

  constructor() {
    effect(() => {
      const author = this.author();
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
    this.hasPhoto = false;
  }

  onAuthorUpdated(): void {
    this.hasPhoto = true;
    this.photoTimestamp = Date.now();
  }

  quickMatch(): void {
    const author = this.author();
    if (!author || this.authorQuickMatch.isPending()) return;
    this.authorQuickMatch.run(author.id, () => this.onAuthorUpdated());
  }
}
