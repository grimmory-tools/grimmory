import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from "@angular/forms";
import {ToggleSwitch} from "primeng/toggleswitch";
import {AppSettingKey, AppSettings, PublicReviewSettings, ReviewProviderConfig} from '../../../core/model/app-settings.model';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {SettingsHelperService} from '../../../core/service/settings-helper.service';
import {Observable} from 'rxjs';
import {filter, take} from 'rxjs/operators';

const DEFAULT_PROVIDERS: readonly ReviewProviderConfig[] = [
  {provider: 'Amazon', enabled: true, maxReviews: 5},
  {provider: 'GoodReads', enabled: false, maxReviews: 5},
  {provider: 'Douban', enabled: false, maxReviews: 5}
] as const;

const REQUIRED_PROVIDERS = ['Amazon', 'GoodReads', 'Douban'] as const;

@Component({
  selector: 'app-public-reviews-settings-component',
  imports: [FormsModule, ToggleSwitch],
  templateUrl: './public-reviews-settings-component.html',
  styleUrl: './public-reviews-settings-component.scss'
})
export class PublicReviewsSettingsComponent implements OnInit {

  publicReviewSettings: PublicReviewSettings = {
    downloadEnabled: true,
    providers: [...DEFAULT_PROVIDERS]
  };

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.loadSettings();
  }

  onPublicReviewsToggle(checked: boolean): void {
    this.publicReviewSettings.downloadEnabled = checked;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, this.publicReviewSettings);
  }

  onProviderToggle(providerName: string, enabled: boolean): void {
    this.updateProviderProperty(providerName, 'enabled', enabled);
  }

  onMaxReviewsChange(providerName: string, maxReviews: number): void {
    this.updateProviderProperty(providerName, 'maxReviews', maxReviews);
  }

  private loadSettings(): void {
    this.appSettings$.pipe(
      filter((settings): settings is AppSettings => !!settings),
      take(1)
    ).subscribe({
      next: (settings) => this.initializeSettings(settings),
      error: (error) => {
        console.error('Failed to load settings:', error);
        this.settingsHelper.showMessage('error', 'Error', 'Failed to load settings.');
      }
    });
  }

  private initializeSettings(settings: AppSettings): void {
    if (settings.metadataPublicReviewsSettings) {
      this.publicReviewSettings = {...settings.metadataPublicReviewsSettings};
    }

    this.ensureAllProviders();
  }

  private updateProviderProperty<T extends keyof ReviewProviderConfig>(
    providerName: string,
    property: T,
    value: ReviewProviderConfig[T]
  ): void {
    const provider = this.findProvider(providerName);
    if (provider) {
      provider[property] = value;
      this.settingsHelper.saveSetting(AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, this.publicReviewSettings);
    }
  }

  private findProvider(providerName: string): ReviewProviderConfig | undefined {
    return this.publicReviewSettings.providers.find(p => p.provider === providerName);
  }

  private ensureAllProviders(): void {
    REQUIRED_PROVIDERS.forEach(providerName => {
      const exists = this.findProvider(providerName);
      if (!exists) {
        this.publicReviewSettings.providers.push({
          provider: providerName,
          enabled: false,
          maxReviews: 10
        });
      }
    });
  }
}
