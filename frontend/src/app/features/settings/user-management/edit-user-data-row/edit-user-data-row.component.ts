import { Component, effect, inject, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Checkbox } from 'primeng/checkbox';
import { MultiSelect } from 'primeng/multiselect';
import { InputText } from 'primeng/inputtext';
import { TranslocoService } from '@jsverse/transloco';
import { Library } from '../../../book/model/library.model';
import { LibraryService } from '../../../book/service/library.service';
import { UserWithEditing } from '../user-management.component';
import { ContentRestrictionsEditorComponent } from '../content-restrictions-editor/content-restrictions-editor.component';
 
@Component({
  selector: 'app-edit-user-data-row',
  imports: [
    FormsModule,
    Checkbox,
    MultiSelect,
    InputText,
    ContentRestrictionsEditorComponent,
  ],
  templateUrl: './edit-user-data-row.component.html',
  styleUrls: ['./edit-user-data-row.component.scss'],
})
export class EditUserDataRowComponent  {
  localLibraryIds: number[] = [];
 
  user  = input.required<UserWithEditing>();
 
  libraryIdsChange = output<number[]>();
 
  private libraryService = inject(LibraryService);
  private translocoService = inject(TranslocoService);

  constructor() {
    effect(() => {
      this.localLibraryIds = [
        ...(this.user().selectedLibraryIds ?? [])
      ];
    });
}
 
  get allLibraries(): Library[] {
    return this.libraryService.libraries();
  }
 
  get isPermissionDisabled(): boolean {
    return !this.user().isEditing || this.user().permissions.admin;
  }
 
  get libraryAccessLabel(): string {
    const u = this.user();
    if (u.libraryNames) return u.libraryNames;
    return this.translocoService.translate(
      u.permissions.admin
        ? 'settingsUsers.userInfo.allLibrariesAdmin'
        : 'settingsUsers.userInfo.noLibrariesAssigned'
    );
  }
 
  t(key: string, params?: Record<string, unknown>): string {
    return this.translocoService.translate(`settingsUsers.${key}`, params ?? {});
  }
 

  onLibraryIdsChange(): void {
    this.libraryIdsChange.emit([...this.localLibraryIds]);
  }
 
  onAdminCheckboxChange(): void {
    const u = this.user();
    if (u.permissions.admin) {
      Object.assign(u.permissions, {
        canUpload: true,
        canDownload: true,
        canDeleteBook: true,
        canEditMetadata: true,
        canManageLibrary: true,
        canEmailBook: true,
        canSyncKoReader: true,
        canSyncKobo: true,
        canAccessOpds: true,
        canAccessBookdrop: true,
        canAccessLibraryStats: true,
        canAccessUserStats: true,
        canManageMetadataConfig: true,
        canManageGlobalPreferences: true,
        canAccessTaskManager: true,
        canManageEmailConfig: true,
        canManageIcons: true,
        canManageFonts: true,
        canBulkAutoFetchMetadata: true,
        canBulkCustomFetchMetadata: true,
        canBulkEditMetadata: true,
        canBulkRegenerateCover: true,
        canMoveOrganizeFiles: true,
        canBulkLockUnlockMetadata: true,
        canBulkResetGrimmoryReadProgress: true,
        canBulkResetBookloreReadProgress: true,
        canBulkResetKoReaderReadProgress: true,
        canBulkResetBookReadStatus: true,
      });
    }
  }
}