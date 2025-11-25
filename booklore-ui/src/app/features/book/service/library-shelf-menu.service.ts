import {inject, Injectable} from '@angular/core';
import {ConfirmationService, MenuItem, MessageService} from 'primeng/api';
import {Router} from '@angular/router';
import {LibraryService} from './library.service';
import {ShelfService} from './shelf.service';
import {Library} from '../model/library.model';
import {Shelf} from '../model/shelf.model';
import {DialogService} from 'primeng/dynamicdialog';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import {LibraryCreatorComponent} from '../../library-creator/library-creator.component';
import {ShelfEditDialogComponent} from '../components/shelf-edit-dialog/shelf-edit-dialog.component';
import {MagicShelf, MagicShelfService} from '../../magic-shelf/service/magic-shelf.service';
import {MetadataFetchOptionsComponent} from '../../metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MagicShelfComponent} from '../../magic-shelf/component/magic-shelf-component';
import {TaskCreateRequest, TaskType} from '../../settings/task-management/task.service';
import {MetadataRefreshRequest} from '../../metadata/model/request/metadata-refresh-request.model';
import {TaskHelperService} from '../../settings/task-management/task-helper.service';
import {UserService} from "../../settings/user-management/user.service";

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
  private dialogService = inject(DialogService);
  private magicShelfService = inject(MagicShelfService);
  private userService = inject(UserService);

  initializeLibraryMenuItems(entity: Library | Shelf | MagicShelf | null): MenuItem[] {
    return [
      {
        label: 'Options',
        items: [
          {
            label: 'Edit Library',
            icon: 'pi pi-pen-to-square',
            command: () => {
              this.dialogService.open(LibraryCreatorComponent, {
                header: 'Edit Library',
                modal: true,
                closable: true,
                style: {
                  position: 'absolute',
                  top: '15%',
                },
                data: {
                  mode: 'edit',
                  libraryId: entity?.id
                }
              });
            }
          },
          {
            label: 'Re-scan Library',
            icon: 'pi pi-refresh',
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to refresh library: ${entity?.name}?`,
                header: 'Confirmation',
                rejectButtonProps: {
                  label: 'Cancel',
                  severity: 'secondary',
                },
                acceptButtonProps: {
                  label: 'Yes',
                  severity: 'success',
                },
                accept: () => {
                  this.libraryService.refreshLibrary(entity?.id!).subscribe({
                    complete: () => {
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Library refresh scheduled'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to refresh library',
                      });
                    }
                  });
                }
              });
            }
          },
          {
            label: 'Custom Fetch Metadata',
            icon: 'pi pi-sync',
            command: () => {
              this.dialogService.open(MetadataFetchOptionsComponent, {
                header: 'Metadata Refresh Options',
                modal: true,
                closable: true,
                data: {
                  libraryId: entity?.id,
                  metadataRefreshType: MetadataRefreshType.LIBRARY
                }
              })
            }
          },
          {
            label: 'Auto Fetch Metadata',
            icon: 'pi pi-bolt',
            command: () => {
              this.taskHelperService.refreshMetadataTask({
                refreshType: MetadataRefreshType.LIBRARY,
                libraryId: entity?.id ?? undefined
              }).subscribe();
            }
          },
          {
            separator: true
          },
          {
            label: 'Delete Library',
            icon: 'pi pi-trash',
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to delete library: ${entity?.name}?`,
                header: 'Confirmation',
                rejectButtonProps: {
                  label: 'Cancel',
                  severity: 'secondary',
                },
                acceptButtonProps: {
                  label: 'Yes',
                  severity: 'danger',
                },
                accept: () => {
                  this.libraryService.deleteLibrary(entity?.id!).subscribe({
                    complete: () => {
                      this.router.navigate(['/']);
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Library was deleted'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to delete library',
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

  initializeShelfMenuItems(entity: any): MenuItem[] {
    return [
      {
        label: 'Options',
        items: [
          {
            label: 'Edit Shelf',
            icon: 'pi pi-pen-to-square',
            command: () => {
              this.dialogService.open(ShelfEditDialogComponent, {
                header: 'Edit Shelf',
                modal: true,
                closable: true,
                data: {
                  shelfId: entity?.id
                },
                style: {
                  position: 'absolute',
                  top: '15%',
                }
              })
            }
          },
          {
            separator: true
          },
          {
            label: 'Delete Shelf',
            icon: 'pi pi-trash',
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to delete shelf: ${entity?.name}?`,
                header: 'Confirmation',
                acceptButtonProps: {
                  severity: 'danger'
                },
                accept: () => {
                  this.shelfService.deleteShelf(entity?.id!).subscribe({
                    complete: () => {
                      this.router.navigate(['/']);
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Shelf was deleted'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to delete shelf',
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

  initializeMagicShelfMenuItems(entity: any): MenuItem[] {
    const isAdmin = this.userService.getCurrentUser()?.permissions.admin ?? false;
    const isPublicShelf = entity?.isPublic ?? false;
    const disableOptions = isPublicShelf && !isAdmin;

    return [
      {
        label: (isPublicShelf ? 'Public Shelf - ' : '') + (disableOptions ? 'Read only' : 'Options'),
        items: [
          {
            label: 'Edit Magic Shelf',
            icon: 'pi pi-pen-to-square',
            disabled: disableOptions,
            command: () => {
              this.dialogService.open(MagicShelfComponent, {
                header: 'Edit Magic Shelf',
                modal: true,
                closable: true,
                data: {
                  id: entity?.id
                }
              })
            }
          },
          {
            separator: true
          },
          {
            label: 'Delete Magic Shelf',
            icon: 'pi pi-trash',
            disabled: disableOptions,
            command: () => {
              this.confirmationService.confirm({
                message: `Are you sure you want to delete magic shelf: ${entity?.name}?`,
                header: 'Confirmation',
                acceptButtonProps: {
                  severity: 'danger'
                },
                accept: () => {
                  this.magicShelfService.deleteShelf(entity?.id!).subscribe({
                    complete: () => {
                      this.router.navigate(['/']);
                      this.messageService.add({severity: 'info', summary: 'Success', detail: 'Magic shelf was deleted'});
                    },
                    error: () => {
                      this.messageService.add({
                        severity: 'error',
                        summary: 'Failed',
                        detail: 'Failed to delete shelf',
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
}
