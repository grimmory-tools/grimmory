import {inject, signal, type Signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {type DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {injectMutation} from '@tanstack/angular-query-experimental';
import {take} from 'rxjs/operators';

import {
  BOOK_READ_STATUS_LABEL_KEYS,
  CLEAR_BOOK_READ_STATUS_LABEL_KEY,
} from '../components/book-read-status-options';
import {DeleteBooksPartialError, type BookProgressSource} from '../data/book-command.models';
import {BookBackgroundSubmissionService} from '../data/book-background-submission.service';
import {BookCommandService} from '../data/book-command.service';
import {BookShelfCommandService} from '../data/book-shelf-command.service';
import {type BookSummary, type KnownBookReadStatus} from '../data/book-response.models';
import {MetadataRefreshSubmissionService} from '../../metadata/data/metadata-refresh-submission.service';
import {type BookFileAttacherSourceBook} from '../components/book-file-attacher/book-file-attacher.component';
import {BookDialogHelperService} from '../components/book-browser/book-dialog-helper.service';
import {legacyBookCachePatches, withLegacyBookCache} from '../service/book-command-legacy-adapter';
import {resolveSelectedIds, type BrowseSelection} from '../../../shared/browse/selection';

type FetchMatchingIds = () => Promise<readonly number[]>;

export interface BookBrowseBulkCommandsOptions {
  readonly selection: Signal<BrowseSelection>;
  readonly books: Signal<readonly BookSummary[]>;
  readonly fetchIds: Signal<FetchMatchingIds>;
}

export function createBookBrowseBulkCommands({selection, books, fetchIds}: BookBrowseBulkCommandsOptions) {
  const transloco = inject(TranslocoService);
  const dialogHelper = inject(BookDialogHelperService);
  const metadataRefresh = inject(MetadataRefreshSubmissionService);
  const backgroundSubmission = inject(BookBackgroundSubmissionService);
  const confirmationService = inject(ConfirmationService);
  const messageService = inject(MessageService);
  const bookCommands = inject(BookCommandService);
  const shelfCommands = inject(BookShelfCommandService);

  const shelfMembershipMutation = injectMutation(() => withLegacyBookCache(
    shelfCommands.updateMembership(), legacyBookCachePatches.shelfMembership));
  const readStatusMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.setReadStatus(), legacyBookCachePatches.readStatus));
  const refreshMetadataMutation = injectMutation(() => metadataRefresh.refreshMetadata());
  const deleteBooksMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.deleteBooks(), legacyBookCachePatches.deleteBooks));
  const resetProgressMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.resetProgress(), legacyBookCachePatches.resetProgress));
  const metadataLocksMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.setAllMetadataLocks(), legacyBookCachePatches.metadataAllLocks));
  const changeCoversMutation = injectMutation(() => backgroundSubmission.changeCovers());

  const isResolving = signal(false);

  async function withSelectedBookIds(run: (bookIds: readonly number[]) => void): Promise<void> {
    if (isResolving()) {
      return;
    }
    isResolving.set(true);
    let bookIds: readonly number[];
    try {
      bookIds = await resolveSelectedIds(selection().state(), fetchIds());
    } catch {
      messageService.add({
        severity: 'error',
        summary: transloco.translate('browse.bulk.selectionLoadError'),
      });
      return;
    } finally {
      isResolving.set(false);
    }
    if (bookIds.length === 0) {
      selection().clear();
      messageService.add({
        severity: 'info',
        summary: transloco.translate('browse.bulk.selectionEmpty'),
      });
      return;
    }
    run(bookIds);
  }

  function openThenClear(open: (bookIds: Set<number>) => Promise<DynamicDialogRef | null>): void {
    void withSelectedBookIds(bookIds => void open(new Set(bookIds))
      .then(ref => ref?.onClose.pipe(take(1)).subscribe(() => selection().clear())));
  }

  function selectedCount(): string {
    return selection().count().toLocaleString();
  }

  return {
    isResolving: isResolving.asReadonly(),

    toggleShelf(shelfId: number, checked: boolean): void {
      void withSelectedBookIds(bookIds =>
        shelfMembershipMutation.mutate({
          bookIds: [...bookIds],
          assignShelfIds: checked ? [shelfId] : [],
          unassignShelfIds: checked ? [] : [shelfId],
        }));
    },

    removeFromAllShelves(shelfIds: readonly number[]): void {
      if (shelfIds.length === 0) {
        return;
      }
      void withSelectedBookIds(bookIds =>
        shelfMembershipMutation.mutate({
          bookIds: [...bookIds],
          assignShelfIds: [],
          unassignShelfIds: [...shelfIds],
        }));
    },

    editAll(): void {
      openThenClear(bookIds => dialogHelper.openBulkMetadataEditDialog(bookIds));
    },

    editOneByOne(): void {
      openThenClear(bookIds => dialogHelper.openMultibookMetadataEditorDialog(bookIds));
    },

    lockUnlockMetadata(): void {
      openThenClear(bookIds => dialogHelper.openLockUnlockMetadataDialog(bookIds));
    },

    organizeFiles(): void {
      void withSelectedBookIds(bookIds => void dialogHelper.openFileMoverDialog(new Set(bookIds)));
    },

    createShelf(): void {
      void dialogHelper.openShelfCreatorDialog();
    },

    attachFiles(): void {
      const sourceBooks: BookFileAttacherSourceBook[] =
        books().filter(book => selection().isSelected(book.id));
      void dialogHelper.openBulkBookFileAttacherDialog(sourceBooks)
        .then(ref => ref?.onClose.pipe(take(1)).subscribe((result: {success?: boolean} | undefined) => {
          if (result?.success) {
            selection().clear();
          }
        }));
    },

    resetProgress(source: BookProgressSource): void {
      void withSelectedBookIds(bookIds => resetProgressMutation.mutate({bookIds: [...bookIds], source}));
    },

    setMetadataLocks(locked: boolean): void {
      void withSelectedBookIds(bookIds => metadataLocksMutation.mutate({bookIds: [...bookIds], locked}));
    },

    changeCovers(kind: 'regenerate' | 'generate'): void {
      const regenerate = kind === 'regenerate';
      confirmationService.confirm({
        message: transloco.translate(
          regenerate ? 'book.browser.confirm.regenCoverMessage' : 'book.browser.confirm.customCoverMessage',
          {count: selectedCount()},
        ),
        header: transloco.translate(
          regenerate ? 'book.browser.confirm.regenCoverHeader' : 'book.browser.confirm.customCoverHeader',
        ),
        acceptLabel: transloco.translate('common.confirm'),
        rejectLabel: transloco.translate('common.cancel'),
        accept: () => {
          void withSelectedBookIds(bookIds => changeCoversMutation.mutate({kind, bookIds: [...bookIds]}));
        },
      });
    },

    fetchMetadata(): void {
      void withSelectedBookIds(bookIds => refreshMetadataMutation.mutate({bookIds: [...bookIds]}));
    },

    fetchMetadataWithOptions(): void {
      void withSelectedBookIds(bookIds => void dialogHelper.openMetadataRefreshDialog(new Set(bookIds)));
    },

    delete(): void {
      confirmationService.confirm({
        message: transloco.translate('book.browser.confirm.deleteMessage', {count: selectedCount()}),
        header: transloco.translate('book.browser.confirm.deleteHeader'),
        acceptLabel: transloco.translate('common.delete'),
        rejectLabel: transloco.translate('common.cancel'),
        acceptButtonStyleClass: 'p-button-danger',
        rejectButtonStyleClass: 'p-button-outlined',
        accept: () => {
          void withSelectedBookIds(bookIds =>
            deleteBooksMutation.mutate({bookIds: [...bookIds]}, {
              onSuccess: result => selection().pruneDeleted(result.removedBookIds),
              onError: error => {
                if (error instanceof DeleteBooksPartialError) {
                  selection().pruneDeleted(error.completed.removedBookIds);
                }
              },
            }));
        },
      });
    },

    markAs(status: KnownBookReadStatus): void {
      const statusLabel = transloco.translate(
        status === 'UNSET' ? CLEAR_BOOK_READ_STATUS_LABEL_KEY : BOOK_READ_STATUS_LABEL_KEYS[status],
      );
      confirmationService.confirm({
        header: transloco.translate('browse.bulk.updateReadStatus'),
        message: transloco.translate('browse.bulk.markAsMessage', {count: selectedCount(), status: statusLabel}),
        acceptLabel: transloco.translate('common.confirm'),
        rejectLabel: transloco.translate('common.cancel'),
        accept: () => {
          void withSelectedBookIds(bookIds => readStatusMutation.mutate({bookIds: [...bookIds], status}));
        },
      });
    },
  };
}
