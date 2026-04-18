import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {AppSettingKey, type AppSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../shared/service/settings-helper.service';
import {PublicReviewsSettingsComponent} from './public-reviews-settings-component';

describe('PublicReviewsSettingsComponent', () => {
  let fixture: ComponentFixture<PublicReviewsSettingsComponent>;
  let component: PublicReviewsSettingsComponent;
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let settingsHelper: {saveSetting: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    appSettingsSignal = signal<AppSettings | null>(null);
    settingsHelper = {
      saveSetting: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PublicReviewsSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: {appSettings: appSettingsSignal}},
        {provide: SettingsHelperService, useValue: settingsHelper},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PublicReviewsSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('hydrates saved review settings and ensures all required providers exist', async () => {
    appSettingsSignal.set({
      metadataPublicReviewsSettings: {
        downloadEnabled: false,
        autoDownloadEnabled: true,
        providers: [{provider: 'Amazon', enabled: true, maxReviews: 7}],
      },
    } as AppSettings);
    await render();

    expect(component.publicReviewSettings.downloadEnabled).toBe(false);
    expect(component.publicReviewSettings.autoDownloadEnabled).toBe(true);
    expect(component.publicReviewSettings.providers).toEqual(
      expect.arrayContaining([
        expect.objectContaining({provider: 'Amazon', enabled: true, maxReviews: 7}),
        expect.objectContaining({provider: 'GoodReads', enabled: false, maxReviews: 10}),
        expect.objectContaining({provider: 'Douban', enabled: false, maxReviews: 10}),
      ])
    );
  });

  it('hydrates when review settings resolve after initial render', async () => {
    await render();

    expect(component.publicReviewSettings.downloadEnabled).toBe(true);

    appSettingsSignal.set({
      metadataPublicReviewsSettings: {
        downloadEnabled: false,
        autoDownloadEnabled: true,
        providers: [{provider: 'Amazon', enabled: true, maxReviews: 7}],
      },
    } as AppSettings);
    await render();

    expect(component.publicReviewSettings.downloadEnabled).toBe(false);
    expect(component.publicReviewSettings.autoDownloadEnabled).toBe(true);
  });

  it('persists top-level review toggles', async () => {
    await render();

    component.onPublicReviewsToggle(true);
    component.onAutoDownloadToggle(false);

    expect(settingsHelper.saveSetting).toHaveBeenNthCalledWith(
      1,
      AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS,
      component.publicReviewSettings
    );
    expect(settingsHelper.saveSetting).toHaveBeenNthCalledWith(
      2,
      AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS,
      component.publicReviewSettings
    );
  });

  it('updates provider settings and persists them', async () => {
    appSettingsSignal.set({
      metadataPublicReviewsSettings: {
        downloadEnabled: false,
        autoDownloadEnabled: true,
        providers: [{provider: 'Amazon', enabled: true, maxReviews: 7}],
      },
    } as AppSettings);
    await render();

    component.onProviderToggle('Amazon', false);
    component.onMaxReviewsChange('Amazon', 12);

    const amazon = component.publicReviewSettings.providers.find(provider => provider.provider === 'Amazon');
    expect(amazon).toEqual(expect.objectContaining({enabled: false, maxReviews: 12}));
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PUBLIC_REVIEWS_SETTINGS,
      component.publicReviewSettings
    );
  });

  async function render(): Promise<void> {
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
});
