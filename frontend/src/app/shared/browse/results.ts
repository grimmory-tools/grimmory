import {computed, effect, signal, untracked, type EffectCleanupRegisterFn, type Signal} from '@angular/core';

import {findBrowsePageLink, flattenBrowsePages, type BrowsePage} from '../../core/data/browse.models';

const PREFETCH_THRESHOLD = 12;
const DEFAULT_COVER_WAIT_MS = 400;
const DEFAULT_STALE_RESULTS_MS = 600;

export type BrowseStatus = 'pending' | 'error' | 'success';

interface BrowsePageData<T> {
  pages: BrowsePage<T>[];
}

export interface BrowseRenderedRange {
  start: number;
  end: number;
}

export function sameRenderedRange(a: BrowseRenderedRange | null, b: BrowseRenderedRange | null): boolean {
  return a === b || (a !== null && b !== null && a.start === b.start && a.end === b.end);
}

export interface BrowseResultsQuery<T extends {id: number}> {
  data: () => BrowsePageData<T> | undefined;
  isSuccess: () => boolean;
  isError: () => boolean;
  isFetchNextPageError: () => boolean;
  hasNextPage: () => boolean;
  isFetching: () => boolean;
  fetchNextPage: (options?: {cancelRefetch?: boolean}) => Promise<unknown>;
  refetch: () => Promise<unknown>;
}

export interface BrowseResultsDeps<T extends {id: number}> {
  query: BrowseResultsQuery<T>;
  listKey: Signal<string>;
  artworkUrls: (items: readonly T[], lastVisibleIndex: number) => readonly string[];
  scrollToTop: () => void;
  coverWaitMs?: number;
  staleResultsMs?: number;
}

export interface BrowseResults<T extends {id: number}> {
  readonly items: Signal<readonly T[]>;
  readonly total: Signal<number | null>;
  readonly status: Signal<BrowseStatus>;
  readonly nextPageError: Signal<boolean>;
  readonly shownHasNextPage: Signal<boolean>;
  readonly showingPreviousResults: Signal<boolean>;
  onRenderedRange(range: BrowseRenderedRange): void;
  retryInitial(): void;
  retryNextPage(): void;
}

export function createBrowseResults<T extends {id: number}>(
  deps: BrowseResultsDeps<T>,
): BrowseResults<T> {
  const coverWaitMs = deps.coverWaitMs ?? DEFAULT_COVER_WAIT_MS;
  const staleResultsMs = deps.staleResultsMs ?? DEFAULT_STALE_RESULTS_MS;

  const lastVisibleEnd = signal(0);
  const shown = signal<BrowsePageData<T> | undefined>(undefined);
  let shownListKey: string | null = null;

  const showingPreviousResults = computed(() => shown() !== undefined && shown() !== deps.query.data());

  const items = computed<readonly T[]>(() => flattenBrowsePages(shown()));
  const total = computed<number | null>(() => shown()?.pages[0]?.page.totalElements ?? null);
  const status = computed<BrowseStatus>(() => {
    if (items().length > 0 || (shown() !== undefined && deps.query.isSuccess())) {
      return 'success';
    }
    return deps.query.isError() ? 'error' : 'pending';
  });
  const nextPageError = computed(() => items().length > 0 && deps.query.isFetchNextPageError());
  const shownHasNextPage = computed(() => {
    const lastPage = shown()?.pages.at(-1);
    return lastPage !== undefined && findBrowsePageLink(lastPage, 'next') !== undefined;
  });

  function show(listKey: string, data: BrowsePageData<T>): void {
    if (shownListKey !== null && shownListKey !== listKey) {
      deps.scrollToTop();
    }
    shownListKey = listKey;
    shown.set(data);
  }

  function showAfterCovers(listKey: string, data: BrowsePageData<T>, onCleanup: EffectCleanupRegisterFn): void {
    const urls = deps.artworkUrls(flattenBrowsePages(data), lastVisibleEnd());
    if (urls.length === 0) {
      show(listKey, data);
      return;
    }
    let done = false;
    const finish = (): void => {
      if (done) {
        return;
      }
      done = true;
      clearTimeout(timer);
      show(listKey, data);
    };
    const timer = setTimeout(finish, coverWaitMs);
    void Promise.all(urls.map(preloadImage)).then(finish);
    onCleanup(() => {
      done = true;
      clearTimeout(timer);
    });
  }

  effect(onCleanup => {
    const listKey = deps.listKey();
    const data = deps.query.data();
    untracked(() => {
      if (data === undefined) {
        if (shown() !== undefined && shownListKey !== listKey) {
          const timer = setTimeout(() => shown.set(undefined), staleResultsMs);
          onCleanup(() => clearTimeout(timer));
        }
      } else if (shownListKey === listKey || shown() === undefined) {
        show(listKey, data);
      } else {
        showAfterCovers(listKey, data, onCleanup);
      }
    });
  });

  function onRenderedRange(range: BrowseRenderedRange): void {
    lastVisibleEnd.set(range.end);
    if (
      range.end >= items().length - PREFETCH_THRESHOLD &&
      deps.query.hasNextPage() &&
      !deps.query.isFetching()
    ) {
      void deps.query.fetchNextPage({cancelRefetch: false});
    }
  }

  return {
    items,
    total,
    status,
    nextPageError,
    shownHasNextPage,
    showingPreviousResults,
    onRenderedRange,
    retryInitial: () => {
      void deps.query.refetch();
    },
    retryNextPage: () => {
      void deps.query.fetchNextPage();
    },
  };
}

function preloadImage(url: string): Promise<void> {
  return new Promise(resolve => {
    const image = new Image();
    image.onload = () => resolve();
    image.onerror = () => resolve();
    image.src = url;
    if (image.complete) {
      resolve();
    }
  });
}
