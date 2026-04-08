import { Component, ElementRef, inject, NgZone, OnDestroy, OnInit, ViewChild, afterNextRender } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PageTitleService } from "../../../shared/service/page-title.service";
import { BookService } from '../../book/service/book.service';
import { forkJoin, firstValueFrom, from, Observable, of, Subject, Subscription, takeUntil } from "rxjs";
import { debounceTime, filter, map, switchMap } from 'rxjs/operators';
import { BookSetting } from '../../book/model/book.model';
import { UserService } from '../../settings/user-management/user.service';
import { AuthService } from '../../../shared/service/auth.service';
import { API_CONFIG } from '../../../core/config/api-config';
import { PdfAnnotationService } from '../../../shared/service/pdf-annotation.service';
import { ReaderIconComponent } from '../../readers/ebook-reader/shared/icon.component';
import { BookMark } from '../../../shared/service/book-mark.service';
import { EmbedPdfBookService, PdfOutlineItem } from './services/embedpdf-book.service';
import type { AnnotationTransferItem } from '@embedpdf/snippet';
import { PdfBookmarkService } from './services/pdf-bookmark.service';
import { PdfSidebarComponent, PdfAnnotationListItem } from './components/pdf-sidebar.component';
import { parseStoredAnnotations, serializeAnnotations } from './utils/annotation-converter';

import { ProgressSpinner } from 'primeng/progressspinner';
import { MessageService } from 'primeng/api';
import { TranslocoService, TranslocoPipe } from '@jsverse/transloco';
import { CacheStorageService } from '../../../shared/service/cache-storage.service'
import { LocalSettingsService } from '../../../shared/service/local-settings.service';
import { ReadingSessionService } from '../../../shared/service/reading-session.service';
import { WakeLockService } from '../../../shared/service/wake-lock.service';
import { Location } from '@angular/common';
import { FormsModule } from '@angular/forms';

type EmbedPdfMessage =
  | { type: 'ready' }
  | { type: 'documentOpened'; pageCount?: number }
  | { type: 'documentError'; error: string }
  | { type: 'saved'; buffer?: ArrayBuffer }
  | { type: 'saveError'; error: string }
  | { type: 'pageChange'; pageNumber: number; totalPages: number };

@Component({
  selector: 'app-pdf-reader',
  standalone: true,
  imports: [ProgressSpinner, TranslocoPipe, ReaderIconComponent, FormsModule, PdfSidebarComponent],
  providers: [EmbedPdfBookService, PdfBookmarkService],
  templateUrl: './pdf-reader.component.html',
  styleUrl: './pdf-reader.component.scss',
})
export class PdfReaderComponent implements OnInit, OnDestroy {

  isLoading = true;
  totalPages: number = 0;
  isDarkTheme = true;
  canPrint = false;

  authorization = '';

  page!: number;
  spread!: 'none' | 'even' | 'odd';
  zoom!: string;

  bookData!: string;
  bookId!: number;
  bookFileId?: number;
  bookTitle = '';
  isFullscreen = false;
  viewerMode: 'book' | 'document' = 'book';
  isDocViewerInfoVisible = false;
  docViewerReady = false;
  private readonly DOC_VIEWER_DISMISSED_KEY = 'grimmory_doc_viewer_info_dismissed';

  // Doc mode (iframe) state
  private embedPdfIframe: HTMLIFrameElement | null = null;
  private embedPdfMessageHandler?: (e: MessageEvent) => void;
  private embedPdfSaveResolve?: (buffer: ArrayBuffer | null) => void;
  private embedPdfSaveTimer?: ReturnType<typeof setTimeout>;
  private embedPdfInitTime = 0;
  private initTimeout?: ReturnType<typeof setTimeout>;
  private isInitializingBookViewer = false;
  private pdfFetchAbortController?: AbortController;

  // Book mode state
  private bookViewerInitialized = false;

  // Chrome auto-hide
  headerVisible = true;
  footerVisible = true;
  private chromeAutoHideTimer?: ReturnType<typeof setTimeout>;
  private readonly CHROME_HIDE_DELAY = 3000;
  private mouseMoveCleanup?: () => void;
  private documentClickCleanup?: () => void;
  private keydownCleanup?: () => void;
  private touchCleanup?: () => void;
  private lastMouseMoveTime = 0;

  // Mobile touch navigation
  isMobile = false;
  isToolbarOverflowOpen = false;
  @ViewChild('overflowMenu') overflowMenuRef?: ElementRef<HTMLDivElement>;
  private touchStartX = 0;
  private touchStartY = 0;
  private touchStartTime = 0;
  private touchMoveCount = 0;
  private readonly SWIPE_THRESHOLD = 50;
  private readonly TAP_ZONE_RATIO = 0.25; // left/right 25% of screen width
  private readonly TAP_MAX_DURATION = 300; // ms

  // Sidebar
  sidebarOpen = false;
  outline: PdfOutlineItem[] = [];
  pdfBookmarks: BookMark[] = [];
  annotationListItems: PdfAnnotationListItem[] = [];

  // Annotation tools
  activeAnnotationTool: string | null = null;
  readonly annotationColors = ['#FFEB3B', '#4CAF50', '#2196F3', '#E91E63', '#9C27B0', '#FF5722'];
  activeAnnotationColor = '#FFEB3B';

  // Search
  isSearchOpen = false;
  isPanActive = false;
  searchQuery = '';
  @ViewChild('searchInput') searchInputRef?: ElementRef<HTMLInputElement>;
  private readonly searchQuery$ = new Subject<string>();
  private dbAnnotationIds = new Set<string>();


  // Zoom presets
  readonly zoomPresets: { label: string; value: string }[] = [
    { label: 'Fit Page', value: 'fit-page' },
    { label: 'Fit Width', value: 'fit-width' },
    { label: '50%', value: '50%' },
    { label: '75%', value: '75%' },
    { label: '100%', value: '100%' },
    { label: '125%', value: '125%' },
    { label: '150%', value: '150%' },
    { label: '200%', value: '200%' },
  ];
  isZoomMenuOpen = false;

