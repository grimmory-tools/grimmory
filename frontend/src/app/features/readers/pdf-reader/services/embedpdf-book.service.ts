import {Injectable, NgZone, inject} from '@angular/core';
import {ReplaySubject, Subject, skip, take} from 'rxjs';
import type {
  EmbedPdfContainer,
  PluginRegistry,
  ScrollPlugin,
  ScrollCapability,
  ZoomPlugin,
  ZoomCapability,
  ZoomLevel,
  AnnotationPlugin,
  AnnotationCapability,
  AnnotationTransferItem,
  BookmarkPlugin,
  BookmarkCapability,
  PageChangeEvent,
  AnnotationEvent,
  SearchPlugin,
  SearchCapability,
  SpreadPlugin,
  SpreadCapability,
  RotatePlugin,
  RotateCapability,
  PanPlugin,
  PanCapability,
  I18nPlugin,
  I18nCapability,
  DocumentManagerPlugin,
} from '@embedpdf/snippet';
import {ScrollStrategy, SpreadMode, ZoomMode} from '@embedpdf/snippet';
import {PdfActionType, type PdfBookmarkObject} from '@embedpdf/models';


export interface PdfOutlineItem {
  title: string;
  pageIndex: number;
  children: PdfOutlineItem[];
}

export type PdfScrollLayout = 'vertical' | 'horizontal';
export type PdfSpread = 'none' | 'odd' | 'even';

function parsePdfZoomLevel(level: string): ZoomLevel | null {
  switch (level) {
    case 'automatic':
      return ZoomMode.Automatic;
    case 'fit-page':
      return ZoomMode.FitPage;
    case 'fit-width':
      return ZoomMode.FitWidth;
  }

  const percentage = /^(\d+(?:\.\d+)?)%$/.exec(level);
  if (!percentage) return null;

  const numericLevel = Number(percentage[1]) / 100;
  return numericLevel > 0 ? numericLevel : null;
}

function toPdfOutlineItems(items: readonly PdfBookmarkObject[]): PdfOutlineItem[] {
  return items.map(entry => {
    let pageIndex = 0;

    const target = entry.target;
    if (target?.type === 'destination') {
      pageIndex = target.destination.pageIndex;
    } else if (target?.type === 'action' && target.action.type === PdfActionType.Goto) {
      pageIndex = target.action.destination.pageIndex;
    }

    return {
      title: entry.title,
      pageIndex,
      children: toPdfOutlineItems(entry.children ?? []),
    };
  });
}

interface GrimmoryWindowState {
  __grimmoryOrigDprDescriptor?: PropertyDescriptor;
  __grimmoryShimsApplied?: boolean;
  __grimmoryOrigBlob?: typeof Blob;
  __grimmoryOrigWorker?: typeof Worker;
  __grimmoryOrigReleaseDescriptor?: PropertyDescriptor;
}

function isWorkerStatusMessage(value: unknown): value is {type: 'ready' | 'wasmError'} {
  if (typeof value !== 'object' || value === null || !('type' in value)) return false;
  return value.type === 'ready' || value.type === 'wasmError';
}

@Injectable()
export class EmbedPdfBookService {
  private zone = inject(NgZone);

  private container: EmbedPdfContainer | null = null;
  private registry: PluginRegistry | null = null;
  private scroll: ScrollCapability | null = null;
  private zoom: ZoomCapability | null = null;
  private annotation: AnnotationCapability | null = null;
  private bookmark: BookmarkCapability | null = null;
  private search: SearchCapability | null = null;
  private spread: SpreadCapability | null = null;
  private rotate: RotateCapability | null = null;
  private pan: PanCapability | null = null;
  private i18n: I18nCapability | null = null;
  private scrollLayout: PdfScrollLayout = 'vertical';

  private currentDocumentId: string | null = null;

  private pageChangeUnsub?: () => void;

  private annotationEventUnsub?: () => void;
  private layoutReadyUnsub?: () => void;
  private documentOpenedUnsub?: () => void;
  private resizeObserver?: ResizeObserver;


