import {inject, Injectable} from '@angular/core';
import {Router} from '@angular/router';
import {TranslocoService} from '@jsverse/transloco';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {finalize, type Observable} from 'rxjs';

import {LoadingService} from '../../../core/services/loading.service';
import {DialogLauncherService} from '../../../shared/services/dialog-launcher.service';
import {MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {BookDialogHelperService} from '../components/book-browser/book-dialog-helper.service';
import {LibraryService} from './library.service';
import {ShelfService} from './shelf.service';

type LibraryShelfActionTarget = Readonly<{id: number; name: string}>;

@Injectable({providedIn: 'root'})
export class LibraryShelfMenuService {
  private readonly confirmationService = inject(ConfirmationService);
  private readonly messageService = inject(MessageService);
  private readonly libraryService = inject(LibraryService);
  private readonly shelfService = inject(ShelfService);
  private readonly taskHelperService = inject(TaskHelperService);
  private readonly router = inject(Router);
  private readonly dialogLauncherService = inject(DialogLauncherService);
  private readonly magicShelfService = inject(MagicShelfService);
  private readonly loadingService = inject(LoadingService);
  private readonly bookDialogHelperService = inject(BookDialogHelperService);
  private readonly t = inject(TranslocoService);

  addPhysicalBook(libraryId: number): void {
    void this.bookDialogHelperService.openAddPhysicalBookDialog(libraryId);
  }

  importIsbns(libraryId: number): void {
    void this.bookDialogHelperService.openBulkIsbnImportDialog(libraryId);
  }

  editLibrary(libraryId: number): void {
    void this.dialogLauncherService.openLibraryEditDialog(libraryId);
  }

  rescanLibrary(library: LibraryShelfActionTarget): void {
    this.confirmationService.confirm({
      message: this.t.translate('book.shelfMenuService.confirm.rescanLibraryMessage', {name: library.name}),
      header: this.t.translate('book.shelfMenuService.confirm.header'),
      acceptLabel: this.t.translate('book.shelfMenuService.confirm.rescanLabel'),
      rejectLabel: this.t.translate('common.cancel'),
      rejectButtonProps: {
        severity: 'secondary',
      },
      acceptButtonProps: {
        severity: 'success',
      },
      accept: () => {
        this.libraryService.refreshLibrary(library.id).subscribe({
          complete: () => {
            this.messageService.add({
              severity: 'info',
              summary: this.t.translate('common.success'),
              detail: this.t.translate('book.shelfMenuService.toast.libraryRefreshSuccessDetail'),
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
              detail: this.t.translate('book.shelfMenuService.toast.libraryRefreshFailedDetail'),
            });
          },
        });
      },
    });
  }

  customFetchLibraryMetadata(libraryId: number): void {
    void this.bookDialogHelperService.openMetadataRefreshDialogWithContext({
      metadataRefreshType: MetadataRefreshType.LIBRARY,
      libraryId,
    });
  }

  autoFetchLibraryMetadata(libraryId: number): void {
    this.taskHelperService.refreshMetadataTask({
      refreshType: MetadataRefreshType.LIBRARY,
      libraryId,
    }).subscribe();
  }

  findLibraryDuplicates(libraryId: number): void {
    void this.bookDialogHelperService.openDuplicateMergerDialog(libraryId);
  }

  deleteLibrary(library: LibraryShelfActionTarget): void {
    this.confirmationService.confirm({
      message: this.t.translate('book.shelfMenuService.confirm.deleteLibraryMessage', {name: library.name}),
      header: this.t.translate('book.shelfMenuService.confirm.header'),
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.cancel'),
      rejectButtonProps: {
        severity: 'secondary',
      },
      acceptButtonProps: {
        severity: 'danger',
      },
      accept: () => {
        const loader = this.loadingService.show(
          this.t.translate('book.shelfMenuService.loading.deletingLibrary', {name: library.name}),
        );
        this.libraryService.deleteLibrary(library.id)
          .pipe(finalize(() => this.loadingService.hide(loader)))
          .subscribe({
            complete: () => {
              this.navigateHomeIfViewing(`/library/${library.id}/books`);
              this.messageService.add({
                severity: 'info',
                summary: this.t.translate('common.success'),
                detail: this.t.translate('book.shelfMenuService.toast.libraryDeletedDetail'),
              });
            },
            error: () => {
              this.messageService.add({
                severity: 'error',
                summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                detail: this.t.translate('book.shelfMenuService.toast.libraryDeleteFailedDetail'),
              });
            },
          });
      },
    });
  }

  editShelf(shelfId: number): void {
    void this.dialogLauncherService.openShelfEditDialog(shelfId);
  }

  deleteShelf(shelf: LibraryShelfActionTarget): void {
    this.confirmShelfDeletion(shelf, () => this.shelfService.deleteShelf(shelf.id), {
      targetUrl: `/shelf/${shelf.id}/books`,
      confirm: 'book.shelfMenuService.confirm.deleteShelfMessage',
      success: 'book.shelfMenuService.toast.shelfDeletedDetail',
      failure: 'book.shelfMenuService.toast.shelfDeleteFailedDetail',
    });
  }

  editMagicShelf(shelfId: number): void {
    void this.dialogLauncherService.openMagicShelfEditDialog(shelfId);
  }

  copyMagicShelfJson(filterJson: string): void {
    if (!navigator.clipboard) {
      this.notifyJsonCopyFailed();
      return;
    }

    void navigator.clipboard.writeText(filterJson).then(
      () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('common.success'),
          detail: this.t.translate('book.shelfMenuService.toast.magicShelfJsonCopiedDetail'),
        });
      },
      () => this.notifyJsonCopyFailed(),
    );
  }

  private notifyJsonCopyFailed(): void {
    this.messageService.add({
      severity: 'error',
      summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
      detail: this.t.translate('book.shelfMenuService.toast.magicShelfJsonCopyFailedDetail'),
    });
  }

  deleteMagicShelf(shelf: LibraryShelfActionTarget): void {
    this.confirmShelfDeletion(shelf, () => this.magicShelfService.deleteShelf(shelf.id), {
      targetUrl: `/magic-shelf/${shelf.id}/books`,
      confirm: 'book.shelfMenuService.confirm.deleteMagicShelfMessage',
      success: 'book.shelfMenuService.toast.magicShelfDeletedDetail',
      failure: 'book.shelfMenuService.toast.magicShelfDeleteFailedDetail',
    });
  }

  private confirmShelfDeletion(
    shelf: LibraryShelfActionTarget,
    deleteShelf: () => Observable<void>,
    messages: Readonly<{targetUrl: string; confirm: string; success: string; failure: string}>,
  ): void {
    this.confirmationService.confirm({
      message: this.t.translate(messages.confirm, {name: shelf.name}),
      header: this.t.translate('book.shelfMenuService.confirm.header'),
      acceptLabel: this.t.translate('common.yes'),
      rejectLabel: this.t.translate('common.cancel'),
      acceptButtonProps: {
        severity: 'danger',
      },
      rejectButtonProps: {
        severity: 'secondary',
      },
      accept: () => {
        deleteShelf().subscribe({
          complete: () => {
            this.navigateHomeIfViewing(messages.targetUrl);
            this.messageService.add({
              severity: 'info',
              summary: this.t.translate('common.success'),
              detail: this.t.translate(messages.success),
            });
          },
          error: () => {
            this.messageService.add({
              severity: 'error',
              summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
              detail: this.t.translate(messages.failure),
            });
          },
        });
      },
    });
  }

  private navigateHomeIfViewing(deletedTargetUrl: string): void {
    const currentPath = this.router.url.replace(/[?#].*$/, '');
    if (currentPath === deletedTargetUrl) {
      void this.router.navigate(['/']);
    }
  }
}
