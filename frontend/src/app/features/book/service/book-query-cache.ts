import {InfiniteData, QueryClient} from '@tanstack/angular-query-experimental';

import {Book, BookMetadata} from '../model/book.model';
import {AppBookFilters, AppBookSort, AppBookSummary, AppPageResponse} from '../model/app-book.model';
import {BOOKS_QUERY_KEY, bookDetailQueryPrefix, bookRecommendationsQueryPrefix} from './book-query-keys';

const APP_BOOKS_QUERY_PREFIX = ['app-books'] as const;
const APP_FILTER_OPTIONS_QUERY_PREFIX = ['app-filter-options'] as const;
const APP_CATALOG_SUMMARY_QUERY_KEY = ['app-catalog-summary'] as const;
const APP_BOOK_SUMMARIES_QUERY_PREFIX = ['app-book-summaries'] as const;

// Derived aggregates (facet counts, catalog totals, summary lists) that depend on book
// fields. Server-side these are expensive aggregations over the whole catalog, and book
// events arrive in bursts during imports — while the filter panel is open each invalidation
// triggers an immediate refetch. Trailing-debounce so a burst costs one refetch, not one
// per event; the max-wait keeps counts moving under a continuous event stream.
const DERIVED_INVALIDATION_DEBOUNCE_MS = 3_000;
const DERIVED_INVALIDATION_MAX_WAIT_MS = 15_000;

interface PendingDerivedInvalidation {
  timer: ReturnType<typeof setTimeout>;
  firstRequestedAt: number;
}

const pendingDerivedInvalidations = new WeakMap<QueryClient, PendingDerivedInvalidation>();

export function invalidateAppDerivedQueries(queryClient: QueryClient): void {
  const pending = pendingDerivedInvalidations.get(queryClient);
  const firstRequestedAt = pending?.firstRequestedAt ?? Date.now();
  if (pending) {
    clearTimeout(pending.timer);
  }
  const delay = Math.min(
    DERIVED_INVALIDATION_DEBOUNCE_MS,
    Math.max(0, firstRequestedAt + DERIVED_INVALIDATION_MAX_WAIT_MS - Date.now())
  );
  const timer = setTimeout(() => {
    pendingDerivedInvalidations.delete(queryClient);
    queryClient.invalidateQueries({queryKey: APP_FILTER_OPTIONS_QUERY_PREFIX});
    queryClient.invalidateQueries({queryKey: APP_CATALOG_SUMMARY_QUERY_KEY});
    queryClient.invalidateQueries({queryKey: APP_BOOK_SUMMARIES_QUERY_PREFIX});
  }, delay);
  pendingDerivedInvalidations.set(queryClient, {timer, firstRequestedAt});
}

export function invalidateAppBooksQueries(queryClient: QueryClient): void {
  queryClient.invalidateQueries({queryKey: APP_BOOKS_QUERY_PREFIX});
  invalidateAppDerivedQueries(queryClient);
}

export function invalidateLegacyBooksQuery(queryClient: QueryClient): void {
  queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY, exact: true});
}

// --- Full invalidation (refetches from server) ---

export function invalidateBooksQuery(queryClient: QueryClient): void {
  invalidateLegacyBooksQuery(queryClient);
  invalidateAppBooksQueries(queryClient);
}

export function invalidateBookQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  invalidateBooksQuery(queryClient);
  invalidateBookDetailQueries(queryClient, bookIds);
}

export function invalidateBookDetailQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  for (const bookId of new Set(bookIds)) {
    queryClient.invalidateQueries({queryKey: bookDetailQueryPrefix(bookId)});
  }
}

export function removeBookQueries(queryClient: QueryClient, bookIds: Iterable<number>): void {
  for (const bookId of new Set(bookIds)) {
    queryClient.removeQueries({queryKey: bookDetailQueryPrefix(bookId)});
    queryClient.removeQueries({queryKey: bookRecommendationsQueryPrefix(bookId)});
  }
}

export function removeBooksFromCache(queryClient: QueryClient, bookIds: Iterable<number>): void {
  const removedIds = new Set(bookIds);
  if (removedIds.size === 0) {
    return;
  }

  removeAppBooksFromCache(queryClient, removedIds);
  removeBookQueries(queryClient, removedIds);
  invalidateLegacyBooksQuery(queryClient);
  invalidateAppBooksQueries(queryClient);
}

// --- Surgical patches (updates cache directly, no list refetch) ---