  private getMutableWindow(): Window & typeof globalThis & GrimmoryWindowState {
    return window;
  }

  pageChange$ = new Subject<PageChangeEvent>();
  annotationEvent$ = new Subject<AnnotationEvent>();
  documentOpened$ = new ReplaySubject<{pageCount: number}>(1);
  layoutReady$ = new ReplaySubject<void>(1);

  get currentPage(): number {
    return this.scroll?.getCurrentPage() ?? 1;
  }

  get totalPages(): number {
    return this.scroll?.getTotalPages() ?? 0;
  }

  async init(target: HTMLElement, pdfUrl: string, theme: 'dark' | 'light', localeCode: string): Promise<void> {
    // Recreate event streams so the service is reusable after destroy()
    this.pageChange$ = new Subject<PageChangeEvent>();
    this.annotationEvent$ = new Subject<AnnotationEvent>();
    this.documentOpened$ = new ReplaySubject<{pageCount: number}>(1);
    this.layoutReady$ = new ReplaySubject<void>(1);

    this.applyWorkerShims();
    this.ensureHighDpiRendering();
    this.patchReleasePointerCapture();

    const EmbedPDF = (await import('@embedpdf/snippet')).default;

    const wasmUrl = new URL('/assets/pdfium/pdfium.wasm', location.origin).href;
    const requestedLocale = localeCode || 'en';

    this.container = EmbedPDF.init({
      type: 'container',
      target,
      src: pdfUrl,
      wasmUrl,
      worker: true,
      log: false,
      i18n: {
        defaultLocale: requestedLocale,
        fallbackLocale: 'en',
      },
      theme: {preference: theme},
      disabledCategories: [
        'redaction',
        'stamp',
        'document-print',
        'document-export',
        'document-open',
        'document-close',
      ],
      annotations: {
        autoCommit: true,
        autoOpenLinks: false,
        tools: [
          {
            id: 'highlight',
            defaults: {
              opacity: 0.4,
            },
          },
          {
            id: 'inkHighlighter',
            defaults: {
              opacity: 0.4,
            },
          },
        ],
      },
      zoom: {
        defaultZoomLevel: ZoomMode.FitPage,
      },
      render: {
        // Keep book-viewer text crisp across viewport sizes.
        defaultImageQuality: 0.92,
      },
      tiling: this.getTilingConfig(),
    }) ?? null;

    if (!this.container) {
      throw new Error('EmbedPDF.init() returned undefined');
    }

    this.zone.runOutsideAngular(() => {
      this.injectBookModeStyles(target);
      this.setupResizeObserver(target);
    });

    this.registry = await this.container.registry;

    const scrollPlugin = this.registry.getPlugin<ScrollPlugin>('scroll');
    this.scroll = scrollPlugin?.provides() ?? null;
    this.applyScrollLayout(this.scrollLayout);

    const zoomPlugin = this.registry.getPlugin<ZoomPlugin>('zoom');
    this.zoom = zoomPlugin?.provides() ?? null;

    const annotationPlugin = this.registry.getPlugin<AnnotationPlugin>('annotation');
    this.annotation = annotationPlugin?.provides() ?? null;

    const bookmarkPlugin = this.registry.getPlugin<BookmarkPlugin>('bookmark');
    this.bookmark = bookmarkPlugin?.provides() ?? null;

    const searchPlugin = this.registry.getPlugin<SearchPlugin>('search');
    this.search = searchPlugin?.provides() ?? null;

    const spreadPlugin = this.registry.getPlugin<SpreadPlugin>('spread');
    this.spread = spreadPlugin?.provides() ?? null;

    const rotatePlugin = this.registry.getPlugin<RotatePlugin>('rotate');
    this.rotate = rotatePlugin?.provides() ?? null;

    const panPlugin = this.registry.getPlugin<PanPlugin>('pan');
    this.pan = panPlugin?.provides() ?? null;

    const i18nPlugin = this.registry.getPlugin<I18nPlugin>('i18n');
    this.i18n = i18nPlugin?.provides() ?? null;
    this.applyLocale(requestedLocale);

    // wire events
    if (this.scroll) {
      this.pageChangeUnsub = this.scroll.onPageChange((ev: PageChangeEvent) => {
        this.pageChange$.next(ev);
      });

      this.layoutReadyUnsub = this.scroll.onLayoutReady(() => {
        this.zone.run(() => this.layoutReady$.next());
      });
    }

    // Listen for document opened via document-manager plugin
    const dmPlugin = this.registry.getPlugin<DocumentManagerPlugin>('document-manager');
    const documentManager = dmPlugin?.provides() ?? null;
    if (documentManager) {
      this.documentOpenedUnsub = documentManager.onDocumentOpened(ev => {
        this.zone.run(() => {
          this.currentDocumentId = ev.id;
          const pageCount = ev.document?.pageCount ?? this.scroll?.getTotalPages() ?? 0;
          this.documentOpened$.next({pageCount});
        });
      });
    } else {
      // Fallback: emit after a delay once scroll is ready
      this.zone.runOutsideAngular(() => {
        setTimeout(() => {
          this.zone.run(() => {
            this.documentOpened$.next({pageCount: this.scroll?.getTotalPages() ?? 0});
          });
        }, 500);
      });
    }

    if (this.annotation) {
      console.info('[EmbedPDF] Annotation plugin found, hooking events');
      this.annotationEventUnsub = this.annotation.onAnnotationEvent((ev: AnnotationEvent) => {
        console.info('[EmbedPDF] Annotation event:', ev.type);
        this.annotationEvent$.next(ev);
      });
    } else {
      console.warn('[EmbedPDF] Annotation plugin NOT found — events will not fire');
    }
  }

