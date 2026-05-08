import { inject, Injectable } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Router } from '@angular/router';
import { LibraryService } from './library.service';
import { ShelfService } from './shelf.service';
import { Library } from '../model/library.model';
import { Shelf } from '../model/shelf.model';
import { MetadataRefreshType } from '../../metadata/model/request/metadata-refresh-type.enum';
import { MagicShelf, MagicShelfService } from '../../magic-shelf/service/magic-shelf.service';
import { TaskHelperService } from '../../settings/task-management/task-helper.service';
import { UserService } from '../../settings/user-management/user.service';
import { LoadingService } from '../../../core/services/loading.service';
import { finalize } from 'rxjs';
import { DialogLauncherService } from '../../../shared/services/dialog-launcher.service';
import { BookDialogHelperService } from '../components/book-browser/book-dialog-helper.service';
import { TranslocoService } from '@jsverse/transloco';
import type { MenuItem } from 'primeng/api';

@Injectable({
  providedIn: 'root',
})
export class LibraryShelfMenuService {

  private confirmationService = inject(ConfirmationService);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private taskHelperService = inject(TaskHelperService);
  private router = inject(Router);
  private dialogLauncherService = inject(DialogLauncherService);
  private magicShelfService = inject(MagicShelfService);
  private userService = inject(UserService);
  private loadingService = inject(LoadingService);
  private bookDialogHelperService = inject(BookDialogHelperService);
  private readonly t = inject(TranslocoService);

