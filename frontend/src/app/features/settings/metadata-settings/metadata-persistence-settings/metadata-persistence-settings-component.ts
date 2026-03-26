import {Component, inject, OnInit} from '@angular/core';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingKey, AppSettings, MetadataPersistenceSettings, SaveToOriginalFileSettings, SidecarSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../shared/service/settings-helper.service';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type PersistenceToggleKey = Exclude<keyof MetadataPersistenceSettings, 'saveToOriginalFile' | 'sidecarSettings'>;

@Component({
  selector: 'app-metadata-persistence-settings-component',
  imports: [
    ToggleSwitch,
    FormsModule,
    Tooltip,
    TranslocoDirective
  ],
  templateUrl: './metadata-persistence-settings-component.html',
  styleUrl: './metadata-persistence-settings-component.scss'
})
export class MetadataPersistenceSettingsComponent implements OnInit {

  metadataPersistence: MetadataPersistenceSettings = {
    saveToOriginalFile: {
      epub: {
        enabled: false,
        maxFileSizeInMb: 250
      },
      pdf: {
        enabled: false,
        maxFileSizeInMb: 250
      },
      cbx: {
        enabled: false,
        maxFileSizeInMb: 250
      },
      audiobook: {
        enabled: false,
        maxFileSizeInMb: 1000
      }
    },
    convertCbrCb7ToCbz: false,
    moveFilesToLibraryPattern: false,
    sidecarSettings: {
      enabled: false,
      writeOnUpdate: false,
      writeOnScan: false,
      includeCoverFile: false
    }
  };

  isNetworkStorage = false;

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);
  private t = inject(TranslocoService);

  ngOnInit(): void {
    this.loadSettings();
  }

  onPersistenceToggle(key: PersistenceToggleKey): void {
    this.metadataPersistence[key] = !this.metadataPersistence[key];
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onSaveToOriginalFileToggle(format: keyof SaveToOriginalFileSettings): void {
    this.metadataPersistence.saveToOriginalFile[format].enabled =
      !this.metadataPersistence.saveToOriginalFile[format].enabled;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onFilesizeChange(format: keyof SaveToOriginalFileSettings): void {
    void format;
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
  }

  onSidecarToggle(key: keyof SidecarSettings): void {
    if (this.metadataPersistence.sidecarSettings) {
      this.metadataPersistence.sidecarSettings[key] = !this.metadataPersistence.sidecarSettings[key];
      this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
    }
  }

  private loadSettings(): void {
    const settings = this.appSettingsService.appSettings();
    if (settings) {
      this.initializeSettings(settings);
    }
  }

  private initializeSettings(settings: AppSettings): void {
    this.isNetworkStorage = settings.diskType !== 'LOCAL';
    if (settings.metadataPersistenceSettings) {
      const persistenceSettings = settings.metadataPersistenceSettings;

      this.metadataPersistence = {
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
      };
    }
  }
}