  setTheme(theme: 'dark' | 'light'): void {
    this.container?.setTheme(theme);
  }

  setLocale(localeCode: string): void {
    this.applyLocale(localeCode || 'en');
  }

  setScrollLayout(layout: PdfScrollLayout): void {
    const page = this.currentPage;
    this.scrollLayout = layout;
    this.applyScrollLayout(layout);

    // EmbedPDF layout changes are asynchronous and can reset the current page internally.
    // We wait for the layout engine to settle before restoring the page position.
    let handled = false;
    const restorePage = () => {
      if (handled) return;
      handled = true;
      this.scrollToPage(page, 'instant');
    };

    // Use a short timeout as fallback
    const timeoutId = setTimeout(restorePage, 100);

    // If the engine supports onLayoutReady, it will fire layoutReady$.
    // We skip the current replayed value and wait for the next emission.
    this.layoutReady$.pipe(skip(1), take(1)).subscribe(() => {
      clearTimeout(timeoutId);
      restorePage();
    });
  }

  scrollToPage(pageNumber: number, behavior: 'instant' | 'smooth' = 'smooth'): void {
    this.scroll?.scrollToPage({pageNumber, behavior});
  }

  scrollToNextPage(): void {
    this.scroll?.scrollToNextPage('smooth');
  }

  scrollToPreviousPage(): void {
    this.scroll?.scrollToPreviousPage('smooth');
  }

  zoomIn(): void {
    this.zoom?.zoomIn();
  }

  zoomOut(): void {
    this.zoom?.zoomOut();
  }

  setZoomLevel(level: string): void {
    const zoomLevel = parsePdfZoomLevel(level);
    if (zoomLevel !== null) {
      this.zoom?.requestZoom(zoomLevel);
    }
  }

  // --- Search ---

  startSearch(): void {
    this.search?.startSearch();
  }

  stopSearch(): void {
    this.search?.stopSearch();
  }

  searchAllPages(keyword: string): void {
    if (!this.search) return;
    const docId = this.currentDocumentId || undefined;
    const task = this.search.searchAllPages(keyword, docId);
    // After search completes, scroll to the first result
    task.wait(() => {
      this.scrollToActiveSearchResult();
    }, () => { /* search failed or cancelled */ });
  }