  // Spread mode
  spreadMode: 'none' | 'odd' | 'even' = 'none';

  // Footer page navigation
  goToPageInput: number | null = null;
  get sliderTicks(): number[] {
    if (this.totalPages <= 1) return [];
    const step = Math.max(1, Math.floor(this.totalPages / 10));
    const ticks: number[] = [];
    for (let i = 1; i <= this.totalPages; i += step) ticks.push(i);
    if (ticks[ticks.length - 1] !== this.totalPages) ticks.push(this.totalPages);
    return ticks;
  }

  private altBookType?: string;
  private appSettingsSubscription!: Subscription;
  private annotationSaveSubject = new Subject<void>();
  private annotationCacheSubject = new Subject<void>();
  private annotationSaveSubscription!: Subscription;
  private annotationCacheSubscription!: Subscription;
  private annotationsLoaded = false;
  private isImportingAnnotations = false;
  private lastAnnotationData: string | null = null;
  private annotationsDirty = false;
  private pdfBlobUrl: string | null = null;
  private readonly destroy$ = new Subject<void>();

  private bookService = inject(BookService);
  private userService = inject(UserService);
  private authService = inject(AuthService);
  private messageService = inject(MessageService);
  private route = inject(ActivatedRoute);
  private pageTitle = inject(PageTitleService);
  private readingSessionService = inject(ReadingSessionService);
  private location = inject(Location);
  private router = inject(Router);
  private pdfAnnotationService = inject(PdfAnnotationService);
  private cacheStorageService = inject(CacheStorageService);
  private localSettingsService = inject(LocalSettingsService);
  private readonly t = inject(TranslocoService);
  private wakeLockService = inject(WakeLockService);
  readonly embedPdfBook = inject(EmbedPdfBookService);
  readonly pdfBookmarkService = inject(PdfBookmarkService);
  private readonly ngZone = inject(NgZone);

  ngOnInit(): void {
    const dismissed = localStorage.getItem(this.DOC_VIEWER_DISMISSED_KEY);
    this.isDocViewerInfoVisible = dismissed !== 'true';
    this.isMobile = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    setTimeout(() => this.wakeLockService.enable(), 1000);
    this.startChromeAutoHide();
    document.addEventListener('fullscreenchange', this.onFullscreenChange);

    // Listen for mousemove outside Angular zone to avoid constant change detection
    this.ngZone.runOutsideAngular(() => {
      const mouseMoveHandler = () => {
        const now = Date.now();
        if (now - this.lastMouseMoveTime < 200) return;
        this.lastMouseMoveTime = now;
        this.ngZone.run(() => {
          this.showChrome();
          this.startChromeAutoHide();
        });
      };
      document.addEventListener('mousemove', mouseMoveHandler);
      this.mouseMoveCleanup = () => document.removeEventListener('mousemove', mouseMoveHandler);

      const clickHandler = (e: MouseEvent) => {
        if (!this.isZoomMenuOpen) return;
        const wrapper = document.querySelector('.zoom-menu-wrapper');
        if (wrapper && !wrapper.contains(e.target as Node)) {
          this.ngZone.run(() => {
            this.isZoomMenuOpen = false;
          });
        }
      };
      document.addEventListener('click', clickHandler, true);
      this.documentClickCleanup = () => document.removeEventListener('click', clickHandler, true);

      // Intercept 'x' key to prevent EmbedPDF from reloading the page;
      // instead close the reader via SPA navigation.
      const keydownHandler = (e: KeyboardEvent) => {
        const tag = (e.target as HTMLElement)?.tagName;
        const isEditing = tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement)?.isContentEditable;

        if (e.key === 'x' || e.key === 'X') {
          if (isEditing) return;
          e.preventDefault();
          e.stopPropagation();
          this.ngZone.run(() => this.closeReader());
        }

        // Search: Ctrl+F, Cmd+F, or "/"
        if (((e.ctrlKey || e.metaKey) && (e.key === 'f' || e.key === 'F')) || (e.key === '/' && !isEditing)) {
          if (isEditing) return;
          e.preventDefault();
          e.stopPropagation();
          this.ngZone.run(() => {
            if (!this.isSearchOpen) {
              this.toggleSearch();
            } else {
              // Focus search input if already open
              this.searchInputRef?.nativeElement.focus();
            }
          });
        }

        if (e.key === 'Escape') {
          if (this.isSearchOpen) {
            e.preventDefault();
            e.stopPropagation();
            this.ngZone.run(() => this.closeSearch());
          } else if (this.sidebarOpen) {
            e.preventDefault();
            e.stopPropagation();
            this.ngZone.run(() => this.sidebarOpen = false);
          }
        }
      };
      document.addEventListener('keydown', keydownHandler, true);
      this.keydownCleanup = () => document.removeEventListener('keydown', keydownHandler, true);

      // Touch gestures for mobile navigation
      const touchStartHandler = (e: TouchEvent) => {
        this.touchStartX = e.changedTouches[0].clientX;
        this.touchStartY = e.changedTouches[0].clientY;
        this.touchStartTime = Date.now();
        this.touchMoveCount = 0;
      };
      const touchMoveHandler = () => { this.touchMoveCount++; };
      const touchEndHandler = (e: TouchEvent) => {
        this.handleTouchEnd(e);
      };
      document.addEventListener('touchstart', touchStartHandler, { passive: true });
      document.addEventListener('touchmove', touchMoveHandler, { passive: true });
      document.addEventListener('touchend', touchEndHandler, { passive: true });
      this.touchCleanup = () => {
        document.removeEventListener('touchstart', touchStartHandler);
        document.removeEventListener('touchmove', touchMoveHandler);
        document.removeEventListener('touchend', touchEndHandler);
      };
    });

    this.annotationSaveSubscription = this.annotationSaveSubject
      .pipe(debounceTime(1500))
      .subscribe(() => this.persistAnnotations());

