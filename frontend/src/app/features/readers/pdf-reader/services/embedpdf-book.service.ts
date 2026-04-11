import {Injectable, NgZone, inject} from '@angular/core';
import {ReplaySubject, Subject} from 'rxjs';
import type {
  EmbedPdfContainer,
  PluginRegistry,
  ScrollCapability,
  ZoomCapability,
  ZoomMode,
  AnnotationCapability,
  AnnotationTransferItem,
  BookmarkCapability,
  PageChangeEvent,
  AnnotationEvent,
  SearchCapability,
  SpreadCapability,
  SpreadMode,
  RotateCapability,
  PanCapability,
  I18nCapability,
} from '@embedpdf/snippet';

export interface PdfOutlineItem {
  title: string;
  pageIndex: number;
  children: PdfOutlineItem[];
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

  private currentDocumentId: string | null = null;

  private pageChangeUnsub?: () => void;

  private annotationEventUnsub?: () => void;
  private layoutReadyUnsub?: () => void;
  private documentOpenedUnsub?: () => void;
  private resizeObserver?: ResizeObserver;

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
      },
      zoom: {
        defaultZoomLevel: 'fit-page' as ZoomMode,
      },
      render: {
        defaultImageQuality: 4,
      },
      tiling: {
        tileSize: 1024,
        overlapPx: 2,
        extraRings: 1,
      },
    }) ?? null;

    if (!this.container) {
      throw new Error('EmbedPDF.init() returned undefined');
    }

    this.zone.runOutsideAngular(() => {
      this.injectBookModeStyles(target);
      this.setupResizeObserver(target);
    });

    this.registry = await this.container.registry;

    const scrollPlugin = this.registry.getPlugin('scroll');
    this.scroll = scrollPlugin?.provides?.() as ScrollCapability ?? null;

    const zoomPlugin = this.registry.getPlugin('zoom');
    this.zoom = zoomPlugin?.provides?.() as ZoomCapability ?? null;

    const annotationPlugin = this.registry.getPlugin('annotation');
    this.annotation = annotationPlugin?.provides?.() as AnnotationCapability ?? null;

    const bookmarkPlugin = this.registry.getPlugin('bookmark');
    this.bookmark = bookmarkPlugin?.provides?.() as BookmarkCapability ?? null;

    const searchPlugin = this.registry.getPlugin('search');
    this.search = searchPlugin?.provides?.() as SearchCapability ?? null;

    const spreadPlugin = this.registry.getPlugin('spread');
    this.spread = spreadPlugin?.provides?.() as SpreadCapability ?? null;

    const rotatePlugin = this.registry.getPlugin('rotate');
    this.rotate = rotatePlugin?.provides?.() as RotateCapability ?? null;

    const panPlugin = this.registry.getPlugin('pan');
    this.pan = panPlugin?.provides?.() as PanCapability ?? null;

    const i18nPlugin = this.registry.getPlugin('i18n');
    this.i18n = i18nPlugin?.provides?.() as I18nCapability ?? null;
    this.applyLocale(requestedLocale);

    // wire events
    if (this.scroll) {
      this.pageChangeUnsub = this.scroll.onPageChange((ev: PageChangeEvent) => {
        this.pageChange$.next(ev);
      });

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      if (typeof (this.scroll as any).onLayoutReady === 'function') {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        this.layoutReadyUnsub = (this.scroll as any).onLayoutReady(() => {
          this.zone.run(() => this.layoutReady$.next());
        });
      }
    }

    // Listen for document opened via document-manager plugin
    const dmPlugin = this.registry.getPlugin('document-manager');
    const dm = dmPlugin?.provides?.() as Record<string, unknown> | null;
    if (dm && typeof dm['onDocumentOpened'] === 'function') {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      this.documentOpenedUnsub = (dm['onDocumentOpened'] as (cb: (ev: any) => void) => () => void)((ev: {id: string, pageCount?: number}) => {
        this.zone.run(() => {
          this.currentDocumentId = ev.id;
          this.documentOpened$.next({pageCount: ev?.pageCount ?? this.scroll?.getTotalPages() ?? 0});
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
    this.zoom?.requestZoom(level as ZoomMode);
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

  getSpreadMode(): string {
    return this.spread?.getSpreadMode?.() ?? 'none';
  }

  setSpreadMode(mode: string): void {
    this.spread?.setSpreadMode?.(mode as SpreadMode);
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

  async importAnnotations(items: AnnotationTransferItem[]): Promise<void> {
    if (!this.annotation || items.length === 0) return;
    const result = this.annotation.importAnnotations(items) as unknown;
    interface EpdfTask { wait: (res: (v?: unknown) => void, rej: (e?: unknown) => void) => void; }

    if (result && typeof result === 'object' && 'wait' in result) {
      const task = result as EpdfTask;
      if (typeof task.wait === 'function') {
        return new Promise<void>((resolve, reject) => {
          task.wait(() => resolve(), (err) => reject(err));
        });
      }
    }
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
      const result = await new Promise<{bookmarks: unknown[]}>((resolve, reject) => {
        this.bookmark!.getBookmarks().wait(resolve, reject);
      });
      return this.convertBookmarks(result.bookmarks);
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

  private convertBookmarks(items: unknown[]): PdfOutlineItem[] {
    if (!Array.isArray(items)) return [];

    interface EpdfBookmark {
      title?: string;
      target?: {
        type?: 'destination' | 'action' | string;
        destination?: {pageIndex?: number};
        action?: {
          type?: number;
          destination?: {pageIndex?: number};
        };
      };
      children?: unknown[];
    }

    return items.map((item: unknown) => {
      const entry = item as EpdfBookmark;
      let pageIndex = 0;

      const target = entry.target;
      if (target) {
        if (target.type === 'destination') {
          pageIndex = target.destination?.pageIndex ?? 0;
        } else if (target.type === 'action' && target.action?.type === 1) {
          // Type 1 is PdfActionType.Goto (internal to PDF viewer)
          pageIndex = target.action.destination?.pageIndex ?? 0;
        }
      }

      return {
        title: String(entry.title || ''),
        pageIndex: pageIndex,
        children: this.convertBookmarks(entry.children || []),
      };
    });
  }

  /**
   * Ensure devicePixelRatio reports at least 2 so that the EmbedPDF tiling
   * and render layers always produce high-resolution bitmaps.
   * The library reads window.devicePixelRatio at tile-render time; if the
   * browser reports 1 (e.g. some WebViews or forced-desktop viewports),
   * PDF pages appear noticeably pixelated.
   */
  private ensureHighDpiRendering(): void {
    const MIN_DPR = 2.5;
    if (window.devicePixelRatio < MIN_DPR) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const w = window as any;
      w.__grimmoryOrigDprDescriptor = Object.getOwnPropertyDescriptor(window, 'devicePixelRatio');
      Object.defineProperty(window, 'devicePixelRatio', {
        get: () => MIN_DPR,
        configurable: true,
      });
    }
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
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const w = window as any;
    if (w.__grimmoryShimsApplied) return;
    w.__grimmoryShimsApplied = true;

    // --- Blob shim ---
    const OrigBlob = window.Blob;
    w.__grimmoryOrigBlob = OrigBlob;
    w.Blob = function PatchedBlob(parts: BlobPart[], opts?: BlobPropertyBag) {
      if (parts?.length >= 1 && typeof parts[0] === 'string') {
        const src = parts[0] as string;
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
          parts = [patched];
        }
      }
      return new OrigBlob(parts, opts);
    };
    w.Blob.prototype = OrigBlob.prototype;
    Object.setPrototypeOf(w.Blob, OrigBlob);

    // --- Worker shim ---
    const OrigWorker = window.Worker;
    w.__grimmoryOrigWorker = OrigWorker;
    w.Worker = function PatchedWorker(url: string | URL, opts?: WorkerOptions) {
      const worker = new OrigWorker(url, opts);
      const urlStr = typeof url === 'string' ? url : url.toString();
      if (urlStr.startsWith('blob:') && opts?.type === 'module') {
        let readySent = false;
        let wasmError = false;
        setTimeout(() => {
          if (!readySent && !wasmError) {
            readySent = true;
            worker.dispatchEvent(new MessageEvent('message', {
              data: {type: 'ready'}
            }));
          }
        }, 5000);
        worker.addEventListener('message', (evt: MessageEvent) => {
          if (evt.data?.type === 'ready') readySent = true;
          if (evt.data?.type === 'wasmError') wasmError = true;
        });
      }
      return worker;
    };
    w.Worker.prototype = OrigWorker.prototype;
    Object.setPrototypeOf(w.Worker, OrigWorker);
  }

  private restoreWorkerShims(): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const w = window as any;
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
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const w = window as any;
    if (w.__grimmoryOrigRelease) return;
    const orig = Element.prototype.releasePointerCapture;
    w.__grimmoryOrigRelease = orig;
    Element.prototype.releasePointerCapture = function (pointerId: number) {
      try {
        orig.call(this, pointerId);
      } catch (e) {
        if (!(e instanceof DOMException && e.message.includes('pointer'))) throw e;
      }
    };
  }

  private restoreReleasePointerCapture(): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const w = window as any;
    if (w.__grimmoryOrigRelease) {
      Element.prototype.releasePointerCapture = w.__grimmoryOrigRelease;
      delete w.__grimmoryOrigRelease;
    }
  }

  private restoreDevicePixelRatio(): void {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const w = window as any;
    if (w.__grimmoryOrigDprDescriptor) {
      Object.defineProperty(window, 'devicePixelRatio', w.__grimmoryOrigDprDescriptor);
      delete w.__grimmoryOrigDprDescriptor;
    } else if (Object.getOwnPropertyDescriptor(window, 'devicePixelRatio')?.configurable) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      delete (window as any)['devicePixelRatio'];
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
      if (shadow.querySelector('style[data-grimmory-book]')) return;

      const style = document.createElement('style');
      style.setAttribute('data-grimmory-book', '');
      style.textContent = `
        /* ── Grimmory book-mode overrides ── */

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

        /* Hide the built-in footer/status bar */
        [class*="border-t"][class*="bg-bg-surface"][class*="px-4"][class*="py-1"] {
          display: none !important;
        }

        /* Hide bottom notification / popup bar */
        [class*="fixed"][class*="bottom-"],
        [class*="absolute"][class*="bottom-"],
        [class*="snackbar"],
        [class*="toast"],
        [class*="notification"],
        [class*="bottom-bar"],
        [class*="status-bar"],
        [class*="statusbar"] {
          display: none !important;
        }

        /* Hide open/close document buttons */
        [data-epdf-i="open-document"],
        [data-epdf-i="close-document"],
        button[title="Open Document"],
        button[title="Close Document"] {
          display: none !important;
        }


        /* Improve annotation layer rendering on touch devices.

        /* Improve annotation layer rendering on touch devices.
           FreeText annotation overlays can bleed through when the
           appearance-stream image has not finished loading. Prevent the
           interactive text span from blocking the rendered image. */
        [role="textbox"][contenteditable="false"] {
          pointer-events: none;
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
      if (this.zoom && this.getSpreadMode() === 'none') {
        const state = this.zoom.getState();
        if (state.zoomLevel === 'fit-page') {
          // Re-request same zoom mode to trigger recalculation
          this.zoom.requestZoom('fit-page' as ZoomMode);
        }
      }
    });
    this.resizeObserver.observe(target);
    this.layoutReady$.subscribe(() => this.resizeObserver?.disconnect());
  }
}
