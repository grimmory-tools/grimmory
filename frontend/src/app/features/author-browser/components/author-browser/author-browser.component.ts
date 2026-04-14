import {Component, computed, DestroyRef, effect, HostListener, inject, OnInit, signal, ViewChild} from '@angular/core';
import {computeGridColumns} from '../../../../shared/util/viewport.util';
import {NgStyle} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter} from 'rxjs/operators';
import {ProgressSpinner} from 'primeng/progressspinner';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {Slider} from 'primeng/slider';
import {Popover} from 'primeng/popover';
import {Button} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {CdkVirtualScrollViewport, CdkVirtualForOf} from '@angular/cdk/scrolling';
import {CdkAutoSizeVirtualScroll} from '@angular/cdk-experimental/scrolling';
import {BookBrowserScrollService} from '../../../book/components/book-browser/book-browser-scroll.service';
import {MessageService} from 'primeng/api';
import {AuthorService} from '../../service/author.service';
import {AuthorSummary, EnrichedAuthor, AuthorFilters, DEFAULT_AUTHOR_FILTERS} from '../../model/author.model';
import {AuthorCardComponent} from '../author-card/author-card.component';
import {chunk} from '../../../../shared/util/array.util';
import {AuthorScalePreferenceService} from '../../service/author-scale-preference.service';
import {AuthorSelectionService, AuthorCheckboxClickEvent} from '../../service/author-selection.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {ActivatedRoute, NavigationStart, Router} from '@angular/router';
import {UserService} from '../../../settings/user-management/user.service';
import {BookService} from '../../../book/service/book.service';
import {Book, ReadStatus} from '../../../book/model/book.model';

type SortDirection = 'asc' | 'desc';

interface SortOption {
  label: string;
  value: string;
}

interface FilterOption {
  label: string;
  value: string;
}

const DEFAULT_SORT_DIRECTIONS: Record<string, SortDirection> = {
  'name': 'asc',
  'book-count': 'desc',
  'matched': 'desc',
  'recently-added': 'desc',
  'recently-read': 'desc',
  'reading-progress': 'desc',
  'avg-rating': 'desc',
  'photo': 'desc',
  'series-count': 'desc'
};

@Component({
  selector: 'app-author-browser',
  standalone: true,
  templateUrl: './author-browser.component.html',
  styleUrls: ['./author-browser.component.scss'],
  imports: [
    NgStyle,
    FormsModule,
    ProgressSpinner,
    InputText,
    Select,
    Slider,
    Popover,
    Button,
    Divider,
    Tooltip,
    TranslocoDirective,
    AuthorCardComponent,
    CdkVirtualScrollViewport,
    CdkVirtualForOf,
    CdkAutoSizeVirtualScroll,
  ]
})
export class AuthorBrowserComponent implements OnInit {

  private static readonly BASE_WIDTH = 165;
  private static readonly BASE_HEIGHT = 290;
  private static readonly MOBILE_BASE_WIDTH = 140;
  private static readonly MOBILE_BASE_HEIGHT = 250;

  private authorService = inject(AuthorService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  private pageTitle = inject(PageTitleService);
  private scrollService = inject(BookBrowserScrollService);
  private t = inject(TranslocoService);
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);
  protected userService = inject(UserService);
  protected authorScaleService = inject(AuthorScalePreferenceService);
  protected selectionService = inject(AuthorSelectionService);

  virtualScroller: CdkVirtualScrollViewport | undefined;

  @ViewChild('scroll')
  set scrollViewport(vp: CdkVirtualScrollViewport | undefined) {
    this.virtualScroller = vp;
    this.viewportResizeObserver?.disconnect();
    if (vp) {
      const el = vp.elementRef.nativeElement as HTMLElement;
      this.viewportWidth.set(el.clientWidth);
      this.viewportResizeObserver = new ResizeObserver(entries => {
        this.viewportWidth.set(entries[0]?.contentRect.width ?? el.clientWidth);
      });
      this.viewportResizeObserver.observe(el);
    }
  }

  private static readonly GRID_GAP = 20;
  private readonly viewportWidth = signal(0);
  private viewportResizeObserver: ResizeObserver | undefined;

  readonly screenWidth = signal(typeof window !== 'undefined' ? window.innerWidth : 1024);
  thumbnailCacheBusters = new Map<number, number>();
  private selectedAuthors = this.selectionService.selectedAuthors;
  private allAuthorsState = signal<AuthorSummary[] | null>(null);