  nextSearchResult(): void {
    if (!this.search) return;
    const docId = this.currentDocumentId || undefined;
    this.search.nextResult(docId);
    this.scrollToActiveSearchResult();
  }

  previousSearchResult(): void {
    if (!this.search) return;
    const docId = this.currentDocumentId || undefined;
    this.search.previousResult(docId);
    this.scrollToActiveSearchResult();
  }

  private scrollToActiveSearchResult(): void {
    if (!this.search) return;
    const docId = this.currentDocumentId || undefined;
    const state = this.search.getState(docId);
    if (!state || state.results.length === 0 || state.activeResultIndex < 0) return;
    const result = state.results[state.activeResultIndex];
    if (result) {
      this.scrollToPage(result.pageIndex + 1, 'smooth');
    }
  }

  // --- Spread/Layout ---

  getSpreadMode(): SpreadMode {
    return this.spread?.getSpreadMode() ?? SpreadMode.None;
  }

  setSpreadMode(mode: PdfSpread): void {
    const spreadMode = {
      none: SpreadMode.None,
      odd: SpreadMode.Odd,
      even: SpreadMode.Even,
    }[mode];
    this.spread?.setSpreadMode(spreadMode);
  }

  // --- Rotation ---

  rotateClockwise(): void {
    this.rotate?.rotateForward?.();
  }

  rotateCounterClockwise(): void {
    this.rotate?.rotateBackward?.();
  }

  // --- Pan ---

  isPanMode(): boolean {
    return this.pan?.isPanMode() ?? false;
  }

  setPanMode(enabled: boolean): void {
    if (enabled) {
      this.pan?.enablePan();
    } else {
      this.pan?.disablePan();
    }
  }

  setActiveTool(toolId: string | null): void {
    this.annotation?.setActiveTool(toolId);
  }

  getActiveTool(): string | null {
    const tool = this.annotation?.getActiveTool();
    return tool?.id ?? null;
  }

  importAnnotations(items: AnnotationTransferItem[]): void {
    if (!this.annotation || items.length === 0) return;
    this.annotation.importAnnotations(items);
  }

  deleteAnnotation(pageIndex: number, annotationId: string): void {
    this.annotation?.deleteAnnotation(pageIndex, annotationId);
  }

  async exportAnnotations(): Promise<AnnotationTransferItem[]> {
    if (!this.annotation) return [];
    return this.annotation.exportAnnotations().toPromise();
  }

  async getOutline(): Promise<PdfOutlineItem[]> {
    if (!this.bookmark) return [];
    try {
      const result = await new Promise<{bookmarks: PdfBookmarkObject[]}>((resolve, reject) => {
        this.bookmark!.getBookmarks().wait(resolve, reject);
      });
      return toPdfOutlineItems(result.bookmarks);
    } catch {
      return [];
    }
  }

  destroy(): void {
    this.pageChangeUnsub?.();
    this.annotationEventUnsub?.();
    this.layoutReadyUnsub?.();
    this.documentOpenedUnsub?.();
    this.resizeObserver?.disconnect();
    this.resizeObserver = undefined;


    this.pageChange$.complete();
    this.annotationEvent$.complete();
    this.documentOpened$.complete();
    this.layoutReady$.complete();

    if (this.container) {
      this.container.remove();
      this.container = null;
    }

    this.registry = null;
    this.scroll = null;
    this.zoom = null;
    this.annotation = null;
    this.bookmark = null;
    this.search = null;
    this.spread = null;
    this.rotate = null;
    this.pan = null;
    this.i18n = null;
    this.currentDocumentId = null;

    this.restoreWorkerShims();
    this.restoreReleasePointerCapture();
    this.restoreDevicePixelRatio();
  }

  private applyLocale(localeCode: string): void {
    if (!this.i18n) return;

    if (this.i18n.hasLocale(localeCode)) {
      this.i18n.setLocale(localeCode);
      return;
    }

    this.i18n.setLocale('en');
  }

