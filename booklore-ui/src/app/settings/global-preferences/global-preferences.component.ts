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
import {AppSettings} from '../../core/model/app-settings.model';
import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {MetadataAdvancedFetchOptionsComponent} from '../../metadata/metadata-options-dialog/metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';
import {filter, take} from 'rxjs/operators';
import {FileUploadPatternComponent} from '../../file-upload-pattern/file-upload-pattern.component';
import {OpdsSettingsComponent} from '../../opds-settings/opds-settings.component';

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
    MetadataAdvancedFetchOptionsComponent,
    FileUploadPatternComponent,
    OpdsSettingsComponent
  ],
  templateUrl: './global-preferences.component.html',
  styleUrl: './global-preferences.component.scss'
})
export class GlobalPreferencesComponent implements OnInit {
  resolutionOptions = [
    {label: '250x350', value: '250x350'},
    {label: '375x525', value: '375x525'},
    {label: '500x700', value: '500x700'},
    {label: '625x875', value: '625x875'}
  ];

  selectedResolution = '250x350';
  currentMetadataOptions!: MetadataRefreshOptions;
  autoMetadataChecked = false;
  recommendationEnabled = false;

  private appSettingsService = inject(AppSettingsService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;


  ngOnInit(): void {
    this.appSettings$.pipe(
      filter(settings => settings != null),
      take(1)
    ).subscribe(settings => {
      if (settings?.coverResolution) {
        this.selectedResolution = settings.coverResolution;
      }
      if (settings.metadataRefreshOptions) {
        this.currentMetadataOptions = settings.metadataRefreshOptions;
      }
      this.autoMetadataChecked = settings.autoBookSearch ?? false;
      this.recommendationEnabled = settings.similarBookRecommendation ?? false;
    });
  }

  onResolutionChange(): void {
    if (this.selectedResolution) {
      this.saveSetting('cover_image_resolution', this.selectedResolution);
    }
  }

  onAutoMetaChange(event: { checked: boolean }): void {
    this.saveSetting('auto_book_search', event.checked);
  }

  onToggleRecommendation(event: { checked: boolean }) {
    this.saveSetting('similar_book_recommendation', event.checked);
  }

  onMetadataSubmit(metadataRefreshOptions: MetadataRefreshOptions): void {
    this.saveSetting('quick_book_match', metadataRefreshOptions);
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
