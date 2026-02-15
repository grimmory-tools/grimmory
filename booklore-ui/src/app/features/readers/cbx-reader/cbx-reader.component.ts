import {Component, HostListener, inject, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {CommonModule} from '@angular/common';
import {forkJoin, Subject} from 'rxjs';
import {filter, first, map, switchMap, takeUntil, timeout} from 'rxjs/operators';
import {PageTitleService} from "../../../shared/service/page-title.service";
import {CbxReaderService} from '../../book/service/cbx-reader.service';
import {BookService} from '../../book/service/book.service';
import {CbxBackgroundColor, CbxFitMode, CbxPageSpread, CbxPageViewMode, CbxScrollMode, CbxReadingDirection, CbxSlideshowInterval, UserService} from '../../settings/user-management/user.service';
import {MessageService} from 'primeng/api';
import {TranslocoService, TranslocoPipe} from '@jsverse/transloco';
import {Book, BookSetting, BookType} from '../../book/model/book.model';
import {BookState} from '../../book/model/state/book-state.model';
import {ProgressSpinner} from 'primeng/progressspinner';
import {FormsModule} from "@angular/forms";
import {ReadingSessionService} from '../../../shared/service/reading-session.service';
import {ReaderHeaderFooterVisibilityManager} from '../ebook-reader';

import {CbxHeaderComponent} from './layout/header/cbx-header.component';
import {CbxHeaderService} from './layout/header/cbx-header.service';
import {CbxSidebarComponent} from './layout/sidebar/cbx-sidebar.component';
import {CbxSidebarService} from './layout/sidebar/cbx-sidebar.service';
import {CbxFooterComponent} from './layout/footer/cbx-footer.component';
import {CbxFooterService} from './layout/footer/cbx-footer.service';
import {CbxQuickSettingsComponent} from './layout/quick-settings/cbx-quick-settings.component';
import {CbxQuickSettingsService} from './layout/quick-settings/cbx-quick-settings.service';
import {CbxNoteDialogComponent, CbxNoteDialogData, CbxNoteDialogResult} from './dialogs/cbx-note-dialog.component';
import {CbxShortcutsHelpComponent} from './dialogs/cbx-shortcuts-help.component';
import {BookNoteV2} from '../../../shared/service/book-note-v2.service';


@Component({
  selector: 'app-cbx-reader',
  standalone: true,
  imports: [
    CommonModule,
    ProgressSpinner,
    FormsModule,
    TranslocoPipe,
    CbxHeaderComponent,
    CbxSidebarComponent,
    CbxFooterComponent,
    CbxQuickSettingsComponent,
    CbxNoteDialogComponent,
    CbxShortcutsHelpComponent
  ],
  providers: [
    CbxHeaderService,
    CbxSidebarService,
    CbxFooterService,
    CbxQuickSettingsService
  ],
  templateUrl: './cbx-reader.component.html',
  styleUrl: './cbx-reader.component.scss'
})
export class CbxReaderComponent implements OnInit, OnDestroy {
  private destroy$ = new Subject<void>();

  bookType!: BookType;
  bookId!: number;
  bookFileId?: number;
  altBookType?: string;
  pages: number[] = [];
  currentPage = 0;
  isLoading = true;

  pageSpread: CbxPageSpread = CbxPageSpread.ODD;
  pageViewMode: CbxPageViewMode = CbxPageViewMode.SINGLE_PAGE;
  backgroundColor: CbxBackgroundColor = CbxBackgroundColor.GRAY;
  fitMode: CbxFitMode = CbxFitMode.FIT_PAGE;
  scrollMode: CbxScrollMode = CbxScrollMode.PAGINATED;

  private touchStartX = 0;
  private touchEndX = 0;

  currentBook: Book | null = null;
  nextBookInSeries: Book | null = null;
  previousBookInSeries: Book | null = null;

  infiniteScrollPages: number[] = [];
  preloadCount: number = 3;
  isLoadingMore: boolean = false;

  private preloadedImages = new Map<string, HTMLImageElement>();
  previousImageUrls: string[] = [];
  currentImageUrls: string[] = [];
  isPageTransitioning = false;
  imagesLoaded = false;

  private visibilityManager!: ReaderHeaderFooterVisibilityManager;

  isCurrentPageBookmarked = false;
  currentPageHasNotes = false;
  showNoteDialog = false;
  noteDialogData: CbxNoteDialogData | null = null;
  private editingNote: BookNoteV2 | null = null;

  // Footer visibility (for slideshow progress bar positioning)
  isFooterVisible = false;

  // Fullscreen state
  isFullscreen = false;

  // Reading direction
  readingDirection: CbxReadingDirection = CbxReadingDirection.LTR;

  // Slideshow state
  isSlideshowActive = false;
  slideshowInterval: CbxSlideshowInterval = CbxSlideshowInterval.FIVE_SECONDS;
  private slideshowTimer: ReturnType<typeof setInterval> | null = null;

  // Double-tap zoom
  private lastTapTime = 0;
  private originalFitMode: CbxFitMode | null = null;

  // Shortcuts help dialog
  showShortcutsHelp = false;

  // Double page detection
  private pageDimensionsCache = new Map<number, {width: number, height: number}>();

  protected readonly CbxReadingDirection = CbxReadingDirection;
  protected readonly CbxSlideshowInterval = CbxSlideshowInterval;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cbxReaderService = inject(CbxReaderService);
  private bookService = inject(BookService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);
  private pageTitle = inject(PageTitleService);
  private readingSessionService = inject(ReadingSessionService);
  private headerService = inject(CbxHeaderService);
  private sidebarService = inject(CbxSidebarService);
  private footerService = inject(CbxFooterService);
  private quickSettingsService = inject(CbxQuickSettingsService);

  protected readonly CbxScrollMode = CbxScrollMode;
  protected readonly CbxFitMode = CbxFitMode;
  protected readonly CbxBackgroundColor = CbxBackgroundColor;
  protected readonly CbxPageViewMode = CbxPageViewMode;
  protected readonly CbxPageSpread = CbxPageSpread;

  private static readonly TYPE_CBX = 'CBX';
  private static readonly SETTING_GLOBAL = 'Global';

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.headerService.setForceVisible(state.headerVisible);
      this.footerService.setForceVisible(state.footerVisible);
    });

    this.footerService.forceVisible$
      .pipe(takeUntil(this.destroy$))
      .subscribe(visible => this.isFooterVisible = visible);

    this.subscribeToHeaderEvents();
    this.subscribeToSidebarEvents();
    this.subscribeToFooterEvents();
    this.subscribeToQuickSettingsEvents();

    this.route.paramMap.subscribe((params) => {
      this.isLoading = true;
      this.bookId = +params.get('bookId')!;
      this.altBookType = this.route.snapshot.queryParamMap.get('bookType') ?? undefined;

      this.previousBookInSeries = null;
      this.nextBookInSeries = null;
      this.currentBook = null;

      this.bookService.getBookByIdFromAPI(this.bookId, false).pipe(
        switchMap((book) => {
          // Use alternative bookType from query param if provided, otherwise use primary
          this.bookType = (this.altBookType as BookType) ?? book.primaryFile?.bookType!;
          this.currentBook = book;

          // Determine which file ID to use for progress tracking
          if (this.altBookType) {
            const altFile = book.alternativeFormats?.find(f => f.bookType === this.altBookType);
            this.bookFileId = altFile?.id;
          } else {
            this.bookFileId = book.primaryFile?.id;
          }

          return forkJoin([
            this.bookService.getBookSetting(this.bookId, this.bookFileId!),
            this.userService.getMyself()
          ]).pipe(map(([bookSettings, myself]) => ({ book, bookSettings, myself })));
        })
      ).subscribe({
        next: ({ book, bookSettings, myself }) => {
          const userSettings = myself.userSettings;

          this.pageTitle.setBookPageTitle(book);

          const title = book.metadata?.title || book.fileName;
          this.headerService.initialize(this.bookId, title, this.destroy$);
          this.sidebarService.initialize(this.bookId, book, this.destroy$, this.altBookType);

          if (book.metadata?.seriesName) {
            this.loadSeriesNavigationAsync(book);
          }

          const pagesObservable = this.cbxReaderService.getAvailablePages(this.bookId, this.altBookType);

          pagesObservable.subscribe({
            next: (pages) => {
              this.pages = pages;
              if (this.bookType === CbxReaderComponent.TYPE_CBX) {
                const global = userSettings.perBookSetting.cbx === CbxReaderComponent.SETTING_GLOBAL;
                this.pageViewMode = global
                  ? this.CbxPageViewMode[userSettings.cbxReaderSetting.pageViewMode as keyof typeof CbxPageViewMode] || this.CbxPageViewMode.SINGLE_PAGE
                  : this.CbxPageViewMode[bookSettings.cbxSettings?.pageViewMode as keyof typeof CbxPageViewMode] || this.CbxPageViewMode[userSettings.cbxReaderSetting.pageViewMode as keyof typeof CbxPageViewMode] || this.CbxPageViewMode.SINGLE_PAGE;

                this.pageSpread = global
                  ? this.CbxPageSpread[userSettings.cbxReaderSetting.pageSpread as keyof typeof CbxPageSpread] || this.CbxPageSpread.ODD
                  : this.CbxPageSpread[bookSettings.cbxSettings?.pageSpread as keyof typeof CbxPageSpread] || this.CbxPageSpread[userSettings.cbxReaderSetting.pageSpread as keyof typeof CbxPageSpread] || this.CbxPageSpread.ODD;

                this.fitMode = global
                  ? this.CbxFitMode[userSettings.cbxReaderSetting.fitMode as keyof typeof CbxFitMode] || this.CbxFitMode.FIT_PAGE
                  : this.CbxFitMode[bookSettings.cbxSettings?.fitMode as keyof typeof CbxFitMode] || this.CbxFitMode[userSettings.cbxReaderSetting.fitMode as keyof typeof CbxFitMode] || this.CbxFitMode.FIT_PAGE;

                this.scrollMode = global
                  ? this.CbxScrollMode[userSettings.cbxReaderSetting.scrollMode as keyof typeof CbxScrollMode] || CbxScrollMode.PAGINATED
                  : this.CbxScrollMode[bookSettings.cbxSettings?.scrollMode as keyof typeof CbxScrollMode] || this.CbxScrollMode[userSettings.cbxReaderSetting.scrollMode as keyof typeof CbxScrollMode] || CbxScrollMode.PAGINATED;

                this.backgroundColor = global
                  ? this.CbxBackgroundColor[userSettings.cbxReaderSetting.backgroundColor as keyof typeof CbxBackgroundColor] || CbxBackgroundColor.GRAY
                  : this.CbxBackgroundColor[bookSettings.cbxSettings?.backgroundColor as keyof typeof CbxBackgroundColor] || this.CbxBackgroundColor[userSettings.cbxReaderSetting.backgroundColor as keyof typeof CbxBackgroundColor] || CbxBackgroundColor.GRAY;

                this.currentPage = (book.cbxProgress?.page || 1) - 1;

                if (this.scrollMode === CbxScrollMode.INFINITE) {
                  this.initializeInfiniteScroll();
                }
              }

              this.alignCurrentPageToParity();
              this.updateServiceStates();
              this.updateBookmarkState();
              this.updateNotesState();
              this.isLoading = false;

              this.updateCurrentImageUrls();
              this.preloadAdjacentPages();

              const percentage = this.pages.length > 0 ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10 : 0;
              this.readingSessionService.startSession(this.bookId, "CBX", (this.currentPage + 1).toString(), percentage);
            },
            error: (err) => {
              const errorMessage = err?.error?.message || this.t.translate('shared.reader.failedToLoadPages');
              this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: errorMessage});
              this.isLoading = false;
            }
          });
        },
        error: (err) => {
          const errorMessage = err?.error?.message || this.t.translate('shared.reader.failedToLoadBook');
          this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: errorMessage});
          this.isLoading = false;
        }
      });
    });
  }

  private subscribeToHeaderEvents(): void {
    this.headerService.showQuickSettings$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.quickSettingsService.show();
      });

    this.headerService.toggleBookmark$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.toggleBookmark();
      });

    this.headerService.openNoteDialog$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.openNoteDialog();
      });

    this.headerService.toggleFullscreen$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.toggleFullscreen();
      });

    this.headerService.toggleSlideshow$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.toggleSlideshow();
      });

    this.headerService.showShortcutsHelp$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.showShortcutsHelp = true;
      });
  }

  private subscribeToSidebarEvents(): void {
    this.sidebarService.navigateToPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => {
        this.goToPage(page);
      });

    this.sidebarService.bookmarksChanged$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateBookmarkState();
      });

    this.sidebarService.bookmarks$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateBookmarkState();
      });

    this.sidebarService.notes$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.updateNotesState();
      });

    this.sidebarService.editNote$
      .pipe(takeUntil(this.destroy$))
      .subscribe(note => {
        this.openNoteDialogForEdit(note);
      });
  }

  private subscribeToFooterEvents(): void {
    this.footerService.previousPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.previousPage());

    this.footerService.nextPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.nextPage());

    this.footerService.goToPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => this.goToPage(page));

    this.footerService.firstPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.firstPage());

    this.footerService.lastPage$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.lastPage());

    this.footerService.previousBook$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.navigateToPreviousBook());

    this.footerService.nextBook$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.navigateToNextBook());

    this.footerService.sliderChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(page => this.goToPage(page));
  }

  private subscribeToQuickSettingsEvents(): void {
    this.quickSettingsService.fitModeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(mode => this.onFitModeChange(mode));

    this.quickSettingsService.scrollModeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(mode => this.onScrollModeChange(mode));

    this.quickSettingsService.pageViewModeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(mode => this.onPageViewModeChange(mode));

    this.quickSettingsService.pageSpreadChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(spread => this.onPageSpreadChange(spread));

    this.quickSettingsService.backgroundColorChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(color => this.onBackgroundColorChange(color));

    this.quickSettingsService.readingDirectionChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(direction => this.onReadingDirectionChange(direction));

    this.quickSettingsService.slideshowIntervalChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(interval => this.onSlideshowIntervalChange(interval));
  }

  private updateServiceStates(): void {
    this.footerService.updateState({
      currentPage: this.currentPage,
      totalPages: this.pages.length,
      isTwoPageView: this.isTwoPageView,
      previousBookInSeries: this.previousBookInSeries,
      nextBookInSeries: this.nextBookInSeries,
      hasSeries: this.hasSeries
    });

    this.quickSettingsService.updateState({
      fitMode: this.fitMode,
      scrollMode: this.scrollMode,
      pageViewMode: this.pageViewMode,
      pageSpread: this.pageSpread,
      backgroundColor: this.backgroundColor,
      readingDirection: this.readingDirection,
      slideshowInterval: this.slideshowInterval
    });

    this.headerService.updateState({
      isFullscreen: this.isFullscreen,
      isSlideshowActive: this.isSlideshowActive
    });

    this.sidebarService.setCurrentPage(this.currentPage + 1);
  }

  private updateFooterPage(): void {
    this.footerService.setCurrentPage(this.currentPage);
    this.sidebarService.setCurrentPage(this.currentPage + 1);
    this.updateBookmarkState();
    this.updateNotesState();
  }

  private updateCurrentImageUrls(): void {
    if (!this.pages.length) {
      this.currentImageUrls = [];
      return;
    }

    const urls: string[] = [];
    urls.push(this.getPageImageUrl(this.currentPage));

    if (this.isTwoPageView && this.currentPage + 1 < this.pages.length) {
      urls.push(this.getPageImageUrl(this.currentPage + 1));
    }

    this.currentImageUrls = urls;
  }

  private preloadAdjacentPages(): void {
    if (!this.pages.length || this.scrollMode === CbxScrollMode.INFINITE || this.scrollMode === CbxScrollMode.LONG_STRIP) return;

    const pagesToPreload: number[] = [];

    const step = this.isTwoPageView ? 2 : 1;
    for (let i = 1; i <= 2; i++) {
      const nextPage = this.currentPage + (step * i);
      if (nextPage < this.pages.length) {
        pagesToPreload.push(nextPage);
        if (this.isTwoPageView && nextPage + 1 < this.pages.length) {
          pagesToPreload.push(nextPage + 1);
        }
      }
    }

    for (let i = 1; i <= 2; i++) {
      const prevPage = this.currentPage - (step * i);
      if (prevPage >= 0) {
        pagesToPreload.push(prevPage);
        if (this.isTwoPageView && prevPage + 1 < this.pages.length) {
          pagesToPreload.push(prevPage + 1);
        }
      }
    }

    pagesToPreload.forEach(pageIndex => {
      const url = this.getPageImageUrl(pageIndex);
      if (!this.preloadedImages.has(url)) {
        const img = new Image();
        img.src = url;
        this.preloadedImages.set(url, img);
      }
    });

    this.cleanupPreloadedImages(pagesToPreload);
  }

  private cleanupPreloadedImages(keepPages: number[]): void {
    const keepUrls = new Set(keepPages.map(p => this.getPageImageUrl(p)));
    this.currentImageUrls.forEach(url => keepUrls.add(url));

    for (const url of this.preloadedImages.keys()) {
      if (!keepUrls.has(url)) {
        this.preloadedImages.delete(url);
      }
    }
  }

  private transitionToNewPage(): void {
    if (this.scrollMode === CbxScrollMode.INFINITE || this.scrollMode === CbxScrollMode.LONG_STRIP) {
      this.updateCurrentImageUrls();
      return;
    }

    const newUrls = this.getNewImageUrls();

    const allPreloaded = newUrls.every(url => {
      const img = this.preloadedImages.get(url);
      return img && img.complete && img.naturalWidth > 0;
    });

    if (allPreloaded) {
      this.previousImageUrls = [...this.currentImageUrls];
      this.currentImageUrls = newUrls;
      this.isPageTransitioning = true;
      this.imagesLoaded = true;

      setTimeout(() => {
        this.isPageTransitioning = false;
        this.previousImageUrls = [];
      }, 150);
    } else {
      this.isPageTransitioning = true;
      this.imagesLoaded = false;
      this.previousImageUrls = [...this.currentImageUrls];

      this.preloadImagesAndTransition(newUrls);
    }

    this.preloadAdjacentPages();
  }

  private getNewImageUrls(): string[] {
    if (!this.pages.length) return [];

    const urls: string[] = [];
    urls.push(this.getPageImageUrl(this.currentPage));

    if (this.isTwoPageView && this.currentPage + 1 < this.pages.length) {
      urls.push(this.getPageImageUrl(this.currentPage + 1));
    }

    return urls;
  }

  private preloadImagesAndTransition(urls: string[]): void {
    let loadedCount = 0;
    const totalImages = urls.length;

    urls.forEach(url => {
      const img = new Image();
      img.onload = () => {
        loadedCount++;
        this.preloadedImages.set(url, img);

        if (loadedCount === totalImages) {
          this.currentImageUrls = urls;
          this.imagesLoaded = true;

          setTimeout(() => {
            this.isPageTransitioning = false;
            this.previousImageUrls = [];
          }, 150);
        }
      };
      img.onerror = () => {
        loadedCount++;
        if (loadedCount === totalImages) {
          this.currentImageUrls = urls;
          this.imagesLoaded = true;
          this.isPageTransitioning = false;
          this.previousImageUrls = [];
        }
      };
      img.src = url;
    });
  }

  onImageLoad(): void {
    this.imagesLoaded = true;
  }

  get isTwoPageView(): boolean {
    return this.pageViewMode === this.CbxPageViewMode.TWO_PAGE;
  }

  get hasSeries(): boolean {
    return !!this.currentBook?.metadata?.seriesName;
  }

  get showQuickSettings(): boolean {
    return this.quickSettingsService.isVisible;
  }

  nextPage() {
    this.pauseSlideshowOnInteraction();
    this.advancePage(1);
  }

  previousPage() {
    this.pauseSlideshowOnInteraction();
    this.advancePage(-1);
  }

  private advancePage(direction: 1 | -1): void {
    const previousPage = this.currentPage;
    const step = this.getPageStep();

    if (this.scrollMode === CbxScrollMode.INFINITE || this.scrollMode === CbxScrollMode.LONG_STRIP) {
      const newPage = this.currentPage + direction;
      if (newPage >= 0 && newPage < this.pages.length) {
        this.currentPage = newPage;
        this.scrollToPage(this.currentPage);
        this.updateProgress();
        this.updateSessionProgress();
        this.updateFooterPage();
      }
      return;
    }

    if (direction > 0) {
      // Forward navigation
      if (this.isTwoPageView) {
        const effectiveStep = this.shouldShowSinglePage(this.currentPage) ? 1 : step;
        if (this.currentPage + effectiveStep < this.pages.length) {
          this.currentPage += effectiveStep;
        } else if (this.currentPage + 1 < this.pages.length) {
          this.currentPage += 1;
        }
      } else if (this.currentPage < this.pages.length - 1) {
        this.currentPage++;
      }
    } else {
      // Backward navigation
      if (this.isTwoPageView) {
        this.currentPage = Math.max(0, this.currentPage - step);
      } else {
        this.currentPage = Math.max(0, this.currentPage - 1);
      }
    }

    if (this.currentPage !== previousPage) {
      this.transitionToNewPage();
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    }

    // Stop slideshow at last page
    if (this.isSlideshowActive && this.currentPage >= this.pages.length - 1) {
      this.stopSlideshow();
    }
  }

  private getPageStep(): number {
    return this.isTwoPageView ? 2 : 1;
  }

  private alignCurrentPageToParity() {
    if (!this.pages.length || !this.isTwoPageView) return;

    const desiredOdd = this.pageSpread === CbxPageSpread.ODD;
    for (let i = this.currentPage; i >= 0; i--) {
      if ((this.pages[i] % 2 === 1) === desiredOdd) {
        this.currentPage = i;
        this.updateProgress();
        return;
      }
    }
    for (let i = 0; i < this.pages.length; i++) {
      if ((this.pages[i] % 2 === 1) === desiredOdd) {
        this.currentPage = i;
        this.updateProgress();
        return;
      }
    }
  }

  onFitModeChange(mode: CbxFitMode): void {
    this.fitMode = mode;
    this.quickSettingsService.setFitMode(mode);
    this.updateViewerSetting();
  }

  onScrollModeChange(mode: CbxScrollMode): void {
    this.scrollMode = mode;
    this.quickSettingsService.setScrollMode(mode);
    this.updateViewerSetting();

    if (this.scrollMode === CbxScrollMode.INFINITE || this.scrollMode === CbxScrollMode.LONG_STRIP) {
      this.initializeInfiniteScroll();
      setTimeout(() => this.scrollToPage(this.currentPage), 100);
    } else {
      this.updateCurrentImageUrls();
      this.preloadAdjacentPages();
    }
  }

  onPageViewModeChange(mode: CbxPageViewMode): void {
    if (mode === CbxPageViewMode.TWO_PAGE && this.isPhonePortrait()) return;
    this.pageViewMode = mode;
    this.quickSettingsService.setPageViewMode(mode);
    this.alignCurrentPageToParity();
    this.updateCurrentImageUrls();
    this.preloadAdjacentPages();
    this.footerService.setTwoPageView(this.isTwoPageView);
    this.updateViewerSetting();
  }

  onPageSpreadChange(spread: CbxPageSpread): void {
    this.pageSpread = spread;
    this.quickSettingsService.setPageSpread(spread);
    this.alignCurrentPageToParity();
    this.updateCurrentImageUrls();
    this.preloadAdjacentPages();
    this.updateViewerSetting();
  }

  onBackgroundColorChange(color: CbxBackgroundColor): void {
    this.backgroundColor = color;
    this.quickSettingsService.setBackgroundColor(color);
    this.updateViewerSetting();
  }

  private initializeInfiniteScroll(): void {
    this.infiniteScrollPages = [];
    const endIndex = Math.min(this.currentPage + this.preloadCount, this.pages.length);
    for (let i = this.currentPage; i < endIndex; i++) {
      this.infiniteScrollPages.push(i);
    }
  }

  onScroll(event: Event): void {
    if ((this.scrollMode !== CbxScrollMode.INFINITE && this.scrollMode !== CbxScrollMode.LONG_STRIP) || this.isLoadingMore) return;

    const container = event.target as HTMLElement;
    const scrollPosition = container.scrollTop + container.clientHeight;
    const scrollHeight = container.scrollHeight;

    if (scrollPosition >= scrollHeight * 0.8) {
      this.loadMorePages();
    }

    this.updateCurrentPageFromScroll(container);
  }

  private loadMorePages(): void {
    if (this.isLoadingMore) return;

    const lastLoadedIndex = this.infiniteScrollPages[this.infiniteScrollPages.length - 1];
    if (lastLoadedIndex >= this.pages.length - 1) return;

    this.isLoadingMore = true;
    const endIndex = Math.min(lastLoadedIndex + this.preloadCount + 1, this.pages.length);

    setTimeout(() => {
      for (let i = lastLoadedIndex + 1; i < endIndex; i++) {
        this.infiniteScrollPages.push(i);
      }
      this.isLoadingMore = false;
    }, 100);
  }

  private updateCurrentPageFromScroll(container: HTMLElement): void {
    const images = container.querySelectorAll('.page-image');
    const containerRect = container.getBoundingClientRect();

    for (let i = 0; i < images.length; i++) {
      const img = images[i] as HTMLElement;
      const rect = img.getBoundingClientRect();

      if (rect.top <= containerRect.top + containerRect.height / 2 &&
        rect.bottom >= containerRect.top + containerRect.height / 2) {
        const newPage = this.infiniteScrollPages[i];
        if (newPage !== this.currentPage) {
          this.currentPage = newPage;
          this.updateProgress();
          this.updateSessionProgress();
          this.updateFooterPage();
        }
        break;
      }
    }
  }

  private getPageImageUrl(pageIndex: number): string {
    return this.cbxReaderService.getPageImageUrl(this.bookId, this.pages[pageIndex], this.altBookType);
  }

  get infiniteScrollImageUrls(): string[] {
    return this.infiniteScrollPages.map(pageIndex => this.getPageImageUrl(pageIndex));
  }

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = {
      cbxSettings: {
        pageSpread: this.pageSpread,
        pageViewMode: this.pageViewMode,
        fitMode: this.fitMode,
        scrollMode: this.scrollMode,
        backgroundColor: this.backgroundColor,
      }
    };
    this.bookService.updateViewerSetting(bookSetting, this.bookId).subscribe();
  }

  updateProgress(): void {
    const percentage = this.pages.length > 0
      ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10
      : 0;

    this.bookService.saveCbxProgress(this.bookId, this.currentPage + 1, percentage, this.bookFileId).subscribe();
  }

  private updateSessionProgress(): void {
    const percentage = this.pages.length > 0
      ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10
      : 0;
    this.readingSessionService.updateProgress(
      (this.currentPage + 1).toString(),
      percentage
    );
  }

  goToPage(page: number): void {
    if (page < 1 || page > this.pages.length) return;

    const targetIndex = page - 1;
    if (targetIndex === this.currentPage) return;

    this.currentPage = targetIndex;

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      this.ensurePageLoaded(targetIndex);
      this.scrollToPage(targetIndex);
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    } else {
      this.alignCurrentPageToParity();
      this.transitionToNewPage();
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    }
  }

  firstPage(): void {
    this.goToPage(1);
  }

  lastPage(): void {
    this.goToPage(this.pages.length);
  }

  private scrollToPage(pageIndex: number): void {
    this.ensurePageLoaded(pageIndex);

    setTimeout(() => {
      const container = document.querySelector('.image-container.infinite-scroll') as HTMLElement;
      if (!container) return;

      const images = container.querySelectorAll('.page-image');
      const indexInScroll = this.infiniteScrollPages.indexOf(pageIndex);

      if (indexInScroll >= 0 && indexInScroll < images.length) {
        const targetImage = images[indexInScroll] as HTMLElement;
        targetImage.scrollIntoView({behavior: 'smooth', block: 'start'});
      }
    }, 100);
  }

  private ensurePageLoaded(pageIndex: number): void {
    if (this.infiniteScrollPages.includes(pageIndex)) return;

    this.infiniteScrollPages = [];
    const startIndex = Math.max(0, pageIndex - 1);
    const endIndex = Math.min(pageIndex + this.preloadCount, this.pages.length);

    for (let i = startIndex; i < endIndex; i++) {
      this.infiniteScrollPages.push(i);
    }
  }

  onImageClick(): void {
    this.visibilityManager.togglePinned();
  }

  @HostListener('window:keydown', ['$event'])
  handleKeyDown(event: KeyboardEvent) {
    // Ignore if typing in input/textarea
    const target = event.target as HTMLElement;
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
      return;
    }

    const isRtl = this.readingDirection === CbxReadingDirection.RTL;

    switch (event.key) {
      case 'ArrowRight':
        isRtl ? this.previousPage() : this.nextPage();
        event.preventDefault();
        break;
      case 'ArrowLeft':
        isRtl ? this.nextPage() : this.previousPage();
        event.preventDefault();
        break;
      case ' ':
        event.preventDefault();
        event.shiftKey ? this.previousPage() : this.nextPage();
        break;
      case 'Home':
        event.preventDefault();
        this.firstPage();
        break;
      case 'End':
        event.preventDefault();
        this.lastPage();
        break;
      case 'PageUp':
        event.preventDefault();
        this.previousPage();
        break;
      case 'PageDown':
        event.preventDefault();
        this.nextPage();
        break;
      case 'f':
      case 'F':
        event.preventDefault();
        this.toggleFullscreen();
        break;
      case 'd':
      case 'D':
        event.preventDefault();
        this.toggleReadingDirection();
        break;
      case 'p':
      case 'P':
        event.preventDefault();
        this.toggleSlideshow();
        break;
      case '?':
        event.preventDefault();
        this.showShortcutsHelp = true;
        break;
      case 'Escape':
        if (this.showShortcutsHelp) {
          this.showShortcutsHelp = false;
        } else if (this.showNoteDialog) {
          this.showNoteDialog = false;
        } else if (this.showQuickSettings) {
          this.quickSettingsService.close();
        } else if (this.isFullscreen) {
          this.exitFullscreen();
        }
        break;
    }
  }

  @HostListener('document:fullscreenchange')
  onFullscreenChange(): void {
    this.isFullscreen = !!document.fullscreenElement;
    this.headerService.updateState({isFullscreen: this.isFullscreen, isSlideshowActive: this.isSlideshowActive});
  }

  @HostListener('touchstart', ['$event'])
  onTouchStart(event: TouchEvent) {
    this.touchStartX = event.changedTouches[0].screenX;
  }

  @HostListener('touchend', ['$event'])
  onTouchEnd(event: TouchEvent) {
    this.touchEndX = event.changedTouches[0].screenX;
    this.handleSwipeGesture();
  }

  @HostListener('window:resize')
  onResize() {
    this.visibilityManager.updateWindowHeight(window.innerHeight);
    this.enforcePortraitSinglePageView();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.visibilityManager.handleMouseMove(event.clientY);
  }

  @HostListener('document:mouseleave', ['$event'])
  onMouseLeave(event: MouseEvent): void {
    this.visibilityManager.handleMouseLeave();
  }

  private handleSwipeGesture() {
    const delta = this.touchEndX - this.touchStartX;
    if (Math.abs(delta) >= 50) {
      // In RTL mode, swipe directions are reversed
      const isRtl = this.readingDirection === CbxReadingDirection.RTL;
      const shouldGoNext = isRtl ? delta > 0 : delta < 0;
      shouldGoNext ? this.nextPage() : this.previousPage();
    }
  }

  private enforcePortraitSinglePageView() {
    if (this.isPhonePortrait() && this.isTwoPageView) {
      this.pageViewMode = CbxPageViewMode.SINGLE_PAGE;
      this.quickSettingsService.setPageViewMode(this.pageViewMode);
      this.footerService.setTwoPageView(false);
      this.updateViewerSetting();
    }
  }

  private isPhonePortrait(): boolean {
    return window.innerWidth < 768 && window.innerHeight > window.innerWidth;
  }

  get isAtLastPage(): boolean {
    return this.currentPage >= this.pages.length - 1;
  }

  navigateToPreviousBook(): void {
    if (this.previousBookInSeries) {
      this.endReadingSession();
      this.router.navigate(['/cbx-reader/book', this.previousBookInSeries.id]);
    }
  }

  navigateToNextBook(): void {
    if (this.nextBookInSeries) {
      this.endReadingSession();
      this.router.navigate(['/cbx-reader/book', this.nextBookInSeries.id]);
    }
  }

  private loadSeriesNavigationAsync(book: Book): void {
    this.bookService.bookState$.pipe(
      filter((state: BookState) => state.loaded),
      first(),
      timeout(10000)
    ).subscribe({
      next: () => {
        this.loadSeriesNavigation(book);
      },
      error: (err) => {
        console.warn('[SeriesNav] BookService state loading timed out or failed, series navigation will be disabled:', err);
      }
    });
  }

  private loadSeriesNavigation(book: Book): void {
    this.bookService.getBooksInSeries(book.id).subscribe({
      next: (seriesBooks) => {
        const sortedBySeriesNumber = this.sortBooksBySeriesNumber(seriesBooks);
        const currentBookIndex = sortedBySeriesNumber.findIndex(b => b.id === book.id);

        if (currentBookIndex === -1) {
          console.warn('[SeriesNav] Current book not found in series');
          return;
        }

        const hasPreviousBook = currentBookIndex > 0;
        const hasNextBook = currentBookIndex < sortedBySeriesNumber.length - 1;

        this.previousBookInSeries = hasPreviousBook ? sortedBySeriesNumber[currentBookIndex - 1] : null;
        this.nextBookInSeries = hasNextBook ? sortedBySeriesNumber[currentBookIndex + 1] : null;

        this.footerService.setSeriesBooks(this.previousBookInSeries, this.nextBookInSeries);
        this.footerService.setHasSeries(true);
      },
      error: (err) => {
        console.error('[SeriesNav] Failed to load series information:', err);
      }
    });
  }

  private sortBooksBySeriesNumber(books: Book[]): Book[] {
    return books.sort((bookA, bookB) => {
      const seriesNumberA = bookA.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      const seriesNumberB = bookB.metadata?.seriesNumber ?? Number.MAX_SAFE_INTEGER;
      return seriesNumberA - seriesNumberB;
    });
  }

  getBookDisplayTitle(book: Book | null): string {
    if (!book) return '';
    const parts: string[] = [];
    if (book.metadata?.seriesNumber) {
      parts.push(`#${book.metadata.seriesNumber}`);
    }
    const title = book.metadata?.title || book.fileName;
    if (title) {
      parts.push(title);
    }
    if (book.metadata?.subtitle) {
      parts.push(book.metadata.subtitle);
    }
    return parts.join(' - ');
  }

  // Fullscreen methods
  toggleFullscreen(): void {
    if (this.isFullscreen) {
      this.exitFullscreen();
    } else {
      this.enterFullscreen();
    }
  }

  private enterFullscreen(): void {
    const elem = document.documentElement;
    if (elem.requestFullscreen) {
      elem.requestFullscreen().catch(() => {});
    }
  }

  private exitFullscreen(): void {
    if (document.exitFullscreen) {
      document.exitFullscreen().catch(() => {});
    }
  }

  // Reading direction methods
  toggleReadingDirection(): void {
    const newDirection = this.readingDirection === CbxReadingDirection.LTR
      ? CbxReadingDirection.RTL
      : CbxReadingDirection.LTR;
    this.onReadingDirectionChange(newDirection);
  }

  onReadingDirectionChange(direction: CbxReadingDirection): void {
    this.readingDirection = direction;
    this.quickSettingsService.setReadingDirection(direction);
  }

  // Slideshow methods
  toggleSlideshow(): void {
    if (this.isSlideshowActive) {
      this.stopSlideshow();
    } else {
      this.startSlideshow();
    }
  }

  startSlideshow(): void {
    if (this.currentPage >= this.pages.length - 1) return;

    this.isSlideshowActive = true;
    this.headerService.updateState({isFullscreen: this.isFullscreen, isSlideshowActive: true});

    this.slideshowTimer = setInterval(() => {
      if (this.currentPage < this.pages.length - 1) {
        this.advancePage(1);
      } else {
        this.stopSlideshow();
      }
    }, this.slideshowInterval);
  }

  stopSlideshow(): void {
    if (this.slideshowTimer) {
      clearInterval(this.slideshowTimer);
      this.slideshowTimer = null;
    }
    this.isSlideshowActive = false;
    this.headerService.updateState({isFullscreen: this.isFullscreen, isSlideshowActive: false});
  }

  private pauseSlideshowOnInteraction(): void {
    if (this.isSlideshowActive) {
      this.stopSlideshow();
    }
  }

  onSlideshowIntervalChange(interval: CbxSlideshowInterval): void {
    this.slideshowInterval = interval;
    this.quickSettingsService.setSlideshowInterval(interval);

    // Restart slideshow with new interval if active
    if (this.isSlideshowActive) {
      this.stopSlideshow();
      this.startSlideshow();
    }
  }

  // Double-tap zoom
  onImageDoubleClick(): void {
    if (this.originalFitMode === null) {
      // Store current fit mode and switch to actual size
      this.originalFitMode = this.fitMode;
      this.onFitModeChange(CbxFitMode.ACTUAL_SIZE);
    } else {
      // Restore original fit mode
      this.onFitModeChange(this.originalFitMode as CbxFitMode);
      this.originalFitMode = null;
    }
  }

  // Double page detection
  onPageImageLoad(event: Event, pageIndex: number): void {
    const img = event.target as HTMLImageElement;
    if (img.naturalWidth && img.naturalHeight) {
      this.pageDimensionsCache.set(pageIndex, {
        width: img.naturalWidth,
        height: img.naturalHeight
      });
    }
    this.imagesLoaded = true;
  }

  isSpreadPage(pageIndex: number): boolean {
    const dims = this.pageDimensionsCache.get(pageIndex);
    if (!dims) return false;
    return dims.width > dims.height * 1.5;
  }

  shouldShowSinglePage(pageIndex: number): boolean {
    return this.isTwoPageView && this.isSpreadPage(pageIndex);
  }

  // Shortcuts help dialog
  onShortcutsHelpClose(): void {
    this.showShortcutsHelp = false;
  }

  // Long strip mode check
  get isLongStripMode(): boolean {
    return this.scrollMode === CbxScrollMode.LONG_STRIP;
  }

  ngOnDestroy(): void {
    this.stopSlideshow();
    this.endReadingSession();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private endReadingSession(): void {
    if (this.readingSessionService.isSessionActive()) {
      const percentage = this.pages.length > 0 ? Math.round(((this.currentPage + 1) / this.pages.length) * 1000) / 10 : 0;
      this.readingSessionService.endSession((this.currentPage + 1).toString(), percentage);
    }
  }

  private updateBookmarkState(): void {
    this.isCurrentPageBookmarked = this.sidebarService.isPageBookmarked(this.currentPage + 1);
  }

  toggleBookmark(): void {
    this.sidebarService.toggleBookmark(this.currentPage + 1);
  }

  private updateNotesState(): void {
    this.currentPageHasNotes = this.sidebarService.pageHasNotes(this.currentPage + 1);
  }

  openNoteDialog(): void {
    this.editingNote = null;
    this.noteDialogData = {
      pageNumber: this.currentPage + 1
    };
    this.showNoteDialog = true;
  }

  private openNoteDialogForEdit(note: BookNoteV2): void {
    this.editingNote = note;
    this.noteDialogData = {
      pageNumber: parseInt(note.cfi, 10) || this.currentPage + 1,
      noteId: note.id,
      noteContent: note.noteContent,
      color: note.color
    };
    this.showNoteDialog = true;
  }

  onNoteSave(result: CbxNoteDialogResult): void {
    if (this.editingNote) {
      this.sidebarService.updateNote(this.editingNote.id, result.noteContent, result.color);
    } else if (this.noteDialogData) {
      this.sidebarService.createNote(this.noteDialogData.pageNumber, result.noteContent, result.color);
    }
    this.showNoteDialog = false;
    this.noteDialogData = null;
    this.editingNote = null;
  }

  onNoteCancel(): void {
    this.showNoteDialog = false;
    this.noteDialogData = null;
    this.editingNote = null;
  }
}
