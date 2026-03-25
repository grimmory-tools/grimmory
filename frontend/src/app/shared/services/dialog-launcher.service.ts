import {inject, Injectable, Type} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {LibraryCreatorComponent} from '../../features/library-creator/library-creator.component';
import {BookUploaderComponent} from '../components/book-uploader/book-uploader.component';
import {UserProfileDialogComponent} from '../../features/settings/user-profile-dialog/user-profile-dialog.component';
import {MagicShelfComponent} from '../../features/magic-shelf/component/magic-shelf-component';
import {DashboardSettingsComponent} from '../../features/dashboard/components/dashboard-settings/dashboard-settings.component';
import {VersionChangelogDialogComponent} from '../layout/component/layout-menu/version-changelog-dialog/version-changelog-dialog.component';
import {CreateUserDialogComponent} from '../../features/settings/user-management/create-user-dialog/create-user-dialog.component';
import {CreateEmailRecipientDialogComponent} from '../../features/settings/email-v2/create-email-recipient-dialog/create-email-recipient-dialog.component';
import {CreateEmailProviderDialogComponent} from '../../features/settings/email-v2/create-email-provider-dialog/create-email-provider-dialog.component';
import {DirectoryPickerComponent} from '../components/directory-picker/directory-picker.component';
import {BookdropFinalizeResultDialogComponent} from '../../features/bookdrop/component/bookdrop-finalize-result-dialog/bookdrop-finalize-result-dialog.component';
import {BookdropFinalizeResult} from '../../features/bookdrop/service/bookdrop.service';
import {MetadataReviewDialogComponent} from '../../features/metadata/component/metadata-review-dialog/metadata-review-dialog-component';
import {MetadataRefreshType} from '../../features/metadata/model/request/metadata-refresh-type.enum';
import {MetadataFetchOptionsComponent} from '../../features/metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {ShelfEditDialogComponent} from '../../features/book/components/shelf-edit-dialog/shelf-edit-dialog.component';
import {IconPickerComponent} from '../components/icon-picker/icon-picker-component';

/**
 * Dialog size classes - use these to control dialog dimensions
 */
export const DialogSize = {
  XS: 'dialog-xs',   // ~400px - confirmations, simple alerts
  SM: 'dialog-sm',   // ~550px - simple forms, pickers
  MD: 'dialog-md',   // ~700px - standard dialogs
  LG: 'dialog-lg',   // ~900px - complex forms, lists
  XL: 'dialog-xl',   // ~1200px - data-heavy views
  FULL: 'dialog-full', // viewport - fullscreen editors
} as const;

/**
 * Dialog style modifiers - composable with size classes
 */
export const DialogStyle = {
  MINIMAL: 'dialog-minimal', // removes padding for custom headers
} as const;

@Injectable({
  providedIn: 'root',
})
export class DialogLauncherService {

  dialogService = inject(DialogService);

  private defaultDialogOptions = {
    baseZIndex: 10,
    closable: true,
    dismissableMask: true,
    draggable: false,
    modal: true,
    resizable: false,
    showHeader: true,
    maximizable: false,
  }

  openDialog(component: unknown, options: {}): DynamicDialogRef | null {
    return this.dialogService.open(component as Type<any>, {
      ...this.defaultDialogOptions,
      ...options,
    });
  }

  openDashboardSettingsDialog(): DynamicDialogRef | null {
    return this.openDialog(DashboardSettingsComponent, {
      showHeader: false,
      styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
    });
  }

  openLibraryCreateDialog(): DynamicDialogRef | null {
    return this.openDialog(LibraryCreatorComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    });
  }

  openDirectoryPickerDialog(): DynamicDialogRef | null {
    return this.openDialog(DirectoryPickerComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    });
  }

  openLibraryEditDialog(libraryId: number): DynamicDialogRef | null {
    return this.openDialog(LibraryCreatorComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        mode: 'edit',
        libraryId: libraryId
      }
    });
  }

  openLibraryMetadataFetchDialog(libraryId: number): DynamicDialogRef | null {
    return this.openDialog(MetadataFetchOptionsComponent, {
      showHeader: false,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      data: {
        libraryId: libraryId,
        metadataRefreshType: MetadataRefreshType.LIBRARY,
      },
    });
  }

  openShelfEditDialog(shelfId: number): DynamicDialogRef | null {
    return this.openDialog(ShelfEditDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      data: {
        shelfId: shelfId
      },
    })
  }

  openFileUploadDialog(): DynamicDialogRef | null {
    return this.openDialog(BookUploaderComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    });
  }

  openCreateUserDialog(): DynamicDialogRef | null {
    return this.openDialog(CreateUserDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
    });
  }

  openUserProfileDialog(): DynamicDialogRef | null {
    return this.openDialog(UserProfileDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
    });
  }

  openMagicShelfCreateDialog(): DynamicDialogRef | null {
    return this.openDialog(MagicShelfComponent, {
      showHeader: false,
      styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
    });
  }

  openMagicShelfEditDialog(shelfId: number): DynamicDialogRef | null {
    return this.openDialog(MagicShelfComponent, {
      showHeader: false,
      styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      data: {
        id: shelfId,
        editMode: true,
      }
    })
  }

  openVersionChangelogDialog(): DynamicDialogRef | null {
    return this.openDialog(VersionChangelogDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
    });
  }

  openEmailRecipientDialog(): DynamicDialogRef | null {
    return this.openDialog(CreateEmailRecipientDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    });
  }

  openEmailProviderDialog(): DynamicDialogRef | null {
    return this.openDialog(CreateEmailProviderDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
    });
  }

  openBookdropFinalizeResultDialog(result: BookdropFinalizeResult): DynamicDialogRef | null {
    return this.openDialog(BookdropFinalizeResultDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      data: {
        result: result,
      },
    });
  }

  openMetadataReviewDialog(taskId: string): DynamicDialogRef | null {
    return this.openDialog(MetadataReviewDialogComponent, {
      showHeader: false,
      styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
      data: {
        taskId,
      },
    });
  }

  openIconPickerDialog(): DynamicDialogRef | null {
    return this.openDialog(IconPickerComponent, {
      showHeader: false,
      styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
    });
  }
}
