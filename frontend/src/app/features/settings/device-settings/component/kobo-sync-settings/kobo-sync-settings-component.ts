import {Component, DestroyRef, effect, inject, OnInit} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {ConfirmationService, MessageService} from 'primeng/api';
import {KoboService, KoboSyncSettings} from './kobo.service';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {UserService} from '../../../user-management/user.service';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Slider} from 'primeng/slider';
import {Divider} from 'primeng/divider';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../../shared/service/settings-helper.service';
import {AppSettingKey, AppSettings, KoboSettings} from '../../../../../shared/model/app-settings.model';
import {ShelfService} from '../../../../book/service/shelf.service';
import {ExternalDocLinkComponent} from '../../../../../shared/components/external-doc-link/external-doc-link.component';
import {Toast} from 'primeng/toast';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-kobo-sync-setting-component',
  standalone: true,
  templateUrl: './kobo-sync-settings-component.html',
  styleUrl: './kobo-sync-settings-component.scss',
  imports: [FormsModule, Button, InputText, ConfirmDialog, ToggleSwitch, Slider, Divider, ExternalDocLinkComponent, Toast, TranslocoDirective],
  providers: [MessageService, ConfirmationService]
})
export class KoboSyncSettingsComponent implements OnInit {
  private koboService = inject(KoboService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  protected userService = inject(UserService);
  protected appSettingsService = inject(AppSettingsService);
  protected settingsHelperService = inject(SettingsHelperService);
  private shelfService = inject(ShelfService);
  private readonly t = inject(TranslocoService);

  private readonly destroyRef = inject(DestroyRef);
  private readonly sliderChange$ = new Subject<void>();
  private readonly progressThresholdChange$ = new Subject<void>();

  hasKoboTokenPermission = false;
  isAdmin = false;
  credentialsSaved = false;
  showToken = false;

  koboSettings: KoboSettings = {
    convertToKepub: false,
    conversionLimitInMb: 100,
    convertCbxToEpub: false,
    conversionImageCompressionPercentage: 85,
    conversionLimitInMbForCbx: 100,
    forceEnableHyphenation: false,
    forwardToKoboStore: false
  };

  koboSyncSettings: KoboSyncSettings = {
    token: '',
    syncEnabled: false,
    progressMarkAsReadingThreshold: 1,
    progressMarkAsFinishedThreshold: 99,
    autoAddToShelf: true,
    twoWayProgressSync: false
  }

  ngOnInit() {
    this.setupSliderDebouncing();
  }

  private setupSliderDebouncing() {
    this.sliderChange$.pipe(
      debounceTime(500),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.saveSettings();
    });

    this.progressThresholdChange$.pipe(
      debounceTime(500),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateKoboSettings(this.t.translate('settingsDevice.kobo.progressUpdated'));
    });
  }

  private prevHasKoboTokenPermission = false;

  private readonly syncUserEffect = effect(() => {
    const user = this.userService.currentUser();
    if (!user) return;

    const currHasKoboTokenPermission = (user.permissions.canSyncKobo) ?? false;
    const currIsAdmin = user.permissions.admin ?? false;

    if (currHasKoboTokenPermission && !this.prevHasKoboTokenPermission) {
      this.hasKoboTokenPermission = true;
      this.loadKoboUserSettings();
    } else {
      this.hasKoboTokenPermission = currHasKoboTokenPermission;
    }

    this.isAdmin = currIsAdmin;

    this.prevHasKoboTokenPermission = currHasKoboTokenPermission;
  });

  private readonly syncAdminSettingsEffect = effect(() => {
    const user = this.userService.currentUser();
    const settings = this.appSettingsService.appSettings();
    if (!(user?.permissions.admin) || !settings) {
      return;
    }

    this.applyKoboAdminSettings(settings);
  });

  private loadKoboUserSettings() {
    this.koboService.getUser().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (settings: KoboSyncSettings) => {
        this.koboSyncSettings.token = settings.token;
        this.koboSyncSettings.syncEnabled = settings.syncEnabled;
        this.koboSyncSettings.progressMarkAsReadingThreshold = settings.progressMarkAsReadingThreshold ?? 1;
        this.koboSyncSettings.progressMarkAsFinishedThreshold = settings.progressMarkAsFinishedThreshold ?? 99;
        this.koboSyncSettings.autoAddToShelf = settings.autoAddToShelf ?? false;
        this.koboSyncSettings.twoWayProgressSync = settings.twoWayProgressSync ?? false;
        this.credentialsSaved = !!settings.token;
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('settingsDevice.kobo.loadError')});
      }
    });
  }

  private applyKoboAdminSettings(settings: AppSettings) {
    this.koboSettings.convertToKepub = settings.koboSettings?.convertToKepub ?? true;
    this.koboSettings.conversionLimitInMb = settings.koboSettings?.conversionLimitInMb ?? 100;
    this.koboSettings.convertCbxToEpub = settings.koboSettings?.convertCbxToEpub ?? false;
    this.koboSettings.conversionLimitInMbForCbx = settings.koboSettings?.conversionLimitInMbForCbx ?? 100;
    this.koboSettings.forceEnableHyphenation = settings.koboSettings?.forceEnableHyphenation ?? false;
    this.koboSettings.conversionImageCompressionPercentage = settings.koboSettings?.conversionImageCompressionPercentage ?? 85;
    this.koboSettings.forwardToKoboStore = settings.koboSettings?.forwardToKoboStore ?? false;
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

  toggleShowToken() {
    this.showToken = !this.showToken;
  }


  confirmRegenerateToken() {
    this.confirmationService.confirm({
      message: this.t.translate('settingsDevice.kobo.confirmRegenerate'),
      header: this.t.translate('settingsDevice.kobo.confirmRegenerateHeader'),
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.regenerateToken()
    });
  }

  private regenerateToken() {
    this.koboService.createOrUpdateToken().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (settings) => {
        this.koboSyncSettings.token = settings.token;
        this.credentialsSaved = true;
        this.messageService.add({severity: 'success', summary: this.t.translate('settingsDevice.kobo.tokenRegenerated'), detail: this.t.translate('settingsDevice.kobo.tokenRegeneratedDetail')});
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: this.t.translate('common.error'), detail: this.t.translate('settingsDevice.kobo.tokenRegenerateError')});
      }
    });
  }

  onToggleChange() {
    this.saveSettings();
  }

  onSliderChange() {
    this.sliderChange$.next();
  }

  onSyncToggle() {
    if (!this.koboSyncSettings.syncEnabled) {
      this.confirmationService.confirm({
        message: this.t.translate('settingsDevice.kobo.confirmDisable'),
        header: this.t.translate('settingsDevice.kobo.confirmDisableHeader'),
        icon: 'pi pi-exclamation-triangle',
        accept: () => this.updateKoboSettings(this.t.translate('settingsDevice.kobo.syncDisabled')),
        reject: () => {
          this.koboSyncSettings.syncEnabled = true;
        }
      });
    } else {
      this.updateKoboSettings(this.t.translate('settingsDevice.kobo.syncEnabled'));
    }
  }

  onProgressThresholdsChange() {
    this.progressThresholdChange$.next();
  }

  onAutoAddToggle() {
    const message = this.koboSyncSettings.autoAddToShelf
      ? this.t.translate('settingsDevice.kobo.autoAddEnabled')
      : this.t.translate('settingsDevice.kobo.autoAddDisabled');
    this.updateKoboSettings(message);
  }

  onTwoWaySyncToggle() {
    const message = this.koboSyncSettings.twoWayProgressSync
      ? this.t.translate('settingsDevice.kobo.twoWaySyncEnabled')
      : this.t.translate('settingsDevice.kobo.twoWaySyncDisabled');
    this.updateKoboSettings(message);
  }

  private updateKoboSettings(successMessage: string) {
    this.koboService.updateSettings(this.koboSyncSettings).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('settingsDevice.kobo.settingsUpdated'),
          detail: successMessage
        });
        this.shelfService.reloadShelves();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('common.error'),
          detail: this.t.translate('settingsDevice.kobo.settingsUpdateError')
        });
      }
    });
  }

  saveSettings() {
    this.settingsHelperService.saveSetting(AppSettingKey.KOBO_SETTINGS, this.koboSettings);
  }
}
