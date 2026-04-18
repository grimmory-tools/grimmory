import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of, throwError} from 'rxjs';

import {MessageService} from 'primeng/api';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {type AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MetadataMatchWeightsService} from '../../../../shared/service/metadata-match-weights.service';
import {AppSettingKey} from '../../../../shared/model/app-settings.model';
import {MetadataMatchWeightsComponent} from './metadata-match-weights-component';

describe('MetadataMatchWeightsComponent', () => {
  let fixture: ComponentFixture<MetadataMatchWeightsComponent>;
  let component: MetadataMatchWeightsComponent;
  let saveSettings: ReturnType<typeof vi.fn>;
  let recalculateAll: ReturnType<typeof vi.fn>;
  let messageService: {add: ReturnType<typeof vi.fn>};
  let appSettingsSignal: WritableSignal<AppSettings | null>;

  beforeEach(async () => {
    saveSettings = vi.fn(() => of(void 0));
    recalculateAll = vi.fn(() => of(void 0));
    messageService = {add: vi.fn()};
    appSettingsSignal = signal<AppSettings | null>(null);

    await TestBed.configureTestingModule({
      imports: [MetadataMatchWeightsComponent, getTranslocoModule()],
      providers: [
        {
          provide: AppSettingsService,
          useValue: {
            appSettings: appSettingsSignal,
            saveSettings,
          },
        },
        {provide: MetadataMatchWeightsService, useValue: {recalculateAll}},
        {provide: MessageService, useValue: messageService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataMatchWeightsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('hydrates the reactive form from saved match weights', () => {
    appSettingsSignal.set(buildSettings());
    fixture.detectChanges();

    expect(component.form.get('title')?.value).toBe(10);
    expect(component.form.get('authors')?.value).toBe(9);
    expect(component.orderedKeys[0]).toBe('title');
  });

  it('hydrates the reactive form when weights resolve after initial render', () => {
    fixture.detectChanges();

    expect(component.form.get('title')?.value).toBe(0);

    appSettingsSignal.set(buildSettings());
    fixture.detectChanges();

    expect(component.form.get('title')?.value).toBe(10);
    expect(component.form.get('authors')?.value).toBe(9);
  });

  it('persists valid form values through app settings', () => {
    fixture.detectChanges();
    component.save();

    expect(saveSettings).toHaveBeenCalledWith([
      {
        key: AppSettingKey.METADATA_MATCH_WEIGHTS,
        newValue: component.form.value,
      },
    ]);
    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'success'}));
    expect(component.isSaving).toBe(false);
  });

  it('does not save invalid forms', () => {
    fixture.detectChanges();
    component.form.get('title')?.setValue(-1);

    component.save();

    expect(saveSettings).toHaveBeenCalledTimes(0);
  });

  it('reports recalculate failures and resets the loading flag', () => {
    fixture.detectChanges();
    recalculateAll.mockReturnValueOnce(throwError(() => new Error('boom')));

    component.recalculate();

    expect(messageService.add).toHaveBeenCalledWith(expect.objectContaining({severity: 'error'}));
    expect(component.isRecalculating).toBe(false);
  });
});

function buildSettings(): AppSettings {
  return {
    metadataMatchWeights: {
      title: 10,
      subtitle: 5,
      description: 3,
      publisher: 2,
      publishedDate: 1,
      authors: 9,
      categories: 4,
      seriesName: 6,
      seriesNumber: 2,
      seriesTotal: 1,
      isbn13: 8,
      isbn10: 7,
      pageCount: 2,
      language: 1,
      amazonRating: 2,
      amazonReviewCount: 2,
      goodreadsRating: 2,
      goodreadsReviewCount: 2,
      hardcoverRating: 2,
      hardcoverReviewCount: 2,
      doubanRating: 2,
      doubanReviewCount: 2,
      ranobedbRating: 2,
      audibleRating: 2,
      audibleReviewCount: 2,
      coverImage: 3,
    },
  } as AppSettings;
}
