import {
  CreateMutationOptions,
  DefaultError,
  QueryClient,
  WithRequired,
} from '@tanstack/angular-query-experimental';

import {
  AttachBookFilesResult,
  AttachBookFilesVariables,
  BookProgressSource,
  DeleteAdditionalFileVariables,
  DeleteBooksResult,
  ResetBookProgressResult,
  ResetBookProgressVariables,
  SetBookReadStatusResult,
} from '../data/book-command.models';
import {BookDetail, BookDetailMetadata, BookFileResponse} from '../data/book-response.models';
import {AdditionalFile, Book, BOOK_TYPES, ReadStatus} from '../model/book.model';
import type {Shelf} from '../../shelf/data/shelf-query.models';
import {
  invalidateAllLegacyBooks,
  patchListOnlyBookFields,
  patchListOnlyBooksWith,
  patchAttachedBookFilesInLegacyCache,
  removeListOnlyBooks,
} from './legacy-book-cache';
import {SHELVES_QUERY_KEY} from './shelf-query-keys';

type MutationOptionsWithKey<TData, TError, TVariables, TOnMutateResult> =
  WithRequired<CreateMutationOptions<TData, TError, TVariables, TOnMutateResult>, 'mutationKey'>;

type LegacyBookCachePatch<TData, TVariables> = (
  client: QueryClient,
  data: TData,
  variables: TVariables,
) => Promise<void> | void;

export function withLegacyBookCache<
  TData = unknown,
  TError = DefaultError,
  TVariables = void,
  TOnMutateResult = unknown,
>(
  options: MutationOptionsWithKey<TData, TError, TVariables, TOnMutateResult>,
  patch: LegacyBookCachePatch<TData, TVariables>,
): MutationOptionsWithKey<TData, TError, TVariables, TOnMutateResult> {
  const originalOnSuccess = options.onSuccess;
  const originalOnError = options.onError;

  return {
    ...options,
    onSuccess: async (data, variables, onMutateResult, context) => {
      await Promise.all([
        patch(context.client, data, variables),
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
  attachBookFiles: (
    client: QueryClient,
    result: AttachBookFilesResult,
    variables: AttachBookFilesVariables,
  ): Promise<void> => patchAttachedBookFilesInLegacyCache(
    client,
    {
      id: result.updatedBook.id,
      primaryFile: result.updatedBook.primaryFile && toLegacyBookFile(result.updatedBook.primaryFile),
      alternativeFormats: result.updatedBook.alternativeFormats?.map(toLegacyBookFile),
      supplementaryFiles: result.updatedBook.supplementaryFiles?.map(toLegacyBookFile),
    },
    result.deletedSourceBookIds,
    variables.sourceBookIds,
  ),
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
  deleteAdditionalFile: (
    client: QueryClient,
    _result: void,
    {bookId, fileId}: DeleteAdditionalFileVariables,
  ): Promise<void> => patchListOnlyBooksWith(client, [{
    bookId,
    updater: book => ({
      ...book,
      alternativeFormats: book.alternativeFormats?.filter(file => file.id !== fileId),
      supplementaryFiles: book.supplementaryFiles?.filter(file => file.id !== fileId),
    }),
  }]),
  resetProgress: (
    client: QueryClient,
    results: readonly ResetBookProgressResult[],
    {source}: ResetBookProgressVariables,
  ): Promise<void> => patchListOnlyBookFields(client, results.map(result => ({
    bookId: result.bookId,
    fields: resetProgressPatchFields(result, source),
  }))),
  metadataAllLocks: (
    client: QueryClient,
    results: readonly BookDetailMetadata[],
  ): Promise<void> => patchListOnlyBooksWith(client, results.map(result => ({
    bookId: result.bookId,
    updater: book => {
      if (!book.metadata) {
        return book;
      }
      return {
        ...book,
        metadata: {...book.metadata, ...metadataLocks(result)},
      };
    },
  }))),
  shelfMembership: (
    client: QueryClient,
    books: readonly BookDetail[],
  ): Promise<void> => patchLegacyShelfMembership(client, books),
} as const;

function metadataLocks(metadata: BookDetailMetadata): Record<string, boolean> {
  const locks: Record<string, boolean> = {};
  for (const [field, value] of Object.entries(metadata)) {
    if (field.endsWith('Locked') && typeof value === 'boolean') {
      locks[field] = value;
    }
  }
  return locks;
}

function resetProgressPatchFields(result: ResetBookProgressResult, source: BookProgressSource): Partial<Book> {
  if (source !== 'GRIMMORY') {
    return clearedProgressFields(source);
  }
  return {
    ...clearedProgressFields(source),
    readStatus: undefined,
    readStatusModifiedTime: result.readStatusModifiedTime ?? undefined,
    dateFinished: undefined,
    lastReadTime: undefined,
  };
}

function clearedProgressFields(source: BookProgressSource): Partial<Book> {
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
  books: readonly BookDetail[],
): Promise<void> {
  await Promise.all([
    patchListOnlyBooksWith(client, books.map(updated => ({
      bookId: updated.id,
      updater: book => ({
        ...book,
        shelves: updated.shelves?.map(toLegacyShelf),
      }),
    }))),
    client.invalidateQueries({queryKey: SHELVES_QUERY_KEY, exact: true}),
  ]);
}

function toLegacyBookFile(file: BookFileResponse): AdditionalFile {
  return {...file, bookType: BOOK_TYPES.find(type => type === file.bookType)};
}

function toLegacyShelf(shelf: Shelf) {
  return {
    id: shelf.id,
    name: shelf.name,
    icon: shelf.icon,
    iconType: shelf.iconType,
    userId: shelf.userId,
    publicShelf: shelf.publicShelf,
  };
}
