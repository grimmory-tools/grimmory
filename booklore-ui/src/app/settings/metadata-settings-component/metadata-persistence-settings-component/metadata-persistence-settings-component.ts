import {Component, inject, OnInit} from '@angular/core';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {FormsModule} from '@angular/forms';
import {AppSettingKey, AppSettings, MetadataPersistenceSettings} from '../../../core/model/app-settings.model';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {SettingsHelperService} from '../../../core/service/settings-helper.service';
import {Observable} from 'rxjs';
import {filter, take} from 'rxjs/operators';

@Component({
  selector: 'app-metadata-persistence-settings-component',
  imports: [
    ToggleSwitch,
    FormsModule
  ],
  templateUrl: './metadata-persistence-settings-component.html',
  styleUrl: './metadata-persistence-settings-component.scss'
})
export class MetadataPersistenceSettingsComponent implements OnInit {

  metadataPersistence: MetadataPersistenceSettings = {
    saveToOriginalFile: false,
    convertCbrCb7ToCbz: false,
    backupMetadata: true,
    backupCover: true
  };

  private readonly appSettingsService = inject(AppSettingsService);
  private readonly settingsHelper = inject(SettingsHelperService);

  readonly appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.loadSettings();
  }

  onPersistenceToggle(key: keyof MetadataPersistenceSettings): void {
    this.updatePersistenceSettings(key);
    this.settingsHelper.saveSetting(AppSettingKey.METADATA_PERSISTENCE_SETTINGS, this.metadataPersistence);
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
    if (settings.metadataPersistenceSettings) {
      this.metadataPersistence = {...settings.metadataPersistenceSettings};
    }
  }

  private updatePersistenceSettings(key: keyof MetadataPersistenceSettings): void {
    if (key === 'saveToOriginalFile') {
      this.metadataPersistence.saveToOriginalFile = !this.metadataPersistence.saveToOriginalFile;

      if (!this.metadataPersistence.saveToOriginalFile) {
        this.metadataPersistence.convertCbrCb7ToCbz = false;
        this.metadataPersistence.backupMetadata = false;
        this.metadataPersistence.backupCover = false;
      }
    } else {
      this.metadataPersistence[key] = !this.metadataPersistence[key];
    }
  }
}
