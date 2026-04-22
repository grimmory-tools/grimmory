import {inject, Injectable, Type} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {TranslocoService} from '@jsverse/transloco';
import {MetadataRefreshType} from '../../features/metadata/model/request/metadata-refresh-type.enum';
import {BookdropFinalizeResult} from '../../features/bookdrop/service/bookdrop.service';
import {take} from 'rxjs/operators';

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

  readonly dialogService = inject(DialogService);
  private readonly messageService = inject(MessageService);
  private readonly t = inject(TranslocoService);

  /**
   * Tracks in-flight lazy dialog opens by key so a fast double-click (or a
   * re-triggered open while the chunk is still downloading) resolves to the
   * same dialog instead of spawning two copies.
   */
  private readonly inflightOpens = new Map<string, Promise<DynamicDialogRef | null>>();

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

  openDialog(component: Type<unknown>, options: object): DynamicDialogRef | null {
    return this.dialogService.open(component, {
      ...this.defaultDialogOptions,
      ...options,
    });
  }

  /**
   * Wraps a dynamic `import()` + `openDialog` pair with:
   *   - chunk-load error handling (shows a toast instead of an unhandled rejection
   *     when a stale client hits a redeployed or offline chunk),
   *   - double-open guard (concurrent calls with the same key share a promise).
   */
  private lazyOpen(
    key: string,
    importer: () => Promise<Type<unknown>>,
    options: object,
  ): Promise<DynamicDialogRef | null> {
    const existing = this.inflightOpens.get(key);
    if (existing) {
      return existing;
    }

    const promise = importer()
      .then(component => {
        const ref = this.openDialog(component, options);
        if (!ref) {
          this.inflightOpens.delete(key);
          return null;
        }
        ref.onClose.pipe(take(1)).subscribe(() => {
          this.inflightOpens.delete(key);
        });
        return ref;
      })
      .catch(error => {
        console.error(`[DialogLauncher] Failed to load chunk for "${key}"`, error);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.chunkLoadFailedSummary'),
          detail: this.t.translate('common.chunkLoadFailedDetail'),
          life: 6000,
        });
        this.inflightOpens.delete(key);
        return null;
      });

    this.inflightOpens.set(key, promise);
    return promise;
  }

  openDashboardSettingsDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'dashboardSettings',
      async () => (await import('../../features/dashboard/components/dashboard-settings/dashboard-settings.component')).DashboardSettingsComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openLibraryCreateDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'libraryCreate',
      async () => (await import('../../features/library-creator/library-creator.component')).LibraryCreatorComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openDirectoryPickerDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'directoryPicker',
      async () => (await import('../components/directory-picker/directory-picker.component')).DirectoryPickerComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openLibraryEditDialog(libraryId: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `libraryEdit:${libraryId}`,
      async () => (await import('../../features/library-creator/library-creator.component')).LibraryCreatorComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          mode: 'edit',
          libraryId: libraryId,
        },
      },
    );
  }

  openLibraryMetadataFetchDialog(libraryId: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `libraryMetadataFetch:${libraryId}`,
      async () => (await import('../../features/metadata/component/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component')).MetadataFetchOptionsComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
        data: {
          libraryId: libraryId,
          metadataRefreshType: MetadataRefreshType.LIBRARY,
        },
      },
    );
  }

  openShelfEditDialog(shelfId: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `shelfEdit:${shelfId}`,
      async () => (await import('../../features/book/components/shelf-edit-dialog/shelf-edit-dialog.component')).ShelfEditDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
        data: {
          shelfId: shelfId,
        },
      },
    );
  }

  openFileUploadDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'fileUpload',
      async () => (await import('../components/book-uploader/book-uploader.component')).BookUploaderComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openCreateUserDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'createUser',
      async () => (await import('../../features/settings/user-management/create-user-dialog/create-user-dialog.component')).CreateUserDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openUserProfileDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'userProfile',
      async () => (await import('../../features/settings/user-profile-dialog/user-profile-dialog.component')).UserProfileDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.SM} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openMagicShelfCreateDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'magicShelfCreate',
      async () => (await import('../../features/magic-shelf/component/magic-shelf-component')).MagicShelfComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openMagicShelfEditDialog(shelfId: number): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `magicShelfEdit:${shelfId}`,
      async () => (await import('../../features/magic-shelf/component/magic-shelf-component')).MagicShelfComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.XL} ${DialogStyle.MINIMAL}`,
        data: {
          id: shelfId,
          editMode: true,
        },
      },
    );
  }

  openVersionChangelogDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'versionChangelog',
      async () => (await import('../layout/layout-menu/version-changelog-dialog/version-changelog-dialog.component')).VersionChangelogDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openEmailRecipientDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'emailRecipient',
      async () => (await import('../../features/settings/email-v2/create-email-recipient-dialog/create-email-recipient-dialog.component')).CreateEmailRecipientDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openEmailProviderDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'emailProvider',
      async () => (await import('../../features/settings/email-v2/create-email-provider-dialog/create-email-provider-dialog.component')).CreateEmailProviderDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
      },
    );
  }

  openBookdropFinalizeResultDialog(result: BookdropFinalizeResult): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'bookdropFinalizeResult',
      async () => (await import('../../features/bookdrop/component/bookdrop-finalize-result-dialog/bookdrop-finalize-result-dialog.component')).BookdropFinalizeResultDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.MD} ${DialogStyle.MINIMAL}`,
        data: {
          result: result,
        },
      },
    );
  }

  openMetadataReviewDialog(taskId: string): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      `metadataReview:${taskId}`,
      async () => (await import('../../features/metadata/component/metadata-review-dialog/metadata-review-dialog-component')).MetadataReviewDialogComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.FULL} ${DialogStyle.MINIMAL}`,
        data: {
          taskId,
        },
      },
    );
  }

  openIconPickerDialog(): Promise<DynamicDialogRef | null> {
    return this.lazyOpen(
      'iconPicker',
      async () => (await import('../components/icon-picker/icon-picker-component')).IconPickerComponent,
      {
        showHeader: false,
        styleClass: `${DialogSize.LG} ${DialogStyle.MINIMAL}`,
      },
    );
  }
}
