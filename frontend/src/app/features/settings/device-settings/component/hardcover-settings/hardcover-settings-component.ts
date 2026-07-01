import {Component, DestroyRef, computed, effect, inject, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Button} from 'primeng/button';
import {Toast} from 'primeng/toast';
import {MessageService} from 'primeng/api';
import {ExternalDocLinkComponent} from '../../../../../shared/components/external-doc-link/external-doc-link.component';
import {UserService} from '../../../user-management/user.service';
import {HardcoverSyncSettingsService} from './hardcover-sync-settings.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {Checkbox} from 'primeng/checkbox';

@Component({
  standalone: true,
  selector: 'app-hardcover-settings-component',
  imports: [
    FormsModule,
    InputText,
    ToggleSwitch,
    Button,
    Toast,
    ExternalDocLinkComponent,
    TranslocoDirective,
    Checkbox
  ],
  providers: [MessageService],
  templateUrl: './hardcover-settings-component.html',
  styleUrls: ['./hardcover-settings-component.scss']
})
export class HardcoverSettingsComponent {
  private readonly messageService = inject(MessageService);
  private readonly hardcoverSyncSettingsService = inject(HardcoverSyncSettingsService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly hasPermission = computed(() => {
    const user = this.userService.currentUser();
    return !!(user?.permissions.canSyncKoReader || user?.permissions.canSyncKobo || user?.permissions.admin);
  });
  hardcoverSyncEnabled = signal(false);
  hardcoverApiKey = signal('');
  showHardcoverApiKey = signal(false);
  private prevHasPermission = false;
  private savedHardcoverSyncEnabled = false;
  private savedHardcoverApiKey = '';
  overwriteExistingData = false;

  constructor() {
    effect(() => {
      const currHasPermission = this.hasPermission();
      if (currHasPermission && !this.prevHasPermission) {
        this.loadHardcoverSettings();
      }
      this.prevHasPermission = currHasPermission;
    });
  }

  private loadHardcoverSettings() {
    this.hardcoverSyncSettingsService.getSettings().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: settings => {
        this.hardcoverSyncEnabled.set(settings.hardcoverSyncEnabled ?? false);
        this.hardcoverApiKey.set(settings.hardcoverApiKey ?? '');
        this.rememberSavedSettings();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsDevice.hardcover.loadError')
        });
      }
    });
  }

  toggleShowHardcoverApiKey() {
    this.showHardcoverApiKey.update(showHardcoverApiKey => !showHardcoverApiKey);
  }

  triggerHardcoverImport() {
    this.hardcoverImport();
  }

  onHardcoverSyncToggle(enabled: boolean) {
    this.hardcoverSyncEnabled.set(enabled);
    const message = enabled
      ? this.t.translate('settingsDevice.hardcover.syncEnabledMsg')
      : this.t.translate('settingsDevice.hardcover.syncDisabledMsg');
    this.updateHardcoverSettings(message);
  }

  onHardcoverApiKeyChange() {
    this.updateHardcoverSettings(this.t.translate('settingsDevice.hardcover.apiKeyUpdated'));
  }

  private hardcoverImport() {
    this.hardcoverSyncSettingsService.startImport(this.overwriteExistingData).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.hardcover.importStarted')});
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsDevice.hardcover.importFailed'),
          detail: this.t.translate('settingsDevice.hardcover.importError')
        });
      }
    })
  }

  private updateHardcoverSettings(successMessage: string) {
    this.hardcoverSyncSettingsService.updateSettings({
      hardcoverSyncEnabled: this.hardcoverSyncEnabled(),
      hardcoverApiKey: this.hardcoverApiKey()
    }).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: settings => {
        this.hardcoverSyncEnabled.set(settings.hardcoverSyncEnabled ?? false);
        this.hardcoverApiKey.set(settings.hardcoverApiKey ?? '');
        this.rememberSavedSettings();
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.hardcover.settingsUpdated'), detail: successMessage});
      },
      error: () => {
        this.restoreSavedSettings();
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('settingsDevice.hardcover.updateFailed'),
          detail: this.t.translate('settingsDevice.hardcover.updateError')
        });
      }
    });
  }

  private rememberSavedSettings() {
    this.savedHardcoverSyncEnabled = this.hardcoverSyncEnabled();
    this.savedHardcoverApiKey = this.hardcoverApiKey();
  }

  private restoreSavedSettings() {
    this.hardcoverSyncEnabled.set(this.savedHardcoverSyncEnabled);
    this.hardcoverApiKey.set(this.savedHardcoverApiKey);
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
