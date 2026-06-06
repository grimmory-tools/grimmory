import {inject, Injectable} from '@angular/core';
import {defer, from, Observable, of, throwError, timer} from 'rxjs';
import {catchError, map, switchMap} from 'rxjs/operators';
import {ReaderAnnotationService, Annotation} from '../features/annotations/annotation-renderer.service';
import {ReaderEventService, ViewEvent, TextSelection} from './event.service';
import {PageInfo, ThemeInfo, PageDecorator} from '../shared/header-footer.util';
import {EpubStreamingService, EpubBookInfo} from './epub-streaming.service';

export type {ViewEvent, TextSelection} from './event.service';
export type {PageInfo, ThemeInfo} from '../shared/header-footer.util';

interface FoliateTocItem {
  label: string;
  href: string;
  subitems?: FoliateTocItem[];
}

interface TocItem {
  label: string;
  href: string;
  subitems?: TocItem[];
}

export interface BookMetadata {
  title?: string;
  authors?: string[];
  language?: string;
  publisher?: string;
  description?: string;
  identifier?: string;
  coverUrl?: string | null;

  [key: string]: unknown;
}

interface RendererContent {
  index: number;
  doc: Document;
}

export interface FoliateRenderer {
  heads?: HTMLElement[];
  feet?: HTMLElement[];
  getContents(): RendererContent[];
  setAttribute(name: string, value: string | number): void;
  removeAttribute(name: string): void;
  setStyles?(css: string): void;
}

interface FoliateBook {
  toc?: FoliateTocItem[];
  metadata?: BookMetadata;
  getCover?(): Promise<Blob | null> | null;
}

interface FoliateSearchSubitem {
  cfi: string;
  excerpt: {
    pre: string;
    match: string;
    post: string;
  };
}

interface FoliateSearchProgress {
  progress: number;
}

interface FoliateSearchSectionResult {
  label?: string;
  subitems?: FoliateSearchSubitem[];
}

type FoliateSearchResult = FoliateSearchProgress | FoliateSearchSectionResult | 'done';

interface FoliateViewElement extends HTMLElement {
  renderer?: FoliateRenderer | null;
  book?: FoliateBook;
  open(target: File | object): Promise<void>;
  goTo(target: string | number): Promise<void>;
  goToFraction(fraction: number): Promise<void>;
  prev(): void;
  next(): void;
  getCFI(index: number, range: Range): string | null;
  deselect(): void;
  addAnnotation(annotation: { value: string }): void;
  deleteAnnotation(annotation: { value: string }): Promise<void>;
  showAnnotation(annotation: { value: string }): Promise<void>;
  getSectionFractions?(): number[];
  search?(opts: { query: string; matchCase?: boolean; matchWholeWords?: boolean; index?: number }): AsyncGenerator<FoliateSearchResult>;
  resolveNavigation?(target: string): { index?: number } | undefined;
  clearSearch?(): void;
}

interface StreamingBookFactoryWindow extends Window {
  makeStreamingBook?: (
    bookId: number,
    baseUrl: string,
    bookInfo: EpubBookInfo,
    authToken: string | null,
    bookType?: string
  ) => Promise<object>;
}

@Injectable({
  providedIn: 'root'
})
export class ReaderViewManagerService {
  private annotationService = inject(ReaderAnnotationService);
  private eventService = inject(ReaderEventService);
  private epubStreamingService = inject(EpubStreamingService);
  private view: FoliateViewElement | null = null;

  public get events$(): Observable<ViewEvent> {
    return this.eventService.events$;
  }

  createView(container: HTMLElement): void {
    this.view = document.createElement('foliate-view') as FoliateViewElement;
    this.view.style.width = '100%';
    this.view.style.height = '100%';
    this.view.style.display = 'block';
    container.appendChild(this.view);

    this.eventService.initialize(this.view, {
      prev: () => this.prev(),
      next: () => this.next(),
      getCFI: (index: number, range: Range) => this.view?.getCFI(index, range) ?? null,
      getContents: () => this.view?.renderer?.getContents() ?? null
    });
  }

  loadEpub(epubPath: string): Observable<void> {
    if (!this.view) {
      return throwError(() => new Error('View not created'));
    }

    const view = this.view;
    return timer(100).pipe(
      switchMap(() => from(fetch(epubPath))),
      switchMap(response => {
        if (!response.ok) {
          throw new Error(`EPUB not found: ${response.status}`);
        }
        return from(response.blob());
      }),
      switchMap(blob => {
        const file = new File([blob], epubPath.split('/').pop() || 'book.epub', {
          type: 'application/epub+zip'
        });
        return from(view.open(file) as Promise<void>);
      }),
      map(() => undefined),
      catchError(err => throwError(() => err))
    );
  }