  private applyScrollLayout(layout: PdfScrollLayout): void {
    if (!this.scroll) return;
    const strategy = layout === 'vertical' ? ScrollStrategy.Vertical : ScrollStrategy.Horizontal;
    this.scroll.setScrollStrategy(strategy);
  }

  /**
   * Ensure devicePixelRatio reports at least 2 so that the EmbedPDF tiling
   * and render layers always produce high-resolution bitmaps.
   * The library reads window.devicePixelRatio at tile-render time; if the
   * browser reports 1 (e.g. some WebViews or forced-desktop viewports),
   * PDF pages appear noticeably pixelated.
   *
   * On small viewports (phones / responsive mode) we use a lower minimum
   * to avoid exceeding the browser's image-memory budget once that budget
   * is breached the browser silently degrades earlier tile bitmaps, which
   * makes the PDF text appear blurry. Annotation appearance images amplify
   * the problem because they add extra high-resolution bitmaps on top of
   * the page tiles.
   */
  private ensureHighDpiRendering(): void {
    const isSmallViewport = window.innerWidth <= 768;
    const currentDpr = window.devicePixelRatio || 1;

    // On small screens, enforce DPR 2.0 to avoid low-DPR blur while still
    // avoiding the memory spikes caused by very high DPR values.
    // Modern phones often have DPR 3.0+, which combined with annotation layers
    // exceeds the browser's texture memory budget, leading to "emergency" downsampling (blurriness).
    const targetDpr = isSmallViewport ? 2 : Math.max(currentDpr, 2.5);

    if (currentDpr !== targetDpr) {
      const w = this.getMutableWindow();
      w.__grimmoryOrigDprDescriptor = Object.getOwnPropertyDescriptor(window, 'devicePixelRatio');
      Object.defineProperty(window, 'devicePixelRatio', {
        get: () => targetDpr,
        configurable: true,
      });
      console.info(`[EmbedPDF] Adjusted DPR from ${currentDpr} to ${targetDpr} (viewport: ${window.innerWidth}px)`);
    }
  }

  /**
   * Return tiling config tuned for the current viewport.
   * Small screens get smaller tiles and no extra rings to stay within
   * the browser's image-memory budget when annotations are present.
   */
  private getTilingConfig(): {tileSize: number; overlapPx: number; extraRings: number} {
    const isSmallViewport = window.innerWidth <= 768;
    return isSmallViewport
      ? {tileSize: 640, overlapPx: 2, extraRings: 1}
      : {tileSize: 1024, overlapPx: 2, extraRings: 1};
  }

