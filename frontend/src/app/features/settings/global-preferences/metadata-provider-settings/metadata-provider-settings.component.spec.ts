import {signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';

import {MessageService} from 'primeng/api';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {type AppSettings, AppSettingKey} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MetadataProviderSettingsComponent} from './metadata-provider-settings.component';

describe('MetadataProviderSettingsComponent', () => {
  let fixture: ComponentFixture<MetadataProviderSettingsComponent>;
  let component: MetadataProviderSettingsComponent;
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let saveSettings: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    appSettingsSignal = signal<AppSettings | null>(null);
    saveSettings = vi.fn(() => of(void 0));

    await TestBed.configureTestingModule({
      imports: [MetadataProviderSettingsComponent, getTranslocoModule()],
      providers: [
        {
          provide: AppSettingsService,
          useValue: {
            appSettings: appSettingsSignal,
            saveSettings,
          },
        },
        {provide: MessageService, useValue: {add: vi.fn()}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataProviderSettingsComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  it('does not hydrate Google Books as enabled without an API key', () => {
    appSettingsSignal.set(buildSettings({enabled: true, apiKey: ''}));
    fixture.detectChanges();

    expect(component.googleEnabled).toBe(false);
    expect(component.googleApiKeyConfigured).toBe(false);
  });

  it('hydrates Google Books as enabled with an API key', () => {
    appSettingsSignal.set(buildSettings({enabled: true, apiKey: 'valid-key'}));
    fixture.detectChanges();

    expect(component.googleEnabled).toBe(true);
    expect(component.googleApiKeyConfigured).toBe(true);
  });

  it('disables Google Books when the API key is cleared', () => {
    component.googleEnabled = true;

    component.onGoogleApiKeyChange('   ');

    expect(component.googleEnabled).toBe(false);
    expect(component.googleApiKeyConfigured).toBe(false);
  });

  it('persists Google Books as enabled when an API key is configured', () => {
    component.googleEnabled = true;
    component.googleApiKey = '  configured-key  ';

    component.saveSettings();

    const providerSettings = getSavedProviderSettings();
    expect(providerSettings.google).toEqual({
      enabled: true,
      language: '',
      apiKey: 'configured-key',
    });
  });

  it('does not persist Google Books as enabled without an API key', () => {
    component.googleEnabled = true;
    component.googleApiKey = '';

    component.saveSettings();

    expect(getSavedProviderSettings().google.enabled).toBe(false);
  });

  function getSavedProviderSettings() {
    const payload = saveSettings.mock.calls[0][0] as {
      key: string;
      newValue: {google: {enabled: boolean; language: string; apiKey: string}};
    }[];

    expect(payload[0].key).toBe(AppSettingKey.METADATA_PROVIDER_SETTINGS);
    return payload[0].newValue;
  }
});

function buildSettings(google: {enabled: boolean; apiKey: string}): AppSettings {
  return {
    metadataProviderSettings: {
      google: {
        ...google,
        language: '',
      },
    },
  } as AppSettings;
}