export function addBookToCache(queryClient: QueryClient, _book: Book): void {
  invalidateLegacyBooksQuery(queryClient);
}

export function patchBooksInCache(queryClient: QueryClient, updatedBooks: Book[]): void {
  patchAppBooksFromBooksInCache(queryClient, updatedBooks);
  invalidateLegacyBooksQuery(queryClient);
  invalidateBookDetailQueries(queryClient, updatedBooks.map(b => b.id));
  invalidateAppBooksQueries(queryClient);
}

export function patchBookMetadataInCache(queryClient: QueryClient, bookId: number, metadata: BookMetadata): void {
  patchAppBooksSummaryFieldsInCache(queryClient, [{bookId, patch: metadataToSummaryPatch(metadata)}]);
  invalidateLegacyBooksQuery(queryClient);
  invalidateBookDetailQueries(queryClient, [bookId]);
  invalidateAppBooksQueries(queryClient);
}

export function patchBookInCacheWith(queryClient: QueryClient, bookId: number, _updater: (book: Book) => Book): void {
  invalidateLegacyBooksQuery(queryClient);
  invalidateBookDetailQueries(queryClient, [bookId]);
  invalidateAppBooksQueries(queryClient);
}

export function patchBookFieldsInCache(queryClient: QueryClient, updates: {bookId: number; fields: Partial<Book>}[]): void {
  invalidateLegacyBooksQuery(queryClient);
  // Patch the visible rows in place for instant feedback (no multi-page refetch)...
  patchAppBooksFieldsInCache(queryClient, updates);
  invalidateBookDetailQueries(queryClient, updates.map(u => u.bookId));
  // ...then reconcile only those app-books views whose active filter or sort actually
  // depends on a changed field, so e.g. a status tab drops a book that no longer matches.
  // Views that don't filter/sort by the changed field keep the cheap in-place patch.
  const changedFields = withServerDerivedFields(new Set(updates.flatMap(u => Object.keys(u.fields))));
  queryClient.invalidateQueries({
    queryKey: APP_BOOKS_QUERY_PREFIX,
    predicate: query => appBooksViewDependsOn(query.queryKey, changedFields),
  });
  invalidateAppDerivedQueries(queryClient);
}

const PROGRESS_KEYS = ['epubProgress', 'pdfProgress', 'cbxProgress', 'audiobookProgress'] as const;

// Book fields mapped onto the AppBookSort.field token their value drives server-side.
const SORT_FIELD_BY_BOOK_FIELD: Record<string, string> = {
  readStatus: 'readStatus',
  personalRating: 'personalRating',
  metadataMatchScore: 'matchScore',
  lastReadTime: 'lastReadTime',
  dateFinished: 'dateFinished',
  // Every per-format progress field feeds the single readingProgress sort key.
  ...Object.fromEntries(PROGRESS_KEYS.map(key => [key, 'readingProgress'])),
};

// Saving progress makes the server recalculate readStatus and stamp dateFinished on completion
// (ReadingProgressService.calculateReadStatus). The client never sends those fields back, so a
// view filtered or sorted by them must reconcile off the progress write alone.
function withServerDerivedFields(changedFields: Set<string>): Set<string> {
  if (!PROGRESS_KEYS.some(key => changedFields.has(key))) return changedFields;
  return new Set([...changedFields, 'readStatus', 'dateFinished']);
}

// True when an app-books query's filter membership or sort order depends on one of the
// changed Book fields — i.e. patching in place is not enough and it must be refetched.
function appBooksViewDependsOn(queryKey: readonly unknown[], changedFields: Set<string>): boolean {
  const filters = queryKey[1] as AppBookFilters | undefined;
  const sort = queryKey[2] as AppBookSort | undefined;

  if (filters) {
    // A magic shelf's rule is evaluated server-side and can match on any field, so membership after
    // a change is not decidable here. A plain shelfId holds explicit book ids and is unaffected.
    if (filters.magicShelfId != null) return true;
    if (changedFields.has('readStatus') && filters.status?.length) return true;
    if (changedFields.has('personalRating') &&
      (filters.personalRating?.length || filters.minRating != null || filters.maxRating != null)) return true;
    if (changedFields.has('metadataMatchScore') && filters.matchScore?.length) return true;
  }

  if (sort?.field) {
    // AppBookSort.field may be a comma-joined multi-sort with a leading '-' for descending.
    const sortTokens = sort.field.split(',').map(token => token.replace(/^-/, ''));
    for (const field of changedFields) {
      const token = SORT_FIELD_BY_BOOK_FIELD[field];
      if (token && sortTokens.includes(token)) return true;
    }
  }
  return false;
}

