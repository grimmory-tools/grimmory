import {Component, inject, OnDestroy, OnInit} from '@angular/core';

import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Button} from 'primeng/button';
import {ToastModule} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {KoreaderService} from './koreader.service';
import {UserService} from '../../../user-management/user.service';
import {filter, takeUntil} from 'rxjs/operators';
import {Subject} from 'rxjs';
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
    ToastModule,
    ExternalDocLinkComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  providers: [MessageService],
  templateUrl: './koreader-settings-component.html',
  styleUrls: ['./koreader-settings-component.scss']
})
export class KoreaderSettingsComponent implements OnInit, OnDestroy {
  editMode = true;
  showPassword = false;
  koReaderSyncEnabled = false;
  syncWithBookloreReader = false;
  koReaderUsername = '';
  koReaderPassword = '';
  credentialsSaved = false;
  readonly koreaderEndpoint = `${window.location.origin}/api/koreader`;

  private readonly messageService = inject(MessageService);
  private readonly koreaderService = inject(KoreaderService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);

  private readonly destroy$ = new Subject<void>();
  hasPermission = false;

  ngOnInit() {
    let prevHasPermission = false;
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      const currHasPermission = (userState.user?.permissions.canSyncKoReader || userState.user?.permissions.admin) ?? false;
      this.hasPermission = currHasPermission;
      if (currHasPermission && !prevHasPermission) {
        this.loadKoreaderSettings();
      }
      prevHasPermission = currHasPermission;
    });
  }

  private loadKoreaderSettings() {
    this.koreaderService.getUser().subscribe({
      next: koreaderUser => {
        this.koReaderUsername = koreaderUser.username;
        this.koReaderPassword = koreaderUser.password;
        this.koReaderSyncEnabled = koreaderUser.syncEnabled;
        this.syncWithBookloreReader = koreaderUser.syncWithBookloreReader ?? false;
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
    this.koreaderService.toggleSync(enabled).subscribe({
      next: () => {
        this.koReaderSyncEnabled = enabled;
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.koreader.syncUpdated'), detail: enabled ? this.t.translate('settingsDevice.koreader.syncEnabled') : this.t.translate('settingsDevice.koreader.syncDisabled')});
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: this.t.translate('settingsDevice.koreader.syncUpdateFailed'), detail: this.t.translate('settingsDevice.koreader.syncUpdateError')});
      }
    });
  }

  onToggleSyncWithBookloreReader(enabled: boolean) {
    this.koreaderService.toggleSyncProgressWithBookloreReader(enabled).subscribe({
      next: () => {
        this.syncWithBookloreReader = enabled;
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

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
