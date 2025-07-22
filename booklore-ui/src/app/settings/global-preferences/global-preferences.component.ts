import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Observable} from 'rxjs';

import {Divider} from 'primeng/divider';
import {DropdownModule} from 'primeng/dropdown';
import {Select} from 'primeng/select';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {MessageService} from 'primeng/api';

import {AppSettingsService} from '../../core/service/app-settings.service';
import {BookService} from '../../book/service/book.service';
import {AppSettingKey, AppSettings} from '../../core/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {FileUploadPatternComponent} from './file-upload-pattern/file-upload-pattern.component';
import {InputText} from 'primeng/inputtext';

@Component({
  selector: 'app-global-preferences',
  standalone: true,
  imports: [
    Divider,
    DropdownModule,
    Select,
    Button,
    Tooltip,
    ToggleSwitch,
    FormsModule,
    FileUploadPatternComponent,
    InputText
  ],
  templateUrl: './global-preferences.component.html',
  styleUrl: './global-preferences.component.scss'
})
export class GlobalPreferencesComponent implements OnInit {
  readonly resolutionOptions = [
    {label: '250x350', value: '250x350'},
    {label: '375x525', value: '375x525'},
    {label: '500x700', value: '500x700'},
    {label: '625x875', value: '625x875'}
  ];

  selectedResolution = '250x350';

  toggles = {
    autoBookSearch: false,
    similarBookRecommendation: false,
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
      if (settings?.coverResolution) {
        this.selectedResolution = settings.coverResolution;
      }
      if (settings?.cbxCacheSizeInMb) {
        this.cbxCacheValue = settings.cbxCacheSizeInMb;
      }
      if (settings?.maxFileUploadSizeInMb) {
        this.maxFileUploadSizeInMb = settings.maxFileUploadSizeInMb;
      }
      this.toggles.autoBookSearch = settings.autoBookSearch ?? false;
      this.toggles.similarBookRecommendation = settings.similarBookRecommendation ?? false;
    });
  }

  onResolutionChange(): void {
    this.saveSetting(AppSettingKey.COVER_IMAGE_RESOLUTION, this.selectedResolution);
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
