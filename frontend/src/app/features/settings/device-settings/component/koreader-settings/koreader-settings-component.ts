import {Component, DestroyRef, effect, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';

import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Button} from 'primeng/button';
import {Toast} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {KoreaderService} from './koreader.service';
import {UserService} from '../../../user-management/user.service';
import {ExternalDocLinkComponent} from '../../../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {API_CONFIG} from '../../../../../core/config/api-config';

@Component({
  standalone: true,
  selector: 'app-koreader-settings-component',
  imports: [
    FormsModule,
    InputText,
    ToggleSwitch,
    Button,
    Toast,
    ExternalDocLinkComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  providers: [MessageService],
  templateUrl: './koreader-settings-component.html',
  styleUrls: ['./koreader-settings-component.scss']
})
export class KoreaderSettingsComponent {
  editMode = true;
  showPassword = false;
  koReaderSyncEnabled = false;
  syncWithGrimmoryReader = false;
  koReaderUsername = '';
  koReaderPassword = '';
  credentialsSaved = false;
  readonly koreaderEndpoint = `${API_CONFIG.BASE_URL}/api/koreader`;

  private readonly messageService = inject(MessageService);
  private readonly koreaderService = inject(KoreaderService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  hasPermission = false;
  private prevHasPermission = false;

  constructor() {
    effect(() => {
      const user = this.userService.currentUser();
      if (!user) return;

      const currHasPermission = (user.permissions.canSyncKoReader || user.permissions.admin) ?? false;
      this.hasPermission = currHasPermission;
      if (currHasPermission && !this.prevHasPermission) {
        this.loadKoreaderSettings();
      }
      this.prevHasPermission = currHasPermission;
    });
  }

  private loadKoreaderSettings() {
    this.koreaderService.getUser().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: koreaderUser => {
        this.koReaderUsername = koreaderUser.username;
        this.koReaderPassword = koreaderUser.password;
        this.koReaderSyncEnabled = koreaderUser.syncEnabled;
        this.syncWithGrimmoryReader = koreaderUser.syncWithGrimmoryReader ?? koreaderUser.syncWithBookloreReader ?? false;
        this.credentialsSaved = true;
      },
      error: err => {
        if (err.status !== 404) {
          this.messageService.add({
            severity: 'error',
            summary: this.t.translate('common.error'),
            detail: this.t.translate('settingsDevice.koreader.loadError')
          });
        }
      }
    });
  }


  get canSave(): boolean {
    const u = this.koReaderUsername?.trim() ?? '';
    const p = this.koReaderPassword ?? '';
    return u.length > 0 && p.length >= 6;
  }

  onEditSave() {
    if (!this.editMode) {
      this.saveCredentials();
    }
    this.editMode = !this.editMode;
  }

  onToggleEnabled(enabled: boolean) {
    this.koreaderService.toggleSync(enabled).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.koReaderSyncEnabled = enabled;
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.koreader.syncUpdated'), detail: enabled ? this.t.translate('settingsDevice.koreader.syncEnabled') : this.t.translate('settingsDevice.koreader.syncDisabled')});
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: this.t.translate('settingsDevice.koreader.syncUpdateFailed'), detail: this.t.translate('settingsDevice.koreader.syncUpdateError')});
      }
    });
  }

  onToggleSyncWithGrimmoryReader(enabled: boolean) {
    this.koreaderService.toggleSyncProgressWithGrimmoryReader(enabled).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.syncWithGrimmoryReader = enabled;
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsDevice.koreader.syncUpdated'),
          detail: enabled ? this.t.translate('settingsDevice.koreader.grimmoryReaderEnabled') : this.t.translate('settingsDevice.koreader.grimmoryReaderDisabled')
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsDevice.koreader.syncUpdateFailed'),
          detail: this.t.translate('settingsDevice.koreader.grimmoryReaderError')
        });
      }
    });
  }

  toggleShowPassword() {
    this.showPassword = !this.showPassword;
  }


  saveCredentials() {
    this.koreaderService.createUser(this.koReaderUsername, this.koReaderPassword)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.credentialsSaved = true;
          this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.koreader.saved'), detail: this.t.translate('settingsDevice.koreader.credentialsSaved')});
        },
        error: () =>
          this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('settingsDevice.koreader.credentialsError')})
      });
  }


  copyText(text: string, label: string = 'Text') {
    if (!text) {
      return;
    }
    navigator.clipboard.writeText(text).then(() => {
      this.messageService.add({
        severity: 'success',
        summary: this.t.translate('settingsDevice.copied'),
        detail: this.t.translate('settingsDevice.copiedDetail', {label})
      });
    }).catch(err => {
      console.error('Copy failed', err);
      this.messageService.add({
        severity: 'error',
        summary: this.t.translate('settingsDevice.copyFailed'),
        detail: this.t.translate('settingsDevice.copyFailedDetail', {label})
      });
    });
  }

}