    // Debounced search
    this.searchQuery$.pipe(
      takeUntil(this.destroy$),
      debounceTime(400),
      filter(() => this.isSearchOpen)
    ).subscribe(query => {
      if (query.length > 1) {
        this.embedPdfBook.searchAllPages(query);
      }
    });

    // Keep lastAnnotationData warm so persistAnnotationsSync() always has fresh data

    this.annotationCacheSubscription = this.annotationCacheSubject
      .pipe(debounceTime(500))
      .subscribe(() => this.cacheAnnotationData());

    this.route.paramMap.pipe(
      takeUntil(this.destroy$),
      switchMap((params) => {
        this.isLoading = true;
        this.bookId = +params.get('bookId')!;
        this.altBookType = this.route.snapshot.queryParamMap.get('bookType') ?? undefined;

        return from(this.bookService.fetchFreshBookDetail(this.bookId, false)).pipe(
          switchMap((book) => {
            if (this.altBookType) {
              const altFile = book.alternativeFormats?.find(f => f.bookType === this.altBookType);
              this.bookFileId = altFile?.id;
            } else {
              this.bookFileId = book.primaryFile?.id;
            }

            return forkJoin([
              this.bookService.getBookSetting(this.bookId, this.bookFileId!),
              this.userService.getMyself()
            ]).pipe(map(([bookSetting, myself]) => ({ book, bookSetting, myself })));
          })
        );
      }),
      switchMap(({book, bookSetting, myself}) => {
        return this.getBookData(this.bookId.toString(), this.altBookType).pipe(
          map(bookData => ({book, bookSetting, myself, bookData}))
        );
      })
    ).subscribe({
      next: ({ book, bookSetting, myself, bookData }) => {
        const pdfMeta = book;
        const pdfPrefs = bookSetting;

        this.pageTitle.setBookPageTitle(pdfMeta);
        this.bookTitle = pdfMeta.metadata?.title || '';

        const globalOrIndividual = myself.userSettings.perBookSetting.pdf;
        if (globalOrIndividual === 'Global') {
          this.zoom = myself.userSettings.pdfReaderSetting.pageZoom || 'page-fit';
          this.spread = (myself.userSettings.pdfReaderSetting.pageSpread || 'none') === 'off' ? 'none' : (myself.userSettings.pdfReaderSetting.pageSpread as 'none' | 'even' | 'odd') || 'none';
        } else {
          this.zoom = pdfPrefs.pdfSettings?.zoom || myself.userSettings.pdfReaderSetting.pageZoom || 'page-fit';
          const rawSpread = pdfPrefs.pdfSettings?.spread || myself.userSettings.pdfReaderSetting.pageSpread || 'none';
          this.spread = rawSpread === 'off' ? 'none' : rawSpread as 'none' | 'even' | 'odd';
          this.isDarkTheme = pdfPrefs.pdfSettings?.isDarkTheme ?? true;
        }
        this.spreadMode = this.spread;
        this.canPrint = myself.permissions.canDownload || myself.permissions.admin;
        this.page = pdfMeta.pdfProgress?.page || 1;
        this.zoom = this.normalizeZoom(this.zoom);
        this.bookData = bookData;
        const token = this.authService.getInternalAccessToken();
        this.authorization = token ? `Bearer ${token}` : '';
        this.isLoading = false;

        // Initialize book viewer after loading completes
        this.ngZone.runOutsideAngular(() => this.ensureViewerElementAndInitialize());
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('readerPdf.toast.failedToLoadBook') });
        this.isLoading = false;
      }
    });
  }

  dismissDocViewerInfo(): void {
    this.isDocViewerInfoVisible = false;
    localStorage.setItem(this.DOC_VIEWER_DISMISSED_KEY, 'true');
  }

  // --- Book viewer (EmbedPDF direct) ---

  private async ensureViewerElementAndInitialize(attempt = 0): Promise<void> {
    const targetEl = document.getElementById('book-viewer');
    if (!targetEl) {
      if (attempt < 20) {
        setTimeout(() => this.ensureViewerElementAndInitialize(attempt + 1), 50);
      } else {
        console.error('[BookViewer] Failed to find #book-viewer element after 20 attempts');
        this.ngZone.run(() => {
          this.isLoading = false;
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('readerPdf.toast.failedToLoadBook')
          });
        });
      }
      return;
    }

    this.ngZone.run(() => this.initBookViewer());
  }

  private async initBookViewer(): Promise<void> {
    if (this.viewerMode !== 'book' || this.bookViewerInitialized || this.isInitializingBookViewer) return;

    const targetEl = document.getElementById('book-viewer');
    if (!targetEl) return;

    this.isInitializingBookViewer = true;

    // Capture current book state to ensure consistency across async calls
    const currentBookId = this.bookId;
    const currentBookData = this.bookData;

    try {
      // Abort any pending fetch from a previous (stale) initialization attempt
      if (this.pdfFetchAbortController) {
        this.pdfFetchAbortController.abort();
      }
      this.pdfFetchAbortController = new AbortController();

      let pdfUrl: string;
      if (currentBookData.startsWith('blob:')) {
        pdfUrl = currentBookData;
      } else {
        pdfUrl = await this.fetchAsObjectUrl(currentBookData, this.pdfFetchAbortController.signal);
        this.revokePdfBlobUrl();
        this.pdfBlobUrl = pdfUrl;
      }

      // Check if we were aborted or navigated away during the fetch
      if (this.pdfFetchAbortController.signal.aborted || currentBookId !== this.bookId || this.viewerMode !== 'book') {
        return;
      }

      await this.embedPdfBook.init(
        targetEl,
        pdfUrl,
        this.isDarkTheme ? 'dark' : 'light'
      );
      this.bookViewerInitialized = true;

      // Initialize bookmark service early so toggleBookmark works before documentOpened$
      this.pdfBookmarkService.initialize(this.bookId);

      // Subscribe to page changes (outside zone, debounced)
      this.embedPdfBook.pageChange$.pipe(
        takeUntil(this.destroy$),
        debounceTime(100)
      ).subscribe(ev => {
        this.ngZone.run(() => {
          this.onPageChange(ev.pageNumber);
          this.totalPages = ev.totalPages;
        });
      });

      // Subscribe to annotation events for debounced save
      this.embedPdfBook.annotationEvent$.pipe(
        takeUntil(this.destroy$)
      ).subscribe((ev) => {
        this.ngZone.run(() => {
          if (this.annotationsLoaded && !this.isImportingAnnotations) {
            // Update the set of tracked IDs for synchronization
            if (ev.type === 'create' || ev.type === 'update') {
              const id = ev.annotation?.id;
              // Only track specifically allowed user annotation types (exclude links, etc.)
              const type = ev.annotation?.type as number;
              const isAllowed = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15].includes(type);

              if (id && isAllowed) {
                this.dbAnnotationIds.add(id);
              }
            } else if (ev.type === 'delete') {
              const id = ev.annotation?.id;
              if (id) {
                this.dbAnnotationIds.delete(id);
              }
            }

            this.annotationsDirty = true;
            this.annotationSaveSubject.next();
            this.annotationCacheSubject.next();
            this.refreshAnnotationList();
          }
        });
      });

      // Subscribe to document opened
      this.embedPdfBook.documentOpened$.pipe(
        takeUntil(this.destroy$)
      ).subscribe(ev => {
        this.totalPages = ev.pageCount;
        const percentage = this.totalPages > 0 ? Math.round((this.page / this.totalPages) * 1000) / 10 : 0;
        this.readingSessionService.startSession(this.bookId, "PDF", this.page.toString(), percentage);
        this.readingSessionService.updateProgress(this.page.toString(), percentage);

        // Apply saved zoom level
        if (this.zoom && this.zoom !== 'fit-page') {
          this.embedPdfBook.setZoomLevel(this.zoom);
        }

        // Apply saved spread mode
        if (this.spread && this.spread !== 'none') {
          this.embedPdfBook.setSpreadMode(this.spread);
          this.spreadMode = this.spread;
        }

        // Load outline, bookmarks, and annotations
        this.loadOutline();
        this.loadBookmarks();
        this.loadAnnotations();
      });

      // Use onLayoutReady for initial page scroll (fires when document layout is calculated)
      this.embedPdfBook.layoutReady$.pipe(
        takeUntil(this.destroy$)
      ).subscribe(() => {
        if (this.page > 1) {
          this.embedPdfBook.scrollToPage(this.page, 'instant');
        }
      });

    } catch (err) {
      console.error('[BookViewer] Init failed:', err);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('readerPdf.toast.failedToLoadBook')
      });
    } finally {
      this.isInitializingBookViewer = false;
    }
  }

  private async fetchAsObjectUrl(url: string, signal?: AbortSignal): Promise<string> {
    const headers: Record<string, string> = {};
    if (this.authorization) {
      headers['Authorization'] = this.authorization;
    }
    try {
      const response = await fetch(url, { headers, credentials: 'include', signal });
      if (!response.ok) throw new Error(`PDF fetch failed: ${response.status}`);
      const blob = await response.blob();
      return URL.createObjectURL(blob);
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        console.info('[PDF Reader] Fetch aborted for:', url);
        return '';
      }
      throw err;
    }
  }

  private destroyBookViewer(): void {
    this.embedPdfBook.destroy();
    this.bookViewerInitialized = false;
    this.revokePdfBlobUrl();
  }

  private async loadOutline(): Promise<void> {
    this.outline = await this.embedPdfBook.getOutline();
  }

  private loadBookmarks(): void {
    this.pdfBookmarkService.loadBookmarks().subscribe(bookmarks => {
      this.pdfBookmarks = bookmarks;
    });
  }

  private loadAnnotations(): void {
    this.pdfAnnotationService.getAnnotations(this.bookId).subscribe({
      next: async (response) => {
        console.info('[PDF Annotations] GET response:', response);
        if (response?.data) {
          const allItems = parseStoredAnnotations(response.data);
          this.dbAnnotationIds = new Set(allItems.map(i => i.annotation.id));
          
          // Deduplicate items based on animation ID to heal previous corruption
          const seenIds = new Set<string>();
          const items = allItems.filter(item => {
            if (seenIds.has(item.annotation.id)) return false;
            seenIds.add(item.annotation.id);
            return true;
          });

          if (allItems.length !== items.length) {
            console.warn('[PDF Annotations] Deduplicated from', allItems.length, 'to', items.length);
          }

          if (items.length > 0) {
            this.isImportingAnnotations = true;
            try {
              // Chunk the import to prevent script timeout and keep UI responsive
              const CHUNK_SIZE = 500;
              for (let i = 0; i < items.length; i += CHUNK_SIZE) {
                const chunk = items.slice(i, i + CHUNK_SIZE);
                await this.embedPdfBook.importAnnotations(chunk);
                // Breathe to prevent long-task timeouts
                if (items.length > CHUNK_SIZE) {
                  await new Promise(r => setTimeout(r, 0));
                }
              }
              console.info('[PDF Annotations] Import complete');
            } catch (err) {
              console.error('[PDF Annotations] Import failed:', err);
            } finally {
              this.isImportingAnnotations = false;
              this.annotationsLoaded = true;
              this.cacheAnnotationData();
              this.refreshAnnotationList();
            }
            return;
          }
        }
        console.info('[PDF Annotations] No stored annotations, annotationsLoaded=true');
        this.annotationsLoaded = true;
        this.refreshAnnotationList();
      },
      error: (err) => {
        console.warn('[PDF Annotations] GET error:', err);
        this.annotationsLoaded = true;
      }
    });
  }

  private async refreshAnnotationList(): Promise<void> {
    if (!this.annotationsLoaded || this.viewerMode === 'document') return;
    try {
      const allItems = await this.embedPdfBook.exportAnnotations();
      const items = this.filterAndDeduplicateAnnotations(allItems);
      this.annotationListItems = items.map(item => {
        const ann = item.annotation as unknown as Record<string, unknown>;
        return {
          id: String(ann['id'] || ''),
          pageIndex: (ann['pageIndex'] as number) ?? 0,
          type: this.getAnnotationTypeName(ann['type'] as number),
          color: (ann['strokeColor'] ?? ann['color']) as string | undefined,
          text: ann['contents'] as string | undefined,
        };
      });
    } catch {
      this.annotationListItems = [];
    }
  }

  private getAnnotationTypeName(type: number): string {
    switch (type) {
      case 9: return 'Highlight';
      case 15: return 'Ink';
      case 3: return 'Text';
      default: return 'Annotation';
    }
  }

  // --- Annotation tools ---

  toggleAnnotationTool(toolId: string | null): void {
    if (this.activeAnnotationTool === toolId) {
      this.activeAnnotationTool = null;
    } else {
      this.isPanActive = false;
      this.embedPdfBook.setPanMode(false);
      this.activeAnnotationTool = toolId;
    }
    this.embedPdfBook.setActiveTool(this.activeAnnotationTool);
  }

  togglePanMode(): void {
    this.isPanActive = !this.isPanActive;
    if (this.isPanActive) {
      this.activeAnnotationTool = null;
      this.embedPdfBook.setActiveTool(null);
    }
    this.embedPdfBook.setPanMode(this.isPanActive);
  }

  setAnnotationColor(color: string): void {
    this.activeAnnotationColor = color;
  }

  // --- Search ---

  toggleSearch(): void {
    this.isSearchOpen = !this.isSearchOpen;
    if (this.isSearchOpen) {
      afterNextRender(() => this.searchInputRef?.nativeElement.focus());
      this.embedPdfBook.startSearch();
    } else {
      this.searchQuery = '';
      this.embedPdfBook.stopSearch();
    }
  }

  closeSearch(): void {
    this.isSearchOpen = false;
    this.searchQuery = '';
    this.embedPdfBook.stopSearch();
  }

  onSearchInput(): void {
    this.searchQuery$.next(this.searchQuery);
  }


  onSearchNext(): void {
    this.embedPdfBook.nextSearchResult();
  }

  onSearchPrevious(): void {
    this.embedPdfBook.previousSearchResult();
  }

  // --- Zoom ---

  toggleZoomMenu(): void {
    this.isZoomMenuOpen = !this.isZoomMenuOpen;
  }

  setZoomPreset(value: string): void {
    this.zoom = value;
    this.embedPdfBook.setZoomLevel(value);
    this.isZoomMenuOpen = false;
    this.updateViewerSetting();
  }

  // --- Spread ---

  cycleSpreadMode(): void {
    const modes: ('none' | 'odd' | 'even')[] = ['none', 'odd', 'even'];
    const idx = modes.indexOf(this.spreadMode);
    this.spreadMode = modes[(idx + 1) % modes.length];
    this.spread = this.spreadMode;
    this.embedPdfBook.setSpreadMode(this.spreadMode);
    this.updateViewerSetting();
  }

  // --- Rotate ---

  rotateClockwise(): void {
    this.embedPdfBook.rotateClockwise();
  }

  // --- Bookmarks ---

  get isCurrentPageBookmarked(): boolean {
    return this.pdfBookmarkService.isPageBookmarked(this.page);
  }

  toggleBookmark(): void {
    this.pdfBookmarkService.toggleBookmark(this.page).subscribe(success => {
      if (success) {
        this.pdfBookmarks = this.pdfBookmarkService.getAllBookmarks();
      }
    });
  }

  onDeleteBookmark(bookmarkId: number): void {
    this.pdfBookmarkService.deleteBookmark(bookmarkId).subscribe(success => {
      if (success) {
        this.pdfBookmarks = this.pdfBookmarkService.getAllBookmarks();
      }
    });
  }

  onDeleteAnnotation(annotationId: string): void {
    const item = this.annotationListItems.find(a => a.id === annotationId);
    if (item) {
      this.embedPdfBook.deleteAnnotation(item.pageIndex, annotationId);
      this.annotationListItems = this.annotationListItems.filter(a => a.id !== annotationId);
      this.annotationSaveSubject.next();
    }
  }

  // --- Sidebar ---

  toggleSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  onSidebarClosed(): void {
    this.sidebarOpen = false;
  }

  onSidebarNavigateToPage(pageNumber: number): void {
    this.sidebarOpen = false;
    if (this.viewerMode === 'book') {
      this.embedPdfBook.scrollToPage(pageNumber);
    }
    this.onPageChange(pageNumber);
  }

  // --- Viewer mode switching ---

  async setViewerMode(mode: 'book' | 'document') {
    if (mode === this.viewerMode) return;

    this.docViewerReady = false;
    if (this.initTimeout) {
      clearTimeout(this.initTimeout);
      this.initTimeout = undefined;
    }

    if (mode === 'document') {
      // Save annotations before switching
      await this.persistAnnotations();
      this.destroyBookViewer();
      this.viewerMode = mode;
      this.initTimeout = setTimeout(() => {
        this.initTimeout = undefined;
        this.initDocViewerIframe();
      }, 100);
    } else {
      // Switching from doc to book — save in background, don't block the switch
      if (this.embedPdfIframe) {
        this.saveEmbedPdfDocument().finally(() => this.destroyDocViewerIframe());
      }
      this.viewerMode = mode;
      this.initTimeout = setTimeout(() => {
        this.initTimeout = undefined;
        this.initBookViewer();
      }, 150);
    }
  }

  // --- Doc viewer (iframe) ---

  private async initDocViewerIframe() {
    if (this.embedPdfIframe) return;

    const t0 = performance.now();
    this.embedPdfInitTime = t0;

    try {
      const headers: Record<string, string> = {};
      if (this.authorization) {
        headers['Authorization'] = this.authorization;
      }

      const response = await fetch(this.bookData, { headers, credentials: 'include' });
      if (!response.ok) throw new Error(`PDF fetch failed: ${response.status}`);

      if (this.viewerMode !== 'document') return;

      const pdfBuffer = await response.arrayBuffer();
      const targetEl = document.getElementById('embedpdf-viewer');
      if (!targetEl) throw new Error('#embedpdf-viewer not found');

      const iframe = document.createElement('iframe');
      iframe.src = '/assets/embedpdf-frame.html';
      iframe.style.cssText = 'width:100%;height:100%;border:none;';
      iframe.setAttribute('allow', 'fullscreen');

      this.embedPdfMessageHandler = (e: MessageEvent) => {
        if (e.origin !== location.origin) return;
        if (e.source !== iframe.contentWindow) return;
        this.handleEmbedPdfMessage(e.data);
      };
      window.addEventListener('message', this.embedPdfMessageHandler);

      await new Promise<void>((resolve) => {
        iframe.onload = () => resolve();
        targetEl.appendChild(iframe);
      });

      this.embedPdfIframe = iframe;

      iframe.contentWindow!.postMessage({
        type: 'init',
        buffer: pdfBuffer,
        wasmUrl: '/assets/pdfium/pdfium.wasm',
        theme: this.isDarkTheme ? 'dark' : 'light'
      }, location.origin, [pdfBuffer]);

    } catch (err) {
      console.error('[EmbedPDF] FATAL:', err);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: 'Failed to load Document Viewer. Check browser console for details.'
      });
    }
  }

  private handleEmbedPdfMessage(msg: EmbedPdfMessage): void {
    switch (msg.type) {
      case 'ready':
        this.docViewerReady = true;
        break;
      case 'documentOpened':
        break;
      case 'documentError':
        console.error('[EmbedPDF] Document error:', msg.error);
        break;
      case 'saved':
        this.embedPdfSaveResolve?.(msg.buffer ?? null);
        this.embedPdfSaveResolve = undefined;
        break;
      case 'saveError':
        console.error('[EmbedPDF] Save error:', msg.error);
        this.embedPdfSaveResolve?.(null);
        this.embedPdfSaveResolve = undefined;
        break;
      case 'pageChange':
        this.onPageChange(msg.pageNumber);
        this.totalPages = msg.totalPages;
        break;
    }
  }

  private async saveEmbedPdfDocument(): Promise<void> {
    if (!this.embedPdfIframe?.contentWindow) return;

    try {
      const buffer: ArrayBuffer | null = await new Promise((resolve) => {
        this.embedPdfSaveResolve = resolve;
        this.embedPdfIframe!.contentWindow!.postMessage({ type: 'save' }, location.origin);
        const timer = setTimeout(() => {
          if (this.embedPdfSaveResolve === resolve) {
            resolve(null);
            this.embedPdfSaveResolve = undefined;
          }
        }, 5000);
        this.embedPdfSaveTimer = timer;
      });

      if (this.embedPdfSaveTimer) {
        clearTimeout(this.embedPdfSaveTimer);
        this.embedPdfSaveTimer = undefined;
      }

      if (!buffer) return;

      const headers: Record<string, string> = { 'Content-Type': 'application/pdf' };
      if (this.authorization) {
        headers['Authorization'] = this.authorization;
      }

      const url = this.altBookType
        ? `${API_CONFIG.BASE_URL}/api/v1/books/${this.bookId}/content?bookType=${this.altBookType}`
        : `${API_CONFIG.BASE_URL}/api/v1/books/${this.bookId}/content`;

      const uploadResponse = await fetch(url, {
        method: 'PUT',
        headers,
        credentials: 'include',
        body: buffer
      });
      if (!uploadResponse.ok) {
        console.error('[EmbedPDF] Upload failed:', uploadResponse.status);
      }
    } catch (err) {
      console.error('[EmbedPDF] Failed to save document:', err);
    }
  }

  private async destroyDocViewerIframe(): Promise<void> {
    if (this.embedPdfMessageHandler) {
      window.removeEventListener('message', this.embedPdfMessageHandler);
      this.embedPdfMessageHandler = undefined;
    }

    if (this.embedPdfSaveTimer) {
      clearTimeout(this.embedPdfSaveTimer);
      this.embedPdfSaveTimer = undefined;
    }
    this.embedPdfSaveResolve?.(null);
    this.embedPdfSaveResolve = undefined;

    if (this.embedPdfIframe) {
      this.embedPdfIframe.remove();
      this.embedPdfIframe = null;
    }

    this.embedPdfInitTime = 0;
  }

  // --- Common viewer methods ---

  onPageChange(page: number): void {
    if (page !== this.page) {
      this.page = page;
      this.updateProgress();
      const percentage = this.totalPages > 0 ? Math.round((this.page / this.totalPages) * 1000) / 10 : 0;
      this.readingSessionService.updateProgress(this.page.toString(), percentage);
    }
  }

  toggleDarkTheme(): void {
    this.isDarkTheme = !this.isDarkTheme;
    this.updateViewerSetting();
    // Sync theme with active viewer
    if (this.viewerMode === 'book') {
      this.embedPdfBook.setTheme(this.isDarkTheme ? 'dark' : 'light');
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage(
        { type: 'setTheme', theme: this.isDarkTheme ? 'dark' : 'light' },
        location.origin
      );
    }
  }

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = {
      pdfSettings: {
        spread: this.spread,
        zoom: this.zoom,
        isDarkTheme: this.isDarkTheme,
      }
    }
    this.bookService.updateViewerSetting(bookSetting, this.bookId).subscribe();
  }

  updateProgress(): void {
    const percentage = this.totalPages > 0 ? Math.round((this.page / this.totalPages) * 1000) / 10 : 0;
    this.bookService.savePdfProgress(this.bookId, this.page, percentage, this.bookFileId).subscribe();
  }

  ngOnDestroy(): void {
    if (this.initTimeout) clearTimeout(this.initTimeout);
    if (this.pdfFetchAbortController) this.pdfFetchAbortController.abort();
    this.wakeLockService.disable();
    if (this.chromeAutoHideTimer) clearTimeout(this.chromeAutoHideTimer);

    // Tear down all subscriptions via takeUntil
    this.destroy$.next();
    this.destroy$.complete();

    // Save progress via fetch+keepalive so it survives navigation
    this.saveProgressSync();

    if (this.readingSessionService.isSessionActive()) {
      const percentage = this.totalPages > 0 ? Math.round((this.page / this.totalPages) * 1000) / 10 : 0;
      this.readingSessionService.endSession(this.page.toString(), percentage);
    }

    this.annotationSaveSubscription?.unsubscribe();
    this.annotationCacheSubscription?.unsubscribe();
    // Cannot await async export in ngOnDestroy — use cached annotation data
    this.persistAnnotationsSync();
    this.destroyBookViewer();
    this.destroyDocViewerIframe();

    if (this.appSettingsSubscription) {
      this.appSettingsSubscription.unsubscribe();
    }
    document.removeEventListener('fullscreenchange', this.onFullscreenChange);
    this.mouseMoveCleanup?.();
    this.documentClickCleanup?.();
    this.keydownCleanup?.();
    this.touchCleanup?.();

    this.revokePdfBlobUrl();
    if (this.bookData?.startsWith('blob:')) {
      URL.revokeObjectURL(this.bookData);
    }

    this.pdfBookmarkService.reset();
  }

  private revokePdfBlobUrl(): void {
    if (this.pdfBlobUrl) {
      URL.revokeObjectURL(this.pdfBlobUrl);
      this.pdfBlobUrl = null;
    }
  }

  // --- Chrome auto-hide ---

  showChrome(): void {
    this.headerVisible = true;
    this.footerVisible = true;
  }

  hideChrome(): void {
    this.headerVisible = false;
    this.footerVisible = false;
  }

  private startChromeAutoHide(): void {
    if (this.chromeAutoHideTimer) clearTimeout(this.chromeAutoHideTimer);
    this.chromeAutoHideTimer = setTimeout(() => this.hideChrome(), this.CHROME_HIDE_DELAY);
  }

  onHeaderTriggerZoneEnter(): void {
    this.headerVisible = true;
    this.startChromeAutoHide();
  }

  onFooterTriggerZoneEnter(): void {
    this.footerVisible = true;
    this.startChromeAutoHide();
  }

  // --- Touch navigation ---

  private handleTouchEnd(e: TouchEvent): void {
    if (this.viewerMode !== 'book') return;

    // Ignore touches on interactive elements
    const target = e.target instanceof Element ? e.target : (e.target as Node)?.parentElement;
    if (target instanceof Element && target.closest('.pdf-header-toolbar, .pdf-footer, .pdf-sidebar, .search-bar, .toolbar-overflow-menu, button, input, a')) return;

    const endX = e.changedTouches[0].clientX;
    const endY = e.changedTouches[0].clientY;
    const deltaX = endX - this.touchStartX;
    const deltaY = endY - this.touchStartY;
    const elapsed = Date.now() - this.touchStartTime;

    // Swipe detection: enough horizontal movement, more horizontal than vertical
    if (this.touchMoveCount >= 3 && Math.abs(deltaX) > this.SWIPE_THRESHOLD && Math.abs(deltaX) > Math.abs(deltaY) * 1.5) {
      this.ngZone.run(() => {
        if (deltaX < 0) {
          this.goToNextPage();
        } else {
          this.goToPreviousPage();
        }
      });
      return;
    }

    // Tap detection: short duration, minimal movement
    if (elapsed < this.TAP_MAX_DURATION && this.touchMoveCount < 3) {
      const screenWidth = window.innerWidth;
      const tapX = this.touchStartX;

      this.ngZone.run(() => {
        if (tapX < screenWidth * this.TAP_ZONE_RATIO) {
          // Left zone: previous page
          this.goToPreviousPage();
        } else if (tapX > screenWidth * (1 - this.TAP_ZONE_RATIO)) {
          // Right zone: next page
          this.goToNextPage();
        } else {
          // Center zone: toggle chrome
          if (this.headerVisible || this.footerVisible) {
            this.hideChrome();
          } else {
            this.showChrome();
            this.startChromeAutoHide();
          }
        }
      });
    }
  }

  // --- Toolbar overflow menu ---

  toggleToolbarOverflow(): void {
    this.isToolbarOverflowOpen = !this.isToolbarOverflowOpen;
    if (this.isToolbarOverflowOpen) {
      afterNextRender(() => this.overflowMenuRef?.nativeElement.focus());
    }
  }

  closeToolbarOverflow(): void {
    this.isToolbarOverflowOpen = false;
  }

  // --- Fullscreen ---

  toggleFullscreen(): void {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.();
    } else {
      document.exitFullscreen?.();
    }
  }

  private onFullscreenChange = (): void => {
    this.isFullscreen = !!document.fullscreenElement;
  };

  // --- Footer page navigation ---

  goToFirstPage(): void {
    if (this.viewerMode === 'book') {
      this.embedPdfBook.scrollToPage(1);
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: 1 }, location.origin);
    }
    this.onPageChange(1);
  }

  goToPreviousPage(): void {
    if (this.page > 1) {
      const p = this.page - 1;
      if (this.viewerMode === 'book') {
        this.embedPdfBook.scrollToPreviousPage();
      } else if (this.embedPdfIframe?.contentWindow) {
        this.embedPdfIframe.contentWindow.postMessage({ type: 'prevPage' }, location.origin);
      }
      this.onPageChange(p);
    }
  }

  goToNextPage(): void {
    if (this.page < this.totalPages) {
      const p = this.page + 1;
      if (this.viewerMode === 'book') {
        this.embedPdfBook.scrollToNextPage();
      } else if (this.embedPdfIframe?.contentWindow) {
        this.embedPdfIframe.contentWindow.postMessage({ type: 'nextPage' }, location.origin);
      }
      this.onPageChange(p);
    }
  }

  goToLastPage(): void {
    if (this.viewerMode === 'book') {
      this.embedPdfBook.scrollToPage(this.totalPages);
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: this.totalPages }, location.origin);
    }
    this.onPageChange(this.totalPages);
  }

  onSliderChange(event: Event): void {
    const value = +(event.target as HTMLInputElement).value;
    if (this.viewerMode === 'book') {
      this.embedPdfBook.scrollToPage(value, 'instant');
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: value }, location.origin);
    }
    this.onPageChange(value);
  }

  onGoToPage(): void {
    if (this.goToPageInput && this.goToPageInput >= 1 && this.goToPageInput <= this.totalPages) {
      if (this.viewerMode === 'book') {
        this.embedPdfBook.scrollToPage(this.goToPageInput);
      } else if (this.embedPdfIframe?.contentWindow) {
        this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: this.goToPageInput }, location.origin);
      }
      this.onPageChange(this.goToPageInput);
      this.goToPageInput = null;
    }
  }

  closeReader = async (): Promise<void> => {
    try {
      if (this.viewerMode === 'document' && this.embedPdfIframe) {
        await this.saveEmbedPdfDocument();
        await this.destroyDocViewerIframe();
      } else {
        await this.persistAnnotations();
      }
    } catch (e) {
      console.error('[PDF Reader] Error saving on close:', e);
    }
    if (this.readingSessionService.isSessionActive()) {
      const percentage = this.totalPages > 0 ? Math.round((this.page / this.totalPages) * 1000) / 10 : 0;
      this.readingSessionService.endSession(this.page.toString(), percentage);
    }
    // Navigate back within the SPA; fall back to home if there's no history
    if (window.history.length > 1) {
      this.location.back();
    } else {
      this.router.navigate(['/']);
    }
  }

  private getBookData(
    bookId: string,
    fileType: string | undefined,
  ): Observable<string> {
    const uri = fileType
      ? `${API_CONFIG.BASE_URL}/api/v1/books/${bookId}/content?bookType=${fileType}`
      : `${API_CONFIG.BASE_URL}/api/v1/books/${bookId}/content`;
    if (!this.localSettingsService.get().cacheStorageEnabled) return of(uri);
    return from(this.cacheStorageService.getCache(uri)).pipe(
      switchMap(res => res.blob()),
      map(blob => URL.createObjectURL(blob))
    )
  }

  private async persistAnnotations(): Promise<void> {
    if (!this.annotationsLoaded || !this.bookId || this.viewerMode === 'document') return;
    try {
      const allItems = await this.embedPdfBook.exportAnnotations();
      const items = this.filterAndDeduplicateAnnotations(allItems);
      const data = serializeAnnotations(items);
      this.lastAnnotationData = data;
      this.annotationsDirty = false;
      await firstValueFrom(this.pdfAnnotationService.saveAnnotations(this.bookId, data));
      console.info('[PDF Annotations] Saved', items.length, 'annotations for book', this.bookId);
    } catch (e) {
      console.error('[PDF Annotations] Failed to save annotations:', e);
    }
  }

  private persistAnnotationsSync(): void {
    if (!this.bookId || this.lastAnnotationData === null) return;
    const url = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations/book/${this.bookId}`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.authorization) {
      headers['Authorization'] = this.authorization;
    }
    fetch(url, {
      method: 'PUT',
      headers,
      credentials: 'include',
      body: JSON.stringify({ data: this.lastAnnotationData }),
      keepalive: true,
    }).catch(() => { /* fire-and-forget */ });
  }

  private cacheAnnotationData(): void {
    this.embedPdfBook.exportAnnotations().then(allItems => {
      const items = this.filterAndDeduplicateAnnotations(allItems);
      this.lastAnnotationData = serializeAnnotations(items);
    }).catch(() => { /* fire-and-forget */ });
  }

  private saveProgressSync(): void {
    if (!this.bookId || !this.page) return;
    const percentage = this.totalPages > 0 ? Math.round((this.page / this.totalPages) * 1000) / 10 : 0;
    const body: Record<string, unknown> = {
      bookId: this.bookId,
      pdfProgress: { page: this.page, percentage }
    };
    if (this.bookFileId) {
      body['fileProgress'] = {
        bookFileId: this.bookFileId,
        positionData: String(this.page),
        progressPercent: percentage
      };
    }
    const url = `${API_CONFIG.BASE_URL}/api/v1/books/progress`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.authorization) {
      headers['Authorization'] = this.authorization;
    }
    fetch(url, {
      method: 'POST',
      headers,
      credentials: 'include',
      body: JSON.stringify(body),
      keepalive: true,
    }).catch(() => { /* fire-and-forget */ });
  }

  private filterAndDeduplicateAnnotations(allItems: AnnotationTransferItem[]): AnnotationTransferItem[] {
    const seenIds = new Set<string>();
    const allowedSubtypes = new Set([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15]);

    return allItems.filter(item => {
      const ann = item.annotation;
      const id = ann?.id;
      if (!id || seenIds.has(id)) return false;
      
      // Strict whitelist check
      const type = ann?.type as number;
      if (!allowedSubtypes.has(type)) return false;

      // Only allow if it's in our DB-tracked set (for sync)
      if (!this.dbAnnotationIds.has(id)) return false;

      seenIds.add(id);
      return true;
    });
  }

  private normalizeZoom(zoom: string): string {
    const map: Record<string, string> = {
      'page-fit': 'fit-page',
      'page-width': 'fit-width',
    };
    const mapped = map[zoom] ?? zoom;
    // Ensure numerical zoom has % suffix for EmbedPDF
    if (mapped && /^\d+$/.test(mapped)) {
      return mapped + '%';
    }
    return mapped;
  }
}