// Project the subset of Book fields that also live on AppBookSummary so the paginated
// app-books cache can be patched in place instead of triggering a full multi-page refetch.
function bookFieldsToSummaryPatch(fields: Partial<Book>): Partial<AppBookSummary> {
  const patch: Partial<AppBookSummary> = {};
  if ('readStatus' in fields) patch.readStatus = fields.readStatus ?? null;
  if ('personalRating' in fields) patch.personalRating = fields.personalRating ?? null;
  if ('lastReadTime' in fields) patch.lastReadTime = fields.lastReadTime ?? null;
  if ('metadataMatchScore' in fields) patch.metadataMatchScore = fields.metadataMatchScore ?? null;

  // A file-progress update (save or reset) maps to the summary's single readProgress percent.
  for (const key of PROGRESS_KEYS) {
    if (key in fields) {
      const progress = fields[key] as {percentage?: number} | undefined;
      patch.readProgress = progress?.percentage ?? null;
    }
  }
  return patch;
}

export function patchAppBooksFieldsInCache(queryClient: QueryClient, updates: {bookId: number; fields: Partial<Book>}[]): void {
  if (updates.length === 0) return;
  patchAppBooksSummaryFieldsInCache(
    queryClient,
    updates.map(u => ({bookId: u.bookId, patch: bookFieldsToSummaryPatch(u.fields)}))
  );
}

function patchAppBooksFromBooksInCache(queryClient: QueryClient, books: Book[]): void {
  patchAppBooksSummaryFieldsInCache(
    queryClient,
    books.map(book => ({bookId: book.id, patch: bookToSummaryPatch(book)}))
  );
}

function patchAppBooksSummaryFieldsInCache(
  queryClient: QueryClient,
  updates: {bookId: number; patch: Partial<AppBookSummary>}[]
): void {
  const patchMap = new Map<number, Partial<AppBookSummary>>();
  for (const update of updates) {
    if (Object.keys(update.patch).length > 0) patchMap.set(update.bookId, update.patch);
  }
  // Nothing summary-visible changed (e.g. a dateFinished-only update) — skip the rewrite.
  if (patchMap.size === 0) return;

  queryClient.setQueriesData<InfiniteData<AppPageResponse<AppBookSummary>>>(
    {queryKey: APP_BOOKS_QUERY_PREFIX},
    current => {
      if (!current) return current;
      return {
        ...current,
        pages: current.pages.map(page => ({
          ...page,
          content: page.content.map(summary => {
            const patch = patchMap.get(summary.id);
            return patch ? {...summary, ...patch} : summary;
          }),
        })),
      };
    }
  );
}

function removeAppBooksFromCache(queryClient: QueryClient, bookIds: Set<number>): void {
  queryClient.setQueriesData<InfiniteData<AppPageResponse<AppBookSummary>>>(
    {queryKey: APP_BOOKS_QUERY_PREFIX},
    current => {
      if (!current) return current;
      return {
        ...current,
        pages: current.pages.map(page => {
          const nextContent = page.content.filter(summary => !bookIds.has(summary.id));
          const removedCount = page.content.length - nextContent.length;
          if (removedCount === 0) return page;
          const totalElements = Math.max(0, page.totalElements - removedCount);
          const totalPages = page.size > 0 ? Math.ceil(totalElements / page.size) : 0;
          return {
            ...page,
            content: nextContent,
            totalElements,
            totalPages,
            hasNext: page.page < totalPages - 1,
          };
        }),
      };
    }
  );
}

export function patchAppBooksCoverInCache(
  queryClient: QueryClient,
  patches: {id: number; coverUpdatedOn?: string | null; audiobookCoverUpdatedOn?: string | null}[]
): void {
  if (patches.length === 0) return;
  const patchMap = new Map(patches.map(p => [p.id, p]));

  queryClient.setQueriesData<InfiniteData<AppPageResponse<AppBookSummary>>>(
    {queryKey: APP_BOOKS_QUERY_PREFIX},
    (current) => {
      if (!current) return current;
      return {
        ...current,
        pages: current.pages.map(page => ({
          ...page,
          content: page.content.map(summary => {
            const patch = patchMap.get(summary.id);
            if (!patch) return summary;
            return {
              ...summary,
              ...('coverUpdatedOn' in patch ? {coverUpdatedOn: patch.coverUpdatedOn ?? null} : {}),
              ...('audiobookCoverUpdatedOn' in patch ? {audiobookCoverUpdatedOn: patch.audiobookCoverUpdatedOn ?? null} : {}),
            };
          })
        }))
      };
    }
  );
}