  /**
   * Apply the same Worker/Blob shims that embedpdf-frame.html uses.
   * EmbedPDF creates a blob-URL module Worker for PDFium WASM. Some browsers
   * (or bundler setups) swallow the "ready" postMessage from the worker.
   * These shims:
   *   1) Patch Blob to inject `self.postMessage({type:"ready"})` after `runner.prepare()`.
   *   2) Patch Worker to inject a fallback synthetic "ready" if the real one never arrives.
   * Idempotent — safe to call multiple times.
   */
  private applyWorkerShims(): void {
    const w = this.getMutableWindow();
    if (w.__grimmoryShimsApplied) return;
    w.__grimmoryShimsApplied = true;

    // --- Blob shim ---
    const OrigBlob = window.Blob;
    w.__grimmoryOrigBlob = OrigBlob;
    class PatchedBlob extends OrigBlob {
      static override [Symbol.hasInstance](value: unknown): boolean {
        return value instanceof OrigBlob;
      }

      constructor(parts?: BlobPart[], opts?: BlobPropertyBag) {
        let patchedParts = parts;
        if (parts?.length && typeof parts[0] === 'string') {
          const src = parts[0];
          if (src.includes('wasmInit') && src.includes('runner.prepare()')) {
            let patched = src;
            patched = patched.replace(
              'self.postMessage({ type: "wasmError", error: message });',
              'console.error("[Worker] WASM init FAILED:", message);\n' +
              '      self.postMessage({ type: "wasmError", error: message });'
            );
            patched = patched.replace(
              'await runner.prepare();',
              'await runner.prepare();\n' +
              '      console.log("[Worker] prepare() OK, posting ready");\n' +
              '      self.postMessage({ type: "ready" });'
            );
            patchedParts = [patched];
          }
        }
        super(patchedParts, opts);
      }
    }
    w.Blob = PatchedBlob;

    // --- Worker shim ---
    const OrigWorker = window.Worker;
    w.__grimmoryOrigWorker = OrigWorker;
    class PatchedWorker extends OrigWorker {
      static override [Symbol.hasInstance](value: unknown): boolean {
        return value instanceof OrigWorker;
      }

      constructor(url: string | URL, opts?: WorkerOptions) {
        super(url, opts);
        const urlStr = typeof url === 'string' ? url : url.toString();
        if (urlStr.startsWith('blob:') && opts?.type === 'module') {
          let readySent = false;
          let wasmError = false;
          setTimeout(() => {
            if (!readySent && !wasmError) {
              readySent = true;
              this.dispatchEvent(new MessageEvent('message', {
                data: {type: 'ready'}
              }));
            }
          }, 5000);
          this.addEventListener('message', (event: MessageEvent<unknown>) => {
            if (!isWorkerStatusMessage(event.data)) return;
            if (event.data.type === 'ready') readySent = true;
            if (event.data.type === 'wasmError') wasmError = true;
          });
        }
      }
    }
    w.Worker = PatchedWorker;
  }

  private restoreWorkerShims(): void {
    const w = this.getMutableWindow();
    if (!w.__grimmoryShimsApplied) return;
    if (w.__grimmoryOrigBlob) {
      window.Blob = w.__grimmoryOrigBlob;
      delete w.__grimmoryOrigBlob;
    }
    if (w.__grimmoryOrigWorker) {
      window.Worker = w.__grimmoryOrigWorker;
      delete w.__grimmoryOrigWorker;
    }
    delete w.__grimmoryShimsApplied;
  }

  /**
   * Patch Element.prototype.releasePointerCapture to swallow "Invalid pointer id"
   * DOMExceptions thrown by EmbedPDF's internal Svelte rendering during
   * annotation drag/resize interactions. The error is benign—the pointer has
   * already been released by the time the cleanup call fires.
   */
  private patchReleasePointerCapture(): void {
    const w = this.getMutableWindow();
    if (w.__grimmoryOrigReleaseDescriptor) return;

    const descriptor = Object.getOwnPropertyDescriptor(Element.prototype, 'releasePointerCapture');
    const original: unknown = descriptor?.value;
    if (!descriptor || typeof original !== 'function') return;

    w.__grimmoryOrigReleaseDescriptor = descriptor;
    Element.prototype.releasePointerCapture = function (pointerId: number) {
      try {
        Reflect.apply(original, this, [pointerId]);
      } catch (e) {
        if (!(e instanceof DOMException && e.message.includes('pointer'))) throw e;
      }
    };
  }

  private restoreReleasePointerCapture(): void {
    const w = this.getMutableWindow();
    if (w.__grimmoryOrigReleaseDescriptor) {
      Object.defineProperty(
        Element.prototype,
        'releasePointerCapture',
        w.__grimmoryOrigReleaseDescriptor,
      );
      delete w.__grimmoryOrigReleaseDescriptor;
    }
  }

  private restoreDevicePixelRatio(): void {
    const w = this.getMutableWindow();
    if (w.__grimmoryOrigDprDescriptor) {
      Object.defineProperty(window, 'devicePixelRatio', w.__grimmoryOrigDprDescriptor);
      delete w.__grimmoryOrigDprDescriptor;
    } else if (Object.getOwnPropertyDescriptor(window, 'devicePixelRatio')?.configurable) {
      Reflect.deleteProperty(window, 'devicePixelRatio');
    }
  }

