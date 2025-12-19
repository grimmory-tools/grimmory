import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Observable} from 'rxjs';
import {Button} from 'primeng/button';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';

import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {BookService} from '../../book/service/book.service';
import {AppSettingKey, AppSettings, CoverCroppingSettings} from '../../../shared/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {InputText} from 'primeng/inputtext';
import {Slider} from 'primeng/slider';

@Component({
  selector: 'app-global-preferences',
  standalone: true,
  imports: [
    Button,
    ToggleSwitch,
    FormsModule,
    InputText,
    Slider
  ],
  templateUrl: './global-preferences.component.html',
  styleUrl: './global-preferences.component.scss'
})
export class GlobalPreferencesComponent implements OnInit {

  toggles = {
    autoBookSearch: false,
    similarBookRecommendation: false,
  };

  coverCroppingSettings: CoverCroppingSettings = {
    verticalCroppingEnabled: false,
    horizontalCroppingEnabled: false,
    aspectRatioThreshold: 2.5,
    smartCroppingEnabled: false
  };

  private appSettingsService = inject(AppSettingsService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;
  cbxCacheValue?: number;
  maxFileUploadSizeInMb?: number;

  ngOnInit(): void {
    this.appSettings$.pipe(
      filter(settings => !!settings),
      take(1)
    ).subscribe(settings => {
      if (settings?.cbxCacheSizeInMb) {
        this.cbxCacheValue = settings.cbxCacheSizeInMb;
      }
      if (settings?.maxFileUploadSizeInMb) {
        this.maxFileUploadSizeInMb = settings.maxFileUploadSizeInMb;
      }
      if (settings?.coverCroppingSettings) {
        this.coverCroppingSettings = {...settings.coverCroppingSettings};
      }
      this.toggles.autoBookSearch = settings.autoBookSearch ?? false;
      this.toggles.similarBookRecommendation = settings.similarBookRecommendation ?? false;
    });
  }

  onToggleChange(settingKey: keyof typeof this.toggles, checked: boolean): void {
    this.toggles[settingKey] = checked;
    const toggleKeyMap: Record<string, AppSettingKey> = {
      autoBookSearch: AppSettingKey.AUTO_BOOK_SEARCH,
      similarBookRecommendation: AppSettingKey.SIMILAR_BOOK_RECOMMENDATION,
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

  saveCacheSize(): void {
    if (!this.cbxCacheValue || this.cbxCacheValue <= 0) {
      this.showMessage('error', 'Invalid Input', 'Please enter a valid cache size in MB.');
      return;
    }

    this.saveSetting(AppSettingKey.CBX_CACHE_SIZE_IN_MB, this.cbxCacheValue);
  }

  saveFileSize() {
    if (!this.maxFileUploadSizeInMb || this.maxFileUploadSizeInMb <= 0) {
      this.showMessage('error', 'Invalid Input', 'Please enter a valid max file upload size in MB.');
      return;
    }
    this.saveSetting(AppSettingKey.MAX_FILE_UPLOAD_SIZE_IN_MB, this.maxFileUploadSizeInMb);
  }

  regenerateCovers(): void {
    this.bookService.regenerateCovers().subscribe({
      next: () =>
        this.showMessage('success', 'Cover Regeneration Started', 'Book covers are being regenerated.'),
      error: () =>
        this.showMessage('error', 'Error', 'Failed to start cover regeneration.')
    });
  }

  private saveSetting(key: string, value: unknown): void {
    this.appSettingsService.saveSettings([{key, newValue: value}]).subscribe({
      next: () =>
        this.showMessage('success', 'Settings Saved', 'The settings were successfully saved!'),
      error: () =>
        this.showMessage('error', 'Error', 'There was an error saving the settings.')
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}
