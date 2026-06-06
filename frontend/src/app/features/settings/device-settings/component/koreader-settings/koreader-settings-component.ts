import {Component, DestroyRef, computed, effect, inject, signal} from '@angular/core';
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
  editMode = signal(true);
  showPassword = signal(false);
  koReaderSyncEnabled = signal(false);
  syncWithWebReader = signal(false);
  koReaderUsername = signal('');
  koReaderPassword = signal('');
  credentialsSaved = signal(false);
  readonly koreaderEndpoint = `${window.location.origin}/api/koreader`;

  private readonly messageService = inject(MessageService);
  private readonly koreaderService = inject(KoreaderService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly hasPermission = computed(() => {
    const user = this.userService.currentUser();
    return !!(user?.permissions.canSyncKoReader || user?.permissions.admin);
  });
  private prevHasPermission = false;

  readonly canSave = computed(() => {
    const username = this.koReaderUsername().trim();
    const password = this.koReaderPassword();
    return username.length > 0 && password.length >= 6;
  });

  constructor() {
    effect(() => {
      const currHasPermission = this.hasPermission();
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
        this.koReaderUsername.set(koreaderUser.username);
        this.koReaderPassword.set(koreaderUser.password);
        this.koReaderSyncEnabled.set(koreaderUser.syncEnabled);
        this.syncWithWebReader.set(koreaderUser.syncWithWebReader ?? false);
        this.credentialsSaved.set(true);
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


  onEditSave() {
    if (!this.editMode()) {
      this.saveCredentials();
    }
    this.editMode.update(editMode => !editMode);
  }

  onToggleEnabled(enabled: boolean) {
    const previousEnabled = this.koReaderSyncEnabled();
    this.koReaderSyncEnabled.set(enabled);
    this.koreaderService.toggleSync(enabled).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.koReaderSyncEnabled.set(enabled);
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.koreader.syncUpdated'), detail: enabled ? this.t.translate('settingsDevice.koreader.syncEnabled') : this.t.translate('settingsDevice.koreader.syncDisabled')});
      },
      error: () => {
        this.koReaderSyncEnabled.set(previousEnabled);
        this.messageService.add({severity: 'error', summary: this.t.translate('settingsDevice.koreader.syncUpdateFailed'), detail: this.t.translate('settingsDevice.koreader.syncUpdateError')});
      }
    });
  }

  onToggleSyncWithWebReader(enabled: boolean) {
    const previousSyncWithWebReader = this.syncWithWebReader();
    this.syncWithWebReader.set(enabled);
    this.koreaderService.toggleSyncProgressWithWebReader(enabled).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.syncWithWebReader.set(enabled);
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsDevice.koreader.syncUpdated'),
          detail: enabled ? this.t.translate('settingsDevice.koreader.grimmoryReaderEnabled') : this.t.translate('settingsDevice.koreader.grimmoryReaderDisabled')
        });
      },
      error: () => {
        this.syncWithWebReader.set(previousSyncWithWebReader);
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsDevice.koreader.syncUpdateFailed'),
          detail: this.t.translate('settingsDevice.koreader.grimmoryReaderError')
        });
      }
    });
  }

  toggleShowPassword() {
    this.showPassword.update(showPassword => !showPassword);
  }


  saveCredentials() {
    this.koreaderService.createUser(this.koReaderUsername(), this.koReaderPassword())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.credentialsSaved.set(true);
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