  initializeLibraryMenuItems(entity: Library | null): MenuItem[] {
    const libraryId = entity?.id;

    return [
      {
        label: this.t.translate('book.shelfMenuService.library.optionsLabel'),
        items: [
          {
            label: this.t.translate('book.shelfMenuService.library.addPhysicalBook'),
            icon: 'pi pi-book',
            disabled: libraryId == null,
            command: async () => {
              if (libraryId == null) {
                return;
              }
              try {
                await this.bookDialogHelperService.openAddPhysicalBookDialog(libraryId);
              } catch {
                this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                  detail: this.t.translate('book.shelfMenuService.toast.dialogLoadFailedDetail'),
                });
              }
            }
          },
          {
            label: this.t.translate('book.shelfMenuService.library.bulkIsbnImport'),
            icon: 'pi pi-barcode',
            disabled: libraryId == null,
            command: async () => {
              if (libraryId == null) {
                return;
              }
              try {
                await this.bookDialogHelperService.openBulkIsbnImportDialog(libraryId);
              } catch {
                this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                  detail: this.t.translate('book.shelfMenuService.toast.dialogLoadFailedDetail'),
                });
              }
            }
          },
          {
            separator: true
          },
          {
            label: this.t.translate('book.shelfMenuService.library.editLibrary'),
            icon: 'pi pi-pen-to-square',
            disabled: libraryId == null,
            command: () => {
              if (libraryId == null) {
                return;
              }
              this.dialogLauncherService.openLibraryEditDialog(libraryId);
            }
          },
          {
            label: this.t.translate('book.shelfMenuService.library.rescanLibrary'),
            icon: 'pi pi-refresh',
            disabled: libraryId == null,
            command: () => {
              if (libraryId == null) {
                return;
              }
              this.confirmationService.confirm({
                message: this.t.translate('book.shelfMenuService.confirm.rescanLibraryMessage', {name: entity?.name}),
                header: this.t.translate('book.shelfMenuService.confirm.header'),
                icon: undefined,
                acceptLabel: this.t.translate('book.shelfMenuService.confirm.rescanLabel'),
                rejectLabel: this.t.translate('common.cancel'),
                acceptIcon: undefined,
                rejectIcon: undefined,
                acceptButtonStyleClass: undefined,
                rejectButtonStyleClass: undefined,
                rejectButtonProps: {
                  label: this.t.translate('common.cancel'),
                  severity: 'secondary',
                },
                acceptButtonProps: {
                  label: this.t.translate('book.shelfMenuService.confirm.rescanLabel'),
                  severity: 'success',
                },
                accept: () => {
                  this.libraryService.refreshLibrary(libraryId).subscribe({
                    complete: () => {
                      this.messageService.add({severity: 'info', summary: this.t.translate('common.success'), detail: this.t.translate('book.shelfMenuService.toast.libraryRefreshSuccessDetail')});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                        detail: this.t.translate('book.shelfMenuService.toast.libraryRefreshFailedDetail'),
                      });
                    }
                  });
                }
              });
            }
          },
          {
            label: this.t.translate('book.shelfMenuService.library.customFetchMetadata'),
            icon: 'pi pi-sync',
            disabled: libraryId == null,
            command: async () => {
              if (libraryId == null) {
                return;
              }
              try {
                await this.bookDialogHelperService.openMetadataRefreshDialogWithContext({
                  metadataRefreshType: MetadataRefreshType.LIBRARY,
                  libraryId
                });
              } catch {
                this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                  detail: this.t.translate('book.shelfMenuService.toast.dialogLoadFailedDetail'),
                });
              }
            }
          },
          {
            label: this.t.translate('book.shelfMenuService.library.autoFetchMetadata'),
            icon: 'pi pi-bolt',
            disabled: libraryId == null,
            command: () => {
              if (libraryId == null) {
                return;
              }
              this.taskHelperService.refreshMetadataTask({
                refreshType: MetadataRefreshType.LIBRARY,
                libraryId
              }).subscribe();
            }
          },
          {
            label: this.t.translate('book.shelfMenuService.library.findDuplicates'),
            icon: 'pi pi-copy',
            disabled: libraryId == null,
            command: async () => {
              if (libraryId == null) {
                return;
              }
              try {
                await this.bookDialogHelperService.openDuplicateMergerDialog(libraryId);
              } catch {
                this.messageService.add({
                  severity: 'error',
                  summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                  detail: this.t.translate('book.shelfMenuService.toast.dialogLoadFailedDetail'),
                });
              }
            }
          },
          {
            separator: true
          },
          {
            label: this.t.translate('book.shelfMenuService.library.deleteLibrary'),
            icon: 'pi pi-trash',
            disabled: libraryId == null,
            command: () => {
              if (libraryId == null) {
                return;
              }
              this.confirmationService.confirm({
                message: this.t.translate('book.shelfMenuService.confirm.deleteLibraryMessage', {name: entity?.name}),
                header: this.t.translate('book.shelfMenuService.confirm.header'),
                acceptLabel: this.t.translate('common.yes'),
                rejectLabel: this.t.translate('common.cancel'),
                rejectButtonProps: {
                  label: this.t.translate('common.cancel'),
                  severity: 'secondary',
                },
                acceptButtonProps: {
                  label: this.t.translate('common.yes'),
                  severity: 'danger',
                },
                accept: () => {
                  const loader = this.loadingService.show(this.t.translate('book.shelfMenuService.loading.deletingLibrary', {name: entity?.name}));

                  this.libraryService.deleteLibrary(libraryId)
                    .pipe(finalize(() => this.loadingService.hide(loader)))
                    .subscribe({
                      complete: () => {
                        this.router.navigate(['/']);
                        this.messageService.add({severity: 'info', summary: this.t.translate('common.success'), detail: this.t.translate('book.shelfMenuService.toast.libraryDeletedDetail')});
                      },
                      error: () => {
                        this.messageService.add({
                          severity: 'error',
                          summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                          detail: this.t.translate('book.shelfMenuService.toast.libraryDeleteFailedDetail'),
                        });
                      }
                    });
                }
              });
            }
          }
        ]
      }
    ];
  }

  initializeShelfMenuItems(entity: Shelf | null): MenuItem[] {
    const user = this.userService.getCurrentUser();
    const shelfId = entity?.id;
    const isOwner = entity?.userId === user?.id;
    const isPublicShelf = entity?.publicShelf ?? false;
    const disableOptions = !isOwner || shelfId == null;

    const items: MenuItem[] = [
      {
        label: this.t.translate('book.shelfMenuService.shelf.editShelf'),
        icon: 'pi pi-pen-to-square',
        disabled: disableOptions,
        command: () => {
          if (shelfId == null) {
            return;
          }

          this.dialogLauncherService.openShelfEditDialog(shelfId);
        }
      },
      {
        separator: true
      },
      {
        label: this.t.translate('book.shelfMenuService.shelf.deleteShelf'),
        icon: 'pi pi-trash',
        disabled: disableOptions,
        command: () => {
          if (shelfId == null) {
            return;
          }

          this.confirmationService.confirm({
            message: this.t.translate('book.shelfMenuService.confirm.deleteShelfMessage', {name: entity?.name}),
            header: this.t.translate('book.shelfMenuService.confirm.header'),
            acceptLabel: this.t.translate('common.yes'),
            rejectLabel: this.t.translate('common.cancel'),
            acceptButtonProps: {
              label: this.t.translate('common.yes'),
              severity: 'danger'
            },
            rejectButtonProps: {
              label: this.t.translate('common.cancel'),
              severity: 'secondary'
            },
            accept: () => {
              this.shelfService.deleteShelf(shelfId).subscribe({
                complete: () => {
                  this.router.navigate(['/']);
                  this.messageService.add({severity: 'info', summary: this.t.translate('common.success'), detail: this.t.translate('book.shelfMenuService.toast.shelfDeletedDetail')});
                },
                error: () => {
                  this.messageService.add({
                    severity: 'error',
                    summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                    detail: this.t.translate('book.shelfMenuService.toast.shelfDeleteFailedDetail'),
                  });
                }
              });
            }
          });
        }
      }
    ];

    /* Keep the grouped label only when it conveys state (public/read-only). */
    if (isPublicShelf || disableOptions) {
      const prefix = isPublicShelf ? this.t.translate('book.shelfMenuService.shelf.publicShelfPrefix') : '';
      const suffix = disableOptions
        ? this.t.translate('book.shelfMenuService.shelf.readOnly')
        : this.t.translate('book.shelfMenuService.shelf.optionsLabel');
      return [{ label: prefix + suffix, items }];
    }

    return items;
  }

  initializeMagicShelfMenuItems(entity: MagicShelf | null): MenuItem[] {
    const isAdmin = this.userService.getCurrentUser()?.permissions.admin ?? false;
    const magicShelfId = entity?.id;
    const isPublicShelf = entity?.isPublic ?? false;
    const disableOptions = magicShelfId == null || (isPublicShelf && !isAdmin);

    return [
      {
        label: this.t.translate('book.shelfMenuService.magicShelf.editMagicShelf'),
        icon: 'pi pi-pen-to-square',
        disabled: disableOptions,
        command: () => {
          if (magicShelfId == null) {
            return;
          }

          this.dialogLauncherService.openMagicShelfEditDialog(magicShelfId);
        }
      },
      {
        label: this.t.translate('book.shelfMenuService.magicShelf.exportJson'),
        icon: 'pi pi-copy',
        command: () => {
          if (entity?.filterJson) {
            navigator.clipboard.writeText(entity.filterJson).then(() => {
              this.messageService.add({severity: 'success', summary: this.t.translate('common.success'), detail: this.t.translate('book.shelfMenuService.toast.magicShelfJsonCopiedDetail')});
            });
          }
        }
      },
      {
        separator: true
      },
      {
        label: this.t.translate('book.shelfMenuService.magicShelf.deleteMagicShelf'),
        icon: 'pi pi-trash',
        disabled: disableOptions,
        command: () => {
          if (magicShelfId == null) {
            return;
          }

          this.confirmationService.confirm({
            message: this.t.translate('book.shelfMenuService.confirm.deleteMagicShelfMessage', {name: entity?.name}),
            header: this.t.translate('book.shelfMenuService.confirm.header'),
            acceptLabel: this.t.translate('common.yes'),
            rejectLabel: this.t.translate('common.cancel'),
            acceptButtonProps: {
              label: this.t.translate('common.yes'),
              severity: 'danger'
            },
            rejectButtonProps: {
              label: this.t.translate('common.cancel'),
              severity: 'secondary'
            },
            accept: () => {
              this.magicShelfService.deleteShelf(magicShelfId).subscribe({
                complete: () => {
                  this.router.navigate(['/']);
                  this.messageService.add({severity: 'info', summary: this.t.translate('common.success'), detail: this.t.translate('book.shelfMenuService.toast.magicShelfDeletedDetail')});
                },
                error: () => {
                  this.messageService.add({
                    severity: 'error',
                    summary: this.t.translate('book.shelfMenuService.toast.failedSummary'),
                    detail: this.t.translate('book.shelfMenuService.toast.magicShelfDeleteFailedDetail'),
                  });
                }
              });
            }
          });
        }
      }
    ];
  }
}
