import {computed, Signal} from '@angular/core';
import {injectMutationState, MutationKey} from '@tanstack/angular-query-experimental';

import {bookCommandKeys} from './book-command-keys';
import {
  BookProgressSource,
  DeleteBooksVariables,
  ResetBookProgressVariables,
  SetBookReadStatusVariables,
} from './book-command.models';
import {BookReadStatus, BookSummary} from './book-response.models';

export interface PendingBookOverlay {
  readonly readStatuses: ReadonlyMap<number, BookReadStatus>;
  readonly progressResets: ReadonlyMap<number, BookProgressSource>;
}

interface BookIdsVariables {
  readonly bookIds: readonly number[];
}

function injectPendingBookValues<TVariables extends BookIdsVariables, TValue>(
  mutationKey: MutationKey,
  fold: (current: TValue | undefined, variables: TVariables) => TValue,
): Signal<ReadonlyMap<number, TValue>> {
  const pendingVariables = injectMutationState<TVariables>(() => ({
    filters: {
      mutationKey,
      status: 'pending',
    },
    select: mutation => mutation.state.variables as TVariables,
  }));

  return computed(() => {
    const values = new Map<number, TValue>();
    for (const variables of pendingVariables()) {
      for (const bookId of variables.bookIds) {
        values.set(bookId, fold(values.get(bookId), variables));
      }
    }
    return values;
  });
}

export function injectPendingBookReadStatuses(): Signal<ReadonlyMap<number, BookReadStatus>> {
  return injectPendingBookValues<SetBookReadStatusVariables, BookReadStatus>(
    bookCommandKeys.readStatus(),
    (_, variables) => variables.status,
  );
}

export function injectPendingBookProgressResets(): Signal<ReadonlyMap<number, BookProgressSource>> {
  return injectPendingBookValues<ResetBookProgressVariables, BookProgressSource>(
    bookCommandKeys.resetProgress(),
    (_, variables) => variables.source,
  );
}

export function injectPendingBookDeletions(): Signal<ReadonlySet<number>> {
  const deletions = injectPendingBookValues<DeleteBooksVariables, boolean>(
    bookCommandKeys.deleteBooks(),
    () => true,
  );
  return computed(() => new Set(deletions().keys()));
}

export function overlayPendingBookState(
  book: BookSummary,
  overlay: PendingBookOverlay,
): BookSummary {
  const readStatus = overlay.readStatuses.get(book.id);
  const progressReset = overlay.progressResets.get(book.id);
  if (readStatus === undefined && progressReset === undefined) {
    return book;
  }

  return {
    ...book,
    ...(progressReset === undefined ? {} : {...clearedProgress(progressReset), readStatus: undefined}),
    ...(readStatus === undefined ? {} : {readStatus}),
  };
}

function clearedProgress(source: BookProgressSource): Partial<BookSummary> {
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
