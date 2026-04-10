import { Component, effect, ElementRef, HostListener, inject, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { forkJoin, from, Subject } from 'rxjs';
import { debounceTime, map, switchMap, takeUntil, tap } from 'rxjs/operators';
import { PageTitleService } from "../../../shared/service/page-title.service";
import { CbxReaderService } from '../../book/service/cbx-reader.service';
import { BookService } from '../../book/service/book.service';
import { CbxBackgroundColor, CbxFitMode, CbxMagnifierLensSize, CbxMagnifierZoom, CbxPageSpread, CbxPageSplitOption, CbxPageViewMode, CbxScrollMode, CbxReadingDirection, CbxSlideshowInterval, UserService } from '../../settings/user-management/user.service';
import { CbxPageDimensionService, DoublePairs } from './core/cbx-page-dimension.service';
import { CbxPageDimension } from './models/cbx-page-dimension.model';
import { MessageService } from 'primeng/api';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { Book, BookSetting, BookType } from '../../book/model/book.model';
import { ProgressSpinner } from 'primeng/progressspinner';
import { FormsModule } from "@angular/forms";
import { ReadingSessionService } from '../../../shared/service/reading-session.service';
import { WakeLockService } from '../../../shared/service/wake-lock.service';
import { ReaderHeaderFooterVisibilityManager } from '../ebook-reader';

import { CbxHeaderComponent } from './layout/header/cbx-header.component';
import { CbxHeaderService } from './layout/header/cbx-header.service';
import { CbxSidebarComponent } from './layout/sidebar/cbx-sidebar.component';
import { CbxSidebarService } from './layout/sidebar/cbx-sidebar.service';
import { CbxFooterComponent } from './layout/footer/cbx-footer.component';
import { CbxFooterService } from './layout/footer/cbx-footer.service';
import { CbxQuickSettingsComponent } from './layout/quick-settings/cbx-quick-settings.component';
import { CbxQuickSettingsService } from './layout/quick-settings/cbx-quick-settings.service';
import { CbxNoteDialogComponent, CbxNoteDialogData, CbxNoteDialogResult } from './dialogs/cbx-note-dialog.component';
import { CbxShortcutsHelpComponent } from './dialogs/cbx-shortcuts-help.component';
import { CanvasRendererComponent } from './renderers/canvas-renderer.component';
import { BookNoteV2 } from '../../../shared/service/book-note-v2.service';
import { ReaderPreferencesService } from '../../settings/reader-preferences/reader-preferences.service';
import {
  clampStripMaxWidthPercent,
  dismissWebtoonHintForSession,
  dismissWebtoonHintForever,
  readStripWidthPercent,
  shouldOfferWebtoonHint,
  writeStripWidthPercentPerBook
} from './core/cbx-reader-storage';


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
    CbxShortcutsHelpComponent,
    CanvasRendererComponent
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
  private static readonly PRELOAD_PAGE_WINDOW = 5;
  private static readonly INFINITE_SCROLL_PAGE_DEBOUNCE_MS = 80;
  private static readonly STRIP_WIDTH_PERSIST_DEBOUNCE_MS = 400;
  private static readonly READER_LAYOUT_GRACE_MS = 1000;
  private static readonly CONTINUATION_HINT_SHOW_PX = 220;
  private static readonly CONTINUATION_HINT_HIDE_PX = 380;
  private static readonly LONG_STRIP_BUFFER = 7;
  private static readonly AUTO_CLOSE_MENU_TIMEOUT = 3000;

  private destroy$ = new Subject<void>();
  private progressSaveSubject$ = new Subject<void>();

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
  preloadCount: number = CbxReaderComponent.PRELOAD_PAGE_WINDOW;
  /** True while a chunk of pages is being prepended/appended (does not block scroll UX). */
  isLoadingMore: boolean = false;
  private infiniteScrollPageDebounceTimer: ReturnType<typeof setTimeout> | null = null;

  // Long-strip state (Kavita-inspired webtoon reader)
  longStripImages: { src: string; page: number }[] = [];
  private longStripLoadedPages = new Set<number>();
  private longStripIntersectionObserver: IntersectionObserver | null = null;
  private longStripScrollHandler: (() => void) | null = null;
  private longStripScrollEndHandler: (() => void) | null = null;
  private longStripScrollDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private longStripScrollEndDebounceTimer: ReturnType<typeof setTimeout> | null = null;
  private longStripIsScrolling = false;
  private longStripAllImagesLoaded = false;
  private longStripInitFinished = false;
  private longStripPrevScrollTop = 0;

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

  // Magnifier
  isMagnifierActive = false;
  @ViewChild('magnifierLens', { static: true }) private magnifierLensRef!: ElementRef<HTMLDivElement>;
  @ViewChild('readerRoot', { read: ElementRef }) private readerRootRef?: ElementRef<HTMLElement>;
  @ViewChild('imageScrollHost', { read: ElementRef }) private imageScrollHostRef?: ElementRef<HTMLElement>;
  magnifierZoom: CbxMagnifierZoom = CbxMagnifierZoom.ZOOM_3X;
  magnifierLensSize: CbxMagnifierLensSize = CbxMagnifierLensSize.MEDIUM;
  private lastMouseEvent: MouseEvent | null = null;

  // Page split option for wide pages
  pageSplitOption: CbxPageSplitOption = CbxPageSplitOption.NO_SPLIT;

  // Brightness filter (0-100, default 100)
  brightness = 100;

  /** 50–100: max width of strip column (infinite / long strip). */
  stripMaxWidthPercent = 100;

  /** Kavita-style hint when archive looks like vertical webtoon strips. */
  showWebtoonSuggestion = false;

  /** Near bottom of strip + next in series: show subtle continuation hint. */
  continuationHintVisible = false;

  private cbxSettingsUsesGlobal = true;
  private readerLayoutGraceUntilMs = 0;
  /** Invalidates delayed scroll/layout work after book or scroll-mode changes. */
  private readerLayoutGeneration = 0;
  /** Avoid continuation hint flicker when scroll height changes (hysteresis). */
  private continuationHintLatched = false;

  // Emulate book mode (spine shadow in two-page view)
  emulateBook = false;

  // Click-to-paginate overlay
  clickToPaginate = false;

  // Auto-close menu after interaction
  autoCloseMenu = false;
  private autoCloseMenuTimer: ReturnType<typeof setTimeout> | null = null;

  // Page dimensions from backend
  pageDimensions: CbxPageDimension[] = [];
  doublePairs: DoublePairs = {};

  // Double page detection (fallback cache for pages without backend dims)
  private pageDimensionsCache = new Map<number, { width: number, height: number }>();

  // Swipe double-action prevention
  private hasHitRightScroll = false;
  private hasHitZeroScroll = false;
  private touchMoveCount = 0;

  // Canvas split state
  canvasSplitState: 'NO_SPLIT' | 'LEFT_PART' | 'RIGHT_PART' = 'NO_SPLIT';

  protected readonly CbxReadingDirection = CbxReadingDirection;
  protected readonly CbxSlideshowInterval = CbxSlideshowInterval;
  protected readonly CbxPageSplitOption = CbxPageSplitOption;

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
  /** Template reads this signal so strip width tracks the slider (same source as quick settings panel). */
  protected readonly cbxQuickSettingsState = this.quickSettingsService.state;
  private pageDimensionService = inject(CbxPageDimensionService);
  private wakeLockService = inject(WakeLockService);
  private readerPreferencesService = inject(ReaderPreferencesService);

  protected readonly CbxScrollMode = CbxScrollMode;
  protected readonly CbxFitMode = CbxFitMode;
  protected readonly CbxBackgroundColor = CbxBackgroundColor;
  protected readonly CbxPageViewMode = CbxPageViewMode;
  protected readonly CbxPageSpread = CbxPageSpread;

  private static readonly TYPE_CBX = 'CBX';
  private static readonly SETTING_GLOBAL = 'Global';

  private bumpReaderLayoutGeneration(): void {
    this.readerLayoutGeneration++;
    this.continuationHintLatched = false;
  }

  private getImageScrollContainer(): HTMLElement | null {
    return this.imageScrollHostRef?.nativeElement ?? null;
  }

  /** Run after the browser has applied layout (two frames — common Angular/DOM pattern). */
  private afterNextPaint(fn: () => void): void {
    requestAnimationFrame(() => requestAnimationFrame(fn));
  }

  constructor() {
    effect(() => {
      this.sidebarService.bookmarks();
      this.updateBookmarkState();
    });

    effect(() => {
      this.sidebarService.notes();
      this.updateNotesState();
    });
  }

  ngOnInit() {
    this.visibilityManager = new ReaderHeaderFooterVisibilityManager(window.innerHeight);
    this.visibilityManager.onStateChange((state) => {
      this.headerService.setForceVisible(state.headerVisible);
      this.footerService.setForceVisible(state.footerVisible);
    });

    this.progressSaveSubject$.pipe(
      debounceTime(2000),
      takeUntil(this.destroy$)
    ).subscribe(() => this.updateProgress());

    this.subscribeToHeaderEvents();
    this.subscribeToSidebarEvents();
    this.subscribeToFooterEvents();
    this.subscribeToQuickSettingsEvents();

    // Enable wake lock after a short delay to not block initial render
    setTimeout(() => this.wakeLockService.enable(), 1000);

    this.route.paramMap.pipe(
      takeUntil(this.destroy$),
      switchMap((params) => {
        this.isLoading = true;
        this.bookId = +params.get('bookId')!;
        this.altBookType = this.route.snapshot.queryParamMap.get('bookType') ?? undefined;

        this.showWebtoonSuggestion = false;
        this.continuationHintVisible = false;
        if (this.infiniteScrollPageDebounceTimer) {
          clearTimeout(this.infiniteScrollPageDebounceTimer);
          this.infiniteScrollPageDebounceTimer = null;
        }
        this.bumpReaderLayoutGeneration();

        this.previousBookInSeries = null;
        this.nextBookInSeries = null;
        this.currentBook = null;

        return from(this.bookService.fetchFreshBookDetail(this.bookId, false)).pipe(
          switchMap((book) => {
            // Use alternative bookType from query param if provided, otherwise use primary
            const resolvedBookType = (this.altBookType as BookType | undefined) ?? book.primaryFile?.bookType;
            if (!resolvedBookType) {
              throw new Error(this.t.translate('shared.reader.failedToLoadBook'));
            }
            this.bookType = resolvedBookType;
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
        );
      })
    ).subscribe({
      next: ({ book, bookSettings, myself }) => {
        const userSettings = myself.userSettings;

        this.pageTitle.setBookPageTitle(book);

        const title = book.metadata?.title || book.fileName;
        this.headerService.initialize(title);
        this.sidebarService.initialize(this.bookId, book, this.destroy$, this.altBookType);

        if (book.metadata?.seriesName) {
          this.loadSeriesNavigation(book);
        }

        forkJoin([
          this.cbxReaderService.getAvailablePages(this.bookId, this.altBookType),
          this.pageDimensionService.getPageDimensions(this.bookId, this.altBookType)
        ]).subscribe({
          next: ([pages, dimensions]) => {
            this.pages = pages;
            this.pageDimensions = dimensions;
            this.doublePairs = this.pageDimensionService.computeDoublePairs(dimensions);

            if (this.bookType === CbxReaderComponent.TYPE_CBX) {
              const global = userSettings.perBookSetting.cbx === CbxReaderComponent.SETTING_GLOBAL;
              this.cbxSettingsUsesGlobal = global;
              this.stripMaxWidthPercent = readStripWidthPercent(
                this.bookId,
                global,
                userSettings.cbxReaderSetting.stripMaxWidthPercent,
                myself.id
              );
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

              // Restore new settings from per-book or global
              const cbxSrc = global ? userSettings.cbxReaderSetting : bookSettings.cbxSettings;
              if (cbxSrc) {
                this.pageSplitOption = cbxSrc.pageSplitOption ?? this.pageSplitOption;
                this.brightness = cbxSrc.brightness ?? 100;
                this.emulateBook = cbxSrc.emulateBook ?? false;
                this.clickToPaginate = cbxSrc.clickToPaginate ?? false;
                this.autoCloseMenu = cbxSrc.autoCloseMenu ?? false;
              }

              this.currentPage = (book.cbxProgress?.page || 1) - 1;

              if (this.scrollMode === CbxScrollMode.INFINITE) {
                this.initializeInfiniteScroll();
              } else if (this.scrollMode === CbxScrollMode.LONG_STRIP) {
                this.initializeLongStrip();
              }
            }

            this.alignCurrentPageToParity();

            this.readerLayoutGraceUntilMs = Date.now() + CbxReaderComponent.READER_LAYOUT_GRACE_MS;
            if (this.bookType === CbxReaderComponent.TYPE_CBX) {
              const webtoon = this.pageDimensionService.detectWebtoon(dimensions);
              this.showWebtoonSuggestion =
                webtoon.isWebtoon &&
                this.scrollMode !== CbxScrollMode.LONG_STRIP &&
                shouldOfferWebtoonHint(this.bookId);
            } else {
              this.showWebtoonSuggestion = false;
            }

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
            this.messageService.add({ severity: 'error', summary: this.t.translate('common.error'), detail: errorMessage });
            this.isLoading = false;
          }
        });
      },
      error: (err) => {
        const errorMessage = err?.error?.message || this.t.translate('shared.reader.failedToLoadBook');
        this.messageService.add({ severity: 'error', summary: this.t.translate('common.error'), detail: errorMessage });
        this.isLoading = false;
      }
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

    this.headerService.toggleMagnifier$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.isMagnifierActive = !this.isMagnifierActive;
        if (!this.isMagnifierActive) {
          this.hideMagnifier();
        }
        this.headerService.updateState({ isMagnifierActive: this.isMagnifierActive });
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

    this.quickSettingsService.magnifierZoomChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(zoom => this.onMagnifierZoomChange(zoom));

    this.quickSettingsService.magnifierLensSizeChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(size => this.onMagnifierLensSizeChange(size));

    this.quickSettingsService.brightnessChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.brightness = value;
        this.quickSettingsService.setBrightness(value);
        this.updateViewerSetting();
      });

    this.quickSettingsService.emulateBookChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.emulateBook = value;
        this.quickSettingsService.setEmulateBook(value);
        this.updateViewerSetting();
      });

    this.quickSettingsService.clickToPaginateChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.clickToPaginate = value;
        this.quickSettingsService.setClickToPaginate(value);
        this.updateViewerSetting();
      });

    this.quickSettingsService.autoCloseMenuChange$
      .pipe(takeUntil(this.destroy$))
      .subscribe(value => {
        this.autoCloseMenu = value;
        this.quickSettingsService.setAutoCloseMenu(value);
        this.updateViewerSetting();
      });

    this.quickSettingsService.stripMaxWidthChange$
      .pipe(
        tap((value) => {
          this.stripMaxWidthPercent = value;
        }),
        debounceTime(CbxReaderComponent.STRIP_WIDTH_PERSIST_DEBOUNCE_MS),
        takeUntil(this.destroy$)
      )
      .subscribe((value) => this.persistStripMaxWidth(value));
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
      slideshowInterval: this.slideshowInterval,
      magnifierZoom: this.magnifierZoom,
      magnifierLensSize: this.magnifierLensSize,
      brightness: this.brightness,
      emulateBook: this.emulateBook,
      clickToPaginate: this.clickToPaginate,
      autoCloseMenu: this.autoCloseMenu,
      stripMaxWidthPercent: this.stripMaxWidthPercent
    });

    this.headerService.updateState({
      isFullscreen: this.isFullscreen,
      isSlideshowActive: this.isSlideshowActive
    });

    this.sidebarService.setCurrentPage(this.currentPage + 1);
  }

  private persistStripMaxWidth(value: number): void {
    const v = clampStripMaxWidthPercent(value);
    const u = this.userService.currentUser();
    if (this.cbxSettingsUsesGlobal) {
      if (u) {
        this.readerPreferencesService.updatePreference(
          ['cbxReaderSetting', 'stripMaxWidthPercent'],
          v,
          { silent: true }
        );
      }
    } else {
      writeStripWidthPercentPerBook(this.bookId, v, u?.id);
    }
  }

  private updateContinuationHint(container: HTMLElement): void {
    if (!this.nextBookInSeries) {
      this.continuationHintLatched = false;
      this.continuationHintVisible = false;
      return;
    }
    if (this.scrollMode !== CbxScrollMode.INFINITE && this.scrollMode !== CbxScrollMode.LONG_STRIP) {
      this.continuationHintLatched = false;
      this.continuationHintVisible = false;
      return;
    }
    if (Date.now() < this.readerLayoutGraceUntilMs) {
      this.continuationHintVisible = false;
      return;
    }
    const edge = container.scrollTop + container.clientHeight;
    const bottom = container.scrollHeight;
    const showPx = CbxReaderComponent.CONTINUATION_HINT_SHOW_PX;
    const hidePx = CbxReaderComponent.CONTINUATION_HINT_HIDE_PX;

    if (this.continuationHintLatched) {
      if (edge < bottom - hidePx) {
        this.continuationHintLatched = false;
        this.continuationHintVisible = false;
      } else {
        this.continuationHintVisible = true;
      }
    } else if (edge >= bottom - showPx) {
      this.continuationHintLatched = true;
      this.continuationHintVisible = true;
    } else {
      this.continuationHintVisible = false;
    }
  }

  onWebtoonSuggestSwitchToLongStrip(): void {
    this.showWebtoonSuggestion = false;
    this.onScrollModeChange(CbxScrollMode.LONG_STRIP);
  }

  onWebtoonSuggestDismiss(): void {
    this.showWebtoonSuggestion = false;
    dismissWebtoonHintForSession(this.bookId);
  }

  onWebtoonSuggestNever(): void {
    this.showWebtoonSuggestion = false;
    dismissWebtoonHintForever();
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

    if (this.isTwoPageView) {
      // Use doublePairs for dimension-aware pairing
      if (Object.keys(this.doublePairs).length > 0) {
        const pairedWith = this.doublePairs[this.currentPage];
        if (pairedWith !== undefined && pairedWith < this.pages.length) {
          urls.push(this.getPageImageUrl(pairedWith));
        }
      } else if (this.currentPage + 1 < this.pages.length) {
        urls.push(this.getPageImageUrl(this.currentPage + 1));
      }
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

  get isFooterVisible(): boolean {
    return this.footerService.forceVisible();
  }

  get showQuickSettings(): boolean {
    return this.quickSettingsService.visible();
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

    if (this.scrollMode === CbxScrollMode.LONG_STRIP) {
      const newPage = this.currentPage + direction;
      if (newPage >= 0 && newPage < this.pages.length) {
        this.currentPage = newPage;
        this.longStripScrollToPage(newPage);
        this.updateProgress();
        this.updateSessionProgress();
        this.updateFooterPage();
      }
      return;
    }

    if (this.scrollMode === CbxScrollMode.INFINITE) {
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
        // Use doublePairs for dimension-aware stepping
        if (Object.keys(this.doublePairs).length > 0) {
          const pairedWith = this.doublePairs[this.currentPage];
          if (pairedWith !== undefined) {
            // Current page is paired — skip past its partner
            const nextPage = Math.max(this.currentPage, pairedWith) + 1;
            if (nextPage < this.pages.length) {
              this.currentPage = nextPage;
            }
          } else {
            // Current page is solo (wide or cover) — advance by 1
            if (this.currentPage + 1 < this.pages.length) {
              this.currentPage += 1;
            }
          }
        } else {
          // Fallback: use old heuristic
          const effectiveStep = this.shouldShowSinglePage(this.currentPage) ? 1 : step;
          if (this.currentPage + effectiveStep < this.pages.length) {
            this.currentPage += effectiveStep;
          } else if (this.currentPage + 1 < this.pages.length) {
            this.currentPage += 1;
          }
        }
      } else if (this.currentPage < this.pages.length - 1) {
        // Single-page mode: handle canvas split state for wide pages
        if (this.pageSplitOption !== CbxPageSplitOption.NO_SPLIT && this.isSpreadPage(this.currentPage)) {
          if (this.canvasSplitState === 'NO_SPLIT' || this.canvasSplitState === 'LEFT_PART') {
            // Show first half, then second half before advancing
            this.canvasSplitState = this.canvasSplitState === 'NO_SPLIT' ? 'LEFT_PART' : 'RIGHT_PART';
            this.updateCurrentImageUrls();
            return;
          }
          // Already showed RIGHT_PART — advance to next page
          this.canvasSplitState = 'NO_SPLIT';
        }
        this.currentPage++;
      }
    } else {
      // Backward navigation
      if (this.isTwoPageView) {
        if (Object.keys(this.doublePairs).length > 0) {
          // Find the page that starts the previous spread
          const targetPage = this.currentPage - 1;
          if (targetPage >= 0) {
            const pairedWith = this.doublePairs[targetPage];
            if (pairedWith !== undefined) {
              // Land on the lower-indexed page of the pair
              this.currentPage = Math.min(targetPage, pairedWith);
            } else {
              // Previous page is solo — just go there
              this.currentPage = targetPage;
            }
          }
        } else {
          this.currentPage = Math.max(0, this.currentPage - step);
        }
      } else {
        // Single-page backward: handle canvas split for wide pages
        if (this.pageSplitOption !== CbxPageSplitOption.NO_SPLIT && this.isSpreadPage(this.currentPage)) {
          if (this.canvasSplitState === 'RIGHT_PART') {
            this.canvasSplitState = 'LEFT_PART';
            this.updateCurrentImageUrls();
            return;
          }
          this.canvasSplitState = 'NO_SPLIT';
        }
        if (this.currentPage > 0) {
          this.currentPage--;
          // If landing on a wide page, start at right half
          if (this.pageSplitOption !== CbxPageSplitOption.NO_SPLIT && this.isSpreadPage(this.currentPage)) {
            this.canvasSplitState = 'RIGHT_PART';
          }
        }
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
    this.bumpReaderLayoutGeneration();
    const previousMode = this.scrollMode;
    this.scrollMode = mode;
    this.quickSettingsService.setScrollMode(mode);
    if (mode === CbxScrollMode.LONG_STRIP) {
      this.showWebtoonSuggestion = false;
    }
    this.updateViewerSetting();

    // Teardown previous mode
    if (previousMode === CbxScrollMode.LONG_STRIP) {
      this.teardownLongStrip();
    }
    if (previousMode === CbxScrollMode.INFINITE) {
      if (this.infiniteScrollPageDebounceTimer) {
        clearTimeout(this.infiniteScrollPageDebounceTimer);
        this.infiniteScrollPageDebounceTimer = null;
      }
      this.infiniteScrollPages = [];
      this.isLoadingMore = false;
    }

    if (this.scrollMode === CbxScrollMode.INFINITE) {
      this.initializeInfiniteScroll();
      this.scrollToPage(this.currentPage, 'auto');
    } else if (this.scrollMode === CbxScrollMode.LONG_STRIP) {
      this.initializeLongStrip();
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
    const startIndex = Math.max(0, this.currentPage - 2);
    const endIndex = Math.min(this.currentPage + this.preloadCount + 1, this.pages.length);
    for (let i = startIndex; i < endIndex; i++) {
      this.infiniteScrollPages.push(i);
    }
  }

  onScroll(event: Event): void {
    if (this.scrollMode !== CbxScrollMode.INFINITE) return;

    const container = event.target as HTMLElement;
    const scrollPosition = container.scrollTop + container.clientHeight;
    const scrollHeight = container.scrollHeight;

    if (!this.isLoadingMore) {
      if (scrollPosition >= scrollHeight * 0.82) {
        this.loadMorePages();
      }
      if (container.scrollTop <= container.clientHeight * 0.18 && this.infiniteScrollPages.length > 0) {
        this.loadPreviousPages(container);
      }
    }

    if (this.infiniteScrollPageDebounceTimer) {
      clearTimeout(this.infiniteScrollPageDebounceTimer);
    }
    const layoutGen = this.readerLayoutGeneration;
    this.infiniteScrollPageDebounceTimer = setTimeout(() => {
      this.infiniteScrollPageDebounceTimer = null;
      if (layoutGen !== this.readerLayoutGeneration || this.scrollMode !== CbxScrollMode.INFINITE) {
        return;
      }
      this.updateCurrentPageFromScroll(container);
    }, CbxReaderComponent.INFINITE_SCROLL_PAGE_DEBOUNCE_MS);

    this.updateContinuationHint(container);
  }

  private loadMorePages(): void {
    if (this.isLoadingMore) return;

    const lastLoadedIndex = this.infiniteScrollPages[this.infiniteScrollPages.length - 1];
    if (lastLoadedIndex === undefined || lastLoadedIndex >= this.pages.length - 1) return;

    this.isLoadingMore = true;
    const endIndex = Math.min(lastLoadedIndex + this.preloadCount + 1, this.pages.length);

    requestAnimationFrame(() => {
      for (let i = lastLoadedIndex + 1; i < endIndex; i++) {
        this.infiniteScrollPages.push(i);
      }
      this.isLoadingMore = false;
    });
  }

  /**
   * Prefetch upward when the user scrolls near the top.
   * Preserves scroll position relative to the first previously visible page.
   */
  private loadPreviousPages(container: HTMLElement): void {
    if (this.isLoadingMore) return;

    const first = this.infiniteScrollPages[0];
    if (first === undefined || first <= 0) return;

    const anchorEl = container.querySelector(
      `.infinite-scroll-wrapper img.page-image[data-page="${first}"]`
    ) as HTMLElement | null;
    if (!anchorEl) return;

    const beforeTop = anchorEl.getBoundingClientRect().top;

    this.isLoadingMore = true;
    const newFirst = Math.max(0, first - this.preloadCount);
    const added: number[] = [];
    for (let p = newFirst; p < first; p++) {
      added.push(p);
    }
    this.infiniteScrollPages = [...added, ...this.infiniteScrollPages];

    this.afterNextPaint(() => {
      const afterTop = anchorEl.getBoundingClientRect().top;
      container.scrollTop += afterTop - beforeTop;
      this.isLoadingMore = false;
    });
  }

  private updateCurrentPageFromScroll(container: HTMLElement): void {
    const images = Array.from(
      container.querySelectorAll('.infinite-scroll-wrapper img.page-image[data-page]')
    ) as HTMLElement[];
    if (!images.length) return;

    const containerRect = container.getBoundingClientRect();
    const midY = containerRect.top + containerRect.height / 2;

    let best: HTMLElement | null = null;
    let bestDist = Number.MAX_VALUE;
    for (const img of images) {
      const rect = img.getBoundingClientRect();
      if (rect.bottom < containerRect.top || rect.top > containerRect.bottom) {
        continue;
      }
      const imgCenterY = rect.top + rect.height / 2;
      const dist = Math.abs(imgCenterY - midY);
      if (dist < bestDist) {
        bestDist = dist;
        best = img;
      }
    }

    if (!best) return;

    const attr = best.getAttribute('data-page');
    const newPage = attr != null ? parseInt(attr, 10) : NaN;
    if (Number.isNaN(newPage) || newPage === this.currentPage) return;

    this.currentPage = newPage;
    this.progressSaveSubject$.next();
    this.updateSessionProgress();
    this.updateFooterPage();
  }

  getPageImageUrl(pageIndex: number): string {
    return this.cbxReaderService.getPageImageUrl(this.bookId, this.pages[pageIndex], this.altBookType);
  }

  // ── Long-strip mode (Kavita-inspired) ──

  private initializeLongStrip(): void {
    this.longStripImages = [];
    this.longStripLoadedPages.clear();
    this.longStripAllImagesLoaded = false;
    this.longStripInitFinished = false;
    this.longStripIsScrolling = false;
    this.longStripPrevScrollTop = 0;

    // Prefetch pages around current position
    this.longStripPrefetchAround(this.currentPage);

    // Set up IntersectionObserver for page visibility tracking
    this.longStripIntersectionObserver = new IntersectionObserver(
      (entries) => this.longStripHandleIntersection(entries),
      { threshold: 0.01 }
    );

    const layoutGen = this.readerLayoutGeneration;
    setTimeout(() => {
      if (layoutGen !== this.readerLayoutGeneration) return;
      this.longStripAttachScrollListeners();
      this.afterNextPaint(() => {
        if (layoutGen !== this.readerLayoutGeneration) return;
        this.longStripWaitForImagesAndScroll(0, layoutGen);
      });
    }, 0);
  }

  private teardownLongStrip(): void {
    if (this.longStripIntersectionObserver) {
      this.longStripIntersectionObserver.disconnect();
      this.longStripIntersectionObserver = null;
    }
    this.longStripDetachScrollListeners();
    this.longStripImages = [];
    this.longStripLoadedPages.clear();
    this.longStripAllImagesLoaded = false;
    this.longStripInitFinished = false;
  }

  private longStripPrefetchAround(pageNum: number): void {
    const buffer = CbxReaderComponent.LONG_STRIP_BUFFER;
    const start = Math.max(0, pageNum - buffer);
    const end = Math.min(this.pages.length - 1, pageNum + buffer);

    for (let i = start; i <= end; i++) {
      if (!this.longStripLoadedPages.has(i)) {
        this.longStripLoadedPages.add(i);
        this.longStripImages.push({ src: this.getPageImageUrl(i), page: i });
      }
    }

    // Keep sorted by page number
    this.longStripImages.sort((a, b) => a.page - b.page);
  }

  onLongStripImageLoad(event: Event, pageIndex: number): void {
    const img = event.target as HTMLImageElement;
    if (!img.naturalWidth || !img.naturalHeight) return;

    // Cache dimensions
    this.pageDimensionsCache.set(pageIndex, {
      width: img.naturalWidth,
      height: img.naturalHeight
    });

    // Attach intersection observer to track visibility
    if (this.longStripIntersectionObserver) {
      this.longStripIntersectionObserver.observe(img);
    }
  }

  /**
   * Positions the strip once the current page's image exists (matches infinite scroll: no smooth jump on mode switch).
   * Only waits for the active page, not the whole prefetch window.
   * Retries until the img node exists single finish() guarantees one scroll.
   */
  private longStripWaitForImagesAndScroll(attempt: number, layoutGen: number): void {
    if (layoutGen !== this.readerLayoutGeneration) return;
    if (this.scrollMode !== CbxScrollMode.LONG_STRIP) return;
    if (this.longStripInitFinished) return;

    const container = this.getImageScrollContainer();
    if (!container) {
      if (attempt < 30) {
        requestAnimationFrame(() => this.longStripWaitForImagesAndScroll(attempt + 1, layoutGen));
      }
      return;
    }

    const img = container.querySelector(`img[data-page="${this.currentPage}"]`) as HTMLImageElement | null;
    if (!img) {
      if (attempt < 30) {
        requestAnimationFrame(() => this.longStripWaitForImagesAndScroll(attempt + 1, layoutGen));
      }
      return;
    }

    const finish = (): void => {
      if (layoutGen !== this.readerLayoutGeneration) return;
      if (this.longStripInitFinished) return;
      this.longStripAllImagesLoaded = true;
      this.longStripDoScroll(this.currentPage, 'instant', layoutGen);
      this.longStripInitFinished = true;
    };

    if (img.complete) {
      this.afterNextPaint(finish);
      return;
    }

    const scheduleFinish = (): void => this.afterNextPaint(finish);
    img.addEventListener('load', scheduleFinish, { once: true });
    img.addEventListener('error', scheduleFinish, { once: true });
  }

  /**
   * One-step scroll within a container avoids scrollIntoView + reflow (double teleport) on mode switches.
   */
  private snapScrollContainerToElement(
    container: HTMLElement,
    el: Element,
    block: 'start' | 'center'
  ): void {
    const cRect = container.getBoundingClientRect();
    const eRect = el.getBoundingClientRect();
    const delta =
      block === 'center'
        ? (eRect.top + eRect.height / 2) - (cRect.top + cRect.height / 2)
        : eRect.top - cRect.top;
    container.scrollTop += delta;
  }

  private longStripDoScroll(pageNum: number, behavior: ScrollBehavior = 'smooth', layoutGen?: number): void {
    const gen = layoutGen ?? this.readerLayoutGeneration;
    const container = this.getImageScrollContainer();
    const el = container?.querySelector(`img[data-page="${pageNum}"]`) ?? null;
    if (!container || !el) return;

    this.longStripIsScrolling = true;
    if (behavior === 'instant' || behavior === 'auto') {
      this.snapScrollContainerToElement(container, el, 'center');
    } else {
      el.scrollIntoView({ behavior, block: 'start', inline: 'nearest' });
    }

    const settleMs = behavior === 'instant' || behavior === 'auto' ? 80 : 600;
    setTimeout(() => {
      if (gen !== this.readerLayoutGeneration) return;
      this.longStripIsScrolling = false;
    }, settleMs);
  }

  private longStripScrollToPage(pageNum: number, behavior: ScrollBehavior = 'smooth'): void {
    this.longStripPrefetchAround(pageNum);

    const layoutGen = this.readerLayoutGeneration;
    setTimeout(() => {
      if (layoutGen !== this.readerLayoutGeneration) return;
      this.longStripDoScroll(pageNum, behavior, layoutGen);
    }, 50);
  }

  private longStripHandleIntersection(entries: IntersectionObserverEntry[]): void {
    if (!this.longStripAllImagesLoaded || this.longStripIsScrolling) return;

    for (const entry of entries) {
      if (entry.isIntersecting) {
        const pageAttr = entry.target.getAttribute('data-page');
        if (pageAttr != null) {
          const page = parseInt(pageAttr, 10);
          // Prefetch more images when a page enters the viewport
          this.longStripPrefetchAround(page);
        }
      }
    }
  }

  private longStripAttachScrollListeners(): void {
    const container = this.getImageScrollContainer();
    if (!container) return;

    this.longStripScrollHandler = () => {
      if (this.longStripScrollDebounceTimer) clearTimeout(this.longStripScrollDebounceTimer);
      this.longStripScrollDebounceTimer = setTimeout(() => {
        this.longStripOnScroll(container);
      }, 20);
    };

    const supportsScrollEnd = 'onscrollend' in document;
    this.longStripScrollEndHandler = () => {
      if (this.longStripScrollEndDebounceTimer) clearTimeout(this.longStripScrollEndDebounceTimer);
      this.longStripScrollEndDebounceTimer = setTimeout(() => {
        this.longStripOnScrollEnd(container);
      }, supportsScrollEnd ? 20 : 100);
    };

    container.addEventListener('scroll', this.longStripScrollHandler, { passive: true });
    container.addEventListener(
      supportsScrollEnd ? 'scrollend' : 'scroll',
      this.longStripScrollEndHandler,
      { passive: true }
    );
  }

  private longStripDetachScrollListeners(): void {
    const container = this.getImageScrollContainer();
    if (!container) return;

    if (this.longStripScrollHandler) {
      container.removeEventListener('scroll', this.longStripScrollHandler);
      this.longStripScrollHandler = null;
    }
    if (this.longStripScrollEndHandler) {
      container.removeEventListener('scrollend', this.longStripScrollEndHandler);
      container.removeEventListener('scroll', this.longStripScrollEndHandler);
      this.longStripScrollEndHandler = null;
    }
    if (this.longStripScrollDebounceTimer) {
      clearTimeout(this.longStripScrollDebounceTimer);
      this.longStripScrollDebounceTimer = null;
    }
    if (this.longStripScrollEndDebounceTimer) {
      clearTimeout(this.longStripScrollEndDebounceTimer);
      this.longStripScrollEndDebounceTimer = null;
    }
  }

  private longStripOnScroll(container: HTMLElement): void {
    const scrollTop = container.scrollTop;

    // Track direction
    if (scrollTop > this.longStripPrevScrollTop) {
      // scrolling down
    }
    this.longStripPrevScrollTop = scrollTop;

    // If performing a programmatic scroll, check if target is visible
    if (this.longStripIsScrolling) {
      const target = container.querySelector(`img[data-page="${this.currentPage}"]`);
      if (target && this.longStripIsElementVisible(target, container)) {
        this.longStripIsScrolling = false;
      }
    }

    this.updateContinuationHint(container);
  }

  private longStripOnScrollEnd(container: HTMLElement): void {
    if (this.longStripIsScrolling) return;

    const images = Array.from(container.querySelectorAll('.long-strip-wrapper img[data-page]')) as HTMLImageElement[];
    const closest = this.longStripFindClosestImage(images, container);

    if (closest) {
      const page = parseInt(closest.getAttribute('data-page') ?? '0', 10);
      if (page !== this.currentPage) {
        this.currentPage = page;
        this.progressSaveSubject$.next();
        this.updateSessionProgress();
        this.updateFooterPage();
      }
    }

    this.updateContinuationHint(container);
  }

  private longStripFindClosestImage(images: HTMLImageElement[], container: HTMLElement): HTMLImageElement | null {
    let closest: HTMLImageElement | null = null;
    let closestDist = Number.MAX_VALUE;
    const containerRect = container.getBoundingClientRect();
    const midY = containerRect.top + containerRect.height / 2;

    for (const img of images) {
      const rect = img.getBoundingClientRect();
      if (rect.bottom < containerRect.top || rect.top > containerRect.bottom) {
        continue;
      }
      const imgCenterY = rect.top + rect.height / 2;
      const dist = Math.abs(imgCenterY - midY);
      if (dist < closestDist) {
        closestDist = dist;
        closest = img;
      }
    }

    return closest;
  }

  private longStripIsElementVisible(elem: Element, container: HTMLElement): boolean {
    const rect = elem.getBoundingClientRect();
    const containerRect = container.getBoundingClientRect();
    return rect.bottom >= containerRect.top && rect.top <= containerRect.bottom;
  }

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = {
      cbxSettings: {
        pageSpread: this.pageSpread,
        pageViewMode: this.pageViewMode,
        fitMode: this.fitMode,
        scrollMode: this.scrollMode,
        backgroundColor: this.backgroundColor,
        pageSplitOption: this.pageSplitOption,
        brightness: this.brightness,
        emulateBook: this.emulateBook,
        clickToPaginate: this.clickToPaginate,
        autoCloseMenu: this.autoCloseMenu,
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

    if (this.scrollMode === CbxScrollMode.LONG_STRIP) {
      this.longStripPrefetchAround(targetIndex);
      this.longStripScrollToPage(targetIndex);
      this.updateProgress();
      this.updateSessionProgress();
      this.updateFooterPage();
    } else if (this.scrollMode === CbxScrollMode.INFINITE) {
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

  private scrollToPage(pageIndex: number, behavior: ScrollBehavior = 'smooth'): void {
    this.ensurePageLoaded(pageIndex);

    const layoutGen = this.readerLayoutGeneration;
    setTimeout(() => {
      if (layoutGen !== this.readerLayoutGeneration) return;
      const container = this.getImageScrollContainer();
      if (!container) return;

      const targetImage = container.querySelector(
        `.infinite-scroll-wrapper img.page-image[data-page="${pageIndex}"]`
      ) as HTMLElement | null;

      if (targetImage) {
        targetImage.scrollIntoView({ behavior, block: 'start' });
      } else if (behavior === 'auto') {
        container.scrollTop = 0;
      }
    }, 50);
  }

  private ensurePageLoaded(pageIndex: number): void {
    if (this.infiniteScrollPages.includes(pageIndex)) return;

    this.infiniteScrollPages = [];
    // Load a window around the target page
    const startIndex = Math.max(0, pageIndex - 1);
    const endIndex = Math.min(pageIndex + this.preloadCount + 1, this.pages.length);

    for (let i = startIndex; i < endIndex; i++) {
      this.infiniteScrollPages.push(i);
    }
  }

  onImageClick(): void {
    this.visibilityManager.togglePinned();
    this.scheduleAutoCloseMenu();
  }

  private scheduleAutoCloseMenu(): void {
    if (!this.autoCloseMenu) return;
    if (this.autoCloseMenuTimer) {
      clearTimeout(this.autoCloseMenuTimer);
    }
    this.autoCloseMenuTimer = setTimeout(() => {
      this.visibilityManager.unpinIfPinned();
      this.autoCloseMenuTimer = null;
    }, CbxReaderComponent.AUTO_CLOSE_MENU_TIMEOUT);
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
        if (isRtl) {
          this.previousPage();
        } else {
          this.nextPage();
        }
        event.preventDefault();
        break;
      case 'ArrowLeft':
        if (isRtl) {
          this.nextPage();
        } else {
          this.previousPage();
        }
        event.preventDefault();
        break;
      case ' ':
        event.preventDefault();
        if (event.shiftKey) {
          this.previousPage();
        } else {
          this.nextPage();
        }
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
      case 'm':
      case 'M':
        event.preventDefault();
        this.isMagnifierActive = !this.isMagnifierActive;
        if (!this.isMagnifierActive) {
          this.hideMagnifier();
        }
        this.headerService.updateState({ isMagnifierActive: this.isMagnifierActive });
        break;
      case '+':
      case '=':
        if (this.isMagnifierActive) {
          event.preventDefault();
          this.cycleMagnifierZoom(1);
        }
        break;
      case '-':
        if (this.isMagnifierActive) {
          event.preventDefault();
          this.cycleMagnifierZoom(-1);
        }
        break;
      case ']':
        if (this.isMagnifierActive) {
          event.preventDefault();
          this.cycleMagnifierLensSize(1);
        }
        break;
      case '[':
        if (this.isMagnifierActive) {
          event.preventDefault();
          this.cycleMagnifierLensSize(-1);
        }
        break;
      case '?':
        event.preventDefault();
        this.showShortcutsHelp = true;
        break;
      case 'Escape':
        if (this.isMagnifierActive) {
          this.isMagnifierActive = false;
          this.hideMagnifier();
          this.headerService.updateState({ isMagnifierActive: false });
        } else if (this.showShortcutsHelp) {
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
    const target = this.readerRootRef?.nativeElement ?? document.documentElement;
    this.isFullscreen = document.fullscreenElement === target;
    this.headerService.updateState({ isFullscreen: this.isFullscreen, isSlideshowActive: this.isSlideshowActive });
  }

  @HostListener('touchstart', ['$event'])
  onTouchStart(event: TouchEvent) {
    this.touchStartX = event.changedTouches[0].screenX;
    this.touchMoveCount = 0;
  }

  @HostListener('touchmove')
  onTouchMove() {
    this.touchMoveCount++;
  }

  @HostListener('touchend', ['$event'])
  onTouchEnd(event: TouchEvent) {
    this.touchEndX = event.changedTouches[0].screenX;
    // Filter tremor/jitter: ignore if fewer than 3 move events
    if (this.touchMoveCount >= 3) {
      this.handleSwipeGesture();
    }
  }

  @HostListener('window:resize')
  onResize() {
    this.visibilityManager.updateWindowHeight(window.innerHeight);
    this.enforcePortraitSinglePageView();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    this.lastMouseEvent = event;
    this.visibilityManager.handleMouseMove(event.clientY);
    if (this.isMagnifierActive) {
      this.updateMagnifier(event);
    }
  }

  @HostListener('document:mouseleave')
  onMouseLeave(): void {
    this.visibilityManager.handleMouseLeave();
    if (this.isMagnifierActive) {
      this.hideMagnifier();
    }
  }

  private handleSwipeGesture() {
    if (this.scrollMode === CbxScrollMode.INFINITE || this.scrollMode === CbxScrollMode.LONG_STRIP) return;

    const delta = this.touchEndX - this.touchStartX;
    const threshold = Math.min(75, window.innerWidth * 0.1);
    if (Math.abs(delta) < threshold) return;

    const isRtl = this.readingDirection === CbxReadingDirection.RTL;
    const shouldGoNext = isRtl ? delta > 0 : delta < 0;
    const shouldGoPrev = !shouldGoNext;

    // Double-action prevention: at boundaries, require two consecutive swipes
    if (shouldGoNext && this.currentPage >= this.pages.length - 1) {
      if (!this.hasHitRightScroll) {
        this.hasHitRightScroll = true;
        return;
      }
    }
    if (shouldGoPrev && this.currentPage <= 0) {
      if (!this.hasHitZeroScroll) {
        this.hasHitZeroScroll = true;
        return;
      }
    }

    // Reset boundary flags on successful navigation
    this.hasHitRightScroll = false;
    this.hasHitZeroScroll = false;

    if (shouldGoNext) {
      this.nextPage();
    } else {
      this.previousPage();
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

  /** Last loaded long-strip page is the archive's final page (show next-book CTA when in series). */
  get isAtEndOfLongStrip(): boolean {
    if (this.longStripImages.length === 0) return false;
    const last = this.longStripImages[this.longStripImages.length - 1];
    return last.page >= this.pages.length - 1;
  }

  /** Last loaded infinite-scroll index is the archive's final page (show next-book CTA when in series). */
  get isAtEndOfInfiniteScroll(): boolean {
    if (this.infiniteScrollPages.length === 0) return false;
    const lastIdx = this.infiniteScrollPages[this.infiniteScrollPages.length - 1];
    return lastIdx >= this.pages.length - 1;
  }

  navigateToPreviousBook(): void {
    if (this.previousBookInSeries) {
      this.endReadingSession();
      this.router.navigate(['/cbx-reader/book', this.previousBookInSeries.id], { replaceUrl: true });
    }
  }

  navigateToNextBook(): void {
    if (this.nextBookInSeries) {
      this.endReadingSession();
      this.router.navigate(['/cbx-reader/book', this.nextBookInSeries.id], { replaceUrl: true });
    }
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
    const elem = this.readerRootRef?.nativeElement ?? document.documentElement;
    if (elem.requestFullscreen) {
      void elem.requestFullscreen().catch(() => undefined);
    }
  }

  private exitFullscreen(): void {
    if (document.exitFullscreen) {
      void document.exitFullscreen().catch(() => undefined);
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
    this.headerService.updateState({ isFullscreen: this.isFullscreen, isSlideshowActive: true });

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
    this.headerService.updateState({ isFullscreen: this.isFullscreen, isSlideshowActive: false });
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

  onMagnifierZoomChange(zoom: CbxMagnifierZoom): void {
    this.magnifierZoom = zoom;
    this.quickSettingsService.setMagnifierZoom(zoom);
    this.refreshMagnifier();
  }

  onMagnifierLensSizeChange(size: CbxMagnifierLensSize): void {
    this.magnifierLensSize = size;
    this.quickSettingsService.setMagnifierLensSize(size);
    this.refreshMagnifier();
  }

  private refreshMagnifier(): void {
    if (this.isMagnifierActive && this.lastMouseEvent) {
      this.updateMagnifier(this.lastMouseEvent);
    }
  }

  private cycleMagnifierZoom(direction: 1 | -1): void {
    const values = Object.values(CbxMagnifierZoom).filter(v => typeof v === 'number') as number[];
    values.sort((a, b) => a - b);
    const currentIndex = values.indexOf(this.magnifierZoom as number);
    const newIndex = currentIndex + direction;
    if (newIndex >= 0 && newIndex < values.length) {
      this.onMagnifierZoomChange(values[newIndex] as CbxMagnifierZoom);
    }
  }

  private cycleMagnifierLensSize(direction: 1 | -1): void {
    const values = Object.values(CbxMagnifierLensSize).filter(v => typeof v === 'number') as number[];
    values.sort((a, b) => a - b);
    const currentIndex = values.indexOf(this.magnifierLensSize as number);
    const newIndex = currentIndex + direction;
    if (newIndex >= 0 && newIndex < values.length) {
      this.onMagnifierLensSizeChange(values[newIndex] as CbxMagnifierLensSize);
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
    // Prefer backend-provided dimensions
    if (this.pageDimensions.length > pageIndex) {
      return this.pageDimensions[pageIndex].wide;
    }
    // Fallback to client-side cache
    const dims = this.pageDimensionsCache.get(pageIndex);
    if (!dims) return false;
    return dims.width > dims.height * 1.5;
  }

  shouldShowSinglePage(pageIndex: number): boolean {
    return this.isTwoPageView && this.isSpreadPage(pageIndex);
  }

  shouldUseCanvasRenderer(): boolean {
    if (this.pageSplitOption === CbxPageSplitOption.NO_SPLIT) return false;
    if (!this.pageViewMode || this.isTwoPageView) return false;
    return this.isSpreadPage(this.currentPage) && this.canvasSplitState !== 'NO_SPLIT';
  }

  onClickZonePrev(event: Event): void {
    event.stopPropagation();
    if (this.scrollMode === CbxScrollMode.INFINITE) {
      const container = this.getImageScrollContainer();
      if (container) {
        container.scrollBy({ top: -container.clientHeight * 0.9, behavior: 'smooth' });
      }
    } else {
      this.previousPage();
    }
  }

  onClickZoneNext(event: Event): void {
    event.stopPropagation();
    if (this.scrollMode === CbxScrollMode.INFINITE) {
      const container = this.getImageScrollContainer();
      if (container) {
        container.scrollBy({ top: container.clientHeight * 0.9, behavior: 'smooth' });
      }
    } else {
      this.nextPage();
    }
  }

  onClickZoneMenu(event: Event): void {
    event.stopPropagation();
    this.onImageClick();
  }

  private updateMagnifier(event: MouseEvent): void {
    const el = this.magnifierLensRef?.nativeElement;
    if (!el) return;

    const lensSize = this.magnifierLensSize as number;
    const zoom = this.magnifierZoom as number;

    const target = document.elementFromPoint(event.clientX, event.clientY);
    if (!(target instanceof HTMLImageElement) || !target.classList.contains('page-image')) {
      el.style.display = 'none';
      return;
    }

    if (!target.naturalWidth || !target.naturalHeight) {
      el.style.display = 'none';
      return;
    }

    const imgRect = target.getBoundingClientRect();
    const scale = Math.min(imgRect.width / target.naturalWidth, imgRect.height / target.naturalHeight);
    const renderedWidth = target.naturalWidth * scale;
    const renderedHeight = target.naturalHeight * scale;
    const imgOffsetX = (imgRect.width - renderedWidth) / 2;
    const imgOffsetY = (imgRect.height - renderedHeight) / 2;

    const relX = (event.clientX - imgRect.left - imgOffsetX) / renderedWidth;
    const relY = (event.clientY - imgRect.top - imgOffsetY) / renderedHeight;

    if (relX < 0 || relX > 1 || relY < 0 || relY > 1) {
      el.style.display = 'none';
      return;
    }

    const bgWidth = renderedWidth * zoom;
    const bgHeight = renderedHeight * zoom;
    const bgPosX = -(relX * bgWidth - lensSize / 2);
    const bgPosY = -(relY * bgHeight - lensSize / 2);

    el.style.display = 'block';
    el.style.width = `${lensSize}px`;
    el.style.height = `${lensSize}px`;
    el.style.transform = `translate(${event.clientX - lensSize / 2}px, ${event.clientY - lensSize / 2}px)`;
    el.style.backgroundImage = `url('${target.src}')`;
    el.style.backgroundSize = `${bgWidth}px ${bgHeight}px`;
    el.style.backgroundPosition = `${bgPosX}px ${bgPosY}px`;
  }

  private hideMagnifier(): void {
    const el = this.magnifierLensRef?.nativeElement;
    if (el) {
      el.style.display = 'none';
    }
  }

  // Shortcuts help dialog
  onShortcutsHelpClose(): void {
    this.showShortcutsHelp = false;
  }



  ngOnDestroy(): void {
    this.stopSlideshow();
    if (this.infiniteScrollPageDebounceTimer) {
      clearTimeout(this.infiniteScrollPageDebounceTimer);
      this.infiniteScrollPageDebounceTimer = null;
    }
    this.teardownLongStrip();
    this.endReadingSession();
    this.wakeLockService.disable();
    if (this.autoCloseMenuTimer) {
      clearTimeout(this.autoCloseMenuTimer);
    }
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