  loadEpubStreaming(bookId: number, bookType?: string): Observable<void> {
    if (!this.view) {
      return throwError(() => new Error('View not created'));
    }

    return this.epubStreamingService.getBookInfo(bookId, bookType).pipe(
      switchMap(bookInfo => from(this.openStreamingBook(bookId, bookInfo, bookType))),
      map(() => undefined),
      catchError(err => throwError(() => err))
    );
  }

  private async openStreamingBook(bookId: number, bookInfo: EpubBookInfo, bookType?: string): Promise<void> {
    const makeStreamingBook = (window as StreamingBookFactoryWindow).makeStreamingBook;
    if (!makeStreamingBook) {
      throw new Error('makeStreamingBook not available - Foliate script may not be loaded');
    }
    const baseUrl = this.epubStreamingService.getBaseUrl();
    const authToken = this.epubStreamingService.getAuthToken();
    const book = await makeStreamingBook(bookId, baseUrl, bookInfo, authToken, bookType);
    const view = this.view;
    if (!view) {
      throw new Error('View not created');
    }
    await view.open(book);
  }

  destroy(): void {
    this.eventService.destroy();
    this.view?.remove();
    this.view = null;
  }

  goTo(target?: string | number | null): Observable<void> {
    const resolvedTarget = target ?? 0;
    if (!this.view) {
      return of(undefined);
    }
    const view = this.view;
    return defer(() =>
      from(view.goTo(resolvedTarget) as Promise<void>)
    ).pipe(
      map(() => undefined)
    );
  }

  goToSection(index: number): Observable<void> {
    return this.goTo(index);
  }

  goToFraction(fraction: number): Observable<void> {
    if (!this.view) {
      return of(undefined);
    }
    const view = this.view;
    return defer(() => from(view.goToFraction(fraction) as Promise<void>)).pipe(
      map(() => undefined)
    );
  }

  captureMiddlePageSnippet(): { before: string; highlight: string; after: string } | null {
    const renderer = this.view?.renderer;
    const contents = renderer?.getContents();
    if (!contents || contents.length === 0) return null;

    const candidates = contents
      .map(({doc}) => {
        const win = doc.defaultView;
        if (!win) return null;
        return {doc, win: win as Window & typeof globalThis};
      })
      .filter(Boolean) as {doc: Document; win: Window & typeof globalThis}[];

    for (const {doc, win} of candidates) {
      const snippet = this.snippetFromIframe(doc, win);
      if (snippet) return snippet;
    }
    return null;
  }