  loading = computed(() => this.allAuthorsState() === null || this.bookService.isBooksLoading());
  protected currentUser = this.userService.currentUser;
  selectedCount = computed(() => this.selectedAuthors().size);
  searchTerm = signal('');
  sortBy = signal('name');
  sortDirection = signal<SortDirection>('asc');
  filters = signal<AuthorFilters>({...DEFAULT_AUTHOR_FILTERS});
  private enrichedAuthors = computed(() => {
    const authors = this.allAuthorsState();
    if (!authors) {
      return [];
    }

    return this.enrichAuthors(authors, this.bookService.books());
  });
  libraryOptions = computed<FilterOption[]>(() => {
    const allLabel = this.t.translate('authorBrowser.filters.all');
    const librarySet = new Set<string>();

    for (const author of this.enrichedAuthors()) {
      for (const library of author.libraryNames) {
        librarySet.add(library);
      }
    }

    return [
      {label: allLabel, value: 'all'},
      ...[...librarySet].sort().map(library => ({label: library, value: library}))
    ];
  });
  genreOptions = computed<FilterOption[]>(() => {
    const allLabel = this.t.translate('authorBrowser.filters.all');
    const genreSet = new Set<string>();

    for (const author of this.enrichedAuthors()) {
      for (const category of author.categories) {
        genreSet.add(category);
      }
    }

    return [
      {label: allLabel, value: 'all'},
      ...[...genreSet].sort().map(category => ({label: category, value: category}))
    ];
  });
  activeFilterCount = computed(() => {
    const filters = this.filters();
    let count = 0;
    if (filters.matchStatus !== 'all') count++;
    if (filters.photoStatus !== 'all') count++;
    if (filters.readStatus !== 'all') count++;
    if (filters.bookCount !== 'all') count++;
    if (filters.library !== 'all') count++;
    if (filters.genre !== 'all') count++;
    return count;
  });
  filteredAuthors = computed<EnrichedAuthor[]>(() => {
    let result = this.enrichedAuthors();
    const search = this.searchTerm().trim().toLowerCase();

    if (search) {
      result = result.filter(author => author.name.toLowerCase().includes(search));
    }

    result = this.applyFilters(result, this.filters());
    return this.applySort(result, this.sortBy(), this.sortDirection());
  });
  private readonly syncAuthorsEffect = effect(() => {
    const authors = this.authorService.allAuthors();
    if (authors !== null) {
      this.allAuthorsState.set(authors);
    }
  });
  private readonly syncSelectionEffect = effect(() => {
    this.selectionService.setCurrentAuthors(this.filteredAuthors());
  });

  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth.set(window.innerWidth);
  }

  readonly isMobile = computed(() => this.screenWidth() <= 767);

  readonly cardWidth = computed(() => {
    const base = this.isMobile()
      ? AuthorBrowserComponent.MOBILE_BASE_WIDTH
      : AuthorBrowserComponent.BASE_WIDTH;
    return Math.round(base * this.authorScaleService.scaleFactor());
  });

  readonly cardHeight = computed(() => {
    const base = this.isMobile()
      ? AuthorBrowserComponent.MOBILE_BASE_HEIGHT
      : AuthorBrowserComponent.BASE_HEIGHT;
    return Math.round(base * this.authorScaleService.scaleFactor());
  });

  readonly gridColumnMinWidth = computed(() => `${this.cardWidth()}px`);

  readonly gridColumns = computed(() => {
    return computeGridColumns(this.viewportWidth(), this.cardWidth() || 165, AuthorBrowserComponent.GRID_GAP);
  });

  readonly authorRows = computed(() => {
    return chunk(this.filteredAuthors(), this.gridColumns());
  });

  sortOptions: SortOption[] = [];

  private readonly validSortValues = [
    'name', 'book-count', 'matched', 'recently-added', 'recently-read',
    'reading-progress', 'avg-rating', 'photo', 'series-count'
  ];

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('authorBrowser.pageTitle'));
    this.destroyRef.onDestroy(() => this.viewportResizeObserver?.disconnect());
    this.sortOptions = [
      {label: this.t.translate('authorBrowser.sort.name'), value: 'name'},
      {label: this.t.translate('authorBrowser.sort.bookCount'), value: 'book-count'},
      {label: this.t.translate('authorBrowser.sort.matched'), value: 'matched'},
      {label: this.t.translate('authorBrowser.sort.recentlyAdded'), value: 'recently-added'},
      {label: this.t.translate('authorBrowser.sort.recentlyRead'), value: 'recently-read'},
      {label: this.t.translate('authorBrowser.sort.readingProgress'), value: 'reading-progress'},
      {label: this.t.translate('authorBrowser.sort.avgRating'), value: 'avg-rating'},
      {label: this.t.translate('authorBrowser.sort.photo'), value: 'photo'},
      {label: this.t.translate('authorBrowser.sort.seriesCount'), value: 'series-count'}
    ];

    const sortParam = this.activatedRoute.snapshot.queryParamMap.get('sort');
    const dirParam = this.activatedRoute.snapshot.queryParamMap.get('dir') as SortDirection | null;
    if (sortParam && this.validSortValues.includes(sortParam)) {
      this.sortBy.set(sortParam);
      this.sortDirection.set(dirParam === 'asc' || dirParam === 'desc' ? dirParam : DEFAULT_SORT_DIRECTIONS[sortParam]);
    }

    this.setupScrollPositionTracking();
    this.t.langChanges$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.sortOptions = [
          {label: this.t.translate('authorBrowser.sort.name'), value: 'name'},
          {label: this.t.translate('authorBrowser.sort.bookCount'), value: 'book-count'},
          {label: this.t.translate('authorBrowser.sort.matched'), value: 'matched'},
          {label: this.t.translate('authorBrowser.sort.recentlyAdded'), value: 'recently-added'},
          {label: this.t.translate('authorBrowser.sort.recentlyRead'), value: 'recently-read'},
          {label: this.t.translate('authorBrowser.sort.readingProgress'), value: 'reading-progress'},
          {label: this.t.translate('authorBrowser.sort.avgRating'), value: 'avg-rating'},
          {label: this.t.translate('authorBrowser.sort.photo'), value: 'photo'},
          {label: this.t.translate('authorBrowser.sort.seriesCount'), value: 'series-count'}
        ];
      });
    this.destroyRef.onDestroy(() => this.selectionService.deselectAll());
  }



  onSearchChange(value: string): void {
    this.searchTerm.set(value);
  }

  onSortChange(value: string): void {
    this.sortBy.set(value);
    const nextDirection = DEFAULT_SORT_DIRECTIONS[value] || 'asc';
    this.sortDirection.set(nextDirection);
    this.updateSortQueryParams(value, nextDirection);
  }

  toggleSortDirection(): void {
    const next: SortDirection = this.sortDirection() === 'asc' ? 'desc' : 'asc';
    this.sortDirection.set(next);
    this.updateSortQueryParams(this.sortBy(), next);
  }

  onFilterChange(key: keyof AuthorFilters, value: string): void {
    this.filters.update(current => ({...current, [key]: value}));
  }

  resetFilters(): void {
    this.filters.set({...DEFAULT_AUTHOR_FILTERS});
  }

  get canEditMetadata(): boolean {
    const user = this.userService.currentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canEditMetadata;
  }

  get canDeleteBook(): boolean {
    const user = this.userService.currentUser();
    return !!user?.permissions?.admin || !!user?.permissions?.canDeleteBook;
  }

  isAuthorSelected(authorId: number): boolean {
    return this.selectedAuthors().has(authorId);
  }

  onCheckboxClicked(event: AuthorCheckboxClickEvent): void {
    this.selectionService.handleCheckboxClick(event);
  }

  selectAllAuthors(): void {
    this.selectionService.selectAll();
  }

  deselectAllAuthors(): void {
    this.selectionService.deselectAll();
  }

  navigateToAuthor(author: AuthorSummary): void {
    this.router.navigate(['/author', author.id]);
  }

  navigateToAuthorEdit(author: AuthorSummary): void {
    this.router.navigate(['/author', author.id], {queryParams: {tab: 'edit'}});
  }

  deleteAuthor(author: AuthorSummary): void {
    this.authorService.deleteAuthors([author.id]).subscribe({
      next: () => {
        this.removeAuthorsFromList([author.id]);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.deleteSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteSuccessDetail')
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.deleteFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteFailedDetail')
        });
      }
    });
  }

  onAuthorQuickMatched(updated: AuthorSummary): void {
    this.thumbnailCacheBusters.set(updated.id, Date.now());
    this.allAuthorsState.update(current =>
      (current ?? []).map(author => author.id === updated.id ? updated : author)
    );
  }

  autoMatchSelected(): void {
    const ids = this.selectionService.getSelectedIds();
    this.selectionService.deselectAll();
    this.authorService.autoMatchAuthors(ids).subscribe({
      next: (matched) => {
        this.thumbnailCacheBusters.set(matched.id, Date.now());
        this.allAuthorsState.update(current => (current ?? []).map(author => author.id === matched.id
          ? {...author, asin: matched.asin, hasPhoto: matched.hasPhoto}
          : author
        ));
      },
      complete: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.autoMatchSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.autoMatchSuccessDetail')
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.autoMatchFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.autoMatchFailedDetail')
        });
      }
    });
  }

  deleteSelected(): void {
    const ids = this.selectionService.getSelectedIds();
    this.authorService.deleteAuthors(ids).subscribe({
      next: () => {
        this.selectionService.deselectAll();
        this.removeAuthorsFromList(ids);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('authorBrowser.toast.deleteSelectedSuccessSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteSelectedSuccessDetail')
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('authorBrowser.toast.deleteFailedSummary'),
          detail: this.t.translate('authorBrowser.toast.deleteFailedDetail')
        });
      }
    });
  }

  private updateSortQueryParams(sort: string, dir: SortDirection): void {
    this.router.navigate([], {
      queryParams: {sort, dir},
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  private enrichAuthors(authors: AuthorSummary[], books: Book[]): EnrichedAuthor[] {
    const booksByAuthor = new Map<string, Book[]>();
    for (const book of books) {
      if (book.metadata?.authors) {
        for (const authorName of book.metadata.authors) {
          const key = authorName.toLowerCase();
          let list = booksByAuthor.get(key);
          if (!list) {
            list = [];
            booksByAuthor.set(key, list);
          }
          list.push(book);
        }
      }
    }

    return authors.map(author => {
      const authorBooks = booksByAuthor.get(author.name.toLowerCase()) || [];

      const libraryIds = new Set<number>();
      const libraryNameSet = new Set<string>();
      const categorySet = new Set<string>();
      const seriesSet = new Set<string>();
      let latestAddedOn: string | null = null;
      let lastReadTime: string | null = null;
      let readCount = 0;
      let inProgressCount = 0;
      let ratingSum = 0;
      let ratingCount = 0;

      for (const book of authorBooks) {
        libraryIds.add(book.libraryId);
        if (book.libraryName) libraryNameSet.add(book.libraryName);

        if (book.metadata?.categories) {
          for (const cat of book.metadata.categories) categorySet.add(cat);
        }
        if (book.metadata?.seriesName) {
          seriesSet.add(book.metadata.seriesName.toLowerCase());
        }
        if (book.addedOn && (!latestAddedOn || book.addedOn > latestAddedOn)) {
          latestAddedOn = book.addedOn;
        }
        if (book.lastReadTime && (!lastReadTime || book.lastReadTime > lastReadTime)) {
          lastReadTime = book.lastReadTime;
        }
        if (book.readStatus === ReadStatus.READ) readCount++;
        if (book.readStatus === ReadStatus.READING || book.readStatus === ReadStatus.RE_READING) inProgressCount++;
        if (book.personalRating != null) {
          ratingSum += book.personalRating;
          ratingCount++;
        }
      }

      const totalBooks = authorBooks.length;
      let readStatus: EnrichedAuthor['readStatus'] = 'unread';
      if (totalBooks > 0) {
        if (readCount === totalBooks) {
          readStatus = 'all-read';
        } else if (inProgressCount > 0) {
          readStatus = 'in-progress';
        } else if (readCount > 0) {
          readStatus = 'some-read';
        }
      }

      return {
        ...author,
        libraryIds,
        libraryNames: [...libraryNameSet].sort(),
        categories: [...categorySet].sort(),
        readStatus,
        hasSeries: seriesSet.size > 0,
        seriesCount: seriesSet.size,
        latestAddedOn,
        lastReadTime,
        readingProgress: totalBooks > 0 ? Math.round((readCount / totalBooks) * 100) : 0,
        avgPersonalRating: ratingCount > 0 ? ratingSum / ratingCount : null
      };
    });
  }

  private applyFilters(authors: EnrichedAuthor[], filters: AuthorFilters): EnrichedAuthor[] {
    return authors.filter(a => {
      if (filters.matchStatus === 'matched' && !a.asin) return false;
      if (filters.matchStatus === 'unmatched' && a.asin) return false;

      if (filters.photoStatus === 'has-photo' && !a.hasPhoto) return false;
      if (filters.photoStatus === 'no-photo' && a.hasPhoto) return false;

      if (filters.readStatus !== 'all' && a.readStatus !== filters.readStatus) return false;

      if (filters.bookCount !== 'all') {
        const c = a.bookCount;
        switch (filters.bookCount) {
          case '0': if (c !== 0) return false; break;
          case '1': if (c !== 1) return false; break;
          case '2': if (c !== 2) return false; break;
          case '3': if (c !== 3) return false; break;
          case '4': if (c !== 4) return false; break;
          case '5': if (c !== 5) return false; break;
          case '6-10': if (c < 6 || c > 10) return false; break;
          case '11-20': if (c < 11 || c > 20) return false; break;
          case '21-35': if (c < 21 || c > 35) return false; break;
          case '36+': if (c < 36) return false; break;
        }
      }

      if (filters.library !== 'all' && !a.libraryNames.includes(filters.library)) return false;
      if (filters.genre !== 'all' && !a.categories.includes(filters.genre)) return false;

      return true;
    });
  }

  private getScrollPositionKey(): string {
    const path = this.activatedRoute.snapshot.routeConfig?.path ?? '';
    return this.scrollService.createKey(path, this.activatedRoute.snapshot.params);
  }

  private setupScrollPositionTracking(): void {
    this.router.events.pipe(
      filter(event => event instanceof NavigationStart),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.dismissBodyMenus();
      this.saveScrollPosition();
    });
  }

  private dismissBodyMenus(): void {
    document.querySelectorAll('.p-tieredmenu-overlay').forEach(el => el.remove());
  }

  private saveScrollPosition(): void {
    if (this.virtualScroller) {
      const key = this.getScrollPositionKey();
      const position = this.virtualScroller.measureScrollOffset('top') ?? 0;
      this.scrollService.savePosition(key, position);
    }
  }

  private removeAuthorsFromList(ids: number[]): void {
    const idSet = new Set(ids);
    this.allAuthorsState.update(current => (current ?? []).filter(author => !idSet.has(author.id)));
  }

  private applySort(authors: EnrichedAuthor[], sortBy: string, direction: SortDirection): EnrichedAuthor[] {
    const sorted = [...authors];
    const dir = direction === 'asc' ? 1 : -1;

    switch (sortBy) {
      case 'name':
        return sorted.sort((a, b) => dir * a.name.localeCompare(b.name));
      case 'book-count':
        return sorted.sort((a, b) => dir * (a.bookCount - b.bookCount));
      case 'matched':
        return sorted.sort((a, b) => {
          const aVal = a.asin ? 1 : 0;
          const bVal = b.asin ? 1 : 0;
          if (aVal !== bVal) return dir * (aVal - bVal);
          return a.name.localeCompare(b.name);
        });
      case 'recently-added':
        return sorted.sort((a, b) => {
          if (!a.latestAddedOn && !b.latestAddedOn) return a.name.localeCompare(b.name);
          if (!a.latestAddedOn) return 1;
          if (!b.latestAddedOn) return -1;
          return dir * a.latestAddedOn.localeCompare(b.latestAddedOn);
        });
      case 'recently-read':
        return sorted.sort((a, b) => {
          if (!a.lastReadTime && !b.lastReadTime) return a.name.localeCompare(b.name);
          if (!a.lastReadTime) return 1;
          if (!b.lastReadTime) return -1;
          return dir * a.lastReadTime.localeCompare(b.lastReadTime);
        });
      case 'reading-progress':
        return sorted.sort((a, b) => dir * (a.readingProgress - b.readingProgress));
      case 'avg-rating':
        return sorted.sort((a, b) => {
          if (a.avgPersonalRating == null && b.avgPersonalRating == null) return a.name.localeCompare(b.name);
          if (a.avgPersonalRating == null) return 1;
          if (b.avgPersonalRating == null) return -1;
          return dir * (a.avgPersonalRating - b.avgPersonalRating);
        });
      case 'photo':
        return sorted.sort((a, b) => {
          const aVal = a.hasPhoto ? 1 : 0;
          const bVal = b.hasPhoto ? 1 : 0;
          if (aVal !== bVal) return dir * (aVal - bVal);
          return a.name.localeCompare(b.name);
        });
      case 'series-count':
        return sorted.sort((a, b) => dir * (a.seriesCount - b.seriesCount));
      default:
        return sorted;
    }
  }
}
