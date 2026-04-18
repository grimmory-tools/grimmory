import {signal, type WritableSignal} from '@angular/core';
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
  let appSettingsSignal: WritableSignal<AppSettings | null>;
  let settingsHelper: {saveSetting: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    appSettingsSignal = signal<AppSettings | null>(null);
    settingsHelper = {
      saveSetting: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [MetadataPersistenceSettingsComponent, getTranslocoModule()],
      providers: [
        {provide: AppSettingsService, useValue: {appSettings: appSettingsSignal}},
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

  it('hydrates persistence settings and network-storage state from app settings', async () => {
    appSettingsSignal.set(buildSettings());
    await render();

    expect(component.isNetworkStorage).toBe(true);
    expect(component.form.controls.saveToOriginalFile.disabled).toBe(true);
    expect(component.form.controls.moveFilesToLibraryPattern.disabled).toBe(true);
    expect(component.form.controls.sidecarSettings.disabled).toBe(true);
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

  it('hydrates when app settings arrive after the component is created', async () => {
    await render();

    expect(component.isNetworkStorage).toBe(false);
    expect(component.metadataPersistence.moveFilesToLibraryPattern).toBe(false);

    appSettingsSignal.set(buildSettings());
    await render();

    expect(component.isNetworkStorage).toBe(true);
    expect(component.metadataPersistence.moveFilesToLibraryPattern).toBe(true);
    expect(component.metadataPersistence.saveToOriginalFile.epub.maxFileSizeInMb).toBe(123);
  });

  it('re-enables network-sensitive controls when storage switches back to local', async () => {
    appSettingsSignal.set(buildSettings());
    await render();

    appSettingsSignal.set(buildSettings('LOCAL'));
    await render();

    expect(component.isNetworkStorage).toBe(false);
    expect(component.form.controls.saveToOriginalFile.enabled).toBe(true);
    expect(component.form.controls.moveFilesToLibraryPattern.enabled).toBe(true);
    expect(component.form.controls.sidecarSettings.enabled).toBe(true);
  });

  it('toggles persistence flags and saves the updated settings', async () => {
    appSettingsSignal.set(buildSettings());
    await render();

    component.onPersistenceToggle('convertCbrCb7ToCbz', false);

    expect(component.metadataPersistence.convertCbrCb7ToCbz).toBe(false);
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PERSISTENCE_SETTINGS,
      component.metadataPersistence
    );
  });

  it('toggles save-to-original-file settings and persists the current metadata settings', async () => {
    appSettingsSignal.set(buildSettings());
    await render();

    component.onSaveToOriginalFileToggle('pdf', true);

    expect(component.metadataPersistence.saveToOriginalFile.pdf.enabled).toBe(true);
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PERSISTENCE_SETTINGS,
      component.metadataPersistence
    );
  });

  it('toggles sidecar settings and saves the updated state', async () => {
    appSettingsSignal.set(buildSettings());
    await render();

    component.onSidecarToggle('includeCoverFile', true);

    expect(component.metadataPersistence.sidecarSettings?.includeCoverFile).toBe(true);
    expect(settingsHelper.saveSetting).toHaveBeenCalledWith(
      AppSettingKey.METADATA_PERSISTENCE_SETTINGS,
      component.metadataPersistence
    );
  });

  async function render(): Promise<void> {
    fixture.detectChanges();
    await new Promise(resolve => setTimeout(resolve, 0));
    await fixture.whenStable();
    fixture.detectChanges();
  }
});

function buildSettings(diskType: AppSettings['diskType'] = 'NETWORK'): AppSettings {
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
    diskType,
    metadataPersistenceSettings,
  } as AppSettings;
}
