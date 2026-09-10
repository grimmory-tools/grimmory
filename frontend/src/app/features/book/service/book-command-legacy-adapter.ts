import {
  CreateMutationOptions,
  DefaultError,
  QueryClient,
  WithRequired,
} from '@tanstack/angular-query-experimental';

import {
  DeleteBooksResult,
  ResetBookProgressResult,
  SetAllBookMetadataLocksResult,
  SetBookReadStatusResult,
} from '../data/book-command.models';
import {UpdateBookShelfMembershipResult} from '../data/book-shelf-command.models';
import {BookShelf} from '../data/book-response.models';
import {Book, ReadStatus} from '../model/book.model';
import {Shelf} from '../model/shelf.model';
import {
  invalidateAllLegacyBooks,
  patchListOnlyBookFields,
  patchListOnlyBooksWith,
  removeListOnlyBooks,
} from './legacy-book-cache';
import {SHELVES_QUERY_KEY} from './shelf-query-keys';

type MutationOptionsWithKey<TData, TError, TVariables, TOnMutateResult> =
  WithRequired<CreateMutationOptions<TData, TError, TVariables, TOnMutateResult>, 'mutationKey'>;

type LegacyBookCachePatch<TData> = (
  client: QueryClient,
  data: TData,
) => Promise<void> | void;

export function withLegacyBookCache<
  TData = unknown,
  TError = DefaultError,
  TVariables = void,
  TOnMutateResult = unknown,
>(
  options: MutationOptionsWithKey<TData, TError, TVariables, TOnMutateResult>,
  patch: LegacyBookCachePatch<TData>,
): MutationOptionsWithKey<TData, TError, TVariables, TOnMutateResult> {
  const originalOnSuccess = options.onSuccess;
  const originalOnError = options.onError;

  return {
    ...options,
    onSuccess: async (data, variables, onMutateResult, context) => {
      await Promise.all([
        patch(context.client, data),
        originalOnSuccess?.(data, variables, onMutateResult, context),
      ]);
    },
    onError: async (error, variables, onMutateResult, context) => {
      await Promise.all([
        invalidateAllLegacyBooks(context.client),
        originalOnError?.(error, variables, onMutateResult, context),
      ]);
    },
  };
}

export const legacyBookCachePatches = {
  readStatus: (
    client: QueryClient,
    results: readonly SetBookReadStatusResult[],
  ): Promise<void> => patchListOnlyBookFields(client, results.map(result => ({
    bookId: result.bookId,
    fields: {
      readStatus: ReadStatus[result.readStatus],
      readStatusModifiedTime: result.readStatusModifiedTime ?? undefined,
      dateFinished: result.dateFinished ?? undefined,
    },
  }))),
  deleteBooks: (
    client: QueryClient,
    result: DeleteBooksResult,
  ): Promise<void> => removeListOnlyBooks(client, result.removedBookIds),
  resetProgress: (
    client: QueryClient,
    results: readonly ResetBookProgressResult[],
  ): Promise<void> => patchListOnlyBookFields(client, results.map(result => ({
    bookId: result.bookId,
    fields: resetProgressPatchFields(result),
  }))),
  metadataAllLocks: (
    client: QueryClient,
    results: readonly SetAllBookMetadataLocksResult[],
  ): Promise<void> => patchListOnlyBooksWith(client, results.map(result => ({
    bookId: result.bookId,
    updater: book => {
      if (!book.metadata) {
        return book;
      }
      return {
        ...book,
        metadata: {...book.metadata, ...result.metadataLocks},
      };
    },
  }))),
  shelfMembership: (
    client: QueryClient,
    result: UpdateBookShelfMembershipResult,
  ): Promise<void> => patchLegacyShelfMembership(client, result),
} as const;

function resetProgressPatchFields(result: ResetBookProgressResult): Partial<Book> {
  if (result.source !== 'GRIMMORY') {
    return clearedProgressFields(result.source);
  }
  return {
    ...clearedProgressFields(result.source),
    readStatus: undefined,
    readStatusModifiedTime: result.readStatusModifiedTime ?? undefined,
    dateFinished: undefined,
    lastReadTime: undefined,
  };
}

function clearedProgressFields(source: ResetBookProgressResult['source']): Partial<Book> {
  if (source === 'KOREADER') {
    return {koreaderProgress: undefined};
  }
  if (source === 'KOBO') {
    return {koboProgress: undefined};
  }
  return {
    epubProgress: undefined,
    pdfProgress: undefined,
    cbxProgress: undefined,
    audiobookProgress: undefined,
  };
}

async function patchLegacyShelfMembership(
  client: QueryClient,
  result: UpdateBookShelfMembershipResult,
): Promise<void> {
  await Promise.all([
    patchListOnlyBooksWith(client, result.updatedBookShelves.map(update => ({
      bookId: update.bookId,
      updater: book => ({
        ...book,
        shelves: update.shelves.map(toLegacyShelf),
      }),
    }))),
    client.invalidateQueries({queryKey: SHELVES_QUERY_KEY, exact: true}),
  ]);
}

function toLegacyShelf(shelf: BookShelf): Shelf {
  return {
    id: shelf.id,
    name: shelf.name,
    icon: shelf.icon,
    iconType: shelf.iconType as Shelf['iconType'],
    userId: shelf.userId,
    publicShelf: shelf.publicShelf,
    bookCount: shelf.bookCount,
  };
}