export function patchAppBooksMetadataLockInCache(queryClient: QueryClient, bookId: number, allMetadataLocked: boolean): void {
  queryClient.setQueriesData<InfiniteData<AppPageResponse<AppBookSummary>>>(
    {queryKey: APP_BOOKS_QUERY_PREFIX},
    current => {
      if (!current) return current;

      return {
        ...current,
        pages: current.pages.map(page => ({
          ...page,
          content: page.content.map(summary =>
            summary.id === bookId ? {...summary, allMetadataLocked} : summary
          ),
        })),
      };
    }
  );
  invalidateLegacyBooksQuery(queryClient);
}

function metadataToSummaryPatch(metadata: BookMetadata): Partial<AppBookSummary> {
  return {
    title: metadata.title ?? '',
    authors: metadata.authors ?? [],
    publisher: metadata.publisher ?? null,
    seriesName: metadata.seriesName ?? null,
    seriesNumber: metadata.seriesNumber ?? null,
    categories: metadata.categories ?? null,
    tags: metadata.tags ?? null,
    moods: metadata.moods ?? null,
    language: metadata.language ?? null,
    narrator: metadata.narrator ?? metadata.audiobookMetadata?.narrator ?? null,
    isbn13: metadata.isbn13 ?? null,
    isbn10: metadata.isbn10 ?? null,
    coverUpdatedOn: metadata.coverUpdatedOn ?? null,
    audiobookCoverUpdatedOn: metadata.audiobookCoverUpdatedOn ?? null,
    publishedDate: metadata.publishedDate ?? null,
    pageCount: metadata.pageCount ?? null,
    ageRating: metadata.ageRating ?? null,
    contentRating: metadata.contentRating ?? null,
    amazonRating: metadata.amazonRating ?? null,
    amazonReviewCount: metadata.amazonReviewCount ?? null,
    goodreadsRating: metadata.goodreadsRating ?? null,
    goodreadsReviewCount: metadata.goodreadsReviewCount ?? null,
    hardcoverRating: metadata.hardcoverRating ?? null,
    hardcoverReviewCount: metadata.hardcoverReviewCount ?? null,
    ranobedbRating: metadata.ranobedbRating ?? null,
    lubimyczytacRating: metadata.lubimyczytacRating ?? null,
    audibleRating: metadata.audibleRating ?? null,
    audibleReviewCount: metadata.audibleReviewCount ?? null,
    allMetadataLocked: metadata.allMetadataLocked ?? null,
  };
}

function bookToSummaryPatch(book: Book): Partial<AppBookSummary> {
  const patch: Partial<AppBookSummary> = {};
  if (book.libraryId != null) patch.libraryId = book.libraryId;
  if (book.addedOn !== undefined) patch.addedOn = book.addedOn ?? null;
  if (book.lastReadTime !== undefined) patch.lastReadTime = book.lastReadTime ?? null;
  if (book.readStatus !== undefined) patch.readStatus = book.readStatus ?? null;
  if (book.personalRating !== undefined) patch.personalRating = book.personalRating ?? null;
  if (book.metadataMatchScore !== undefined) patch.metadataMatchScore = book.metadataMatchScore ?? null;
  if (book.fileSizeKb !== undefined) patch.fileSizeKb = book.fileSizeKb ?? null;
  if (book.isPhysical !== undefined) patch.isPhysical = book.isPhysical ?? null;
  if (book.primaryFile !== undefined) {
    patch.primaryFileId = book.primaryFile?.id ?? null;
    patch.primaryFileType = book.primaryFile?.bookType ?? null;
    patch.primaryFileDocumentParseStatus = book.primaryFile?.documentParseStatus ?? null;
    patch.primaryFileName = book.primaryFile?.fileName ?? null;
  }
  for (const key of PROGRESS_KEYS) {
    if (key in book) {
      const progress = book[key] as {percentage?: number} | undefined;
      patch.readProgress = progress?.percentage ?? null;
    }
  }
  return book.metadata ? {...patch, ...metadataToSummaryPatch(book.metadata)} : patch;
}
