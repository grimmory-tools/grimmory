import {Component, DestroyRef, effect, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormsModule} from '@angular/forms';
import {Accordion, AccordionPanel, AccordionHeader, AccordionContent} from 'primeng/accordion';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

import {Library} from '../../book/model/library.model';
import {LibraryService} from '../../book/service/library.service';
import {MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {AppSettingKey, AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';
import {MetadataAdvancedFetchOptionsComponent} from '../../metadata/component/metadata-options-dialog/metadata-advanced-fetch-options/metadata-advanced-fetch-options.component';
import {SidecarService} from '../../metadata/service/sidecar.service';

@Component({
  selector: 'app-library-metadata-settings-component',
  standalone: true,
  imports: [FormsModule, MetadataAdvancedFetchOptionsComponent, Accordion, AccordionPanel, AccordionHeader, AccordionContent, ExternalDocLinkComponent, Button, Tooltip, TranslocoDirective],
  templateUrl: './library-metadata-settings.component.html',
  styleUrls: ['./library-metadata-settings.component.scss']
})
export class LibraryMetadataSettingsComponent {
  private libraryService = inject(LibraryService);
  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);
  private sidecarService = inject(SidecarService);
  private t = inject(TranslocoService);
  private destroyRef = inject(DestroyRef);

  defaultMetadataOptions: MetadataRefreshOptions = this.getDefaultMetadataOptions();
  libraryMetadataOptions: Record<number, MetadataRefreshOptions> = {};
  activePanel: number | null = null;
  sidecarExporting: Record<number, boolean> = {};
  sidecarImporting: Record<number, boolean> = {};
  isLocalStorage = true;
  private cachedDefaultOptions: Record<number, MetadataRefreshOptions> = {};
  private readonly syncLibraryOptionsEffect = effect(() => {
    this.libraries.forEach(library => {
      if (library.id && !this.libraryMetadataOptions[library.id]) {
        const libraryOptions = this.getLibrarySpecificOptions(library.id);
        if (libraryOptions) {
          this.libraryMetadataOptions[library.id] = libraryOptions;
        }
      }
    });
  });

  get libraries(): Library[] {
    return this.libraryService.libraries();
  }

  private readonly syncAppSettingsEffect = effect(() => {
    const appSettings = this.appSettingsService.appSettings();
    if (appSettings) {
      this.isLocalStorage = appSettings.diskType === 'LOCAL';
      this.defaultMetadataOptions = appSettings.defaultMetadataRefreshOptions;
      this.cachedDefaultOptions = {};
      this.initializeLibraryOptions(appSettings);
      this.updateLibraryOptionsFromSettings(appSettings);
    }
  });

  onPanelChange(event: unknown) {
    this.activePanel = typeof event === 'number' ? event : null;
  }

  onDefaultMetadataOptionsSubmitted(options: MetadataRefreshOptions) {
    this.defaultMetadataOptions = options;
    this.saveDefaultMetadataOptions(options);
  }

  onLibraryMetadataOptionsSubmitted(libraryId: number, options: MetadataRefreshOptions) {
    this.libraryMetadataOptions[libraryId] = {...options, libraryId};
    this.saveLibraryMetadataOptions();
  }

  hasLibraryOverride(libraryId: number): boolean {
    return libraryId in this.libraryMetadataOptions;
  }

  getLibraryOptions(libraryId: number): MetadataRefreshOptions {
    if (this.libraryMetadataOptions[libraryId]) {
      return this.libraryMetadataOptions[libraryId];
    }
    if (!this.cachedDefaultOptions[libraryId]) {
      this.cachedDefaultOptions[libraryId] = {...this.defaultMetadataOptions, libraryId};
    }
    return this.cachedDefaultOptions[libraryId];
  }

  trackByLibrary(index: number, library: Library): number | undefined {
    return library.id;
  }

  private saveDefaultMetadataOptions(options: MetadataRefreshOptions) {
    const settingsToSave = [
      {
        key: AppSettingKey.QUICK_BOOK_MATCH,
        newValue: options
      }
    ];

    this.appSettingsService.saveSettings(settingsToSave).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsLibMeta.defaultSettings.saveSuccess'));
        this.updateLibrariesUsingDefaults();
      },
      error: (error) => {
        console.error('Error saving default metadata options:', error);
        this.showMessage('error', this.t.translate('settingsLibMeta.defaultSettings.saveFailed'), this.t.translate('settingsLibMeta.defaultSettings.saveError'));
      }
    });
  }

  private saveLibraryMetadataOptions() {
    const libraryOptionsArray = Object.values(this.libraryMetadataOptions).filter(option =>
      option.libraryId !== null && option.libraryId !== undefined
    );

    const settingsToSave = [
      {
        key: AppSettingKey.LIBRARY_METADATA_REFRESH_OPTIONS,
        newValue: libraryOptionsArray
      }
    ];

    this.appSettingsService.saveSettings(settingsToSave).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.showMessage('success', this.t.translate('common.success'), this.t.translate('settingsLibMeta.libraryOverrides.saveSuccess'));
      },
      error: (error) => {
        console.error('Error saving library metadata options:', error);
        this.showMessage('error', this.t.translate('settingsLibMeta.libraryOverrides.saveFailed'), this.t.translate('settingsLibMeta.libraryOverrides.saveError'));
      }
    });
  }

  private updateLibrariesUsingDefaults() {
    Object.keys(this.libraryMetadataOptions).forEach(libraryIdStr => {
      const libraryId = parseInt(libraryIdStr, 10);
      if (!this.hasLibrarySpecificOptionsInSettings(libraryId)) {
        delete this.libraryMetadataOptions[libraryId];
      }
    });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string) {
    this.messageService.add({
      severity,
      summary,
      detail,
      life: 5000
    });
  }

  private initializeLibraryOptions(appSettings: AppSettings) {
    if (appSettings?.libraryMetadataRefreshOptions) {
      appSettings.libraryMetadataRefreshOptions.forEach(option => {
        if (option.libraryId) {
          this.libraryMetadataOptions[option.libraryId] = option;
        }
      });
    }
  }

  private updateLibraryOptionsFromSettings(appSettings: AppSettings) {
    void appSettings;
    Object.keys(this.libraryMetadataOptions).forEach(libraryIdStr => {
      const libraryId = parseInt(libraryIdStr, 10);
      if (!this.hasLibrarySpecificOptions(libraryId)) {
        this.libraryMetadataOptions[libraryId] = {...this.defaultMetadataOptions};
      }
    });
  }

  private hasLibrarySpecificOptions(libraryId: number): boolean {
    return libraryId in this.libraryMetadataOptions;
  }

  private hasLibrarySpecificOptionsInSettings(libraryId: number): boolean {
    const settings = this.appSettingsService.appSettings();
    return settings?.libraryMetadataRefreshOptions?.some(
      option => option.libraryId === libraryId
    ) || false;
  }

  private getLibrarySpecificOptions(libraryId: number): MetadataRefreshOptions | null {
    const settings = this.appSettingsService.appSettings();
    return settings?.libraryMetadataRefreshOptions?.find(
      option => option.libraryId === libraryId
    ) || null;
  }

  private getDefaultMetadataOptions(): MetadataRefreshOptions {
    return {
      libraryId: null,
      refreshCovers: false,
      mergeCategories: false,
      reviewBeforeApply: false,
      fieldOptions: {
        title: {p1: null, p2: null, p3: null, p4: null},
        subtitle: {p1: null, p2: null, p3: null, p4: null},
        description: {p1: null, p2: null, p3: null, p4: null},
        authors: {p1: null, p2: null, p3: null, p4: null},
        publisher: {p1: null, p2: null, p3: null, p4: null},
        publishedDate: {p1: null, p2: null, p3: null, p4: null},
        seriesName: {p1: null, p2: null, p3: null, p4: null},
        seriesNumber: {p1: null, p2: null, p3: null, p4: null},
        seriesTotal: {p1: null, p2: null, p3: null, p4: null},
        isbn13: {p1: null, p2: null, p3: null, p4: null},
        isbn10: {p1: null, p2: null, p3: null, p4: null},
        language: {p1: null, p2: null, p3: null, p4: null},
        categories: {p1: null, p2: null, p3: null, p4: null},
        cover: {p1: null, p2: null, p3: null, p4: null},
        pageCount: {p1: null, p2: null, p3: null, p4: null},
        asin: {p1: null, p2: null, p3: null, p4: null},
        goodreadsId: {p1: null, p2: null, p3: null, p4: null},
        comicvineId: {p1: null, p2: null, p3: null, p4: null},
        hardcoverId: {p1: null, p2: null, p3: null, p4: null},
        hardcoverBookId: {p1: null, p2: null, p3: null, p4: null},
        googleId: {p1: null, p2: null, p3: null, p4: null},
        amazonRating: {p1: null, p2: null, p3: null, p4: null},
        amazonReviewCount: {p1: null, p2: null, p3: null, p4: null},
        goodreadsRating: {p1: null, p2: null, p3: null, p4: null},
        goodreadsReviewCount: {p1: null, p2: null, p3: null, p4: null},
        hardcoverRating: {p1: null, p2: null, p3: null, p4: null},
        hardcoverReviewCount: {p1: null, p2: null, p3: null, p4: null},
        lubimyczytacId: {p1: null, p2: null, p3: null, p4: null},
        lubimyczytacRating: {p1: null, p2: null, p3: null, p4: null},
        ranobedbId: {p1: null, p2: null, p3: null, p4: null},
        ranobedbRating: {p1: null, p2: null, p3: null, p4: null},
        audibleId: {p1: null, p2: null, p3: null, p4: null},
        audibleRating: {p1: null, p2: null, p3: null, p4: null},
        audibleReviewCount: {p1: null, p2: null, p3: null, p4: null},
        moods: {p1: null, p2: null, p3: null, p4: null},
        tags: {p1: null, p2: null, p3: null, p4: null}
      }
    };
  }

  exportSidecarForLibrary(libraryId: number, event: Event): void {
    event.stopPropagation();
    this.sidecarExporting[libraryId] = true;

    this.sidecarService.bulkExport(libraryId).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (response) => {
        this.sidecarExporting[libraryId] = false;
        this.showMessage('success', this.t.translate('settingsLibMeta.libraryOverrides.exportComplete'), this.t.translate('settingsLibMeta.libraryOverrides.exportSuccess', {count: response.exported}));
      },
      error: (error) => {
        this.sidecarExporting[libraryId] = false;
        console.error('Bulk sidecar export failed:', error);
        this.showMessage('error', this.t.translate('settingsLibMeta.libraryOverrides.exportFailed'), this.t.translate('settingsLibMeta.libraryOverrides.exportError'));
      }
    });
  }

  importSidecarForLibrary(libraryId: number, event: Event): void {
    event.stopPropagation();
    this.sidecarImporting[libraryId] = true;

    this.sidecarService.bulkImport(libraryId).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (response) => {
        this.sidecarImporting[libraryId] = false;
        this.showMessage('success', this.t.translate('settingsLibMeta.libraryOverrides.importComplete'), this.t.translate('settingsLibMeta.libraryOverrides.importSuccess', {count: response.imported}));
      },
      error: (error) => {
        this.sidecarImporting[libraryId] = false;
        console.error('Bulk sidecar import failed:', error);
        this.showMessage('error', this.t.translate('settingsLibMeta.libraryOverrides.importFailed'), this.t.translate('settingsLibMeta.libraryOverrides.importError'));
      }
    });
  }

  isSidecarExporting(libraryId: number): boolean {
    return this.sidecarExporting[libraryId] ?? false;
  }

  isSidecarImporting(libraryId: number): boolean {
    return this.sidecarImporting[libraryId] ?? false;
  }
}
