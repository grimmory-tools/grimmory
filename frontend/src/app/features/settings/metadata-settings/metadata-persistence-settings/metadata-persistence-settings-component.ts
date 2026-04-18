import {Component, effect, inject} from '@angular/core';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {FormBuilder, ReactiveFormsModule} from '@angular/forms';
import {AppSettingKey, AppSettings, MetadataPersistenceSettings, SaveToOriginalFileSettings, SidecarSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../shared/service/settings-helper.service';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective} from '@jsverse/transloco';

type PersistenceToggleKey = Exclude<keyof MetadataPersistenceSettings, 'saveToOriginalFile' | 'sidecarSettings'>;

@Component({
  selector: 'app-metadata-persistence-settings-component',
  imports: [
    ToggleSwitch,
    ReactiveFormsModule,
    Tooltip,
    TranslocoDirective
  ],
  templateUrl: './metadata-persistence-settings-component.html',
  styleUrl: './metadata-persistence-settings-component.scss'
})
export class MetadataPersistenceSettingsComponent {

  isNetworkStorage = false;

  private readonly fb = inject(FormBuilder);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly form = this.fb.nonNullable.group({
    saveToOriginalFile: this.fb.nonNullable.group({
      epub: this.createFormatSettingsGroup(250),
      pdf: this.createFormatSettingsGroup(250),
      cbx: this.createFormatSettingsGroup(250),
      audiobook: this.createFormatSettingsGroup(1000),
    }),
    convertCbrCb7ToCbz: [false],
    moveFilesToLibraryPattern: [false],
    sidecarSettings: this.fb.nonNullable.group({
      enabled: [false],
      writeOnUpdate: [false],
      writeOnScan: [false],
      includeCoverFile: [false],
    }),
  });

  private readonly syncSettingsEffect = effect(() => {
    const settings = this.appSettingsService.appSettings();
    if (!settings) {
      return;
    }

    this.initializeSettings(settings);
  });

  get metadataPersistence(): MetadataPersistenceSettings {
    return this.form.getRawValue();
  }

  onPersistenceToggle(key: PersistenceToggleKey, checked: boolean): void {
    this.form.controls[key].setValue(checked, {emitEvent: false});
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onSaveToOriginalFileToggle(format: keyof SaveToOriginalFileSettings, checked: boolean): void {
    this.form.controls.saveToOriginalFile.controls[format].controls.enabled.setValue(checked, {emitEvent: false});
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onFilesizeChange(format: keyof SaveToOriginalFileSettings): void {
    void format;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onSidecarToggle(key: keyof SidecarSettings, checked: boolean): void {
    this.form.controls.sidecarSettings.controls[key].setValue(checked, {emitEvent: false});
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  private initializeSettings(settings: AppSettings): void {
    this.isNetworkStorage = settings.diskType !== 'LOCAL';
    this.updateNetworkStorageState();

    if (settings.metadataPersistenceSettings) {
      const persistenceSettings = settings.metadataPersistenceSettings;

      this.form.patchValue({
        ...persistenceSettings,
        saveToOriginalFile: {
          epub: {
            enabled: persistenceSettings.saveToOriginalFile?.epub?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.epub?.maxFileSizeInMb ?? 250
          },
          pdf: {
            enabled: persistenceSettings.saveToOriginalFile?.pdf?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.pdf?.maxFileSizeInMb ?? 250
          },
          cbx: {
            enabled: persistenceSettings.saveToOriginalFile?.cbx?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.cbx?.maxFileSizeInMb ?? 250
          },
          audiobook: {
            enabled: persistenceSettings.saveToOriginalFile?.audiobook?.enabled ?? false,
            maxFileSizeInMb: persistenceSettings.saveToOriginalFile?.audiobook?.maxFileSizeInMb ?? 1000
          }
        },
        sidecarSettings: {
          enabled: persistenceSettings.sidecarSettings?.enabled ?? false,
          writeOnUpdate: persistenceSettings.sidecarSettings?.writeOnUpdate ?? false,
          writeOnScan: persistenceSettings.sidecarSettings?.writeOnScan ?? false,
          includeCoverFile: persistenceSettings.sidecarSettings?.includeCoverFile ?? false
        }
      }, {emitEvent: false});
    }
  }

  private updateNetworkStorageState(): void {
    const controls = [
      this.form.controls.saveToOriginalFile,
      this.form.controls.moveFilesToLibraryPattern,
      this.form.controls.sidecarSettings,
    ];

    controls.forEach(control => {
      if (this.isNetworkStorage) {
        control.disable({emitEvent: false});
      } else {
        control.enable({emitEvent: false});
      }
    });
  }

  private createFormatSettingsGroup(maxFileSizeInMb: number) {
    return this.fb.nonNullable.group({
      enabled: [false],
      maxFileSizeInMb: [maxFileSizeInMb],
    });
  }
}
