import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {AppSettingKey, AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MetadataProviderFieldSelectorComponent} from './metadata-provider-field-selector.component';

describe('MetadataProviderFieldSelectorComponent', () => {
  const saveSettings = vi.fn(() => of(void 0));
  const appSettings = signal<AppSettings | null>(null);

  beforeEach(async () => {
    saveSettings.mockClear();
    appSettings.set(null);

    await TestBed.configureTestingModule({
      imports: [MetadataProviderFieldSelectorComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: {appSettings, saveSettings}},
      ],
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('loads selected provider-specific fields from app settings on init', () => {
    appSettings.set({
      metadataProviderSpecificFields: {
        asin: true,
        amazonRating: false,
        amazonReviewCount: true,
      },
    } as AppSettings);

    const fixture = TestBed.createComponent(MetadataProviderFieldSelectorComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedFields).toEqual(['asin', 'amazonReviewCount']);
  });

  it('adds and removes selected fields while persisting the full field state', () => {
    const fixture = TestBed.createComponent(MetadataProviderFieldSelectorComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.selectedFields = ['asin'];

    component.toggleField('googleId', true);
    component.toggleField('asin', false);

    expect(component.selectedFields).toEqual(['googleId']);
    expect(saveSettings).toHaveBeenCalledTimes(2);
    expect(saveSettings).toHaveBeenLastCalledWith([{
      key: AppSettingKey.METADATA_PROVIDER_SPECIFIC_FIELDS,
      newValue: expect.objectContaining({
        asin: false,
        googleId: true,
      }),
    }]);
  });

  it('translates provider and field labels through Transloco', () => {
    const fixture = TestBed.createComponent(MetadataProviderFieldSelectorComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.getProviderLabel('amazon')).toBe('Amazon');
    expect(component.getFieldLabel('asin')).toBeDefined();
  });
});
