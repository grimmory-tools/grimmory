import {ComponentFixture, TestBed} from '@angular/core/testing';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

import {getTranslocoModule} from '../../../../core/testing/transloco-testing';
import {AppSettingKey, type AppSettings, type MetadataPersistenceSettings} from '../../../../shared/model/app-settings.model';
import {AppSettingsService} from '../../../../shared/service/app-settings.service';
import {SettingsHelperService} from '../../../../shared/service/settings-helper.service';
import {MetadataPersistenceSettingsComponent} from './metadata-persistence-settings-component';

describe('MetadataPersistenceSettingsComponent', () => {
  let fixture: ComponentFixture<MetadataPersistenceSettingsComponent>;
  let component: MetadataPersistenceSettingsComponent;
  let appSettingsService: {appSettings: ReturnType<typeof vi.fn>};
  let settingsHelper: {saveSetting: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    appSettingsService = {
      appSettings: vi.fn(() => buildSettings()),
    };
    settingsHelper = {
      saveSetting: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MetadataPersistenceSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: appSettingsService},
        {provide: SettingsHelperService, useValue: settingsHelper},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MetadataPersistenceSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('hydrates persistence settings and network-storage state from app settings', () => {
    expect(component.isNetworkStorage).toBe(true);
    expect(component.metadataPersistence.moveFilesToLibraryPattern).toBe(true);
    expect(component.metadataPersistence.convertCbrCb7ToCbz).toBe(true);
    expect(component.metadataPersistence.saveToOriginalFile.epub).toEqual({
      enabled: true,
      maxFileSizeInMb: 123,
    });
    expect(component.metadataPersistence.saveToOriginalFile.pdf).toEqual({
      enabled: false,
      maxFileSizeInMb: 250,
    });
    expect(component.metadataPersistence.sidecarSettings).toEqual({
      enabled: true,
      writeOnUpdate: true,
      writeOnScan: false,
      includeCoverFile: false,
    });
  });

  it('toggles persistence flags and saves the updated settings', () => {
    component.onPersistenceToggle('convertCbrCb7ToCbz');

    expect(component.metadataPersistence.convertCbrCb7ToCbz).toBe(false);
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PERSISTENCE_SETTINGS,
      component.metadataPersistence
    );
  });

  it('toggles save-to-original-file settings and persists the current metadata settings', () => {
    component.onSaveToOriginalFileToggle('pdf');

    expect(component.metadataPersistence.saveToOriginalFile.pdf.enabled).toBe(true);
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PERSISTENCE_SETTINGS,
      component.metadataPersistence
    );
  });

  it('toggles sidecar settings and saves the updated state', () => {
    component.onSidecarToggle('includeCoverFile');

    expect(component.metadataPersistence.sidecarSettings?.includeCoverFile).toBe(true);
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PERSISTENCE_SETTINGS,
      component.metadataPersistence
    );
  });
});

function buildSettings(): AppSettings {
  const metadataPersistenceSettings: MetadataPersistenceSettings = {
    moveFilesToLibraryPattern: true,
    convertCbrCb7ToCbz: true,
    saveToOriginalFile: {
      epub: {enabled: true, maxFileSizeInMb: 123},
      pdf: {enabled: false, maxFileSizeInMb: 250},
      cbx: {enabled: true, maxFileSizeInMb: 64},
      audiobook: {enabled: false, maxFileSizeInMb: 1000},
    },
    sidecarSettings: {
      enabled: true,
      writeOnUpdate: true,
      writeOnScan: false,
      includeCoverFile: false,
    },
  };

  return {
    diskType: 'NETWORK',
    metadataPersistenceSettings,
  } as AppSettings;
}
