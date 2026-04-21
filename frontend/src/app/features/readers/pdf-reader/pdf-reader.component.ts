import { Component, ElementRef, inject, Injector, NgZone, OnDestroy, OnInit, afterNextRender, viewChild, DestroyRef, signal, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PageTitleService } from "../../../shared/service/page-title.service";
import { BookService } from '../../book/service/book.service';
import { forkJoin, from, Observable, of, Subject, Subscription } from "rxjs";
import { debounceTime, filter, map, switchMap, take } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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

  // --- Template-bound signals ---
  readonly isLoading = signal(true);
  readonly totalPages = signal(0);
  readonly isDarkTheme = signal(true);
  readonly page = signal(1);
  readonly zoom = signal('fit-page');
  readonly bookTitle = signal('');
  readonly isFullscreen = signal(false);
  readonly viewerMode = signal<'book' | 'document'>('book');
  readonly docViewerReady = signal(false);
  readonly isDocViewerInfoVisible = signal(false);
  readonly headerVisible = signal(true);
  readonly footerVisible = signal(true);
  readonly sidebarOpen = signal(false);
  readonly isSearchOpen = signal(false);
  readonly isPanActive = signal(false);
  readonly searchQuery = signal('');
  readonly isToolbarOverflowOpen = signal(false);
  readonly isZoomMenuOpen = signal(false);
  readonly activeAnnotationTool = signal<string | null>(null);
  readonly spreadMode = signal<'none' | 'odd' | 'even'>('none');
  readonly goToPageInput = signal<number | null>(null);
  readonly outline = signal<PdfOutlineItem[]>([]);
  readonly pdfBookmarks = signal<BookMark[]>([]);
  readonly isPhone = signal(false);
  readonly annotationListItems = signal<PdfAnnotationListItem[]>([]);

  readonly isInitialScrollDone = signal(false);

  readonly sliderTicks = computed(() => {
    const total = this.totalPages();
    if (total <= 1) return [];
    const step = Math.max(1, Math.floor(total / 10));
    const ticks: number[] = [];
    for (let i = 1; i <= total; i += step) ticks.push(i);
    if (ticks[ticks.length - 1] !== total) ticks.push(total);
    return ticks;
  });

  readonly isCurrentPageBookmarked = computed(() => {
    this.pdfBookmarks(); // re-evaluate when bookmark list changes
    return this.pdfBookmarkService.isPageBookmarked(this.page());
  });

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

  // --- Internal state (not template-bound) ---
  canPrint = false;
  bookData!: string;
  bookId!: number;
  bookFileId?: number;
  private spread!: 'none' | 'even' | 'odd';
  private readonly DOC_VIEWER_DISMISSED_KEY = 'grimmory_doc_viewer_info_dismissed';

  // Doc mode (iframe) state
  private embedPdfIframe: HTMLIFrameElement | null = null;
  private embedPdfMessageHandler?: (e: MessageEvent) => void;
  private embedPdfSaveResolve?: (buffer: ArrayBuffer | null) => void;
  private embedPdfSaveTimer?: ReturnType<typeof setTimeout>;
  private embedPdfInitTime = 0;
  private pendingPdfBuffer?: ArrayBuffer;
  private initTimeout?: ReturnType<typeof setTimeout>;
  private isInitializingBookViewer = false;
  private pdfFetchAbortController?: AbortController;
  private cachedPdfBuffer: ArrayBuffer | null = null;
  private suppressProgressSave = false;

  // Book mode state
  private bookViewerInitialized = false;

  // Chrome auto-hide
  private chromeAutoHideTimer?: ReturnType<typeof setTimeout>;
  private readonly CHROME_HIDE_DELAY = 3000;
  private mouseMoveCleanup?: () => void;
  private documentClickCleanup?: () => void;
  private keydownCleanup?: () => void;
  private touchCleanup?: () => void;
  private lastMouseMoveTime = 0;

  // Mobile touch navigation
  private isMobile = false;
  readonly overflowMenuRef = viewChild<ElementRef<HTMLDivElement>>('overflowMenu');
  readonly bookViewerContainerRef = viewChild<ElementRef<HTMLDivElement>>('bookViewerContainer');
  private touchStartX = 0;
  private touchStartY = 0;
  private touchStartTime = 0;
  private touchMoveCount = 0;
  private readonly SWIPE_THRESHOLD = 50;
  private readonly TAP_ZONE_RATIO = 0.25;
  private readonly TAP_MAX_DURATION = 300;

  // Annotation tools
  readonly annotationColors = ['#FFEB3B', '#4CAF50', '#2196F3', '#E91E63', '#9C27B0', '#FF5722'];
  activeAnnotationColor = '#FFEB3B';

  // Search
  readonly searchInputRef = viewChild<ElementRef<HTMLInputElement>>('searchInput');
  private readonly searchQuery$ = new Subject<string>();
  private dbAnnotationIds = new Set<string>();

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
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

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
    this.isDocViewerInfoVisible.set(dismissed !== 'true');

    const syncPhoneMode = () => {
      const isPhone = window.innerWidth < 768;
      this.isPhone.set(isPhone);
      this.isPanActive.set(isPhone);
      if (this.bookViewerInitialized) {
        this.embedPdfBook.setPanMode(isPhone);
      }
    };
    syncPhoneMode();
    window.addEventListener('resize', syncPhoneMode);
    this.destroyRef.onDestroy(() => window.removeEventListener('resize', syncPhoneMode));

    this.isMobile = 'ontouchstart' in window || navigator.maxTouchPoints > 0;

    setTimeout(() => this.wakeLockService.enable(), 1000);
    this.startChromeAutoHide();
    document.addEventListener('fullscreenchange', this.onFullscreenChange);

    this.t.langChanges$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(lang => {
        if (this.viewerMode() === 'book') {
          this.embedPdfBook.setLocale(lang);
        } else if (this.embedPdfIframe?.contentWindow) {
          this.embedPdfIframe.contentWindow.postMessage({ type: 'setLocale', locale: lang }, location.origin);
        }
      });

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
        if (!this.isZoomMenuOpen()) return;
        const wrapper = document.querySelector('.zoom-menu-wrapper');
        if (wrapper && !wrapper.contains(e.target as Node)) {
          this.ngZone.run(() => {
            this.isZoomMenuOpen.set(false);
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
            if (!this.isSearchOpen()) {
              this.toggleSearch();
            } else {
              this.searchInputRef()?.nativeElement.focus();
            }
          });
        }

        if (e.key === 'Escape') {
          if (this.isSearchOpen()) {
            e.preventDefault();
            e.stopPropagation();
            this.ngZone.run(() => this.closeSearch());
          } else if (this.sidebarOpen()) {
            e.preventDefault();
            e.stopPropagation();
            this.ngZone.run(() => this.sidebarOpen.set(false));
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
      takeUntilDestroyed(this.destroyRef),
      debounceTime(400),
      filter(() => this.isSearchOpen())
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
      takeUntilDestroyed(this.destroyRef),
      switchMap((params) => {
        this.isLoading.set(true);
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
        this.bookTitle.set(pdfMeta.metadata?.title || '');

        const globalOrIndividual = myself.userSettings.perBookSetting.pdf;
        let zoomVal: string;
        let spreadVal: 'none' | 'even' | 'odd';
        if (globalOrIndividual === 'Global') {
          zoomVal = myself.userSettings.pdfReaderSetting.pageZoom || 'page-fit';
          spreadVal = (myself.userSettings.pdfReaderSetting.pageSpread || 'none') === 'off' ? 'none' : (myself.userSettings.pdfReaderSetting.pageSpread as 'none' | 'even' | 'odd') || 'none';
        } else {
          zoomVal = pdfPrefs.pdfSettings?.zoom || myself.userSettings.pdfReaderSetting.pageZoom || 'page-fit';
          const rawSpread = pdfPrefs.pdfSettings?.spread || myself.userSettings.pdfReaderSetting.pageSpread || 'none';
          spreadVal = rawSpread === 'off' ? 'none' : rawSpread as 'none' | 'even' | 'odd';
          this.isDarkTheme.set(pdfPrefs.pdfSettings?.isDarkTheme ?? true);
        }
        this.spread = spreadVal;
        this.spreadMode.set(spreadVal);
        this.canPrint = myself.permissions.canDownload || myself.permissions.admin;
        this.page.set(pdfMeta.pdfProgress?.page || 1);
        this.zoom.set(this.normalizeZoom(zoomVal));
        this.bookData = bookData;
        this.isInitialScrollDone.set(false);
        this.isLoading.set(false);

        // Schedule viewer initialization after the template renders the container
        afterNextRender(() => {
          this.ngZone.runOutsideAngular(() => this.initBookViewer());
        }, { injector: this.injector });
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('readerPdf.toast.failedToLoadBook') });
        this.isLoading.set(false);
      }
    });
  }

  dismissDocViewerInfo(): void {
    this.isDocViewerInfoVisible.set(false);
    localStorage.setItem(this.DOC_VIEWER_DISMISSED_KEY, 'true');
  }

  // --- Book viewer (EmbedPDF direct) ---

  private async initBookViewer(): Promise<void> {
    if (this.viewerMode() !== 'book' || this.bookViewerInitialized || this.isInitializingBookViewer) return;

    const targetEl = this.bookViewerContainerRef()?.nativeElement;
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
      if (this.pdfBlobUrl) {
        pdfUrl = this.pdfBlobUrl;
      } else if (currentBookData.startsWith('blob:')) {
        pdfUrl = currentBookData;
      } else {
        pdfUrl = await this.fetchAsObjectUrl(currentBookData, this.pdfFetchAbortController.signal);
        this.revokePdfBlobUrl();
        this.pdfBlobUrl = pdfUrl;
      }

      // Check if we were aborted or navigated away during the fetch
      if (this.pdfFetchAbortController.signal.aborted || currentBookId !== this.bookId || this.viewerMode() !== 'book') {
        return;
      }

      await this.embedPdfBook.init(
        targetEl,
        pdfUrl,
        this.isDarkTheme() ? 'dark' : 'light',
        this.t.getActiveLang()
      );
      this.bookViewerInitialized = true;

      if (this.isPanActive()) {
        this.embedPdfBook.setPanMode(true);
      }

      // Cache the raw PDF bytes so the doc-viewer switch never needs a network request
      if (!this.cachedPdfBuffer) {
        const blobSrc = this.pdfBlobUrl || (this.bookData.startsWith('blob:') ? this.bookData : null);
        if (blobSrc) {
          fetch(blobSrc).then(r => r.arrayBuffer()).then(buf => {
            this.cachedPdfBuffer = buf;
          }).catch(() => { /* caching is best-effort */ });
        }
      }

      // Initialize bookmark service early so toggleBookmark works before documentOpened$
      this.pdfBookmarkService.initialize(this.bookId);

      // Subscribe to page changes (outside zone, debounced)
      this.embedPdfBook.pageChange$.pipe(
        takeUntilDestroyed(this.destroyRef),
        debounceTime(100)
      ).subscribe(ev => {
        this.ngZone.run(() => {
          this.onPageChange(ev.pageNumber);
          this.totalPages.set(ev.totalPages);
        });
      });

      // Subscribe to annotation events for debounced save
      this.embedPdfBook.annotationEvent$.pipe(
        takeUntilDestroyed(this.destroyRef)
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
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(ev => {
        this.totalPages.set(ev.pageCount);
        const currentPage = this.page();
        const percentage = ev.pageCount > 0 ? Math.round((currentPage / ev.pageCount) * 1000) / 10 : 0;
        this.readingSessionService.startSession(this.bookId, "PDF", currentPage.toString(), percentage);
        this.readingSessionService.updateProgress(currentPage.toString(), percentage);

        // Apply saved zoom level
        const currentZoom = this.zoom();
        if (currentZoom && currentZoom !== 'fit-page') {
          this.embedPdfBook.setZoomLevel(currentZoom);
        }

        // Apply saved spread mode
        if (this.spread && this.spread !== 'none') {
          this.embedPdfBook.setSpreadMode(this.spread);
          this.spreadMode.set(this.spread);
        }
      });

      // Use onLayoutReady for initial page scroll (fires when document layout is calculated)
      this.embedPdfBook.layoutReady$.pipe(
        takeUntilDestroyed(this.destroyRef),
        take(1)
      ).subscribe(() => {
        const currentPage = this.page();
        if (currentPage > 1) {
          this.embedPdfBook.scrollToPage(currentPage, 'instant');

          // Wait for the scroll to be processed before revealing the viewer.
          // EmbedPDF processes scrollToPage asynchronously, so revealing
          // immediately would flash page 1 before jumping to the target.
          const scrollSub = this.embedPdfBook.pageChange$.pipe(
            filter(ev => ev.pageNumber >= currentPage - 1),
            take(1)
          ).subscribe(() => {
            this.ngZone.run(() => this.isInitialScrollDone.set(true));
          });
          // Fallback in case pageChange$ doesn't fire (e.g. single-page PDF)
          setTimeout(() => {
            if (!this.isInitialScrollDone()) {
              scrollSub.unsubscribe();
              this.ngZone.run(() => this.isInitialScrollDone.set(true));
            }
          }, 800);
        } else {
          this.isInitialScrollDone.set(true);
        }

        // Load outline, bookmarks, and annotations after layout is ready and settled
        this.loadOutline();
        this.loadBookmarks();
        this.loadAnnotations();
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
    const token = this.authService.getInternalAccessToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
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

  private destroyBookViewer(revoke = true): void {
    this.embedPdfBook.destroy();
    this.bookViewerInitialized = false;
    if (revoke) {
      this.revokePdfBlobUrl();
    }
  }

  private async loadOutline(): Promise<void> {
    this.outline.set(await this.embedPdfBook.getOutline());
  }

  private loadBookmarks(): void {
    this.pdfBookmarkService.loadBookmarks().subscribe(bookmarks => {
      this.pdfBookmarks.set(bookmarks);
    });
  }

  private loadAnnotations(): void {
    this.pdfAnnotationService.getAnnotations(this.bookId).subscribe({
      next: async (response) => {
        console.info('[PDF Annotations] GET response:', response);
        if (response?.data) {
          this.lastAnnotationData = response.data;
          const allItems = parseStoredAnnotations(response.data);
          this.dbAnnotationIds = new Set(allItems.map(i => i.annotation.id));

          // Deduplicate items based on annotation ID to heal previous corruption
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
    if (!this.annotationsLoaded || this.viewerMode() === 'document') return;
    try {
      const allItems = await this.embedPdfBook.exportAnnotations();
      const items = this.filterAndDeduplicateAnnotations(allItems);
      console.info(`[PDF Annotations] refresh list: exported ${allItems.length}, kept ${items.length}`);
      this.annotationListItems.set(items.map(item => {
        const ann = item.annotation as unknown as Record<string, unknown>;
        return {
          id: String(ann['id'] || ''),
          pageIndex: (ann['pageIndex'] as number) ?? 0,
          type: this.getAnnotationTypeName(ann['type'] as number),
          color: (ann['strokeColor'] ?? ann['color']) as string | undefined,
          text: ann['contents'] as string | undefined,
        };
      }));
    } catch {
      this.annotationListItems.set([]);
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
    if (this.activeAnnotationTool() === toolId) {
      this.activeAnnotationTool.set(null);
    } else {
      this.isPanActive.set(false);
      this.embedPdfBook.setPanMode(false);
      this.activeAnnotationTool.set(toolId);
    }
    this.embedPdfBook.setActiveTool(this.activeAnnotationTool());
  }

  togglePanMode(): void {
    this.isPanActive.update(v => !v);
    if (this.isPanActive()) {
      this.activeAnnotationTool.set(null);
      this.embedPdfBook.setActiveTool(null);
    }
    this.embedPdfBook.setPanMode(this.isPanActive());
  }

  setAnnotationColor(color: string): void {
    this.activeAnnotationColor = color;
  }

  // --- Search ---

  toggleSearch(): void {
    this.isSearchOpen.update(v => !v);
    if (this.isSearchOpen()) {
      afterNextRender(() => this.searchInputRef()?.nativeElement.focus(), { injector: this.injector });
      this.embedPdfBook.startSearch();
    } else {
      this.searchQuery.set('');
      this.embedPdfBook.stopSearch();
    }
  }

  closeSearch(): void {
    this.isSearchOpen.set(false);
    this.searchQuery.set('');
    this.embedPdfBook.stopSearch();
  }

  onSearchQueryChange(value: string): void {
    this.searchQuery.set(value);
    this.searchQuery$.next(value);
  }

  onSearchNext(): void {
    this.embedPdfBook.nextSearchResult();
  }

  onSearchPrevious(): void {
    this.embedPdfBook.previousSearchResult();
  }

  // --- Zoom ---

  toggleZoomMenu(): void {
    this.isZoomMenuOpen.update(v => !v);
  }

  setZoomPreset(value: string): void {
    this.zoom.set(value);
    this.embedPdfBook.setZoomLevel(value);
    this.isZoomMenuOpen.set(false);
    this.updateViewerSetting();
  }

  // --- Spread ---

  cycleSpreadMode(): void {
    const modes: ('none' | 'odd' | 'even')[] = ['none', 'odd', 'even'];
    const idx = modes.indexOf(this.spreadMode());
    const next = modes[(idx + 1) % modes.length];
    this.spreadMode.set(next);
    this.spread = next;
    this.embedPdfBook.setSpreadMode(next);
    this.updateViewerSetting();
  }

  // --- Rotate ---

  rotateClockwise(): void {
    this.embedPdfBook.rotateClockwise();
  }

  // --- Bookmarks ---

  toggleBookmark(): void {
    this.pdfBookmarkService.toggleBookmark(this.page()).subscribe(success => {
      if (success) {
        this.pdfBookmarks.set(this.pdfBookmarkService.getAllBookmarks());
      }
    });
  }

  onDeleteBookmark(bookmarkId: number): void {
    this.pdfBookmarkService.deleteBookmark(bookmarkId).subscribe(success => {
      if (success) {
        this.pdfBookmarks.set(this.pdfBookmarkService.getAllBookmarks());
      }
    });
  }

  onDeleteAnnotation(annotationId: string): void {
    const items = this.annotationListItems();
    const item = items.find(a => a.id === annotationId);
    if (item) {
      this.embedPdfBook.deleteAnnotation(item.pageIndex, annotationId);
      this.annotationListItems.set(items.filter(a => a.id !== annotationId));
      this.annotationSaveSubject.next();
    }
  }

  // --- Sidebar ---

  toggleSidebar(): void {
    this.sidebarOpen.update(v => !v);
  }

  onSidebarClosed(): void {
    this.sidebarOpen.set(false);
  }

  onSidebarNavigateToPage(pageNumber: number): void {
    this.sidebarOpen.set(false);
    if (this.viewerMode() === 'book') {
      this.embedPdfBook.scrollToPage(pageNumber);
    }
    this.onPageChange(pageNumber);
  }

  // --- Viewer mode switching ---

  async setViewerMode(mode: 'book' | 'document') {
    if (mode === this.viewerMode()) return;

    this.docViewerReady.set(false);
    if (this.initTimeout) {
      clearTimeout(this.initTimeout);
      this.initTimeout = undefined;
    }

    if (mode === 'document') {
      this.suppressProgressSave = true;
      // Ensure annotations are captured before destroying the book viewer
      await this.persistAnnotations();
      this.destroyBookViewer(false); // Do not revoke blob URL
      this.viewerMode.set(mode);
      this.initTimeout = setTimeout(() => {
        this.initTimeout = undefined;
        this.initDocViewerIframe();
      }, 100);
    } else {
      // Switching from doc to book — save in background, don't block the switch
      if (this.embedPdfIframe) {
        this.saveEmbedPdfDocument().finally(() => this.destroyDocViewerIframe());
      }
      this.viewerMode.set(mode);
      this.isInitialScrollDone.set(false);
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
      let pdfBuffer: ArrayBuffer;

      if (this.cachedPdfBuffer) {
        // Use the in-memory cache — completely network-free
        pdfBuffer = this.cachedPdfBuffer.slice(0);
      } else {
        const source = this.pdfBlobUrl || this.bookData;
        if (source.startsWith('blob:')) {
          const res = await fetch(source);
          pdfBuffer = await res.arrayBuffer();
        } else {
          const headers: Record<string, string> = {};
          const token = this.authService.getInternalAccessToken();
          if (token) {
            headers['Authorization'] = `Bearer ${token}`;
          }
          const response = await fetch(source, { headers, credentials: 'include' });
          if (!response.ok) throw new Error(`PDF fetch failed: ${response.status}`);
          pdfBuffer = await response.arrayBuffer();
        }
      }

      if (this.viewerMode() !== 'document') return;

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
      this.pendingPdfBuffer = pdfBuffer;

      iframe.contentWindow!.postMessage({
        type: 'init',
        wasmUrl: '/assets/pdfium/pdfium.wasm',
        theme: this.isDarkTheme() ? 'dark' : 'light',
        locale: this.t.getActiveLang()
      }, location.origin);

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
        this.docViewerReady.set(true);
        if (this.pendingPdfBuffer && this.embedPdfIframe?.contentWindow) {
          const buf = this.pendingPdfBuffer;
          this.pendingPdfBuffer = undefined;
          this.embedPdfIframe.contentWindow.postMessage(
            { type: 'load', buffer: buf },
            location.origin,
            [buf]
          );
        }
        break;
      case 'documentOpened':
        // Scroll to the page the user was on in book mode and re-enable progress saving
        if (this.embedPdfIframe?.contentWindow && this.page() > 1) {
          this.embedPdfIframe.contentWindow.postMessage(
            { type: 'scrollToPage', pageNumber: this.page() },
            location.origin
          );
        }
        setTimeout(() => { this.suppressProgressSave = false; }, 500);
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
        this.totalPages.set(msg.totalPages);
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
      const uploadToken = this.authService.getInternalAccessToken();
      if (uploadToken) {
        headers['Authorization'] = `Bearer ${uploadToken}`;
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

    this.pendingPdfBuffer = undefined;
    this.embedPdfInitTime = 0;
  }

  // --- Common viewer methods ---

  onPageChange(page: number | undefined): void {
    if (page == null || page === this.page()) {
      return;
    }
    this.page.set(page);
    this.updateProgress();
    const total = this.totalPages();
    const percentage = total > 0 ? Math.round((page / total) * 1000) / 10 : 0;
    this.readingSessionService.updateProgress(page.toString(), percentage);
  }


  toggleDarkTheme(): void {
    this.isDarkTheme.update(v => !v);
    this.updateViewerSetting();
    // Sync theme with active viewer
    if (this.viewerMode() === 'book') {
      this.embedPdfBook.setTheme(this.isDarkTheme() ? 'dark' : 'light');
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage(
        { type: 'setTheme', theme: this.isDarkTheme() ? 'dark' : 'light' },
        location.origin
      );
    }
  }

  private updateViewerSetting(): void {
    const bookSetting: BookSetting = {
      pdfSettings: {
        spread: this.spread,
        zoom: this.zoom(),
        isDarkTheme: this.isDarkTheme(),
      }
    }
    this.bookService.updateViewerSetting(bookSetting, this.bookId).subscribe();
  }

  updateProgress(): void {
    if (this.suppressProgressSave) return;
    const currentPage = this.page();
    const total = this.totalPages();
    const percentage = total > 0 ? Math.round((currentPage / total) * 1000) / 10 : 0;
    this.bookService.savePdfProgress(this.bookId, currentPage, percentage, this.bookFileId).subscribe();
  }

  ngOnDestroy(): void {
    if (this.initTimeout) clearTimeout(this.initTimeout);
    if (this.pdfFetchAbortController) this.pdfFetchAbortController.abort();
    this.wakeLockService.disable();
    if (this.chromeAutoHideTimer) clearTimeout(this.chromeAutoHideTimer);

    // Save progress via fetch+keepalive so it survives navigation
    this.saveProgressSync();

    if (this.readingSessionService.isSessionActive()) {
      const currentPage = this.page();
      const total = this.totalPages();
      const percentage = total > 0 ? Math.round((currentPage / total) * 1000) / 10 : 0;
      this.readingSessionService.endSession(currentPage.toString(), percentage);
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
    this.cachedPdfBuffer = null;
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
    this.headerVisible.set(true);
    this.footerVisible.set(true);
  }

  hideChrome(): void {
    this.headerVisible.set(false);
    this.footerVisible.set(false);
  }

  private startChromeAutoHide(): void {
    if (this.chromeAutoHideTimer) clearTimeout(this.chromeAutoHideTimer);
    this.chromeAutoHideTimer = setTimeout(() => this.hideChrome(), this.CHROME_HIDE_DELAY);
  }

  onHeaderTriggerZoneEnter(): void {
    this.headerVisible.set(true);
    this.startChromeAutoHide();
  }

  onFooterTriggerZoneEnter(): void {
    this.footerVisible.set(true);
    this.startChromeAutoHide();
  }

  // --- Touch navigation ---

  private handleTouchEnd(e: TouchEvent): void {
    if (this.viewerMode() !== 'book') return;

    // Ignore touches on interactive elements
    const target = e.target instanceof Element ? e.target : (e.target as Node)?.parentElement;
    if (target instanceof Element && target.closest('.pdf-header-toolbar, .pdf-footer, .pdf-sidebar, .search-bar, .toolbar-overflow-menu, button, input, a')) return;

    const endX = e.changedTouches[0].clientX;
    const endY = e.changedTouches[0].clientY;
    const deltaX = endX - this.touchStartX;
    const deltaY = endY - this.touchStartY;
    const elapsed = Date.now() - this.touchStartTime;

    // Swipe detection: enough horizontal movement, more horizontal than vertical.
    // Skip if pan mode is active OR if on a phone to prevent accidental page turns during selection/navigation.
    const skipSwipe = this.isPhone() || this.isPanActive();

    if (!skipSwipe && this.touchMoveCount >= 3 && Math.abs(deltaX) > this.SWIPE_THRESHOLD && Math.abs(deltaX) > Math.abs(deltaY) * 1.5) {
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
        // On phones, disable tap-to-change-page zones to prevent accidental navigation.
        // Taps will only toggle the chrome.
        if (!this.isPhone() && tapX < screenWidth * this.TAP_ZONE_RATIO) {
          // Left zone: previous page
          this.goToPreviousPage();
        } else if (!this.isPhone() && tapX > screenWidth * (1 - this.TAP_ZONE_RATIO)) {
          // Right zone: next page
          this.goToNextPage();
        } else {
          // Center zone (or any zone on phone): toggle chrome
          if (this.headerVisible() || this.footerVisible()) {
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
    this.isToolbarOverflowOpen.update(v => !v);
    if (this.isToolbarOverflowOpen()) {
      afterNextRender(() => this.overflowMenuRef()?.nativeElement.focus(), { injector: this.injector });
    }
  }

  closeToolbarOverflow(): void {
    this.isToolbarOverflowOpen.set(false);
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
    this.isFullscreen.set(!!document.fullscreenElement);
  };

  // --- Footer page navigation ---

  goToFirstPage(): void {
    if (this.viewerMode() === 'book') {
      this.embedPdfBook.scrollToPage(1);
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: 1 }, location.origin);
    }
    this.onPageChange(1);
  }

  goToPreviousPage(): void {
    const currentPage = this.page();
    if (currentPage > 1) {
      const p = currentPage - 1;
      if (this.viewerMode() === 'book') {
        this.embedPdfBook.scrollToPreviousPage();
      } else if (this.embedPdfIframe?.contentWindow) {
        this.embedPdfIframe.contentWindow.postMessage({ type: 'prevPage' }, location.origin);
      }
      this.onPageChange(p);
    }
  }

  goToNextPage(): void {
    const currentPage = this.page();
    const total = this.totalPages();
    if (currentPage < total) {
      const p = currentPage + 1;
      if (this.viewerMode() === 'book') {
        this.embedPdfBook.scrollToNextPage();
      } else if (this.embedPdfIframe?.contentWindow) {
        this.embedPdfIframe.contentWindow.postMessage({ type: 'nextPage' }, location.origin);
      }
      this.onPageChange(p);
    }
  }

  goToLastPage(): void {
    const total = this.totalPages();
    if (this.viewerMode() === 'book') {
      this.embedPdfBook.scrollToPage(total);
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: total }, location.origin);
    }
    this.onPageChange(total);
  }

  onSliderChange(event: Event): void {
    const value = +(event.target as HTMLInputElement).value;
    if (this.viewerMode() === 'book') {
      this.embedPdfBook.scrollToPage(value, 'instant');
    } else if (this.embedPdfIframe?.contentWindow) {
      this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: value }, location.origin);
    }
    this.onPageChange(value);
  }

  onGoToPage(): void {
    const input = this.goToPageInput();
    const total = this.totalPages();
    if (input && input >= 1 && input <= total) {
      if (this.viewerMode() === 'book') {
        this.embedPdfBook.scrollToPage(input);
      } else if (this.embedPdfIframe?.contentWindow) {
        this.embedPdfIframe.contentWindow.postMessage({ type: 'scrollToPage', pageNumber: input }, location.origin);
      }
      this.onPageChange(input);
      this.goToPageInput.set(null);
    }
  }

  closeReader = async (): Promise<void> => {
    try {
      if (this.viewerMode() === 'document' && this.embedPdfIframe) {
        await this.saveEmbedPdfDocument();
        await this.destroyDocViewerIframe();
      } else {
        await this.persistAnnotations();
      }
    } catch (e) {
      console.error('[PDF Reader] Error saving on close:', e);
    }
    if (this.readingSessionService.isSessionActive()) {
      const currentPage = this.page();
      const total = this.totalPages();
      const percentage = total > 0 ? Math.round((currentPage / total) * 1000) / 10 : 0;
      this.readingSessionService.endSession(currentPage.toString(), percentage);
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
    if (!this.annotationsLoaded || !this.bookId || this.viewerMode() === 'document' || !this.annotationsDirty) {
      console.info('[PDF Annotations] Skip save: loaded=', this.annotationsLoaded, 'dirty=', this.annotationsDirty);
      return;
    }

    try {
      const allItems = await this.embedPdfBook.exportAnnotations();
      const items = this.filterAndDeduplicateAnnotations(allItems);
      const data = serializeAnnotations(items);

      console.info(`[PDF Annotations] Initiating save: exported ${allItems.length}, kept ${items.length}`);

      this.lastAnnotationData = data;
      this.annotationsDirty = false; // Speculatively clear to avoid double-save

      // Use raw fetch() instead of HttpClient so the auth interceptor cannot
      // trigger forceLogout() on an expired token during viewer-mode switches.
      const saveUrl = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations/book/${this.bookId}`;
      const saveHeaders: Record<string, string> = { 'Content-Type': 'application/json' };
      const saveToken = this.authService.getInternalAccessToken();
      if (saveToken) {
        saveHeaders['Authorization'] = `Bearer ${saveToken}`;
      }
      fetch(saveUrl, {
        method: 'PUT',
        headers: saveHeaders,
        credentials: 'include',
        body: JSON.stringify({ data }),
      }).then(res => {
        if (res.ok) {
          console.info('[PDF Annotations] Saved', items.length, 'annotations for book', this.bookId);
        } else {
          this.annotationsDirty = true;
          console.error('[PDF Annotations] Save failed:', res.status);
        }
      }).catch(err => {
        this.annotationsDirty = true;
        console.error('[PDF Annotations] Failed to save annotations:', err);
      });
    } catch (e) {
      console.error('[PDF Annotations] Failed to export annotations:', e);
    }
  }

  private persistAnnotationsSync(): void {
    if (!this.bookId || this.lastAnnotationData === null || !this.annotationsDirty) {
      console.info('[PDF Annotations Sync] Skip sync save: dirty=', this.annotationsDirty);
      return;
    }
    this.annotationsDirty = false;

    const url = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations/book/${this.bookId}`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    const syncToken = this.authService.getInternalAccessToken();
    if (syncToken) {
      headers['Authorization'] = `Bearer ${syncToken}`;
    }
    console.info('[PDF Annotations Sync] Sending sync save request');
    fetch(url, {
      method: 'PUT',
      headers,
      credentials: 'include',
      body: JSON.stringify({ data: this.lastAnnotationData }),
      keepalive: true,
    }).catch((err) => { console.error('[PDF Annotations Sync] Sync save failed:', err); });
  }

  private cacheAnnotationData(): void {
    this.embedPdfBook.exportAnnotations().then(allItems => {
      const items = this.filterAndDeduplicateAnnotations(allItems);
      this.lastAnnotationData = serializeAnnotations(items);
      console.info(`[PDF Annotations] Cache updated: ${items.length} items`);
    }).catch(() => { /* fire-and-forget */ });
  }

  private saveProgressSync(): void {
    const currentPage = this.page();
    if (!this.bookId || !currentPage) return;
    const total = this.totalPages();
    const percentage = total > 0 ? Math.round((currentPage / total) * 1000) / 10 : 0;
    const body: Record<string, unknown> = {
      bookId: this.bookId,
      pdfProgress: { page: currentPage, percentage }
    };
    if (this.bookFileId) {
      body['fileProgress'] = {
        bookFileId: this.bookFileId,
        positionData: String(currentPage),
        progressPercent: percentage
      };
    }
    const url = `${API_CONFIG.BASE_URL}/api/v1/books/progress`;
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    const progressToken = this.authService.getInternalAccessToken();
    if (progressToken) {
      headers['Authorization'] = `Bearer ${progressToken}`;
    }
    console.info('[PDF Progress Sync] Sending progress sync request');
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

    const filtered = allItems.filter(item => {
      const ann = item.annotation;
      const id = ann?.id;
      if (!id || seenIds.has(id)) return false;

      // Strict whitelist check
      const type = ann?.type as number;
      if (!allowedSubtypes.has(type)) return false;

      // Only allow if it's in our DB-tracked set (for sync)
      if (!this.dbAnnotationIds.has(id)) {
        // console.warn(`[PDF Annotations] Filtered out unknown ID: ${id} (type: ${type})`);
        return false;
      }

      seenIds.add(id);
      return true;
    });

    return filtered;
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
