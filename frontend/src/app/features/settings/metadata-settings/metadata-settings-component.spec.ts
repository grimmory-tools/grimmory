import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../core/testing/transloco-testing';
import {AppSettingKey, type AppSettings} from '../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../shared/service/settings-helper.service';
import {type MetadataRefreshOptions} from '../../metadata/model/request/metadata-refresh-options.model';
import {MetadataSettingsComponent} from './metadata-settings-component';

describe('MetadataSettingsComponent', () => {
  let fixture: ComponentFixture<MetadataSettingsComponent>;
  let component: MetadataSettingsComponent;
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let settingsHelper: {saveSetting: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    appSettingsSignal = signal<AppSettings | null>(null);
    settingsHelper = {
      saveSetting: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MetadataSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: {appSettings: appSettingsSignal}},
        {provide: SettingsHelperService, useValue: settingsHelper},
      ],
    })
      .overrideComponent(MetadataSettingsComponent, {
        set: {
          template: '',
          imports: [],
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(MetadataSettingsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates when metadata settings arrive after initial render', async () => {
    await render();

    expect(component.form.controls.metadataDownloadOnBookdrop.value).toBe(true);
    expect(component.currentMetadataOptions).toBeUndefined();

    const options = buildMetadataRefreshOptions();
    appSettingsSignal.set({
      defaultMetadataRefreshOptions: options,
      metadataDownloadOnBookdrop: false,
    } as AppSettings);
    await render();

    expect(component.form.controls.metadataDownloadOnBookdrop.value).toBe(false);
    expect(component.currentMetadataOptions).toEqual(options);
  });

  it('persists bookdrop download toggles and metadata option submissions', async () => {
    await render();

    component.onMetadataDownloadOnBookdropToggle(false);

    const options = buildMetadataRefreshOptions();
    component.onMetadataSubmit(options);

    expect(settingsHelper.saveSetting).toHaveBeenNthCalledWith(
      1,
      AppSettingKey.METADATA_DOWNLOAD_ON_BOOKDROP,
      false
    );
    expect(settingsHelper.saveSetting).toHaveBeenNthCalledWith(
      2,
      AppSettingKey.QUICK_BOOK_MATCH,
      options
    );
  });

  async function render(): Promise<void> {
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
});

function buildMetadataRefreshOptions(): MetadataRefreshOptions {
  return {
    libraryId: null,
    refreshCovers: false,
    mergeCategories: true,
    reviewBeforeApply: false,
    replaceMode: 'REPLACE_MISSING',
    fieldOptions: {
      title: {p1: 'Amazon', p2: null, p3: null, p4: null},
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
      pageCount: {p1: null, p2: null, p3: null, p4: null},
      categories: {p1: null, p2: null, p3: null, p4: null},
      cover: {p1: null, p2: null, p3: null, p4: null},
      asin: {p1: null, p2: null, p3: null, p4: null},
      goodreadsId: {p1: null, p2: null, p3: null, p4: null},
      comicvineId: {p1: null, p2: null, p3: null, p4: null},
      hardcoverId: {p1: null, p2: null, p3: null, p4: null},
      hardcoverBookId: {p1: null, p2: null, p3: null, p4: null},
      googleId: {p1: null, p2: null, p3: null, p4: null},
      lubimyczytacId: {p1: null, p2: null, p3: null, p4: null},
      amazonRating: {p1: null, p2: null, p3: null, p4: null},
      amazonReviewCount: {p1: null, p2: null, p3: null, p4: null},
      goodreadsRating: {p1: null, p2: null, p3: null, p4: null},
      goodreadsReviewCount: {p1: null, p2: null, p3: null, p4: null},
      hardcoverRating: {p1: null, p2: null, p3: null, p4: null},
      hardcoverReviewCount: {p1: null, p2: null, p3: null, p4: null},
      lubimyczytacRating: {p1: null, p2: null, p3: null, p4: null},
      ranobedbId: {p1: null, p2: null, p3: null, p4: null},
      ranobedbRating: {p1: null, p2: null, p3: null, p4: null},
      audibleId: {p1: null, p2: null, p3: null, p4: null},
      audibleRating: {p1: null, p2: null, p3: null, p4: null},
      audibleReviewCount: {p1: null, p2: null, p3: null, p4: null},
      moods: {p1: null, p2: null, p3: null, p4: null},
      tags: {p1: null, p2: null, p3: null, p4: null},
    },
    enabledFields: {
      title: true,
      subtitle: true,
      description: true,
      authors: true,
      publisher: true,
      publishedDate: true,
      seriesName: true,
      seriesNumber: true,
      seriesTotal: true,
      isbn13: true,
      isbn10: true,
      language: true,
      pageCount: true,
      categories: true,
      cover: true,
      asin: true,
      goodreadsId: true,
      comicvineId: true,
      hardcoverId: true,
      hardcoverBookId: true,
      googleId: true,
      lubimyczytacId: true,
      amazonRating: true,
      amazonReviewCount: true,
      goodreadsRating: true,
      goodreadsReviewCount: true,
      hardcoverRating: true,
      hardcoverReviewCount: true,
      lubimyczytacRating: true,
      ranobedbId: true,
      ranobedbRating: true,
      audibleId: true,
      audibleRating: true,
      audibleReviewCount: true,
      moods: true,
      tags: true,
    },
  };
}
