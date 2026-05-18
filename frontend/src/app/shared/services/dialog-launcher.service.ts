import {inject, Injectable, Type} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {TranslocoService} from '@jsverse/transloco';
import {MessageService} from 'primeng/api';
import {MetadataRefreshType} from '../../features/metadata/model/request/metadata-refresh-type.enum';
import {BookdropFinalizeResult} from '../../features/bookdrop/service/bookdrop.service';

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

  private readonly dialogService = inject(DialogService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

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

  openDialog(component: unknown, options: object): DynamicDialogRef | null {
    return this.dialogService.open(component as Type<unknown>, {
      ...this.defaultDialogOptions,
      ...options,
    });
  }

  launchLazyDialog(dialogFn: () => Promise<DynamicDialogRef | null>): Promise<DynamicDialogRef | null> {
    return dialogFn().catch(err => {
      console.error('Failed to load dialog', err);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('common.error'),
        detail: this.t.translate('common.dialogLoadError')
      });
      return null;
    });
  }

  async openDashboardSettingsDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {DashboardSettingsComponent} = await import('../../features/dashboard/components/dashboard-settings/dashboard-settings.component');
      return this.openDialog(DashboardSettingsComponent, {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openLibraryCreateDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {LibraryCreatorComponent} = await import('../../features/library-creator/library-creator.component');
      return this.openDialog(LibraryCreatorComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openDirectoryPickerDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {DirectoryPickerComponent} = await import('../components/directory-picker/directory-picker.component');
      return this.openDialog(DirectoryPickerComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openLibraryEditDialog(libraryId: number): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {LibraryCreatorComponent} = await import('../../features/library-creator/library-creator.component');
      return this.openDialog(LibraryCreatorComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          mode: 'edit',
          libraryId: libraryId
        }
      });
    });
  }

  async openLibraryMetadataFetchDialog(libraryId: number): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {MetadataFetchOptionsComponent} = await import('../../features/metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component');
      return this.openDialog(MetadataFetchOptionsComponent, {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
        data: {
          libraryId: libraryId,
          metadataRefreshType: MetadataRefreshType.LIBRARY,
        },
      });
    });
  }

  async openShelfEditDialog(shelfId: number): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {ShelfEditDialogComponent} = await import('../../features/book/components/shelf-edit-dialog/shelf-edit-dialog.component');
      return this.openDialog(ShelfEditDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
        data: {
          shelfId: shelfId
        },
      });
    });
  }

  async openFileUploadDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {BookUploaderComponent} = await import('../components/book-uploader/book-uploader.component');
      return this.openDialog(BookUploaderComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openCreateUserDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {CreateUserDialogComponent} = await import('../../features/settings/user-management/create-user-dialog/create-user-dialog.component');
      return this.openDialog(CreateUserDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openUserProfileDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {UserProfileDialogComponent} = await import('../../features/settings/user-profile-dialog/user-profile-dialog.component');
      return this.openDialog(UserProfileDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openMagicShelfCreateDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {MagicShelfComponent} = await import('../../features/magic-shelf/component/magic-shelf-component');
      return this.openDialog(MagicShelfComponent, {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openMagicShelfEditDialog(shelfId: number): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {MagicShelfComponent} = await import('../../features/magic-shelf/component/magic-shelf-component');
      return this.openDialog(MagicShelfComponent, {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
        data: {
          id: shelfId,
          editMode: true,
        }
      });
    });
  }

  async openVersionChangelogDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {VersionChangelogDialogComponent} = await import('../layout/layout-sidebar/version-changelog-dialog/version-changelog-dialog.component');
      return this.openDialog(VersionChangelogDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openEmailRecipientDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {CreateEmailRecipientDialogComponent} = await import('../../features/settings/email-v2/create-email-recipient-dialog/create-email-recipient-dialog.component');
      return this.openDialog(CreateEmailRecipientDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openEmailProviderDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {CreateEmailProviderDialogComponent} = await import('../../features/settings/email-v2/create-email-provider-dialog/create-email-provider-dialog.component');
      return this.openDialog(CreateEmailProviderDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      });
    });
  }

  async openBookdropFinalizeResultDialog(result: BookdropFinalizeResult): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {BookdropFinalizeResultDialogComponent} = await import('../../features/bookdrop/component/bookdrop-finalize-result-dialog/bookdrop-finalize-result-dialog.component');
      return this.openDialog(BookdropFinalizeResultDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          result: result,
        },
      });
    });
  }

  async openMetadataReviewDialog(taskId: string): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {MetadataReviewDialogComponent} = await import('../../features/metadata/component/metadata-review-dialog/metadata-review-dialog-component');
      return this.openDialog(MetadataReviewDialogComponent, {
        showHeader: false,
        styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        data: {
          taskId,
        },
      });
    });
  }

  async openIconPickerDialog(): Promise<DynamicDialogRef | null> {
    return this.launchLazyDialog(async () => {
      const {IconPickerComponent} = await import('../components/icon-picker/icon-picker-component');
      return this.openDialog(IconPickerComponent, {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      });
    });
  }
}
