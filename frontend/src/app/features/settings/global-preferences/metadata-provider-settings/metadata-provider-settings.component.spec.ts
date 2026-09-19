import {computed, signal, type WritableSignal} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {of} from 'rxjs';

import {MessageService} from '@openng/optimus-ui/api';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {type AppSettings, AppSettingKey} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {MetadataCatalogService} from '../../../../shared/metadata/metadata-catalog.service';
import {METADATA_PROVIDER_LIST} from '../../../../shared/metadata/metadata-providers';
import {MetadataSourceQueryService} from '../../../metadata/sources/metadata-source-query.service';
import {MetadataProviderSettingsComponent} from './metadata-provider-settings.component';

describe('MetadataProviderSettingsComponent', () => {
  let fixture: ComponentFixture<MetadataProviderSettingsComponent>;
  let component: MetadataProviderSettingsComponent;
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let saveSettings: ReturnType<typeof vi.fn>;
  let refreshProviders: ReturnType<typeof vi.fn>;
  let backendEnabled: WritableSignal<Partial<Record<string, boolean>>>;

  beforeEach(async () => {
    appSettingsSignal = signal<AppSettings | null>(null);
    saveSettings = vi.fn(() => of(void 0));
    refreshProviders = vi.fn(() => Promise.resolve());
    backendEnabled = signal<Partial<Record<string, boolean>>>({});

    await TestBed.configureTestingModule({
      imports: [MetadataProviderSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: MetadataCatalogService, useValue: {providers: () => METADATA_PROVIDER_LIST}},
        {
          provide: MetadataSourceQueryService,
          useValue: {
            providers: computed(() => METADATA_PROVIDER_LIST.map(provider => ({
              ...provider,
              enabled: backendEnabled()[provider.id] ?? false,
            }))),
            refreshProviders,
          },
        },
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

  it('persists Google Books as enabled when an API key is configured', () => {
    component.enabled.Google = true;
    component.googleApiKey = '  configured-key  ';

    component.saveSettings();

    const providerSettings = getSavedProviderSettings();
    expect(providerSettings.google).toEqual({
      enabled: true,
      language: '',
      apiKey: 'configured-key',
    });
  });

  it('persists the toggle as set, leaving the backend to decide whether it is usable', () => {
    component.enabled.Google = true;
    component.googleApiKey = '';

    component.saveSettings();

    expect(getSavedProviderSettings().google.enabled).toBe(true);
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
