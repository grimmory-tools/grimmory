import {Component, effect, inject} from '@angular/core';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {ToggleSwitch} from "primeng/toggleswitch";
import {AppSettingKey, AppSettings, PublicReviewSettings, ReviewProviderConfig} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../shared/service/settings-helper.service';
import {TranslocoDirective} from '@jsverse/transloco';

const DEFAULT_PROVIDERS: readonly ReviewProviderConfig[] = [
  {provider: 'Amazon', enabled: true, maxReviews: 5},
  {provider: 'GoodReads', enabled: false, maxReviews: 5},
  {provider: 'Douban', enabled: false, maxReviews: 5}
] as const;

const REQUIRED_PROVIDERS = ['Amazon', 'GoodReads', 'Douban'] as const;

type ReviewProviderFormGroup = FormGroup<{
  provider: FormControl<string>;
  enabled: FormControl<boolean>;
  maxReviews: FormControl<number>;
}>;

@Component({
  selector: 'app-public-reviews-settings-component',
  imports: [ReactiveFormsModule, ToggleSwitch, TranslocoDirective],
  templateUrl: './public-reviews-settings-component.html',
  styleUrl: './public-reviews-settings-component.scss'
})
export class PublicReviewsSettingsComponent {

  private readonly fb = inject(FormBuilder);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly form = this.fb.nonNullable.group({
    downloadEnabled: [{value: true, disabled: true}],
    autoDownloadEnabled: [{value: false, disabled: true}],
    providers: this.fb.array<ReviewProviderFormGroup>([]),
  });

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (!settings || this.form.dirty) {
      return;
    }

    this.initializeSettings(settings);
    this.form.controls.downloadEnabled.enable({emitEvent: false});
    this.form.controls.autoDownloadEnabled.enable({emitEvent: false});
  });

  onPublicReviewsToggle(checked: boolean): void {
    this.form.controls.downloadEnabled.setValue(checked, {emitEvent: false});
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, this.buildSettingsPayload());
  }

  onAutoDownloadToggle(checked: boolean): void {
    this.form.controls.autoDownloadEnabled.setValue(checked, {emitEvent: false});
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, this.buildSettingsPayload());
  }

  onProviderToggle(providerName: string, enabled: boolean): void {
    const provider = this.findProvider(providerName);
    if (provider) {
      provider.controls.enabled.setValue(enabled, {emitEvent: false});
      this.settingsHelper.saveSetting(AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, this.buildSettingsPayload());
    }
  }

  onMaxReviewsChange(providerName: string, maxReviews: number): void {
    const provider = this.findProvider(providerName);
    if (provider) {
      provider.controls.maxReviews.setValue(maxReviews, {emitEvent: false});
      this.settingsHelper.saveSetting(AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS, this.buildSettingsPayload());
    }
  }

  private initializeSettings(settings: AppSettings): void {
    const publicReviewSettings = this.buildReviewSettings(settings);

    this.form.patchValue({
      downloadEnabled: publicReviewSettings.downloadEnabled,
      autoDownloadEnabled: publicReviewSettings.autoDownloadEnabled,
    }, {emitEvent: false});
    this.form.setControl(
      'providers',
      this.fb.array(publicReviewSettings.providers.map(provider => this.createProviderForm(provider)))
    );
  }

  get providerControls(): ReviewProviderFormGroup[] {
    return this.form.controls.providers.controls;
  }

  get publicReviewSettings(): PublicReviewSettings {
    return this.buildSettingsPayload();
  }

  private findProvider(providerName: string): ReviewProviderFormGroup | undefined {
    return this.providerControls.find(provider => provider.controls.provider.value === providerName);
  }

  private createProviderForm(provider: ReviewProviderConfig): ReviewProviderFormGroup {
    return this.fb.nonNullable.group({
      provider: [provider.provider],
      enabled: [provider.enabled],
      maxReviews: [provider.maxReviews],
    });
  }

  private buildSettingsPayload(): PublicReviewSettings {
    return {
      downloadEnabled: this.form.controls.downloadEnabled.getRawValue(),
      autoDownloadEnabled: this.form.controls.autoDownloadEnabled.getRawValue(),
      providers: this.form.controls.providers.getRawValue(),
    };
  }

  private buildReviewSettings(settings: AppSettings): PublicReviewSettings {
    const baseSettings = settings.metadataPublicReviewsSettings
      ? {
        ...settings.metadataPublicReviewsSettings,
        providers: settings.metadataPublicReviewsSettings.providers.map(provider => ({...provider}))
      }
      : {
        downloadEnabled: true,
        autoDownloadEnabled: false,
        providers: DEFAULT_PROVIDERS.map(provider => ({...provider}))
      };

    REQUIRED_PROVIDERS.forEach(providerName => {
      const exists = baseSettings.providers.some(provider => provider.provider === providerName);
      if (!exists) {
        baseSettings.providers.push({
          provider: providerName,
          enabled: false,
          maxReviews: 10
        });
      }
    });

    return baseSettings;
  }
}
