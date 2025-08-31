import {Component, inject, OnInit} from '@angular/core';
import {MetadataAdvancedFetchOptionsComponent} from '../../metadata/metadata-options-dialog/metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';
import {MetadataProviderSettingsComponent} from '../global-preferences/metadata-provider-settings/metadata-provider-settings.component';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {AppSettingsService} from '../../core/service/app-settings.service';
import {SettingsHelperService} from '../../core/service/settings-helper.service';
import {Observable} from 'rxjs';
import {AppSettingKey, AppSettings} from '../../core/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {MetadataMatchWeightsComponent} from '../global-preferences/metadata-match-weights-component/metadata-match-weights-component';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MetadataPersistenceSettingsComponent} from './metadata-persistence-settings-component/metadata-persistence-settings-component';
import {PublicReviewsSettingsComponent} from './public-reviews-settings-component/public-reviews-settings-component';

@Component({
  selector: 'app-metadata-settings-component',
  standalone: true,
  imports: [
    MetadataAdvancedFetchOptionsComponent,
    MetadataProviderSettingsComponent,
    ReactiveFormsModule,
    FormsModule,
    MetadataMatchWeightsComponent,
    ToggleSwitch,
    MetadataPersistenceSettingsComponent,
    PublicReviewsSettingsComponent
  ],
  templateUrl: './metadata-settings-component.html',
  styleUrl: './metadata-settings-component.scss'
})
export class MetadataSettingsComponent implements OnInit {

  currentMetadataOptions!: MetadataRefreshOptions;
  metadataDownloadOnBookdrop = true;

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.loadSettings();
  }

  onMetadataDownloadOnBookdropToggle(checked: boolean): void {
    this.metadataDownloadOnBookdrop = checked;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP, checked);
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions): void {
    this.currentMetadataOptions = metadataRefreshOptions;
    this.settingsHelper.saveSetting(AppSettingKey.QUICK_BOOK_MATCH, metadataRefreshOptions);
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
    if (settings.metadataRefreshOptions) {
      this.currentMetadataOptions = settings.metadataRefreshOptions;
    }

    this.metadataDownloadOnBookdrop = settings.metadataDownloadOnBookdrop ?? true;
  }
}
