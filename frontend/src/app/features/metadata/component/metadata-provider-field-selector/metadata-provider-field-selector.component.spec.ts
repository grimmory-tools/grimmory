import {signal} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {of} from 'rxjs';
import {beforeEach, describe, expect, it, vi} from 'vitest';
import {TranslocoService} from '@jsverse/transloco';

import {AppSettingKey, AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MetadataProviderFieldSelectorComponent} from './metadata-provider-field-selector.component';

describe('MetadataProviderFieldSelectorComponent', () => {
  const saveSettings = vi.fn(() => of(void 0));
  const translate = vi.fn((key: string) => `translated:${key}`);
  const appSettings = signal<AppSettings | null>(null);

  beforeEach(() => {
    saveSettings.mockClear();
    translate.mockClear();
    appSettings.set(null);

    TestBed.configureTestingModule({
      providers: [
        {provide: AppSettingsService, useValue: {appSettings, saveSettings}},
        {provide: TranslocoService, useValue: {translate}},
      ]
    });
  });

  it('loads selected provider-specific fields from app settings on init', () => {
    appSettings.set({
      metadataProviderSpecificFields: {
        asin: true,
        amazonRating: false,
        amazonReviewCount: true,
      },
    } as AppSettings);

    const component = TestBed.runInInjectionContext(() => new MetadataProviderFieldSelectorComponent());
    component.ngOnInit();

    expect(component.selectedFields).toEqual(['asin', 'amazonReviewCount']);
  });

  it('adds and removes selected fields while persisting the full field state', () => {
    const component = TestBed.runInInjectionContext(() => new MetadataProviderFieldSelectorComponent());
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
    const component = TestBed.runInInjectionContext(() => new MetadataProviderFieldSelectorComponent());

    expect(component.getProviderLabel('amazon')).toBe('translated:settingsMeta.fieldSelector.providers.amazon');
    expect(component.getFieldLabel('asin')).toBe('translated:settingsMeta.fieldSelector.fields.asin');
    expect(translate).toHaveBeenCalledWith('settingsMeta.fieldSelector.providers.amazon');
    expect(translate).toHaveBeenCalledWith('settingsMeta.fieldSelector.fields.asin');
  });
});
