import {inject, type Signal} from '@angular/core';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService} from '@openng/optimus-ui/api';
import {type DynamicDialogRef} from '@openng/optimus-ui/dynamicdialog';
import {injectMutation} from '@tanstack/angular-query';
import {take} from 'rxjs/operators';

import {BOOK_READ_STATUS_LABEL_KEYS} from '../data/book-values';
import {DeleteBooksPartialError, type BookProgressSource} from '../data/book-command.models';
import {BookCommandService} from '../data/book-command.service';
import {ShelfCommandService} from '../../shelf/data/shelf-command.service';
import {type BookSummary, type BookReadStatus} from '../data/book-response.models';
import {MetadataSourceCommandService} from '../../metadata/sources/metadata-source-command.service';
import {type BookFileAttacherSourceBook} from '../components/book-file-attacher/book-file-attacher.component';
import {BookDialogHelperService} from '../service/book-dialog-helper.service';
import {legacyBookCachePatches, withLegacyBookCache} from '../service/book-command-legacy-adapter';
import {injectSelectedIds} from '../../../shared/browse/selected-ids';
import {type BrowseSelection} from '../../../shared/browse/selection';

type FetchMatchingIds = () => Promise<readonly number[]>;

const RESET_PROGRESS_CONFIRM_MESSAGE_KEYS = {
  GRIMMORY: 'book.browser.confirm.resetGrimmoryMessage',
  KOREADER: 'book.browser.confirm.resetKOReaderMessage',
} as const satisfies Partial<Record<BookProgressSource, string>>;

export interface BookBrowseBulkCommandsOptions {
  readonly selection: Signal<BrowseSelection>;
  readonly books: Signal<readonly BookSummary[]>;
  readonly fetchIds: Signal<FetchMatchingIds>;
}

export function createBookBrowseBulkCommands({selection, books, fetchIds}: BookBrowseBulkCommandsOptions) {
  const transloco = inject(TranslocoService);
  const dialogHelper = inject(BookDialogHelperService);
  const metadataSourceCommands = inject(MetadataSourceCommandService);
  const confirmationService = inject(ConfirmationService);
  const bookCommands = inject(BookCommandService);
  const shelfCommands = inject(ShelfCommandService);

  const shelfMembershipMutation = injectMutation(() => withLegacyBookCache(
    shelfCommands.updateMembership(), legacyBookCachePatches.shelfMembership));
  const readStatusMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.setReadStatus(), legacyBookCachePatches.readStatus));
  const refreshMetadataMutation = injectMutation(() => metadataSourceCommands.refreshMetadata());
  const deleteBooksMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.deleteBooks(), legacyBookCachePatches.deleteBooks));
  const resetProgressMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.resetProgress(), legacyBookCachePatches.resetProgress));
  const metadataLocksMutation = injectMutation(() => withLegacyBookCache(
    bookCommands.setAllMetadataLocks(), legacyBookCachePatches.metadataAllLocks));
  const regenerateCoversMutation = injectMutation(() => bookCommands.bulkRegenerateCovers());
  const generateCoversMutation = injectMutation(() => bookCommands.bulkGenerateCovers());

  const {isResolving, withSelectedIds} = injectSelectedIds(selection, fetchIds);

  function openThenClear(open: (bookIds: Set<number>) => Promise<DynamicDialogRef | null>): void {
    void withSelectedIds(bookIds => void open(new Set(bookIds))
      .then(ref => ref?.onClose.pipe(take(1)).subscribe(() => selection().clear())));
  }

  function selectedCount(): string {
    return selection().count().toLocaleString();
  }

  return {
    isResolving,

    toggleShelf(shelfId: number, checked: boolean): void {
      void withSelectedIds(bookIds =>
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
      void withSelectedIds(bookIds =>
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
      void withSelectedIds(bookIds => void dialogHelper.openFileMoverDialog(new Set(bookIds)));
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

    resetProgress(source: keyof typeof RESET_PROGRESS_CONFIRM_MESSAGE_KEYS): void {
      confirmationService.confirm({
        message: transloco.translate(RESET_PROGRESS_CONFIRM_MESSAGE_KEYS[source], {count: selectedCount()}),
        header: transloco.translate('book.browser.confirm.resetHeader'),
        acceptLabel: transloco.translate('common.confirm'),
        rejectLabel: transloco.translate('common.cancel'),
        accept: () => {
          void withSelectedIds(bookIds => resetProgressMutation.mutate({bookIds: [...bookIds], source}));
        },
      });
    },

    setMetadataLocks(locked: boolean): void {
      void withSelectedIds(bookIds => metadataLocksMutation.mutate({bookIds: [...bookIds], locked}));
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
          const mutation = regenerate ? regenerateCoversMutation : generateCoversMutation;
          void withSelectedIds(bookIds => mutation.mutate({bookIds: [...bookIds]}));
        },
      });
    },

    fetchMetadata(): void {
      void withSelectedIds(bookIds => refreshMetadataMutation.mutate({refreshType: 'BOOKS', bookIds: [...bookIds]}));
    },

    fetchMetadataWithOptions(): void {
      void withSelectedIds(bookIds => void dialogHelper.openMetadataRefreshDialog(new Set(bookIds)));
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
          void withSelectedIds(bookIds =>
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

    markAs(status: BookReadStatus): void {
      const statusLabel = transloco.translate(BOOK_READ_STATUS_LABEL_KEYS[status]);
      confirmationService.confirm({
        header: transloco.translate('browse.bulk.updateReadStatus'),
        message: transloco.translate('browse.bulk.markAsMessage', {count: selectedCount(), status: statusLabel}),
        acceptLabel: transloco.translate('common.confirm'),
        rejectLabel: transloco.translate('common.cancel'),
        accept: () => {
          void withSelectedIds(bookIds => readStatusMutation.mutate({bookIds: [...bookIds], status}));
        },
      });
    },
  };
}
