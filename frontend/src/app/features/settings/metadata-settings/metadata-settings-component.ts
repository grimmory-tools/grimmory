import {Component, effect, inject} from '@angular/core';
import {FormBuilder, ReactiveFormsModule} from '@angular/forms';
import {MetadataProviderSettingsComponent} from '../global-preferences/metadata-provider-settings/metadata-provider-settings.component';
import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../shared/service/settings-helper.service';
import {AppSettingKey, AppSettings} from '../../../shared/model/app-settings.model';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MetadataMatchWeightsComponent} from '../global-preferences/metadata-match-weights/metadata-match-weights-component';
import {MetadataPersistenceSettingsComponent} from './metadata-persistence-settings/metadata-persistence-settings-component';
import {PublicReviewsSettingsComponent} from './public-reviews-settings/public-reviews-settings-component';
import {MetadataProviderFieldSelectorComponent} from '../../metadata/component/metadata-provider-field-selector/metadata-provider-field-selector.component';
import {TranslocoDirective} from '@jsverse/transloco';

@Component({
  selector: 'app-metadata-settings-component',
  standalone: true,
  imports: [
    MetadataProviderSettingsComponent,
    ReactiveFormsModule,
    MetadataMatchWeightsComponent,
    ToggleSwitch,
    MetadataPersistenceSettingsComponent,
    PublicReviewsSettingsComponent,
    MetadataProviderFieldSelectorComponent,
    TranslocoDirective
  ],
  templateUrl: './metadata-settings-component.html',
  styleUrl: './metadata-settings-component.scss'
})
export class MetadataSettingsComponent {

  currentMetadataOptions!: MetadataRefreshOptions;

  private readonly fb = inject(FormBuilder);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly form = this.fb.nonNullable.group({
    metadataDownloadOnBookdrop: [true],
  });

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (!settings) {
      return;
    }

    this.initializeSettings(settings);
  });

  onMetadataDownloadOnBookdropToggle(checked: boolean): void {
    this.form.controls.metadataDownloadOnBookdrop.setValue(checked, {emitEvent: false});
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP, checked);
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions): void {
    this.currentMetadataOptions = metadataRefreshOptions;
    this.settingsHelper.saveSetting(AppSettingKey.QUICK_BOOK_MATCH, metadataRefreshOptions);
  }

  private initializeSettings(settings: AppSettings): void {
    if (settings.defaultMetadataRefreshOptions) {
      this.currentMetadataOptions = settings.defaultMetadataRefreshOptions;
    }

    this.form.patchValue({
      metadataDownloadOnBookdrop: settings.metadataDownloadOnBookdrop ?? true,
    }, {emitEvent: false});
  }
}
