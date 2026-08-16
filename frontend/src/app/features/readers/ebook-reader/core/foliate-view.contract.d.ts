import type {EpubBookInfo} from './epub-streaming.service';

export interface FoliateTocItem {
  label: string;
  href: string;
  subitems?: FoliateTocItem[];
}

export interface FoliateBookMetadata {
  coverUrl?: string | null;
  [key: string]: unknown;
}

export interface FoliateRenderer {
  heads?: HTMLElement[];
  feet?: HTMLElement[];
  getContents(): {index: number; doc: Document}[];
  setAttribute(name: string, value: string | number): void;
  removeAttribute(name: string): void;
  setStyles?(css: string): void;
}

interface FoliateBook {
  toc?: FoliateTocItem[];
  metadata?: FoliateBookMetadata;
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

export type FoliateSearchResult = FoliateSearchProgress | FoliateSearchSectionResult | 'done';

export interface FoliateLoadEventDetail {
  doc?: Document;
  index?: number;
}

interface FoliateRelocateEventItem {
  href?: string;
  label?: string;
}

export interface FoliateRelocateEventDetail {
  cfi?: string | null;
  fraction?: number;
  tocItem?: FoliateRelocateEventItem;
  pageItem?: FoliateRelocateEventItem;
  section?: {current: number; total: number};
  time?: {section?: number; total?: number};
}

export interface FoliateDrawAnnotationEventDetail {
  draw: (
    overlayer: (rects: DOMRectList, options: {color?: string}) => SVGElement,
    options: {color: string},
  ) => void;
  annotation: {value: string};
  doc: Document;
  range: Range;
}

interface FoliateViewEventMap extends HTMLElementEventMap {
  load: CustomEvent<FoliateLoadEventDetail>;
  relocate: CustomEvent<FoliateRelocateEventDetail>;
  error: CustomEvent<unknown>;
  'draw-annotation': CustomEvent<FoliateDrawAnnotationEventDetail>;
  'show-annotation': CustomEvent<unknown>;
}

export interface FoliateEventTarget extends HTMLElement {
  addAnnotation(annotation: {value: string}): Promise<{index: number; label: string} | undefined>;
  addEventListener<K extends keyof FoliateViewEventMap>(
    type: K,
    listener: (this: FoliateEventTarget, event: FoliateViewEventMap[K]) => void,
    options?: boolean | AddEventListenerOptions,
  ): void;
  addEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject | null,
    options?: boolean | AddEventListenerOptions,
  ): void;
}

export interface FoliateViewElement extends FoliateEventTarget {
  renderer?: FoliateRenderer | null;
  book?: FoliateBook;
  open(target: File | object): Promise<void>;
  goTo(target: string | number): Promise<void>;
  goToFraction(fraction: number): Promise<void>;
  prev(): Promise<void>;
  next(): Promise<void>;
  getCFI(index: number, range: Range): string | null;
  deselect(): void;
  deleteAnnotation(annotation: {value: string}): Promise<void>;
  showAnnotation(annotation: {value: string}): Promise<void>;
  getSectionFractions?(): number[];
  search?(options: {
    query: string;
    matchCase?: boolean;
    matchWholeWords?: boolean;
  }): AsyncGenerator<FoliateSearchResult>;
  clearSearch?(): void;
}

declare global {
  interface HTMLElementTagNameMap {
    'foliate-view': FoliateViewElement;
  }

  interface Window {
    makeStreamingBook?: (
      bookId: number,
      baseUrl: string,
      bookInfo: EpubBookInfo,
      authToken: string | null,
      bookType?: string,
    ) => Promise<object>;
  }
}