  private snippetFromIframe(doc: Document, win: Window): { before: string; highlight: string; after: string } | null {
    const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'LINK', 'META']);
    const BLOCK_TAGS = new Set(['P', 'LI', 'BLOCKQUOTE', 'DD', 'DT', 'FIGCAPTION', 'PRE']);
    const HEADING_TAGS = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);

    const proseTextOf = (el: Element): string => {
      const walker = doc.createTreeWalker(el, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
        acceptNode(n) {
          if (n.nodeType === 1) {
            const tag = (n as Element).tagName;
            if (SKIP_TAGS.has(tag)) return NodeFilter.FILTER_REJECT;
            return NodeFilter.FILTER_SKIP;
          }
          return NodeFilter.FILTER_ACCEPT;
        }
      });
      const parts: string[] = [];
      let node: Node | null;
      while ((node = walker.nextNode())) {
        parts.push(node.nodeValue ?? '');
      }
      return parts.join(' ').replace(/\s+/g, ' ').trim();
    };
    const isHeading = (el: Element | null): boolean => !!el && HEADING_TAGS.has(el.tagName);
    const isProseBlock = (el: Element | null): boolean => {
      if (!el || SKIP_TAGS.has(el.tagName) || isHeading(el)) return false;
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) return false;
      if (r.bottom < 0 || r.top > win.innerHeight) return false;
      const text = proseTextOf(el);
      return text.length >= 40;
    };

    const cy = win.innerHeight / 2;
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT, {
      acceptNode(n) {
        const el = n as Element;
        if (SKIP_TAGS.has(el.tagName)) return NodeFilter.FILTER_REJECT;
        if (BLOCK_TAGS.has(el.tagName)) return NodeFilter.FILTER_ACCEPT;
        if (el.tagName === 'DIV') {
          const display = win.getComputedStyle(el).display;
          if (display === 'block' || display === 'list-item') return NodeFilter.FILTER_ACCEPT;
        }
        return NodeFilter.FILTER_SKIP;
      }
    });

    let best: Element | null = null;
    let bestDistance = Infinity;
    let cursor: Node | null;
    while ((cursor = walker.nextNode())) {
      const el = cursor as Element;
      if (!isProseBlock(el)) continue;
      const r = el.getBoundingClientRect();
      const elCenter = (r.top + r.bottom) / 2;
      const distance = Math.abs(elCenter - cy);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = el;
      }
    }
    if (!best) return null;

    const full = proseTextOf(best);
    if (full.length < 20) return null;
    const sentenceMatch = full.match(/^.{20,200}?[.!?](?=\s|$)/);
    const highlight = sentenceMatch ? sentenceMatch[0].trim() : full.slice(0, Math.min(160, full.length));
    const idx = full.indexOf(highlight);
    const before = idx > 0 ? full.slice(Math.max(0, idx - 30), idx).trim() : '';
    const afterStart = idx + highlight.length;
    const after = afterStart < full.length
      ? full.slice(afterStart, Math.min(full.length, afterStart + 30)).trim()
      : '';
    return {before, highlight, after};
  }

  async findCfiBySnippet(href: string, snippet: string): Promise<string | null> {
    const view = this.view;
    if (!view || !view.search || !view.resolveNavigation) return null;

    const resolved = view.resolveNavigation(href);
    const index = resolved?.index;
    if (typeof index !== 'number') return null;

    try {
      for await (const result of view.search({query: snippet, index})) {
        if (result === 'done') break;
        if (typeof result !== 'object') continue;
        const directCfi = (result as {cfi?: string}).cfi;
        if (typeof directCfi === 'string' && directCfi) {
          return directCfi;
        }
        const subitems = (result as {subitems?: {cfi?: string}[]}).subitems;
        const firstSub = subitems?.[0]?.cfi;
        if (typeof firstSub === 'string' && firstSub) {
          return firstSub;
        }
      }
    } catch {
      return null;
    } finally {
      view.clearSearch?.();
    }
    return null;
  }

  prev(): void {
    this.view?.prev();
  }

  next(): void {
    this.view?.next();
  }

  getRenderer(): FoliateRenderer | null {
    return this.view?.renderer ?? null;
  }

  getSelection(): TextSelection | null {
    const renderer = this.getRenderer();
    if (!renderer) return null;

    const contents = renderer.getContents();
    if (!contents || contents.length === 0) return null;

    const {index, doc} = contents[0];
    if (!doc) return null;

    const selection = doc.defaultView?.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) return null;

    const range = selection.getRangeAt(0);
    const text = range.toString().trim();
    if (!text) return null;

    const cfi = this.view?.getCFI(index, range);
    if (!cfi) return null;

    return {text, cfi, range, index};
  }

  clearSelection(): void {
    this.view?.deselect();
  }

  addAnnotation(annotation: Annotation): Observable<{index: number; label: string} | undefined> {
    return this.annotationService.addAnnotation(this.view, annotation);
  }

  deleteAnnotation(cfi: string): Observable<void> {
    return this.annotationService.deleteAnnotation(this.view, cfi);
  }

  addAnnotations(annotations: Annotation[]): void {
    this.annotationService.addAnnotations(this.view, annotations);
  }

  updateHeadersAndFooters(chapterName: string, pageInfo?: PageInfo, theme?: ThemeInfo, timeRemainingLabel?: string): void {
    const renderer = this.getRenderer();
    PageDecorator.updateHeadersAndFooters(renderer, chapterName, pageInfo, theme, timeRemainingLabel);
  }

  getChapters(): TocItem[] {
    if (!this.view?.book?.toc) return [];

    const mapToc = (items: FoliateTocItem[]): TocItem[] =>
      items.map(item => ({
        label: item.label,
        href: item.href,
        subitems: item.subitems?.length ? mapToc(item.subitems) : undefined
      }));

    return mapToc(this.view.book.toc);
  }

  getSectionFractions(): number[] {
    if (!this.view?.getSectionFractions) return [];
    return this.view.getSectionFractions();
  }

  getMetadata(): Observable<BookMetadata> {
    if (!this.view?.book?.metadata) {
      return of({});
    }

    const {metadata} = this.view.book;

    return this.getCoverUrl().pipe(
      map(coverUrl => ({
        title: metadata.title,
        authors: metadata.authors,
        language: metadata.language,
        publisher: metadata.publisher,
        description: metadata.description,
        identifier: metadata.identifier,
        coverUrl,
        ...metadata
      }))
    );
  }

  getCover(): Observable<Blob | null> {
    if (!this.view?.book?.getCover) {
      return of(null);
    }
    const book = this.view.book;
    return defer(() => {
      const coverPromise = book.getCover?.();
      return coverPromise ? from(coverPromise as Promise<Blob | null>) : of(null);
    });
  }

  getCoverUrl(): Observable<string | null> {
    return this.getCover().pipe(
      map(blob => blob ? URL.createObjectURL(blob) : null)
    );
  }

  async* search(opts: { query: string; matchCase?: boolean; matchWholeWords?: boolean }): AsyncGenerator<FoliateSearchResult> {
    const search = this.view?.search;
    if (!search) return;
    yield* search.call(this.view, opts);
  }

  clearSearch(): void {
    this.view?.clearSearch?.();
  }
}
