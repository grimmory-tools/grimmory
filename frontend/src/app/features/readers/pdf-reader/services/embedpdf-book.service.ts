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

  private currentDocumentId: string | null = null;

  private pageChangeUnsub?: () => void;

  private annotationEventUnsub?: () => void;
  private layoutReadyUnsub?: () => void;
  private documentOpenedUnsub?: () => void;

  readonly pageChange$ = new Subject<PageChangeEvent>();
  readonly annotationEvent$ = new Subject<AnnotationEvent>();
  readonly documentOpened$ = new ReplaySubject<{pageCount: number}>(1);
  readonly layoutReady$ = new ReplaySubject<void>(1);

  get currentPage(): number {
    return this.scroll?.getCurrentPage() ?? 1;
  }

  get totalPages(): number {
    return this.scroll?.getTotalPages() ?? 0;
  }

  async init(target: HTMLElement, pdfUrl: string, theme: 'dark' | 'light'): Promise<void> {
    this.applyWorkerShims();
    this.ensureHighDpiRendering();

    const EmbedPDF = (await import('@embedpdf/snippet')).default;

    const wasmUrl = new URL('/assets/pdfium/pdfium.wasm', location.origin).href;

    this.container = EmbedPDF.init({
      type: 'container',
      target,
      src: pdfUrl,
      wasmUrl,
      worker: true,
      log: false,
      theme: {preference: theme},
      disabledCategories: [
        'redaction',
        'form',
        'stamp',
        'document-print',
        'document-export',
        'document-open',
        'document-close',
        'link',
      ],
      annotations: {
        autoCommit: true,
      },
      zoom: {
        defaultZoomLevel: 'fit-page' as ZoomMode,
      },
      render: {
        defaultImageQuality: 1,
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

    this.zone.runOutsideAngular(() => this.injectBookModeStyles(target));

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
    this.search.searchAllPages(keyword, docId);
  }

  nextSearchResult(): void {
    if (!this.search) return;
    const docId = this.currentDocumentId || undefined;
    this.search.nextResult(docId);
  }

  previousSearchResult(): void {
    if (!this.search) return;
    const docId = this.currentDocumentId || undefined;
    this.search.previousResult(docId);
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

  setActiveTool(toolId: string | null): void {
    this.annotation?.setActiveTool(toolId);
  }

  getActiveTool(): string | null {
    const tool = this.annotation?.getActiveTool();
    return tool?.id ?? null;
  }

  async importAnnotations(items: AnnotationTransferItem[]): Promise<void> {
    this.annotation?.importAnnotations(items);
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
    this.currentDocumentId = null;

    this.restoreWorkerShims();
  }

  private convertBookmarks(items: unknown[]): PdfOutlineItem[] {
    if (!Array.isArray(items)) return [];
    return items.map((item: unknown) => {
      const entry = item as Record<string, unknown>;
      return {
        title: String(entry['title'] || ''),
        pageIndex: typeof entry['pageIndex'] === 'number' ? entry['pageIndex'] : 0,
        children: this.convertBookmarks(entry['children'] as unknown[] || []),
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
    const MIN_DPR = 2;
    if (window.devicePixelRatio < MIN_DPR) {
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

        /* Hide Add Link tool in selection popup */
        [data-epdf-i="add-link"],
        button[title*="Link"] {
          display: none !important;
        }
      `;
      shadow.appendChild(style);
    };
    waitForShadow();
  }
}
