import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {ConfirmationService, MessageService} from 'primeng/api';
import {KoboService, KoboSyncSettings} from './kobo.service';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {UserService} from '../../../user-management/user.service';
import {Subject} from 'rxjs';
import {debounceTime, filter, take, takeUntil} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Slider} from 'primeng/slider';
import {Divider} from 'primeng/divider';
import {AppSettingsService} from '../../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../../shared/service/settings-helper.service';
import {AppSettingKey, KoboSettings} from '../../../../../shared/model/app-settings.model';
import {ShelfService} from '../../../../book/service/shelf.service';
import {ExternalDocLinkComponent} from '../../../../../shared/components/external-doc-link/external-doc-link.component';
import {ToastModule} from 'primeng/toast';

@Component({
  selector: 'app-kobo-sync-setting-component',
  standalone: true,
  templateUrl: './kobo-sync-settings-component.html',
  styleUrl: './kobo-sync-settings-component.scss',
  imports: [FormsModule, Button, InputText, ConfirmDialog, ToggleSwitch, Slider, Divider, ExternalDocLinkComponent, ToastModule],
  providers: [MessageService, ConfirmationService]
})
export class KoboSyncSettingsComponent implements OnInit, OnDestroy {
  private koboService = inject(KoboService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  protected userService = inject(UserService);
  protected appSettingsService = inject(AppSettingsService);
  protected settingsHelperService = inject(SettingsHelperService);
  private shelfService = inject(ShelfService);

  private readonly destroy$ = new Subject<void>();
  private readonly sliderChange$ = new Subject<void>();
  private readonly progressThresholdChange$ = new Subject<void>();

  hasKoboTokenPermission = false;
  isAdmin = false;
  credentialsSaved = false;
  showToken = false;
  showHardcoverApiKey = false;

  koboSettings: KoboSettings = {
    convertToKepub: false,
    conversionLimitInMb: 100,
    convertCbxToEpub: false,
    conversionImageCompressionPercentage: 85,
    conversionLimitInMbForCbx: 100,
    forceEnableHyphenation: false
  };

  koboSyncSettings: KoboSyncSettings = {
    token: '',
    syncEnabled: false,
    progressMarkAsReadingThreshold: 1,
    progressMarkAsFinishedThreshold: 99,
    autoAddToShelf: true,
    hardcoverApiKey: '',
    hardcoverSyncEnabled: false
  }

  ngOnInit() {
    this.setupSliderDebouncing();
    this.setupUserStateSubscription();
  }

  private setupSliderDebouncing() {
    this.sliderChange$.pipe(
      debounceTime(500),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.saveSettings();
    });

    this.progressThresholdChange$.pipe(
      debounceTime(500),
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateKoboSettings('Progress thresholds updated successfully');
    });
  }

  private setupUserStateSubscription() {
    let prevHasKoboTokenPermission = false;
    let prevIsAdmin = false;
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      const currHasKoboTokenPermission = (userState.user?.permissions.canSyncKobo) ?? false;
      const currIsAdmin = userState.user?.permissions.admin ?? false;

      if (currHasKoboTokenPermission && !prevHasKoboTokenPermission) {
        this.hasKoboTokenPermission = true;
        this.loadKoboUserSettings();
      } else {
        this.hasKoboTokenPermission = currHasKoboTokenPermission;
      }

      if (currIsAdmin && !prevIsAdmin) {
        this.isAdmin = true;
        this.loadKoboAdminSettings();
      } else {
        this.isAdmin = currIsAdmin;
      }

      prevHasKoboTokenPermission = currHasKoboTokenPermission;
      prevIsAdmin = currIsAdmin;
    });
  }

  private loadKoboUserSettings() {
    this.koboService.getUser().subscribe({
      next: (settings: KoboSyncSettings) => {
        this.koboSyncSettings.token = settings.token;
        this.koboSyncSettings.syncEnabled = settings.syncEnabled;
        this.koboSyncSettings.progressMarkAsReadingThreshold = settings.progressMarkAsReadingThreshold ?? 1;
        this.koboSyncSettings.progressMarkAsFinishedThreshold = settings.progressMarkAsFinishedThreshold ?? 99;
        this.koboSyncSettings.autoAddToShelf = settings.autoAddToShelf ?? false;
        this.koboSyncSettings.hardcoverApiKey = settings.hardcoverApiKey ?? '';
        this.koboSyncSettings.hardcoverSyncEnabled = settings.hardcoverSyncEnabled ?? false;
        this.credentialsSaved = !!settings.token;
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to load Kobo settings'});
      }
    });
  }

  private loadKoboAdminSettings() {
    this.appSettingsService.appSettings$
      .pipe(
        filter(settings => settings != null),
        take(1),
      )
      .subscribe(settings => {
        this.koboSettings.convertToKepub = settings?.koboSettings?.convertToKepub ?? true;
        this.koboSettings.conversionLimitInMb = settings?.koboSettings?.conversionLimitInMb ?? 100;
        this.koboSettings.convertCbxToEpub = settings?.koboSettings?.convertCbxToEpub ?? false;
        this.koboSettings.conversionLimitInMbForCbx = settings?.koboSettings?.conversionLimitInMbForCbx ?? 100;
        this.koboSettings.forceEnableHyphenation = settings?.koboSettings?.forceEnableHyphenation ?? false;
        this.koboSettings.conversionImageCompressionPercentage = settings?.koboSettings?.conversionImageCompressionPercentage ?? 85;
      });
  }

  copyText(text: string, label: string = 'Text') {
    if (!text) {
      return;
    }
    navigator.clipboard.writeText(text).then(() => {
      this.messageService.add({
        severity: 'success',
        summary: 'Copied',
        detail: `${label} copied to clipboard`
      });
    }).catch(err => {
      console.error('Copy failed', err);
      this.messageService.add({
        severity: 'error',
        summary: 'Copy Failed',
        detail: `Unable to copy ${label.toLowerCase()} to clipboard`
      });
    });
  }

  toggleShowToken() {
    this.showToken = !this.showToken;
  }

  toggleShowHardcoverApiKey() {
    this.showHardcoverApiKey = !this.showHardcoverApiKey;
  }

  onHardcoverSyncToggle() {
    const message = this.koboSyncSettings.hardcoverSyncEnabled
      ? 'Hardcover sync enabled'
      : 'Hardcover sync disabled';
    this.updateKoboSettings(message);
  }

  onHardcoverApiKeyChange() {
    this.updateKoboSettings('Hardcover API key updated');
  }

  confirmRegenerateToken() {
    this.confirmationService.confirm({
      message: 'This will generate a new token and invalidate the previous one. Continue?',
      header: 'Confirm Regeneration',
      icon: 'pi pi-exclamation-triangle',
      accept: () => this.regenerateToken()
    });
  }

  private regenerateToken() {
    this.koboService.createOrUpdateToken().subscribe({
      next: (settings) => {
        this.koboSyncSettings.token = settings.token;
        this.credentialsSaved = true;
        this.messageService.add({severity: 'success', summary: 'Token regenerated', detail: 'New token generated successfully'});
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to regenerate token'});
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
        message: 'Disabling Kobo sync will delete your Kobo shelf. Are you sure you want to proceed?',
        header: 'Confirm Disable',
        icon: 'pi pi-exclamation-triangle',
        accept: () => this.updateKoboSettings('Kobo sync disabled'),
        reject: () => {
          this.koboSyncSettings.syncEnabled = true;
        }
      });
    } else {
      this.updateKoboSettings('Kobo sync enabled');
    }
  }

  onProgressThresholdsChange() {
    this.progressThresholdChange$.next();
  }

  onAutoAddToggle() {
    const message = this.koboSyncSettings.autoAddToShelf
      ? 'New books will be automatically added to Kobo shelf'
      : 'Auto-add to Kobo shelf disabled';
    this.updateKoboSettings(message);
  }

  private updateKoboSettings(successMessage: string) {
    this.koboService.updateSettings(this.koboSyncSettings).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Settings Updated',
          detail: successMessage
        });
        if (!this.koboSyncSettings.syncEnabled) {
          this.shelfService.reloadShelves();
        }
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update Kobo settings'
        });
      }
    });
  }

  saveSettings() {
    this.settingsHelperService.saveSetting(AppSettingKey.KOBO_SETTINGS, this.koboSettings)
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Settings Saved',
            detail: 'Kobo settings updated successfully'
          });
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Save Failed',
            detail: 'Failed to save Kobo settings'
          });
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