  private injectBookModeStyles(target: HTMLElement): void {
    const waitForShadow = (attempt = 0): void => {
      const epContainer = target.querySelector('embedpdf-container');
      const shadow = epContainer?.shadowRoot;
      if (!shadow) {
        if (attempt < 25) setTimeout(() => waitForShadow(attempt + 1), 200);
        return;
      }
      if (shadow.querySelector('style[data-grimmory-book]')) {
        return;
      }

      const style = document.createElement('style');
      style.setAttribute('data-grimmory-book', '');
      style.textContent = `
        /* ── Grimmory book-mode overrides ── */

        /* Center PDF content vertically and horizontally when smaller than viewport */
        [class*="bg-bg-app"] {
          display: flex !important;
          flex-direction: column !important;
        }
        [class*="bg-bg-app"] > * {
          margin: auto !important;
        }

        /* Force high-quality image rendering for PDF tiles */
        img {
          image-rendering: high-quality;
          -webkit-font-smoothing: antialiased;
        }

        :host {
          --ep-background-app: #1a1a1a;
          --ep-background-surface: #2d2d2d;
          --ep-border-default: #404040;
          --ep-border-subtle: #333333;
          --ep-foreground-primary: rgba(255,255,255,0.95);
          --ep-foreground-secondary: rgba(255,255,255,0.60);
          --ep-accent-primary: #4a90e2;
        }

        :host([data-color-scheme="light"]) {
          --ep-background-app: #f5f5f5;
          --ep-background-surface: #ffffff;
          --ep-border-default: #d0d0d0;
          --ep-border-subtle: #e0e0e0;
          --ep-foreground-primary: rgba(0,0,0,0.87);
          --ep-foreground-secondary: rgba(0,0,0,0.54);
        }

        /* Hide the built-in header toolbar */
        [class*="border-b"][class*="bg-bg-surface"][class*="px-4"][class*="py-2"] {
          display: none !important;
        }

        /* Keep viewer popups/menus visible; only hide explicit file controls. */

        /* Hide open/close document buttons */
        [data-epdf-i="open-document"],
        [data-epdf-i="close-document"],
        button[title="Open Document"],
        button[title="Close Document"] {
          display: none !important;
        }

        /* ── Hide EmbedPDF built-in footer / status bar ── */
        [data-epdf-i*="footer"],
        [data-epdf-i*="status"],
        [data-overlay-id="page-controls"],
        [data-overlay-id*="overlay"],
        [role="contentinfo"] {
          display: none !important;
        }

        /* Improve annotation layer rendering on touch devices.
           FreeText annotation overlays can bleed through when the
           appearance-stream image has not finished loading. Prevent the
           interactive text span from blocking the rendered image. */
        [role="textbox"][contenteditable="false"] {
          pointer-events: none;
        }

        /* Prevent mix-blend-mode:multiply on highlight annotation
           wrappers from forcing the browser to re-rasterise the
           tile compositing group at a lower resolution on mobile.
           The PDFium appearance image already carries the correct
           visual so the CSS blend mode is redundant for committed
           annotations.  During live creation the highlight still
           renders as a semi-transparent overlay (acceptable). */
        @media (max-width: 768px) {
          [data-no-interaction] > div {
            mix-blend-mode: normal !important;
          }
        }
      `;
      shadow.appendChild(style);
    };
    waitForShadow();
  }

  private setupResizeObserver(target: HTMLElement): void {
    if (typeof ResizeObserver === 'undefined') return;

    this.resizeObserver = new ResizeObserver(() => {
      // When the target container resizes (e.g. mobile chrome change),
      // some engines might need a nudge to recalculate "fit-page" zoom correctly.
      if (this.zoom && this.getSpreadMode() === SpreadMode.None) {
        const state = this.zoom.getState();
        if (state.zoomLevel === ZoomMode.FitPage) {
          // Re-request same zoom mode to trigger recalculation
          this.zoom.requestZoom(ZoomMode.FitPage);
        }
      }
    });
    this.resizeObserver.observe(target);
    this.layoutReady$.subscribe(() => this.resizeObserver?.disconnect());
  }

}
