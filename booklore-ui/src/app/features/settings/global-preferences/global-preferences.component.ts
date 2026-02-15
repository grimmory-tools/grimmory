import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Observable} from 'rxjs';
import {Button} from 'primeng/button';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';

import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {BookMetadataManageService} from '../../book/service/book-metadata-manage.service';
import {AppSettingKey, AppSettings, CoverCroppingSettings} from '../../../shared/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {InputText} from 'primeng/inputtext';
import {Slider} from 'primeng/slider';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

@Component({
  selector: 'app-global-preferences',
  standalone: true,
  imports: [
    Button,
    ToggleSwitch,
    FormsModule,
    InputText,
    Slider,
    ExternalDocLinkComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  templateUrl: './global-preferences.component.html',
  styleUrl: './global-preferences.component.scss'
})
export class GlobalPreferencesComponent implements OnInit {

  toggles = {
    autoBookSearch: false,
    similarBookRecommendation: false,
    enableTelemetry: true,
  };

  coverCroppingSettings: CoverCroppingSettings = {
    verticalCroppingEnabled: false,
    horizontalCroppingEnabled: false,
    aspectRatioThreshold: 2.5,
    smartCroppingEnabled: false
  };

  private appSettingsService = inject(AppSettingsService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private messageService = inject(MessageService);
  private t = inject(TranslocoService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;
  maxFileUploadSizeInMb?: number;

  ngOnInit(): void {
    this.appSettings$.pipe(
      filter(settings => !!settings),
      take(1)
    ).subscribe(settings => {
      if (settings?.maxFileUploadSizeInMb) {
        this.maxFileUploadSizeInMb = settings.maxFileUploadSizeInMb;
      }
      if (settings?.coverCroppingSettings) {
        this.coverCroppingSettings = {...settings.coverCroppingSettings};
      }
      this.toggles.autoBookSearch = settings.autoBookSearch ?? false;
      this.toggles.similarBookRecommendation = settings.similarBookRecommendation ?? false;
      this.toggles.enableTelemetry = settings?.telemetryEnabled ?? true;
    });
  }

  onToggleChange(settingKey: keyof typeof this.toggles, checked: boolean): void {
    this.toggles[settingKey] = checked;
    const toggleKeyMap: Record<string, AppSettingKey> = {
      autoBookSearch: AppSettingKey.AUTO_BOOK_SEARCH,
      similarBookRecommendation: AppSettingKey.SIMILAR_BOOK_RECOMMENDATION,
      enableTelemetry: AppSettingKey.TELEMETRY_ENABLED,
    };
    const keyToSend = toggleKeyMap[settingKey];
    if (keyToSend) {
      this.saveSetting(keyToSend, checked);
    } else {
      console.warn(`Unknown toggle key: ${settingKey}`);
    }
  }

  onCoverCroppingChange(): void {
    this.saveSetting(AppSettingKey.COVER_CROPPING_SETTINGS, this.coverCroppingSettings);
  }

  saveFileSize() {
    if (!this.maxFileUploadSizeInMb || this.maxFileUploadSizeInMb <= 0) {
      this.showMessage('error', this.t.translate('settingsApp.fileManagement.invalidInput'), this.t.translate('settingsApp.fileManagement.invalidInputDetail'));
      return;
    }
    this.saveSetting(AppSettingKey.MAX_FILE_UPLOAD_SIZE_IN_MB, this.maxFileUploadSizeInMb);
  }

  regenerateCovers(): void {
    this.bookMetadataManageService.regenerateCovers().subscribe({
      next: () =>
        this.showMessage('success', this.t.translate('settingsApp.covers.regenerateStarted'), this.t.translate('settingsApp.covers.regenerateStartedDetail')),
      error: () =>
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsApp.covers.regenerateError'))
    });
  }

  private saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () =>
        this.showMessage('success', this.t.translate('settingsApp.settingsSaved'), this.t.translate('settingsApp.settingsSavedDetail')),
      error: () =>
        this.showMessage('error', this.t.translate('common.error'), this.t.translate('settingsApp.settingsError'))
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}
