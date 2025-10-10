import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {ConfirmationService, MessageService} from 'primeng/api';
import {Clipboard} from '@angular/cdk/clipboard';
import {KoboService, KoboSyncSettings} from '../kobo.service';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {InputText} from 'primeng/inputtext';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {UserService} from '../../user-management/user.service';
import {Subject} from 'rxjs';
import {debounceTime, filter, take, takeUntil} from 'rxjs/operators';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {Slider} from 'primeng/slider';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {SettingsHelperService} from '../../../core/service/settings-helper.service';
import {AppSettingKey, KoboSettings} from '../../../core/model/app-settings.model';
import {ShelfService} from '../../../book/service/shelf.service';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';

@Component({
  selector: 'app-kobo-sync-setting-component',
  standalone: true,
  templateUrl: './kobo-sync-settings-component.html',
  styleUrl: './kobo-sync-settings-component.scss',
  imports: [FormsModule, Button, InputText, ConfirmDialog, ToggleSwitch, Slider, ExternalDocLinkComponent],
  providers: [MessageService, ConfirmationService]
})
export class KoboSyncSettingsComponent implements OnInit, OnDestroy {
  private koboService = inject(KoboService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private clipboard = inject(Clipboard);
  protected userService = inject(UserService);
  protected appSettingsService = inject(AppSettingsService);
  protected settingsHelperService = inject(SettingsHelperService);
  private shelfService = inject(ShelfService);

  private readonly destroy$ = new Subject<void>();
  private readonly sliderChange$ = new Subject<void>();

  hasKoboTokenPermission = false;
  isAdmin = false;
  credentialsSaved = false;
  showToken = false;

  koboSettings: KoboSettings = {
    convertToKepub: false,
    conversionLimitInMb: 100
  };

  koboSyncSettings: KoboSyncSettings = {
    token: '',
    syncEnabled: false
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
  }

  private setupUserStateSubscription() {
    this.userService.userState$.pipe(
      filter(userState => !!userState?.user && userState.loaded),
      takeUntil(this.destroy$)
    ).subscribe(userState => {
      this.hasKoboTokenPermission = (userState.user?.permissions.canSyncKobo) ?? false;
      this.isAdmin = userState.user?.permissions.admin ?? false;
      if (this.hasKoboTokenPermission) {
        this.loadKoboUserSettings();
      }
      if (this.isAdmin) {
        this.loadKoboAdminSettings();
      }
    });
  }

  private loadKoboUserSettings() {
    this.koboService.getUser().subscribe({
      next: (settings: KoboSyncSettings) => {
        this.koboSyncSettings.token = settings.token;
        this.koboSyncSettings.syncEnabled = settings.syncEnabled;
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
      });
  }

  copyText(text: string) {
    this.clipboard.copy(text);
    this.messageService.add({severity: 'success', summary: 'Copied', detail: 'Token copied to clipboard'});
  }

  toggleShowToken() {
    this.showToken = !this.showToken;
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
        accept: () => this.performToggle(false),
        reject: () => {
          this.koboSyncSettings.syncEnabled = true;
        }
      });
    } else {
      this.performToggle(true);
    }
  }

  private performToggle(enabled: boolean) {
    this.koboService.toggleSync(enabled).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Sync Updated',
          detail: enabled
            ? 'Kobo sync enabled'
            : 'Kobo sync disabled'
        });
        this.shelfService.reloadShelves();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update sync setting'
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
